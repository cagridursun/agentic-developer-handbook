# Lab 07 — Agent Runtime

**The model suggests the next step.**

**The runtime owns execution and stopping.**

**Autonomy is bounded by application controls.**

**This is an agent.**

## The limitation we found

Every lab so far manually determined the execution sequence. Lab 03 was the clearest case: call the model, expect a tool request, execute it, send the result back, call the model again, stop. Five steps, hardcoded, exactly one tool round.

That works when the number and order of steps are known in advance. But what if the model needs zero tools for one question, one for another, and two — where the second depends on the first observation — for a third? The application should not have to encode `if step 1... then maybe step 2... then maybe step 3...` for every possible path. That creates the need for a generalized execution loop.

## Learning goals

- Build the observe → decide → act → observe loop that makes something an agent
- See why fixed orchestration doesn't generalize, and why `while (true)` is worse
- Keep execution and authorization on the application side of the boundary
- Make stopping an explicit runtime responsibility with a visible step budget
- Understand why this agent needs none of RAG, memory, or skills
- Decide when an agent runtime is justified at all

## Starting point

A fictional platform (the same invented Helio flavor as Lab 04, with no dependency on it) and one goal:

> "Investigate the notifications service. If it is degraded, check whether there was a recent deployment and summarize what is known. Do not claim a root cause without evidence."

Two read-only local tools exist: `getServiceStatus(serviceName)` and `getRecentDeployment(serviceName)`. The fictional data is arranged so the model can observe *correlation* — a degraded service, a deployment minutes earlier — without being handed a proven cause. Crucially, the runtime never hardcodes "if degraded, check deployments." That decision belongs to the model.

## Experience 1 — Fixed orchestration

The Lab 03 shape, extended honestly, looks like this:

```
call model → expect tool → execute → call model
          → maybe another tool? → add another branch → execute
          → call model → maybe another tool? → ...
```

Every possible round is encoded by hand. Fixed orchestration is *good* when the workflow is known — it is simpler, cheaper, and easier to test. It becomes the wrong abstraction exactly when the number of model-directed steps is not known in advance.

## Naive fix — Loop until the model stops

The obvious generalization:

```java
while (true) {
    var decision = model.next(...);
    if (decision.isFinalAnswer()) return;
    executeTool(decision);
}
```

## Break it — What if the model never stops?

A model may request the same tool repeatedly, alternate between tools forever, or simply never produce a final answer — consuming unbounded tokens, cost, and time. The demo proves it without running anything unbounded: a scripted model that requests `getServiceStatus(notifications)` on every call is handed to the *real* runtime, which stops it cold:

```
Model decisions made: 4 (budget: 4)
Stop reason:          MAX_STEPS
```

A loop is not enough. An agent runtime needs stopping rules.

## Build — A bounded Agent Runtime

The whole loop is one `for` statement whose bound is the step budget:

```java
for (int step = 1; step <= maxSteps; step++) {
    ModelDecision decision = lastExchange == null
            ? model.start(goal)
            : model.observe(lastExchange);

    if (decision instanceof FinalAnswer answer)   → stop: FINAL_ANSWER
    // else: exactly one ToolRequest
    validate against allowlist + argument rules   → or stop: REJECTED_TOOL_CALL
    execute plain Java, record the ToolExchange
}
→ stop: MAX_STEPS
```

**Step semantics:** one step = one model decision, the final answer counts as a decision, and the run can never make more than `MAX_STEPS` (default 4) model decisions. There is no recursion and no hidden loop.

## The runtime contract

`ModelDecision` is a sealed interface with exactly two cases — `FinalAnswer` and `ToolRequest` (at most one tool per decision). The runtime validates, executes, observes, repeats, and stops. `AgentModel` is the one seam: `start(goal)` and `observe(exchange)`. It exists because a concrete need finally appeared — deterministic loop tests and keeping provider payloads out of loop semantics. It is not a provider framework; the earlier labs didn't need it, so they didn't have it. If Gemini returns several function calls in one turn, the translation fails loudly: parallel tool calls are a later runtime extension, not silently executed.

## Run it locally

Deterministic, no key, no network — a scripted model drives the real runtime:

```sh
./mvnw -pl labs/07-agent-runtime compile exec:java
```

Windows: `.\mvnw.cmd -pl labs/07-agent-runtime compile exec:java`

The scripted model is a test double, not an AI. It exists so the runtime — the thing this lab teaches — is observable and provable.

## Run it with Gemini

Explicit and bounded, requiring `GOOGLE_API_KEY` (one run makes several model requests, so live mode is opt-in):

```sh
./mvnw -pl labs/07-agent-runtime compile exec:java -Dexec.args="--live"
```

`GEMINI_MODEL` overrides the default (`gemini-3.8-flash`). The same runtime and the same budget apply — a live run can never exceed 4 model decisions.

## Read the execution trace

```
Step 1 / 4
Model decision: TOOL getServiceStatus {serviceName=notifications}
Tool result:    {status=DEGRADED, message=Elevated delivery latency...}

Step 2 / 4
Model decision: TOOL getRecentDeployment {serviceName=notifications}
Tool result:    {version=notifications-2.4.1, deployedAt=2026-09-23T13:52:00Z...}

Step 3 / 4
Model decision: FINAL ANSWER
Stop reason: FINAL_ANSWER
```

This is educational output, not observability. Logs, metrics, traces, and token accounting are Milestone 10.

## Why do we call this an agent now?

This handbook has refused the word "agent" for six labs. Here is the breakpoint:

1. The application gives the model a goal.
2. The model observes the available context and results.
3. The model chooses the next allowed step.
4. The application executes it.
5. The model observes the result.
6. This may repeat.
7. The runtime enforces stopping rules.

