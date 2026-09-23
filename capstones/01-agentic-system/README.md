# Capstone 01 — Build a Small Agentic System

This capstone teaches no new concept. It asks you to compose the concepts you already have — and, just as deliberately, to leave some of them out.

**Use the smallest set of capabilities that solves the problem.**

## The challenge

An engineer reports:

> "The notifications service is slow after a release. Investigate what is currently known and prepare a concise engineering handoff. Do not claim a root cause without evidence."

Everything is fictional (the invented Helio platform), deterministic, and local. Your system should investigate what the fictional platform knows and produce a handoff that separates evidence from hypothesis.

## What you already know

[Lab 01 — Model](../../labs/01-model-call/README.md) · [Lab 02 — Structured Output](../../labs/02-structured-output/README.md) · [Lab 03 — Tools](../../labs/03-tool-calling/README.md) · [Lab 04 — Knowledge / RAG](../../labs/04-rag/README.md) · [Lab 05 — Memory](../../labs/05-memory/README.md) · [Lab 06 — Skills](../../labs/06-skills/README.md) · [Lab 07 — Agent Runtime](../../labs/07-agent-runtime/README.md)

## Available building blocks

You **may** use: structured output, tools, knowledge / RAG, memory, skills, an agent runtime. Available assets in `starter/`:

- **Service fixtures** (a Tools decision): read-only status and deployment data for `notifications`, `billing`, `search`
- **Runbooks** (a Knowledge decision): `notifications.md`, `deployment.md`, `incident-response.md`
- **One skill** (a Skills decision): `skills/incident-handoff/SKILL.md`

Available does not mean required. MCP is not available: it has not been taught yet (Milestone 8) — and notice that you will not miss it, because every capability here is local Java.

## Your first task: decide before coding

Fill out [starter/DECISIONS.md](starter/DECISIONS.md) completely before writing implementation code. Every capability gets a yes or a no, a reason, and a simpler alternative you considered. The decision table is the real deliverable; the code demonstrates it.

## Functional goal

The application investigates the fictional incident and produces a concise handoff that does not invent a root cause. Facts must come from the authoritative fixtures; runbook guidance may shape the investigation and the handoff; anything unproven is labeled hypothesis.

## Constraints

- Plain Java 21 — no agent framework, no Spring AI, no LangChain4j
- No external database, no MCP
- No network and no API key in the deterministic path (`./mvnw verify` stays self-contained)
- Read-only tools only
- If you build an agent runtime, it must be bounded with explicit stopping rules
- Fictional data only

## Starter

```sh
./mvnw -pl capstones/01-agentic-system/starter compile exec:java
```

prints the challenge, the available assets, and the reminder to fill `DECISIONS.md`. The starter compiles and its scaffolding is tested; the architecture wiring is your work, marked by the TODO in `StarterMain`. Nothing in the starter forces the reference design.

## Run / verify

```sh
./mvnw verify                                               # whole repository, no key
./mvnw -pl capstones/01-agentic-system/starter test         # scaffolding tests
./mvnw -pl capstones/01-agentic-system/reference test       # reference behavior tests
```

## Success criteria

Expected behavior, not one required architecture:

- The handoff separates observed facts, hypotheses, unknowns, and next actions
- Current facts come from the fixtures, not from model recall
- No unproven root cause is stated as fact
- If a loop exists, it is bounded and its stop conditions are testable
- The deterministic build passes with no key and no network
- Your `DECISIONS.md` can defend every inclusion and every exclusion

## Before opening the reference

Finish `DECISIONS.md` and implement a working design first. The comparison is only useful after you have made your own trade-offs.

## Reference implementation

[reference/](reference/) is **one possible architecture, not the answer key**. It uses model, tools, knowledge, skill, and a bounded agent runtime — and deliberately excludes structured output, memory, and MCP. Its reasoning, including a fixed-workflow alternative it takes seriously, is in [reference/DECISIONS.md](reference/DECISIONS.md).

Deterministic run (scripted model, no key, no network):

```sh
./mvnw -pl capstones/01-agentic-system/reference compile exec:java
```

Explicit live run (Gemini decides the steps, same bounded runtime, requires `GOOGLE_API_KEY`):

```sh
./mvnw -pl capstones/01-agentic-system/reference compile exec:java -Dexec.args="--live"
```

A note on what the tests prove: they prove application behavior — retrieval selection, tool order, validation, stopping. They do **not** prove "this agent is good." Systematic behavioral evaluation is Milestone 9, and this capstone does not pretend to be it.

## Reflection

1. Which capability did you initially want to use but eventually reject?
2. Could this entire application be a fixed workflow instead of an agent? Under what conditions would that be better?
3. Why is service status a Tool result rather than Memory?
4. Why are runbooks Knowledge rather than Skills?
5. Why is the incident-handoff procedure a Skill rather than Knowledge?
6. What information, if any, would justify adding Memory?
7. Would MCP make anything better while all tools are local?
8. Which part of the reference solution has the highest operational risk?
9. What would happen if `getRecentDeployment` modified production instead of reading it?
10. Which capability would you remove first if simplicity became the primary goal?

## Completion checklist

- [ ] I filled DECISIONS.md before coding.
- [ ] I can explain why every included capability is needed.
- [ ] I can explain why every excluded capability is unnecessary.
- [ ] My runtime is bounded if I use one.
- [ ] The model cannot execute arbitrary code.
- [ ] Current facts come from authoritative tools.
- [ ] Documentation and procedural instructions are not confused.
- [ ] I do not claim unproven root cause.
- [ ] Tests do not require an LLM.
- [ ] I can explain a simpler alternative architecture.
