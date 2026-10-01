# Worked example — Order status lookup

A filled-in [ASSESSMENT.md](../ASSESSMENT.md). One defensible reading of the
scenario, not an answer key.

**Lesson: not every product feature needs AI.**

## Use case

### Problem statement

Customers and support agents need to see the status of an order. Today they
email support, and an agent looks it up by hand.

### User / consumer

A customer on the order page, and a support agent in the internal console.

### Desired outcome

Given an order id, return the current status — placed, paid, packed, shipped,
delivered, cancelled — and the tracking link when there is one.

## Simpler alternative

### Can deterministic code solve this? (question 0)

**Yes.** The input is an identifier, the answer is a field in the order
database, and the set of statuses is closed. An endpoint that validates the id,
checks that the caller may see the order, and reads one row solves the problem
completely.

### Existing API, database, or workflow alternative

`GET /orders/{id}` against the order service, rendered by a status component.
The order service already owns the data and the access rules.

### Why an LLM is or is not justified (question 1)

**Not justified.** Nothing here requires probabilistic language understanding
or generation. "Which order does the customer mean?" is answered by the id the
page already has, not by interpreting prose. A model would add latency, cost,
and a chance of stating a status the database does not contain.

The tempting mistake is "we have an AI roadmap, so the order page should chat."
If a later requirement genuinely needs free-text questions ("why is my order
late and can I change the address?"), reassess *that* use case — the status
lookup itself stays deterministic either way.

## Capability decisions

Question 0 is **yes**, so the AI part of the assessment ends here.

| Capability | Consider? | Why / why not? | Simpler alternative |
| --- | --- | --- | --- |
| Model | NOT JUSTIFIED | Deterministic software solves the problem. | An endpoint and a status component. |
| Structured Output | NOT JUSTIFIED | No model output exists to structure. | The order service's existing response type. |
| Tools | NOT JUSTIFIED | There is no model to hand a tool to. Reading the order is ordinary deterministic application logic — an API call or database query, not a model tool. | `GET /orders/{id}`. |
| Knowledge / RAG | NOT JUSTIFIED | The answer is a record, not a document. A lookup by id is not retrieval-augmented generation. | A database query. |
| Memory | NOT JUSTIFIED | Nothing from an earlier interaction is needed. | The order record already persists. |
| Skills | NOT JUSTIFIED | No model-performed procedure exists. | Application code. |
| Agent Runtime | NOT JUSTIFIED | There is no next step to choose; the sequence is one lookup. | A request handler. |
| MCP (protocol boundary) | NOT JUSTIFIED | The order service is already reachable through its API. | The existing HTTP client. |

## Decision authority

No model participates, so no model influences anything. The authority
questions still have ordinary answers, and it is worth writing them down:

- **Binding decisions:** which status is shown — read from the order service.
- **Authoritative data:** the order database, via the order service.
- **Authorization:** the caller must own the order or hold a support role; the
  order service enforces it.
- **Side effects:** none — the feature is read-only.
- **Human approval:** not needed.

## Runtime boundary

### What stops the run?

The request completes after one lookup.

### What time, token, or cost budget exists?

The normal request latency budget of the order page. No tokens.

### If not using one

A single request handler is the whole design: validate id → authorize → read
→ render.

## Evaluation plan

No model-influenced decisions. Ordinary API tests for each status cover the
component contracts; there is no model behavior to evaluate.

## Production questions for later

Ordinary software answers: request metrics and error rates, and the existing
authorization checks.

## Final architecture shape

```
Order page
→ GET /orders/{id} (authorized)
→ status component
```

## Rejected complexity

Model, Structured Output, Tools-as-model-tools, Knowledge / RAG, Memory,
Skills, Agent Runtime, MCP — all of them. Deterministic software solves the
problem, and every AI capability would add cost and a way to be wrong.

## Unresolved questions

None for this use case.
