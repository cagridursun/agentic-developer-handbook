# Worked example — Checkout latency investigation

A filled-in [ASSESSMENT.md](../ASSESSMENT.md). One defensible reading of the
scenario, not an answer key.

**Lesson: an agent runtime earns its place only when the next step depends on
what the previous step observed — and even then, the application owns the
envelope.**

This example is related to
[Capstone 01](../../../capstones/01-agentic-system/README.md), which builds a
similar system for a different service. The capstone is where you implement
it; this is where you decide whether you should.

## Use case

### Problem statement

When the on-call engineer is paged for slow checkout, the first fifteen
minutes are always the same kind of digging: is the checkout service itself
unhealthy, did something deploy, is a dependency — payments, inventory,
the feature-flag service — degraded? Which of those to check next depends on
what the last check showed.

### User / consumer

The on-call engineer, who receives a handoff and decides what to do.

### Desired outcome

A short investigation handoff that separates observed facts, hypotheses,
unknowns, and suggested next actions — without claiming a root cause the
evidence does not support.

## Simpler alternative

### Can deterministic code solve this? (question 0)

**No, not entirely.** Deterministic code should do everything it can: collect
the checkout service's status and recent deployments, show dashboards. It
cannot interpret an unfamiliar combination of observations and decide which
dependency is worth checking next — the part the engineer does by judgment.

### Existing API, database, or workflow alternative

A dashboard plus a runbook. It is the baseline; the question is whether the
first fifteen minutes can be done before the engineer opens a laptop.

### Why an LLM is or is not justified (question 1)

**Justified.** Interpreting mixed observations against runbook guidance and
writing a handoff that keeps evidence and hypothesis apart is reasoning and
generation.

## Capability decisions

| # | Question | Answer |
| --- | --- | --- |
| 2 | Does downstream application code need to consume the model output? | No — an engineer reads the handoff. |
| 3 | Does the system need current or authoritative data or actions? | **Yes** — live status and deployment data. |
| 4 | Does the model need unstructured information outside the interaction? | **Yes** — runbooks. |
| 5 | Must information from earlier interactions survive into later ones? | No — each investigation is one bounded run. |
| 6 | Is there a reusable model-performed procedure worth reviewing independently? | **Yes** — the handoff procedure is shared by every investigation. |
| 7 | Is the next useful action unknown in advance and dependent on observations? | **Yes** — a healthy checkout with a degraded dependency leads somewhere different from a checkout that deployed ten minutes ago. |
| 8 | Does the system need a standardized boundary for externally owned capabilities, one that compatible clients can discover and invoke, where an explicit API integration is not enough? | No — the capabilities are remote, but three explicit HTTP clients are enough; see the protocol note below. |

| Capability | Consider? | Why / why not? | Simpler alternative |
| --- | --- | --- | --- |
| Model | CONSIDER | Interpreting observations and writing the handoff. | Dashboard + runbook, read by the engineer. |
| Structured Output | NOT JUSTIFIED | The handoff is read by a person; no program parses it. CONSIDER it if the handoff must populate incident-tracker fields. | Prose sections defined by the skill. |
| Tools | CONSIDER | Service status, recent deployments, and dependency health are authoritative, live data. The model must never recall or invent them. All tools are **read-only**. | Pasting a dashboard snapshot into the prompt — stale immediately, and it hardcodes which facts matter. |
| Knowledge / RAG | CONSIDER | Runbooks are unstructured guidance; retrieval selects the relevant ones. | Always sending every runbook — workable while there are three, wrong at thirty. |
| Memory | NOT JUSTIFIED | Nothing from a previous investigation should shape this one; "last time it was the database" is exactly the bias the handoff must avoid. | — |
| Skills | CONSIDER | The handoff procedure (facts vs hypotheses vs unknowns, no unproven root cause) is reused by every run and must be reviewable in one place. | Inlining it in the prompt — fine once, drifts the second time. |
| Agent Runtime | CONSIDER | Which check comes next depends on the last observation. A bounded runtime lets the model propose the next read-only check from an allowlist. | A fixed workflow that checks everything every time — **seriously considered**; see below. |
| MCP (protocol boundary) | NOT JUSTIFIED | The status APIs live in other teams' services, but one application needs them and each is one explicit HTTP call. Remote does not mean MCP; see the protocol note. | Three small HTTP clients. |

