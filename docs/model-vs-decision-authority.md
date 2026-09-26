# LLM vs Decision Authority

(Sometimes phrased as "LLM vs decision maker" — this handbook prefers *decision authority*, because the architectural question is not "who reasoned?" but "whose output is binding on the system?")

**The model may participate in a decision. The application owns whether that decision becomes executable.**

**Reasoning capability does not imply decision authority.**

## The trap

The handbook already establishes that a model is not an agent, that the model does not execute tools, and that the model is never the authorization layer. One conceptual trap remains, and it is common:

> LLM = decision maker

That is too imprecise to build on. A model can reason, classify, recommend, rank, choose among options presented to it, propose a next action, or generate a candidate decision. None of that automatically makes the model the *authoritative* decision maker of the system.

The distinction to carry:

**Model influence ≠ decision authority.**

## Three distinct levels

**1. Reasoning.** The model interprets information and produces reasoning or candidate conclusions. Nothing about the system changes yet.

**2. Proposal / recommendation.** The model produces a candidate: a category, a next tool, a priority, a draft action, a diagnosis hypothesis, a refund recommendation, a deployment recommendation. Still nothing has happened.

**3. Authority / execution.** Application-owned controls determine whether a candidate is valid, allowed, authorized, within policy, within budget, and safe to execute — and then actual system code performs the action.

```
Input
  ↓
LLM reasoning
  ↓
Candidate / proposal
  ↓
Application validation
  ↓
Policy / authorization / approval
  ↓
Executable decision
  ↓
Action
```

A low-risk system may intentionally let a model proposal pass through automatically after deterministic validation — the agent runtime in [Lab 07](../labs/07-agent-runtime/README.md) does exactly that for read-only tools. That still does not mean the model owns the authority boundary. Automatic execution after deterministic policy does not transfer authority to the model; it means the application decided, in advance and in code, which proposals are executable.

## Examples

| Scenario | Model role | Decision authority |
| --- | --- | --- |
| Summarize an incident | Generate | User / application |
| Suggest a ticket category | Classify / propose | Application rules |
| Select the next read-only diagnostic tool | Propose next action | Agent runtime |
| Recommend a €500 refund | Recommend | Business policy / human / application |
| Suggest a production deployment | Recommend / propose | Deployment authorization + approval |
| User says "I am an admin" | Context only | Identity / authorization system |

The worked automatic-acceptance example, from Lab 07: the model selects one of two read-only diagnostic tools, and the runtime executes it automatically — but only if the tool is allowlisted, the arguments validate, the step budget remains, and nothing in application policy forbids it. Every one of those conditions is deterministic application code. The model influenced *which* allowed action ran; it never gained the ability to run a disallowed one.

## Model choice vs system decision

"The model chooses the next tool" is acceptable shorthand, and this handbook uses it. The precise architecture behind the shorthand is:

> The model proposes — selects a candidate — next action from the actions exposed to it. The runtime decides whether that proposal is executable.

In architectural writing, prefer *proposes*, *selects a candidate*, or *suggests* over wording that implies unrestricted binding authority. In ordinary prose, natural language wins; the point is the mental model, not vocabulary policing.

## The decision envelope

A useful mental model — not a framework, not a Java type: every model-produced action lives inside an application-owned envelope.

```
             Application-owned envelope
┌─────────────────────────────────────────────┐
│ Allowed actions                             │
│ Argument validation                         │
│ Authorization                               │
│ Step / cost budget                          │
│ Approval policy                             │
│ Stop conditions                             │
│                                             │
│        LLM proposes the next action         │
└─────────────────────────────────────────────┘
```

The model influences choices only inside the envelope. Making the envelope explicit — writing down the allowed actions, the validation, the budget, the approvals — is the real design work of an agentic system. When the envelope is implicit, it is usually larger than anyone intended.

## Delegation is a design decision, not a sin

Do not overcorrect into "LLMs must never make decisions." That is too simplistic, and it is not this handbook's position. A product may intentionally delegate some decisions to a model: ranking search results, choosing a read-only investigation step, routing low-risk support requests, selecting a response style.

The architectural questions for any delegation:

- What decision is being delegated?
- What is the risk if it is wrong?
- What constraints surround it?
- Is deterministic validation possible?
- Does it create a side effect?
- Is authorization involved?
- Does human approval matter?

Read-only, reversible, validated, budget-bounded decisions are good delegation candidates. Side-effecting, irreversible, authorization-adjacent decisions want deterministic policy or a human — and the model's output becomes an *input* to that authority, not a replacement for it.

## The rule in one place

- **Model:** reason, generate, recommend, propose.
- **Application / runtime:** constrain, validate, authorize, execute, stop.
- **Human or business policy:** retains final authority where the risk requires it.

This is the same boundary [Lab 03](../labs/03-tool-calling/README.md) draws for one tool round and [Lab 07](../labs/07-agent-runtime/README.md) draws for the loop — stated once, as its own concept, because it applies to every system in this handbook, agentic or not.
