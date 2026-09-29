# Worked example — Customer-facing release notes

A filled-in [ASSESSMENT.md](../ASSESSMENT.md). One defensible reading of the
scenario, not an answer key.

**Lesson: LLM use does not imply agent use.**

## Use case

### Problem statement

Engineering writes internal release notes: terse, full of ticket numbers,
service names, and jargon. Before each release, someone rewrites them by hand
into concise customer-facing language, and it is slow and inconsistent.

### User / consumer

The product manager who publishes the release announcement. A human reads and
edits the draft before anything is published.

### Desired outcome

Given the internal notes for one release, a customer-facing draft: plain
language, no internal identifiers, grouped into improvements and fixes.

## Simpler alternative

### Can deterministic code solve this? (question 0)

**No.** Stripping ticket numbers with a regex is deterministic, and worth
doing first — but turning "BILL-2231: idempotency key on retry path for
webhook dispatcher" into "Payment notifications are no longer sent twice when
a retry happens" is a rewriting task. No template covers the variety.

### Existing API, database, or workflow alternative

A hand-maintained glossary of internal terms, plus manual rewriting. It works
and is the baseline this must beat.

### Why an LLM is or is not justified (question 1)

**Justified.** The task is language generation: rewrite for a different
audience while preserving meaning. That is what a model is for.

## Capability decisions

| # | Question | Answer |
| --- | --- | --- |
| 2 | Does downstream application code need to consume the model output? | No — a person edits the draft. |
| 3 | Does the system need current or authoritative data or actions? | No — the input notes are the whole source. |
| 4 | Does the model need unstructured information outside the interaction? | No — see the note below. |
| 5 | Must information from earlier interactions survive into later ones? | No — each release is independent. |
| 6 | Is there a reusable model-performed procedure worth reviewing independently? | Unsure — the style rules repeat every release. |
| 7 | Is the next useful action unknown in advance and dependent on observations? | No — the sequence is one rewrite. |
| 8 | Does the system need a standardized boundary for externally owned capabilities, one that compatible clients can discover and invoke, where an explicit API integration is not enough? | No. |

| Capability | Consider? | Why / why not? | Simpler alternative |
| --- | --- | --- | --- |
| Model | CONSIDER | Rewriting for a new audience is generation. | Manual rewriting — the baseline. |
| Structured Output | NOT JUSTIFIED | A human reads and edits prose; no code parses fields. It becomes CONSIDER only if a publishing pipeline must consume, say, `{improvements[], fixes[]}`. | Markdown the editor pastes. |
| Tools | NOT JUSTIFIED | Everything needed is in the input notes. | Paste the notes into the prompt. |
| Knowledge / RAG | NOT JUSTIFIED | The notes are the source. If a large product glossary were needed, retrieval might become a candidate; a short glossary fits in the prompt. | A short glossary in the prompt. |
| Memory | NOT JUSTIFIED | Each release is independent. Last release's wording is not state this one needs. | None needed. |
| Skills | UNDECIDED | The style rules ("no ticket ids, active voice, group by customer impact") are a reusable procedure. If more than one tool or team produces customer-facing text, one reviewed `SKILL.md` beats copies drifting through prompts. For one script, a prompt constant is enough. | A reviewed prompt constant in the code. |
| Agent Runtime | NOT JUSTIFIED | The sequence is known: one call, one draft. There is no next step to choose. | A single model call. |
| MCP (protocol boundary) | NOT JUSTIFIED | Nothing crosses a process boundary. | — |

## Decision authority

- **Model may influence:** the wording, grouping, and tone of the draft.
- **Binding decisions:** what gets published — the product manager decides.
- **Deterministic / application-controlled:** removing ticket ids and internal
  hostnames before the prompt is built; the list of releases the tool may read.
- **Authoritative data:** the internal release notes themselves. The draft must
  not add claims that are not in them.
- **Side effects:** none — the output is a draft.
- **Authorization:** only people who can see the internal notes can run it.
- **Human approval:** always, before publication. The model never publishes.

## Runtime boundary

### What stops the run?

The single model call returns.

### What time, token, or cost budget exists?

One call per release, a few thousand tokens. Trivial — but set a maximum input
size so a pasted changelog of a whole year does not become an expensive
surprise.

### If not using one

The sequence is fixed and short: sanitize → one model call → human edit →
publish. Nothing in it depends on an observation the application cannot
anticipate.

## Production questions for later

- **Evaluation:** a handful of past releases with human-approved rewrites as
  fixed examples; check that no internal identifier leaks into the draft.
- **Observable:** input size, token cost, how much the editor changes the draft.
- **Trust boundaries:** the internal notes may contain customer names or
  security fixes that must not be published verbatim — sanitize before the
  prompt, review after.

## Final architecture shape

```
Internal release notes
→ deterministic sanitizing (ticket ids, hostnames)
→ one model call with a reviewed style prompt
→ human edit and approval
→ publish
```

## Rejected complexity

Structured Output (no program consumes it), Tools, Knowledge / RAG, Memory,
Agent Runtime, MCP. A model is justified; an agent is not.

## Unresolved questions

Skills — decide whether the style rules are shared by more than one producer
of customer-facing text.