That observe → decide → act → observe loop, with the model influencing the next step and the application owning execution and termination, is what this handbook calls an agent. Industry terminology varies — plenty of people call Lab 03 an agent already, and that usage isn't wrong so much as less precise. Within this handbook, the word starts here.

## Agent Runtime vs Tool Calling

Lab 03 was one controlled tool round: the application manually encoded model → tool → model → stop. Lab 07 generalizes it: model → decision → optional tool → observation → model → ... → explicit stop. Tool calling is a mechanism the runtime uses; tool calling alone is not the runtime.

## Agent Runtime vs Fixed Workflow

A fixed workflow — step A → step B → step C — is decided entirely by the application. An agent runtime lets the model influence the next step *from a bounded set of allowed actions*, based on observations. Neither is superior: fixed orchestration is usually better when the sequence is known. Agentic behavior earns its complexity only when the next useful step genuinely depends on model interpretation of the current state.

## Bounded autonomy

The model may choose: the final answer, or one of two allowlisted read-only tools. The model may not choose: arbitrary Java methods, shell commands, network destinations, its own budget, its own runtime, authorization policy, or security rules. The application owns every one of those boundaries. That is bounded autonomy — and it is the only kind this handbook endorses.

## Where did Knowledge, Memory, and Skills go?

Nowhere — they're optional. An agent is not model + tools + RAG + memory + skills + everything else. Those are building blocks a runtime *can* use when the problem needs them. This agent needs a model, two tools, an execution loop, and stopping rules. That is enough, and adding the rest would teach nothing here. You probably don't need all of these — including inside your agents.

One connection: Lab 06 noted that model-driven skill *selection* needs a runtime. This runtime is the architectural place where that could later live; it deliberately doesn't, because one concept per lab remains the rule.

## Stopping rules

- **FINAL_ANSWER** — the model answered instead of requesting a tool.
- **MAX_STEPS** — the budget of model decisions is exhausted; no further model call happens.
- **REJECTED_TOOL_CALL** — the model requested an unlisted tool, malformed or extra arguments, a blank or unknown service. The runtime stops safely rather than guessing what was meant.

Nothing relies on "the model will eventually stop."

## The model is not the executor

Model-generated tool names and arguments are untrusted input. The allowlist is a visible `switch` — never reflection — and every argument is validated before a Java method runs. Tools are read-only by design: side-effecting actions (deploy, restart, send, delete) drag in authorization, approval, idempotency, rollback, and audit. Those matter, and none of them are needed to understand the loop. High-impact actions behind human approval is the production pattern; it is named here, not implemented.

## Reflection

1. What part of the system decided which tool to call next?
2. What part actually executed the Java method?
3. What prevented the model from running forever?
4. Would this use case still need an agent if the exact steps were known?
5. Why is the step budget an application decision?
6. What would change if one tool could modify production state?
7. Does an agent need RAG, memory, and skills to be an agent?

Short guidance: the model decided, the runtime executed (1–2); the budget, not model goodwill (3); a known sequence deserves a workflow, not an agent (4); whoever pays for tokens and carries the risk sets the budget (5); side effects demand approval, idempotency, and audit before autonomy (6); no — building blocks are optional (7).

## Do I actually need an Agent Runtime?

```
Can one model call solve the task?
├── Yes → Do not build an agent.
└── No  → Is the required sequence known in advance?
    ├── Yes → Prefer explicit orchestration or a workflow.
    └── No  → Does the model genuinely need to choose among bounded
              next steps based on observations?
        ├── No  → Keep the workflow deterministic.
        └── Yes → An agent runtime may be justified.
```

If it is justified, decide before writing the loop: What actions are allowed? What is the maximum number of steps? What ends the run? Which actions require human approval? What budget exists for time, tokens, and cost? What happens when a tool fails?

## What we STILL do not have

- No MCP and no remote tools
- No side-effecting tools
- No human approval flow
- No durable agent state
- No automatic skill routing
- No parallel tool execution
- No nested agents, no multi-agent system
- No evaluation framework
- No observability framework

## Production considerations

Discussed, not implemented: token/cost budgets, wall-clock deadlines, tool-call budgets, retries and backoff, idempotency, human approval, resumable runs, persistent execution state, cancellation, parallel tool calls, long-running tools, model fallbacks, tool timeouts, malformed model responses, trace IDs, audit trails, policy enforcement, sandboxing, and side-effect control.

## What limitation remains?

The runtime can use local application capabilities. But every *external* capability still requires custom application integration — new client code per system, per protocol, per vendor. That leads to **Milestone 8 — MCP**, which standardizes how a runtime reaches external capabilities and resources.

To be precise about the order of concepts: MCP does not create the agent. The agent already exists, right here, with two local Java methods. MCP will change how far its tools can reach — not what it is. Not implemented here.

## Sources consulted

Verified against official documentation during this milestone series (2026-09):

- [Gemini API function calling guide](https://ai.google.dev/gemini-api/docs/function-calling) — declarations, the request/execute/respond cycle, and multi-turn function calling with structured function responses (verified at Milestone 3 and reused unchanged).
- [googleapis/java-genai README](https://github.com/googleapis/java-genai) — manual `FunctionDeclaration` usage and the automatic function-execution mode this lab deliberately avoids; `Content`/`Part.fromFunctionResponse` history handling.
- [java-genai Javadoc](https://googleapis.github.io/java-genai/javadoc/com/google/genai/types/GenerateContentResponse.html) — `functionCalls()`, `candidates()`, and builders (also used to construct provider responses locally in tests).
- [Maven Central: com.google.genai:google-genai](https://central.sonatype.com/artifact/com.google.genai/google-genai) — the labs stay on 1.72.0 for consistency across the canonical path.
