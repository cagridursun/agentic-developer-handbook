---
name: incident-handoff
description: Prepare a concise engineering incident handoff from supplied facts and observations. Use when transferring an unresolved or recently mitigated incident between engineers or teams, and the receiver needs the current state without re-reading the whole timeline.
---

# Incident handoff

Produce a handoff document from the supplied context and observations. The
receiver must be able to continue the investigation without asking the author
anything.

## Procedure

1. Separate observed facts from hypotheses. Label each section explicitly.
2. State the customer or user impact in one or two sentences.
3. Cite the evidence available: metrics, alerts, timestamps, deployments.
4. List the questions that remain unknown.
5. List the next actions, each with its owner when an owner is known.
6. Never state an unproven root cause as fact. If a piece of information is
   missing, write "not established" instead of guessing.

## Output structure

Use exactly these sections, in this order:

- **Summary** — two sentences at most.
- **Impact** — who or what is affected, and how badly.
- **Observed facts** — bullet list, timestamps included where available.
- **Hypotheses** — bullet list, clearly marked as unproven.
- **Unknowns** — bullet list.
- **Next actions** — bullet list with owners where known.
