# Lab 06 — Skills

**A tool answers what the application can do.**

**A skill answers how a task should be performed.**

**A skill is reusable procedure, not a capability.**

This is still not an agent — a SKILL.md file does not make a program an agent.

## The limitation we found

Labs 01–05 gave us generation, structured output, tools, knowledge, and memory. But whenever we want the model to perform the same *class* of task consistently — prepare an incident handoff, review a rollout plan, write a release note — the procedural instructions live inside individual prompts, and get copied between them. Wording changes. Steps disappear. Teams end up running different versions of the same procedure, and improving it means finding every copy.

## Learning goals

- See why one-off prompts and copied procedures drift
- Package a reusable procedure as a standards-aligned `SKILL.md`
- Distinguish a skill from a tool, from knowledge, from memory, from a system prompt — and honestly, from a prompt
- Understand progressive disclosure: metadata → instructions → resources
- Understand why skill text is untrusted input
- Decide when a procedure deserves to become a skill at all

## Starting point

One fictional scenario: an **engineering incident handoff**. The facts are deterministic and invented — checkout latency spiked at 14:05 UTC, an alert fired, a deploy preceded it, a rollback mitigated it, root cause unproven, one follow-up open. Everything below is a scripted demonstration in `SkillsExample.main`; it runs fully without an API key.

## Experience 1 — The vague one-off prompt

```
Write an incident handoff from these facts.

Facts:
- Checkout latency p99 rose from 310 ms to 2.1 s starting 14:05 UTC.
...
```

The task gets done — somehow. The application supplied facts and a vague instruction, so the model must invent the structure, the boundary between fact and hypothesis, and the standard for what not to make up. Whatever it invents will differ next time, and differ again for the next engineer.

## Naive fix — Copy the procedure everywhere

The obvious improvement: write the procedure into the prompt.

```
When preparing an incident handoff:

1. Separate observed facts from hypotheses.
2. State the customer impact.
3. Cite the evidence provided.
4. List unresolved questions.
5. Give next actions and owners.
6. Do not invent a root cause.
```

Better instructions — and a new maintenance problem, because this block now gets copied into every prompt that needs it.

## Break it — Procedure drift

The demo carries two copies of "the same" procedure, the way real codebases do:

```
Procedure copy A rules: 6
Procedure copy B rules: 5
Missing from B:         Separate observed facts from hypotheses.
```

Both prompts intend the same task; they now encode different procedures. This is shown deterministically by comparing the instruction blocks — no model output is involved. And the deeper problems aren't token counts: duplication, inconsistency, unreviewable sprawl, and version drift. Fixing the procedure means fixing every copy, forever.

## Build — Extract the procedure into a Skill

The procedure moves into one canonical, version-controlled file:

```
skills/
├── incident-handoff/
│   └── SKILL.md
└── release-summary/
    └── SKILL.md
```

