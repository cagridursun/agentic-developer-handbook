# Agentic System Readiness Assessment

**What is the smallest AI/agentic architecture this problem actually needs?**

This assessment is a reusable architecture decision framework. You bring a use case; it walks you through a fixed order of questions, from the simplest possible answer toward more complex ones, and asks you to justify every step you take.

It is willing to conclude any of these, and often should:

- "You probably do not need an LLM."
- "You need an LLM, but not an agent."
- "A deterministic workflow is simpler than an Agent Runtime."

It is **not** a maturity model, an AI readiness score, a quiz with right answers, or a tool that designs systems for you. There are no points. A use case that ends with "ordinary software" has not failed the assessment — it has passed the most important question in it.

## How to use it

1. Copy [ASSESSMENT.md](ASSESSMENT.md) next to your design notes and fill it in, top to bottom.
2. Answer the decision questions **in order**. An early answer changes what the later questions mean — a "yes" to question 0 ends the AI part of the conversation.
3. Write the decision-authority section even if you conclude that no agent is needed. Every model-backed feature has an authority boundary.
4. Compare with the [worked examples](#worked-examples). They are illustrations, not answer keys.

Prefer to click through it? The local interactive site has a guided version that exports the same `ASSESSMENT.md` — see [site/README.md](../../site/README.md).

## The decision order

Each question can be answered **yes**, **no**, or **unsure**. The order is the point: deterministic software is considered first, and every later capability has to be earned by an earlier answer.

| # | Question | If yes | If no |
| --- | --- | --- | --- |
| 0 | Can deterministic software solve this problem adequately? | **Start with deterministic software.** Stop here; no AI capability is justified. | Continue. |
| 1 | Does the task require probabilistic language understanding, reasoning, or generation? | Model: CONSIDER. | **Prefer deterministic software.** No model, so nothing below is justified. |
| 2 | Does downstream application code need to consume the model output? | Structured Output: CONSIDER. | Structured Output: NOT JUSTIFIED — a human reads prose. |
| 3 | Does the system need current or authoritative application/system data, or actions? | Tools: CONSIDER. | Tools: NOT JUSTIFIED. |
| 4 | Does the model need unstructured information that exists outside the current interaction? | Knowledge / RAG: CONSIDER. | Knowledge / RAG: NOT JUSTIFIED. |
| 5 | Must information created by earlier interactions survive into later interactions? | Memory: CONSIDER. | Memory: NOT JUSTIFIED. |
| 6 | Is there a reusable, model-performed procedure that should be reviewed and reused independently of individual prompts? | Skills: CONSIDER. | Skills: NOT JUSTIFIED. |
| 7 | Is the next useful action genuinely unknown in advance, and dependent on model interpretation of observations? | Agent Runtime: CONSIDER. | **Prefer a fixed workflow.** Agent Runtime: NOT JUSTIFIED. |
| 8 | Are capabilities outside the process, or shared across hosts or systems? | A protocol boundary: FUTURE CONSIDERATION. | Protocol boundary: NOT JUSTIFIED. |

Question 3 is about authority, not convenience: status, balances, permissions, deployments, orders — facts that must come from the system that owns them, never from model recall. Question 4 is about documents: manuals, runbooks, policies — text the model should read, not facts the system owns. A database lookup does not become RAG because the application also calls a model.

Question 7 is the one most often answered "yes" too quickly. If you can write the sequence of steps down in advance — "look up the status, then the deployment, then summarize" — the answer is **no**, and a fixed workflow is simpler, cheaper, and easier to test than an agent runtime. The answer is yes only when *which* step comes next depends on what the previous step observed, and the application cannot reasonably enumerate the branches itself.

## Status vocabulary

The assessment never grades. Every capability gets one of four neutral statuses:

| Status | Meaning |
| --- | --- |
| **CONSIDER** | An answer above gives this capability a reason to exist. It is a candidate, not a mandate — you may still reject it for a simpler design. |
| **NOT JUSTIFIED** | Nothing in the answers requires it. Leaving it out is a decision, and it belongs in "Rejected complexity". |
| **UNDECIDED** | The question is unanswered or answered "unsure". Resolve it before building. |
| **FUTURE CONSIDERATION** | Only for the protocol boundary (MCP): worth evaluating later, never an automatic recommendation. |

## How the summary is derived

The interactive version derives a capability summary from your answers with these rules — simple, deterministic, and printed here so you can check them:

1. **Model** is NOT JUSTIFIED if question 0 is *yes* or question 1 is *no*; CONSIDER if question 0 is *no* and question 1 is *yes*; otherwise UNDECIDED.
2. **Structured Output, Tools, Knowledge / RAG, Memory, Skills, Agent Runtime** each follow their own question: *yes* → CONSIDER, *no* → NOT JUSTIFIED, *unsure* or unanswered → UNDECIDED. Then:
   - if Model is NOT JUSTIFIED, they are all NOT JUSTIFIED (none of them means anything without a model);
   - if Model is UNDECIDED, a CONSIDER becomes UNDECIDED.
3. **Agent Runtime** is additionally UNDECIDED when question 7 is *yes* but Tools is not CONSIDER: a runtime chooses among allowed actions, and without actions there is nothing to choose.
4. **Protocol boundary (MCP)** is NOT JUSTIFIED when Model is NOT JUSTIFIED (a remote capability behind ordinary software is just an API call) or question 8 is *no*; FUTURE CONSIDERATION when question 8 is *yes*; otherwise UNDECIDED. It is never CONSIDER.

No model is called to perform the assessment. The rules produce candidates; the architecture is still your decision, and the result never claims to be "the correct architecture".

## Decision authority is part of the assessment

Choosing capabilities is half the design. The other half is deciding what the model's output is *allowed to do*. The assessment asks, for every use case that keeps a model:

- What decisions may the model influence?
- What decisions become binding on the system?
- What remains deterministic or application-controlled?
- What data is authoritative?
- What actions create side effects?
- What requires authorization?
- What requires human approval?
- What stops the run?
- What time, token, or cost budget exists?

The concept behind these questions is [LLM vs Decision Authority](../../docs/model-vs-decision-authority.md): the model reasons, generates, recommends, and proposes; the application owns the allowed actions, validation, authorization, execution, budgets, and stopping. An architecture that keeps a model but cannot answer these questions is not ready, whatever its capability table says.

## Worked examples

| Example | Where it lands | Lesson |
| --- | --- | --- |
| [Order status lookup](examples/deterministic-lookup.md) | Deterministic software | Not every product feature needs AI. |
| [Customer-facing release notes](examples/model-only-task.md) | One model call | LLM use does not imply agent use. |
| [Technical manuals assistant](examples/rag-assistant.md) | Model + Knowledge / RAG | A RAG assistant is not an agent by default. |
| [Checkout latency investigation](examples/incident-investigator.md) | Bounded agent runtime | An agent earns its place only when the next step depends on observations. |

The examples are one defensible reading of each scenario. A different, well-argued conclusion is a valid outcome of the assessment.

## Where MCP enters — later

Question 8 prepares a question this assessment deliberately does not answer yet.

If the same capability must be exposed across process or application boundaries — shared by several hosts, owned by another team, reached from more than one application — a standardized protocol may become useful. That is where MCP may enter an architecture.

But a remote capability does not automatically require MCP. A normal API and a small client may remain the simpler answer, and a local Java method never needs it. MCP is taught in Milestone 8; until then, this assessment records a protocol boundary only as a **future consideration** to evaluate, never as a recommendation.

## Production questions, asked early

The template ends with questions for later milestones — how behavior will be evaluated, what must be observable, which trust and security boundaries exist. The assessment asks them so the design leaves room for the answers. It does not implement evaluation, observability, or security; those are Milestones 9–11.
