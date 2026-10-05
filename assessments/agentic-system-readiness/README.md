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
| 8 | Does the system need a standardized boundary for externally owned capabilities, one that compatible clients can discover and invoke, where an explicit API integration is not enough? | MCP: CONSIDER. | MCP: NOT JUSTIFIED — ordinary code, or an explicit API client. |

Question 3 is about authority, not convenience: status, balances, permissions, deployments, orders — facts that must come from the system that owns them, never from model recall. Question 4 is about documents: manuals, runbooks, policies — text the model should read, not facts the system owns. A database lookup does not become RAG because the application also calls a model.

Question 7 is the one most often answered "yes" too quickly. If you can write the sequence of steps down in advance — "look up the status, then the deployment, then summarize" — the answer is **no**, and a fixed workflow is simpler, cheaper, and easier to test than an agent runtime. The answer is yes only when *which* step comes next depends on what the previous step observed, and the application cannot reasonably enumerate the branches itself.

Question 8 is answered "yes" for the wrong reason almost as often. A capability being remote is not enough: a method in the same process never needs MCP, and one stable service used by one application is usually served best by an ordinary API client. The answer is yes only when a *standardized* boundary — capabilities that compatible clients can discover and invoke — solves an integration problem that explicit clients do not. An application containing an LLM is not such a problem.

## Status vocabulary

The assessment never grades. Every capability gets one of three neutral statuses:

| Status | Meaning |
| --- | --- |
| **CONSIDER** | An answer above gives this capability a reason to exist. It is a candidate, not a mandate — you may still reject it for a simpler design. |
| **NOT JUSTIFIED** | Nothing in the answers requires it. Leaving it out is a decision, and it belongs in "Rejected complexity". |
| **UNDECIDED** | The question is unanswered or answered "unsure". Resolve it before building. |

## How the summary is derived

The interactive version derives a capability summary from your answers with these rules — simple, deterministic, and printed here so you can check them:

1. **Model** is NOT JUSTIFIED if question 0 is *yes* or question 1 is *no*; CONSIDER if question 0 is *no* and question 1 is *yes*; otherwise UNDECIDED.
2. **Structured Output, Tools, Knowledge / RAG, Memory, Skills, Agent Runtime, MCP** each follow their own question: *yes* → CONSIDER, *no* → NOT JUSTIFIED, *unsure* or unanswered → UNDECIDED. Then:
   - if Model is NOT JUSTIFIED, they are all NOT JUSTIFIED (none of them means anything without a model; behind ordinary software, a remote capability is just an API call);
   - if Model is UNDECIDED, a CONSIDER becomes UNDECIDED.
3. **Agent Runtime** is additionally UNDECIDED when question 7 is *yes* but Tools is not CONSIDER: a runtime chooses among allowed actions, and without actions there is nothing to choose.
4. **MCP** is additionally UNDECIDED when question 8 is *yes* but Tools is not CONSIDER: an external capability boundary needs capabilities for the model to reach. MCP is never a candidate just because the application contains an LLM.

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

## Where MCP enters

[Lab 08](../../labs/08-mcp/README.md) teaches MCP, and question 8 is where it enters the assessment. The question behind it: does this architecture genuinely need a standardized external capability boundary?

```
Is the capability local?
├── Yes → ordinary application code. MCP: NOT JUSTIFIED.
└── No  → Is one explicit API integration sufficient?
          ├── Yes → a normal client may be simpler. MCP: NOT JUSTIFIED.
          └── No  → Do compatible clients benefit from a standardized
                    boundary they can discover and invoke?
                    ├── No     → keep the explicit integration. MCP: NOT JUSTIFIED.
                    ├── Unsure → MCP: UNDECIDED.
                    └── Yes    → MCP: CONSIDER.
```

A remote capability does not automatically require MCP, and MCP does not replace REST, gRPC, or an SDK client; an MCP server can even sit in front of one. If MCP is a candidate, the decision-authority questions still apply in full: a server announcing a tool does not make it executable. The application keeps the allowlist — discovery is not permission — and another autonomous agent is not an MCP question at all (A2A and multi-agent systems come later).

## Evaluation, observability, and security plans

If a model influences any decision, the template asks for an evaluation plan before the design is relied on: which representative cases, what behavior counts as success, which failures can be checked deterministically, which need human judgment, and what change would trigger re-evaluation. Evaluation is not another row in the capability table. It is not a runtime capability you add; it is how you find out whether the capabilities you chose are behaving. The assessment does not recommend an evaluation framework and does not evaluate anything — [Lab 09](../../labs/09-evaluation/README.md) teaches the concepts.

If a model influences any decision, the template also asks for an observability plan: how you would reconstruct a failed run, what should and should never be logged, how model and tool calls are correlated, which metrics would indicate unhealthy behavior, and what trace information diagnosis needs. Like evaluation, observability is not a row in the capability table and the assessment recommends no observability framework or product; [Lab 10](../../labs/10-observability/README.md) teaches the concepts. The assessment does not observe anything.

If a model proposes actions, or untrusted content can reach a model that does, the template then asks for a security plan: which trust boundaries exist; what each tool really does and which ones change state; which principals exist and the least each needs; how arguments are validated, separately from authorization; which content is untrusted and what it can cause if it steers the model; what happens when the model proposes an unauthorized action; which actions need an approval and what it is bound to; which fields are sensitive and where credentials live; how denials are observable without recording being the control; which authorization rules can be tested deterministically; and, for a remote capability, who enforces access control there. Like evaluation and observability, security is not a row in the capability table, there is no score, and the assessment recommends no security product or framework. [Lab 11](../../labs/11-security/README.md) teaches the concepts. The assessment secures nothing: answering these questions on paper is not a control.

The template has no deployment questions. How the system is built, configured, and run is [Lab 12](../../labs/12-deployment/README.md), which ends with a production gap analysis. It follows from decisions this assessment already asks for (which tools change state, which principals exist, which fields are sensitive, where credentials live), so it adds no questions, no capability row, and no score. The assessment deploys nothing.
