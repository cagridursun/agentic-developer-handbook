# Roadmap

Milestones are ordered. They are not dated.

Milestones 0 through 3 are done. Later milestones are planned work and are not implemented.

The learning path is a teaching sequence, not an architecture every application must implement. Skip a concept when the problem does not need it.

## Milestone 0 — Foundation

Done.

- Repository skeleton
- Handbook documents: vision, mental model, glossary, architecture
- Architecture decision records
- Contribution model, code of conduct, and security reporting
- Continuous integration
- A Maven build that succeeds without application code and without API keys

## Milestone 1 — The Model

Done. The lab is [labs/01-model-call](labs/01-model-call/README.md).

- A real LLM call
- The difference between a model and an agent
- What an inference provider is
- The first Java lab

## Milestone 2 — Structured Output

Done. The lab is [labs/02-structured-output](labs/02-structured-output/README.md).

Constrain a model response to a schema the application can validate.

## Milestone 3 — Tools

Done. The lab is [labs/03-tool-calling](labs/03-tool-calling/README.md).

Let the application perform an action the model can only request.

## Milestone 4 — Knowledge / RAG

Not started.

Bring outside information into the prompt. Include the cases where an ordinary query is the right tool and retrieval is not.

## Milestone 5 — Memory

Not started.

Decide what the application keeps across turns, and what it should forget.

## Milestone 6 — Skills

Not started.

Show Agent Skills / `SKILL.md` as instructions for how a task should be performed. A few example skills, not a catalog and not a skill runtime. See [ADR 0004](docs/adr/0004-skills-are-a-building-block.md).

## Milestone 7 — Agent Runtime

Not started.

An execution loop that can call a model, use tools, and stop. This is the first point at which the handbook will call the example an agent.

## Milestone 8 — MCP

Not started.

Connect the runtime to external capabilities through the Model Context Protocol. Show why a local Java call is not a reason to introduce MCP.

## Milestone 9 — Evaluation

Not started.

Check behavior with tests and reviewable examples. Paid provider calls stay opt-in so normal CI does not need API keys.

## Milestone 10 — Observability

Not started.

Logs, metrics, and traces for model calls and tool execution. OpenTelemetry is the intended direction when this milestone starts. It is not a dependency yet.

## Milestone 11 — Security

Not started.

Boundaries: user input, model output, tool arguments, retrieved content, skill text, and protocol messages. The model is not the authorization layer.

## Milestone 12 — Deployment

Not started.

What has to be true to run the reference system as a deployed application. Docker is the intended packaging direction when this milestone starts. It is not part of the repository yet.

## Later

Not scheduled as numbered milestones.

- Multi-agent systems
- A2A
- Fine-tuning
- Additional inference providers
- Curated community examples
- A composed reference application

These topics belong in the handbook. The first three wait until the single-runtime path is concrete enough that adding them teaches something new. Curated community examples extend concepts the canonical path has already taught, and the composed reference application comes after the path is mature enough to show how justified capabilities fit together — they grow alongside the numbered milestones, not instead of them. The contribution rules are in [CONTRIBUTING.md](CONTRIBUTING.md).

## How to read this list

[labs/README.md](labs/README.md) describes the lab format. The labs themselves will be added with the milestone that needs them. Empty lab directories are not created in advance.
