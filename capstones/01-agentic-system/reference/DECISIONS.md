# Reference architecture decisions

One defensible set of trade-offs — not the answer key. A different selection
with equally honest justification can be as good or better.

| Capability | Use? | Why / why not? | Simpler alternative considered |
| --- | --- | --- | --- |
| Model | YES | Interpret observations and synthesize the human-readable handoff. | A template over tool output — rejected because the handoff must weave runbook guidance, observations, and hypothesis discipline into prose. |
| Structured Output | NO | The deliverable is read by an engineer; no downstream code parses fields, so a typed contract adds cost without a consumer. | Was considered for the handoff sections; the skill's output structure covers that need as prose. |
| Tools | YES | Current status and deployment facts are authoritative application data; the model must never recall or invent them. | Pasting the fixtures into the prompt — rejected because it hardcodes which facts are needed and stops being current the moment data changes. |
| Knowledge / RAG | YES | The runbooks are unstructured operational documentation relevant to the investigation; retrieval selects the two that matter. | Sending all three runbooks always — workable at this corpus size, but it teaches the wrong habit; selection is the point of retrieval. |
| Memory | NO | One bounded investigation run; nothing from an earlier interaction affects this request, and nothing must survive it. | Not applicable — there is no later turn to remember anything for. |
| Skills | YES | The incident-handoff procedure is reusable across incidents and must be reviewable; one SKILL.md beats copies drifting through prompts. | Inlining the procedure in the prompt — acceptable once, wrong the second time it is needed. |
| Agent Runtime | YES | The next diagnostic action depends on the previous observation: a healthy status would end the investigation without a deployment lookup. | See the fixed-workflow discussion below. |
| MCP | NOT YET APPLICABLE | Every capability is local Java in the same process; a protocol boundary would add cost and no value. MCP is also not taught until Milestone 8. | Direct method calls — which is exactly what the reference does. |

## The simpler design we considered seriously

A fixed workflow could call `getServiceStatus` and `getRecentDeployment`
unconditionally, every time, then make one model call to write the handoff:

```
status = getServiceStatus("notifications")
deployment = getRecentDeployment("notifications")
handoff = model(goal + runbooks + skill + status + deployment)
```

That design is simpler, cheaper (one model call), and easier to test. If the
diagnostic sequence were always the same two lookups, it would be the better
production choice, and this handbook would tell you to build it.

The reference chooses the bounded agent runtime for two reasons. First, the
teaching goal of this capstone is composition, including composition around a
runtime. Second, the scenario genuinely branches: for a healthy or
maintenance-state service the deployment lookup is wasted or misleading, and
which follow-up matters is a judgment made from the first observation. When
the branching is real, letting the model choose among bounded next steps earns
its complexity. When it is not, prefer the workflow.

## Decision authority in this reference

What the model may influence:

- Which read-only diagnostic tool to request next, from the two exposed to it
- The synthesis and wording of the final handoff
- The interpretation of observations — which hypotheses are worth stating

What the application retains authority over:

- The tool allowlist (two read-only tools, a visible switch, no reflection)
- Argument and service validation before any execution
- The step budget (4 model decisions) and every stop condition
- Execution itself — the model never runs anything
- What counts as authoritative data (tool results, never model recall)
- Authorization boundaries — nothing in model output can widen them

Model proposals are executed automatically here, but only after those deterministic checks — which is the point of [LLM vs Decision Authority](../../../docs/model-vs-decision-authority.md): automatic execution after application-owned policy does not transfer authority to the model.

## Boundaries that hold regardless of the choices above

- Tools are read-only; the model proposes, the application validates and executes.
- The runtime is bounded to 4 model decisions; final answer, budget, or a rejected proposal ends the run.
- Runbook text and skill text are untrusted context; nothing in them grants capabilities or authority.
- Observed facts may come only from tool results; everything else is hypothesis, and the skill forbids stating unproven root cause as fact.