### The fixed workflow, taken seriously

```
status     = checkout.status()
deployment = checkout.recentDeployment()
deps       = [payments, inventory, flags].map(status)
handoff    = model(runbooks + skill + status + deployment + deps)
```

This is simpler, cheaper, and easier to test. If the checks are few and cheap,
**build this instead**, and this assessment would call that the better design.
The runtime becomes the better choice only when the set of possible checks is
large or expensive enough that "check everything" stops being reasonable, and
choosing the next one really needs judgment about the last result.

## Decision authority

- **Model may influence:** which allowlisted read-only check to propose next;
  which hypotheses are worth stating; the wording of the handoff.
- **Binding decisions:** whether a proposed check runs (the runtime); every
  remediation — rollback, restart, flag change — stays with the engineer.
- **Deterministic / application-controlled:** the allowlist of checks, argument
  validation (only known service names), the step budget, every stop
  condition, and execution itself.
- **Authoritative data:** tool results. Model recall is never evidence.
- **Side effects:** none by design. If a tool could roll back or restart, it
  would not be in the allowlist; it would need human approval and a separate
  assessment.
- **Authorization:** the runtime uses a read-only service identity; nothing in
  model output can widen it.
- **Human approval:** required for any action beyond reading. The model can
  *recommend* a rollback in the handoff; it cannot trigger one.

## Runtime boundary

### What stops the run?

A final handoff from the model; the step budget; a proposal for a tool that is
not allowlisted or has invalid arguments (reject and stop, or reject and let
the model answer with what it has — decide which, and test it).

### What time, token, or cost budget exists?

At most 6 model decisions per investigation and a wall-clock limit of two
minutes; the handoff says so when the budget ended the run early.

### If using an Agent Runtime

- **Allowed actions:** `getServiceStatus(service)`,
  `getRecentDeployments(service)`, `getDependencyHealth(service)` — read-only,
  known services only.
- **Max steps:** 6.
- **Failure behavior:** a failing tool returns an error observation, not an
  exception the model can retry forever; the handoff lists what could not be
  checked as an unknown.

## Production questions for later

- **Evaluation:** replay past incidents with recorded tool results; check the
  handoff never states a root cause the observations do not support.
- **Observable:** every proposed step, every rejection, every tool call with
  its arguments, token cost and duration per run.
- **Trust boundaries:** tool results and runbooks are data, not instructions;
  arguments are validated against known service names; the runtime's identity
  is read-only.

## Final architecture shape

```
Page
→ bounded agent runtime (≤ 6 steps, read-only allowlist)
    ↺ model proposes next check → runtime validates → tool executes
→ retrieved runbooks + handoff skill
→ handoff for the on-call engineer (human decides every action)
```

## Rejected complexity

Structured Output (no program consumes the handoff), Memory (would import
bias), MCP (see below), any write-capable tool (needs approval and its own
assessment), and a multi-agent design (one runtime with read-only tools is
enough).

## Protocol boundary (MCP)

The status, deployment, and dependency APIs are owned by other teams and are
already reachable over HTTP. That makes the capabilities remote — and remote
does not mean MCP. One application consumes them, each is one explicit call,
and three small HTTP clients inside the runtime's tool boundary are simpler
than three MCP servers.

The answer would change if several different assistants, IDEs, and runtimes
all needed the same checks, and each was writing its own glue. Then a
standardized boundary that every compatible client can discover and invoke
starts to pay for itself: the owning teams could each run an MCP server,
possibly in front of the same HTTP APIs. Each client would still keep its own
allowlist — discovery is not permission. That is the situation
[Lab 08](../../../labs/08-mcp/README.md) builds, with one capability.

## Unresolved questions

- Whether the fixed workflow is good enough; decide by trying it first.
