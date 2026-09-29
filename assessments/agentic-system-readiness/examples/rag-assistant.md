# Worked example — Technical manuals assistant

A filled-in [ASSESSMENT.md](../ASSESSMENT.md). One defensible reading of the
scenario, not an answer key.

**Lesson: a RAG assistant is not an agent by default.**

## Use case

### Problem statement

Field engineers need answers from a few hundred pages of internal technical
manuals — installation, calibration, troubleshooting. Full-text search returns
pages, not answers, and the relevant guidance is often split across sections.

### User / consumer

A field engineer asking a question in plain language, reading the answer on a
laptop or tablet.

### Desired outcome

A short answer grounded in the manuals, with the sections it came from, so the
engineer can check the source before acting.

## Simpler alternative

### Can deterministic code solve this? (question 0)

**Partly — and that part should stay deterministic.** Keyword search already
finds candidate sections, and it remains the fallback. It cannot synthesize an
answer from two sections that use different wording, which is the actual
complaint.

### Existing API, database, or workflow alternative

The manuals' existing full-text search, and a table of contents.

### Why an LLM is or is not justified (question 1)

**Justified.** Understanding a free-text question and synthesizing an answer
from several passages is language understanding and generation.

## Capability decisions

| # | Question | Answer |
| --- | --- | --- |
| 2 | Does downstream application code need to consume the model output? | No — an engineer reads it. |
| 3 | Does the system need current or authoritative data or actions? | No — the manuals are the source; no live system is involved. |
| 4 | Does the model need unstructured information outside the interaction? | **Yes** — the manuals. |
| 5 | Must information from earlier interactions survive into later ones? | No — each question stands alone. |
| 6 | Is there a reusable model-performed procedure worth reviewing independently? | No — "answer from the passages, cite them" is one line of prompt. |
| 7 | Is the next useful action unknown in advance and dependent on observations? | No — retrieve, then answer. |
| 8 | Does the system need a standardized boundary for externally owned capabilities, one that compatible clients can discover and invoke, where an explicit API integration is not enough? | No. |

| Capability | Consider? | Why / why not? | Simpler alternative |
| --- | --- | --- | --- |
| Model | CONSIDER | Synthesizing an answer from several passages. | Full-text search alone — keep it as the fallback. |
| Structured Output | NOT JUSTIFIED | A person reads the answer. It becomes CONSIDER only if another program needs `{answer, citations[]}` as fields. | Prose with a sources list. |
| Tools | NOT JUSTIFIED | No live system data or actions. If the assistant later had to read an instrument's current firmware version, that would be a tool — and a separate decision. | — |
| Knowledge / RAG | CONSIDER | The manuals are unstructured documents the model has never seen; retrieval selects the passages. Start with the existing keyword search as the retriever; add embeddings only if keyword retrieval measurably misses. | Always sending whole chapters — too large, and teaches nothing about selection. |
| Memory | NOT JUSTIFIED | Questions are independent. Keeping the last few turns in a conversation is conversation history, not memory; add it only if follow-up questions turn out to matter. | None. |
| Skills | NOT JUSTIFIED | The answering procedure is short and does not vary by task. | A prompt constant. |
| Agent Runtime | NOT JUSTIFIED | The sequence is known: retrieve, then answer. Retrieval being involved does not make the next step unknown. | A fixed two-step flow. |
| MCP (protocol boundary) | NOT JUSTIFIED | The manuals and the search index are local to the application. | — |

## Decision authority

- **Model may influence:** which retrieved passages it uses, and the wording of
  the answer.
- **Binding decisions:** none — the output is advice. The engineer decides what
  to do on site.
- **Deterministic / application-controlled:** which manuals are indexed, how
  many passages are retrieved, and which manuals a user may see.
- **Authoritative data:** the manuals. The answer must cite them; when the
  passages do not contain an answer, the assistant says so instead of guessing.
- **Side effects:** none.
- **Authorization:** manual access follows the engineer's existing permissions,
  applied at retrieval time — never by asking the model to withhold content.
- **Human approval:** not needed for reading; the engineer remains responsible
  for acting on the answer.

## Runtime boundary

### What stops the run?

The single model call returns.

### What time, token, or cost budget exists?

A fixed number of retrieved passages per question keeps each call bounded.

### If not using one

Retrieve → one model call. Both steps are known before the question arrives.

## Production questions for later

- **Evaluation:** a fixed set of real engineer questions with the sections that
  answer them; check retrieval hits the right section and the answer cites it.
- **Observable:** the retrieved sections per question, "no answer found" rate,
  latency, token cost.
- **Trust boundaries:** retrieved manual text is input to the prompt and should
  be treated as data, not instructions; permission filtering happens before
  retrieval results reach the model.

## Final architecture shape

```
Question
→ retrieval over the manuals (permission-filtered)
→ one model call: answer from the passages, cite them
→ answer + sources
```

## Rejected complexity

Structured Output, Tools, Memory, Skills, Agent Runtime, MCP, and — for now —
a vector database. RAG is a pattern, not a datastore.

## Unresolved questions

Whether keyword retrieval is good enough; decide with the evaluation set, not
by default.
