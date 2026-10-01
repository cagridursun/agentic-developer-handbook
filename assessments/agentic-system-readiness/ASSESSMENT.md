# Agentic System Readiness Assessment

<!--
A fillable template. Copy it next to your design notes and replace every
"_Answer…_" line. Work top to bottom: the order is deliberate, and an early
answer can make the rest of the document short.

Statuses: CONSIDER · NOT JUSTIFIED · UNDECIDED.
This is not a score. See README.md in this directory for the decision order
and the rules the interactive version uses to derive a summary.
-->

## Use case

### Problem statement

_Answer: what problem exists today, in one or two sentences?_

### User / consumer

_Answer: who or what consumes the result — a person, another service, a report?_

### Desired outcome

_Answer: what does "solved" look like?_

## Simpler alternative

### Can deterministic code solve this? (question 0)

_Answer yes / no / unsure, and why._

If **yes**: start with deterministic software. Fill in the next section, the
final architecture shape, and rejected complexity — the AI capability table
below is NOT JUSTIFIED by definition.

### Existing API, database, or workflow alternative

_Answer: which existing query, API, rule engine, template, or workflow was
considered, and what would it fail to do?_

### Why an LLM is or is not justified (question 1)

_Answer: does the task require probabilistic language understanding, reasoning,
or generation? yes / no / unsure, and why._

## Capability decisions

Answer the decision questions in order:

| # | Question | Answer |
| --- | --- | --- |
| 2 | Does downstream application code need to consume the model output? | _yes / no / unsure_ |
| 3 | Does the system need current or authoritative application/system data, or actions? | _yes / no / unsure_ |
| 4 | Does the model need unstructured information that exists outside the current interaction? | _yes / no / unsure_ |
| 5 | Must information created by earlier interactions survive into later interactions? | _yes / no / unsure_ |
| 6 | Is there a reusable, model-performed procedure that should be reviewed and reused independently of individual prompts? | _yes / no / unsure_ |
| 7 | Is the next useful action genuinely unknown in advance, and dependent on model interpretation of observations? | _yes / no / unsure_ |
| 8 | Does the system need a standardized boundary for externally owned capabilities, one that compatible clients can discover and invoke, where an explicit API integration is not enough? | _yes / no / unsure_ |

Then record each capability:

| Capability | Consider? | Why / why not? | Simpler alternative |
| --- | --- | --- | --- |
| Model | | | |
| Structured Output | | | |
| Tools | | | |
| Knowledge / RAG | | | |
| Memory | | | |
| Skills | | | |
| Agent Runtime | | | |
| MCP (protocol boundary) | | | |

Use **CONSIDER**, **NOT JUSTIFIED**, or **UNDECIDED** for every capability,
MCP included. A remote capability does not automatically need MCP, and an
application containing an LLM is not a reason for it.

## Decision authority

The model may reason, generate, recommend, and propose. The application owns
what becomes binding. See
[LLM vs Decision Authority](../../docs/model-vs-decision-authority.md).

### What decisions may the model influence?

_Answer._

### What decisions become binding on the system?

_Answer._

### What remains deterministic or application-controlled?

_Answer._

### What data is authoritative?

_Answer._

### What actions create side effects?

_Answer._

### What requires authorization?

_Answer._

### What requires human approval?

_Answer._

## Runtime boundary

### What stops the run?

_Answer: for a workflow, usually "the last step"; for an agent runtime, every
explicit stop condition._

### What time, token, or cost budget exists?

_Answer._

### If using an Agent Runtime

- **Allowed actions:** _Answer._
- **Max steps:** _Answer._
- **Failure behavior:** _Answer: what happens when a tool fails, a proposal is
  rejected, or the budget runs out?_

### If not using one

_Answer: why is a fixed workflow, a single model call, or ordinary software
sufficient? Write down the known sequence of steps._

## Evaluation plan

If a model influences any decision, plan how its behavior will be evaluated *before* relying on it. This is not a capability to select and not a framework to choose: it is how you find out whether the architecture above is doing its job. [Lab 09](../../labs/09-evaluation/README.md) teaches the concepts. If no model influences a decision, write "No model-influenced decisions" and stop.

### What representative cases will you use?

_Answer: more than the happy path — an ordinary case, a case where the right behavior is to do less, an awkward or ambiguous case, and a failure case._

### What behavior counts as success?

_Answer: properties, not one correct answer — for the final result and, for an agent, for the trajectory that produced it._

### Which failures can be checked deterministically?

_Answer._

### Which behaviors require human judgment?

_Answer._

### What change would trigger re-evaluation?

_Answer: a new prompt, model, tool, skill, retrieved source, or runtime rule._

## Observability plan

If a model influences any decision, decide *before* a run fails how you will reconstruct it afterwards. This is not a capability to select and not a tool to choose: it is what lets you answer "what actually happened during this run?" when evaluation did not predict the failure. [Lab 10](../../labs/10-observability/README.md) teaches the concepts. If no model influences a decision, write "No model-influenced decisions" and stop.

### How will you reconstruct a failed agent run?

_Answer: which questions you must be able to answer afterwards — which model was called, which decisions were made, which tools ran with which arguments, what they returned, and why the run stopped._

### What should be logged?

_Answer: events with structure and context, not free text._

### What should never be logged?

_Answer: secrets, credentials, and data you have no reason to keep. Observable does not mean record every byte._

### How will model and tool calls be correlated?

_Answer: what identifier ties every model call and tool call of one run together._

### What metrics indicate unhealthy behavior?

_Answer: a few counts or durations across runs, and what each cannot tell you._

### What trace information is needed for diagnosis?

_Answer: the steps of one run, their order and relationships, durations, and failure details._

## Production questions for later

Ask this now; answer it in Milestone 11. Do not implement it here.

### What trust and security boundaries exist?

_Answer: user input, model output, tool arguments, retrieved content, skill
text, protocol messages._

## Final architecture shape

_Answer: the smallest justified architecture, for example:_

```
Fixed workflow
→ authoritative service lookup
→ selected documentation
→ one model synthesis call
```

## Rejected complexity

_Answer: every capability intentionally NOT used, and the one-line reason._

## Unresolved questions

_Answer: every UNDECIDED capability and every open authority question._

## Protocol boundary (MCP)

A local capability never needs MCP, and a remote capability does not
automatically require it: one explicit API integration is often simpler. MCP
may be justified when compatible clients need standardized discovery and
invocation. Discovery is not permission — the allowlist stays in the
application. See [Lab 08](../../labs/08-mcp/README.md).
