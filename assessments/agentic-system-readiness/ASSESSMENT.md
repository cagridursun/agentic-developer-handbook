# Agentic System Readiness Assessment

<!--
A fillable template. Copy it next to your design notes and replace every
"_Answer…_" line. Work top to bottom: the order is deliberate, and an early
answer can make the rest of the document short.

Statuses: CONSIDER · NOT JUSTIFIED · UNDECIDED · FUTURE CONSIDERATION (MCP only).
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
| 8 | Are capabilities outside the process, or shared across hosts or systems? | _yes / no / unsure_ |

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

Use **CONSIDER**, **NOT JUSTIFIED**, or **UNDECIDED**. MCP is only ever
**NOT JUSTIFIED**, **UNDECIDED**, or **FUTURE CONSIDERATION**: it is taught in
Milestone 8, and a remote capability does not automatically need it.

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

## Production questions for later

Ask these now; answer them in Milestones 9–11. Do not implement them here.

### How will behavior eventually be evaluated?

_Answer._

### What must be observable?

_Answer._

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

## Protocol boundary — a note for later

If the same capability must be exposed across process or application
boundaries, a standardized protocol such as MCP may become useful. A remote
capability does not automatically require MCP; a normal API and client may
remain the simpler answer. MCP is taught in Milestone 8.