This follows the official [Agent Skills](https://agentskills.io/) format: a skill is a *directory* named after the skill containing a `SKILL.md`. The directory shape matters because the standard also allows `scripts/`, `references/`, and `assets/` — this lab deliberately uses none of them.

The application selects and loads the skill explicitly:

```java
Skill skill = SkillLoader.load("incident-handoff");
```

The model does not choose the skill in this lab. Model-driven skill selection needs a runtime: [Lab 07](../07-agent-runtime/README.md) builds that runtime, but intentionally focuses on the execution loop alone — the runtime is the architectural place where dynamic skill selection could later live, and one concept per canonical lab remains the rule.

## Anatomy of SKILL.md

```markdown
---
name: incident-handoff
description: Prepare a concise engineering incident handoff from supplied
  facts. Use when transferring an unresolved or recently mitigated incident...
---

# Incident handoff

## Procedure
1. Separate observed facts from hypotheses. ...
```

Per the [specification](https://agentskills.io/specification): `name` is required (max 64 chars, lowercase letters/numbers/hyphens, no edge or consecutive hyphens, **must match the directory name**) and `description` is required (1–1024 chars, saying both *what* the skill does and *when* to use it — that's what selection reads). Optional fields exist (`license`, `compatibility`, `metadata`, `allowed-tools`), and the body is free-form Markdown.

One honest boundary: the `SkillLoader` in this lab parses only the subset it needs and validates the naming rules — it is **not** a full specification validator and does not parse general YAML. The official project ships reference tooling (`skills-ref validate`) for real validation; it is not required by this repository's build or CI.

## Progressive disclosure

The Agent Skills model loads skills in stages:

```
Available skills → metadata (name + description, ~100 tokens each)
                 → relevant skill selected
                 → full SKILL.md instructions loaded
                 → optional resources loaded only if needed
```

The demo shows the first three stages: it lists metadata for both bundled skills, selects `incident-handoff`, and loads *only its* instruction body — `release-summary`'s body never enters the final prompt (there is a test proving it). Loading every skill body into every prompt would defeat part of the design: the description exists precisely so selection can happen cheaply.

## Run it

Works fully without an API key:

```sh
./mvnw -pl labs/06-skills compile exec:java
```

Windows (PowerShell):

```powershell
.\mvnw.cmd -pl labs/06-skills compile exec:java
```

With `GOOGLE_API_KEY` set (from [Google AI Studio](https://aistudio.google.com/apikey)), the final skill-backed prompt is additionally sent to Gemini once and the handoff it produces is printed. `GEMINI_MODEL` overrides the default (`gemini-3.8-flash`).

## What changed architecturally?

Before: every task prompt = task input + a *copied* procedure, one copy per call site, each free to drift.

After: task input + a *selected* reusable skill, loaded from one canonical file that can be reviewed, diffed, and improved centrally. Git history is the version history — no registry, no version server.

The proof of this lab is deterministic: it's the difference between the prompts the application builds, not the difference between model outputs. Whether the skill *improves the model's answers* is an evaluation question, and Evaluation is Milestone 9.

## Skill vs Tool

A tool answers: *what can the application do?* — `getServiceStatus()`, `sendEmail()`, `queryDatabase()`. Executable capability, owned and run by the application (Lab 03).

A skill answers: *how should this kind of task be performed?* — how to prepare a handoff, how to review a PR, how to write a release note. Procedure, expressed as instructions.

The critical example: a `sendEmail` tool gives the application the capability to send email. An `incident-handoff` skill explains how to prepare the handoff — it does not, and cannot, add `sendEmail`. If the system lacks a capability, no skill text can create it.

## Skill vs Prompt

Be honest here: a skill ultimately influences the model the same way a prompt does — it becomes instructions in context. There is no different physical mechanism, and no mysticism.

The difference is engineering, not physics. A one-off prompt is a task-specific instruction embedded in a single request. A skill is a *named, reusable, version-controlled* procedure packaged separately, with metadata describing what it does and when it applies. That buys reuse, reviewability, versioning, consistency, portability across hosts, and progressive loading. Underneath, the model still just receives text.

## Skill vs Knowledge / RAG

Knowledge (Lab 04) asks *what information do I need?* — "production rollout uses a 5% canary." A skill asks *how should I perform the task?* — "when reviewing a rollout plan, verify blast radius, rollback path, health signals, approvals." A skill may reference knowledge; that does not merge the concepts. This lab retrieves nothing.

## Skill vs Memory

Memory (Lab 05) asks *what state from earlier interactions should persist?* — `preferredProgrammingLanguage = Java`. A skill asks *what reusable procedure applies?* A skill is not per-user mutable state, and this lab never rewrites SKILL.md from conversation state.

## Skill vs System Instructions

System-level instructions typically define global, always-on behavior: role, safety, broad rules. A skill is task-specific and loaded when relevant. The distinction is an application/runtime design choice about *what loads when* — nothing in the model enforces it.

## A Skill does not execute code

The standard allows a `scripts/` directory, but a skill never runs anything by itself: any execution is performed by the host application or tooling, under its own trust and execution policy. This lab bundles no scripts. Skill text is untrusted input — a third-party skill could contain instructions to expose credentials, ignore application rules, or bypass authorization. The application's policy stays authoritative, credentials never live in skills, and instruction injection via third-party skills is a real production concern. This is [ADR 0004](../../docs/adr/0004-skills-are-a-building-block.md) in practice.

## Reflection

1. What capability did the incident-handoff skill add to the application? *(Expected insight: none. That is the point.)*
2. What changed compared with copying the instructions into each prompt?
3. Who selected the skill in this lab?
4. Why do we not let the model choose skills yet?
5. What risks appear if a third-party skill contains malicious instructions?
6. If a procedure is only used once, does it need to become a skill?
7. Would a reusable Java method be better than a skill if the procedure can be implemented deterministically?

Short guidance: a skill changes procedure, never capability (1); duplication became one reviewable file (2); the application selected it — selection policy is runtime work (3–4); untrusted instructions must never outrank application policy (5); one-off procedures belong in the prompt (6); and if code can do the task deterministically, code beats instructions to a model (7).

## Do I actually need a Skill?

```
Is this a reusable procedure for a class of model-performed tasks?
├── No  → Keep it local to the prompt.
└── Yes → Can the task be implemented deterministically in code instead?
    ├── Yes → Prefer code.
    └── No  → Does externalizing it improve reuse, reviewability, consistency?
        ├── No  → Keep the prompt simple.
        └── Yes → A skill may be justified.
```

If a skill is justified, five questions remain: Who owns it? Who reviews it? When should it load? What capabilities does it rely on? What must it never be allowed to override?

You probably don't need all of these — and most prompts do not need to become skills.

## What we STILL do not have

- No skill marketplace, remote registry, or installer
- No automatic skill selection or model-driven skill discovery loop
- No script execution from skills
- No agent runtime
- No MCP
- No autonomous loop
- No multi-agent system

## Production considerations

Discussed, not implemented: skill trust and provenance, review and approval workflows, versioning beyond git history, discovery and activation rules, conflicts between skills and instruction precedence, resource loading, script sandboxing, capability requirements, skill evaluation, compatibility across agent hosts, stale skills, organization-specific procedures, and third-party supply-chain risk.

## What limitation remains?

We now have a model, structured output, tools, knowledge, memory, and skills. But every lab still manually decides when the model is called, when a capability is used, when another step happens, and when execution stops. There is no generalized execution loop with stopping rules. That is **Milestone 7 — Agent Runtime**, and it is not implemented here.

## Sources consulted

Verified on 2026-09-24 against official documentation:

- [Agent Skills specification](https://agentskills.io/specification) — directory shape, SKILL.md frontmatter (`name`, `description` required; naming constraints; description limits), optional fields and directories, progressive disclosure guidance, and the `skills-ref validate` reference tooling.
- [agentskills.io](https://agentskills.io/) — the format's home; this lab follows it rather than any competing SKILL.md convention.
- [googleapis/java-genai README](https://github.com/googleapis/java-genai) and [Maven Central](https://central.sonatype.com/artifact/com.google.genai/google-genai) — the same SDK (1.72.0) and `generateContent` usage as Labs 01–05, kept consistent across the canonical path.
