---
name: incident-handoff
description: Prepare a concise engineering incident handoff from supplied facts. Use when transferring an unresolved or recently mitigated incident between engineers, teams, or shifts, and the receiver needs the current state without re-reading the whole timeline.
---

# Incident handoff

Produce a handoff document from the supplied incident facts. The receiver must
be able to continue the investigation without asking the author anything.

## Procedure

1. Separate observed facts from hypotheses. Label each section explicitly.
2. State the customer or user impact in one or two sentences.
3. Cite the evidence that was supplied: metrics, alerts, timestamps, and
   deploy or rollback events.
4. List the questions that remain unresolved.
5. List the next actions, each with its owner when an owner was provided.
6. Never invent a root cause, a metric, a timestamp, or an owner. If a piece
   of information is missing, write "not established" instead of guessing.

## Output structure

Use exactly these sections, in this order:

- **Summary** — two sentences at most.
- **Impact** — who or what is affected, and how badly.
- **Observed facts** — bullet list, timestamps included where supplied.
- **Hypotheses** — bullet list, clearly marked as unproven.
- **Open questions** — bullet list.
- **Next actions** — bullet list with owners.

## Edge cases

- If the incident is already mitigated, say so in the summary and keep the
  open questions section: mitigation is not root cause.
- If the facts conflict, list both versions under observed facts and add the
  conflict to open questions.
- If there are no known next actions, state that explicitly rather than
  omitting the section.
