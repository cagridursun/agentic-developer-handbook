# Labs

Labs are the runnable half of the handbook. Each one adds a single concept to the previous lab and leaves a build that still works.

Eight labs are available: [01-model-call](01-model-call/README.md), [02-structured-output](02-structured-output/README.md), [03-tool-calling](03-tool-calling/README.md), [04-rag](04-rag/README.md), [05-memory](05-memory/README.md), [06-skills](06-skills/README.md), [07-agent-runtime](07-agent-runtime/README.md) — the first lab this handbook calls an agent — and [08-mcp](08-mcp/README.md), where one of that agent's capabilities moves outside the process. Later labs will be added with their milestones. Empty lab directories are not created in advance.

## What every lab answers

1. What problem are we solving?
2. Why does this concept exist?
3. When should I use it?
4. When should I not use it?
5. What does the architecture look like?
6. How does the Java implementation work?
7. What changes in production?

A lab that cannot answer "when should I not use it" is incomplete. The point of the sequence is to know when to stop adding parts.

## Learning experience

Where it fits the concept, new labs follow an experience-first sequence rather than presenting the finished solution immediately:

1. Understand the limitation
2. Experience the naive behavior
3. Break or stress the naive approach
4. Build the capability
5. Verify the behavior
6. Reflect on the trade-offs
7. Decide whether the capability is actually needed

This is a pedagogical guideline, not a rigid template — natural technical headings beat mechanical ones. [05-memory](05-memory/README.md), [06-skills](06-skills/README.md), [07-agent-runtime](07-agent-runtime/README.md), and [08-mcp](08-mcp/README.md) are written this way.

## After Lab 07

With the agent runtime built, the first composition exercise is available: [Capstone 01 — Build a Small Agentic System](../capstones/01-agentic-system/README.md). Capstones combine concepts the labs have already taught — they never introduce a canonical concept, and they are not numbered labs. The centerpiece is a decisions-first exercise: justify which capabilities the problem needs, and which it deliberately does not.

Beside it, the [Agentic System Readiness Assessment](../assessments/agentic-system-readiness/README.md) turns the same judgment toward your own use case: a fixed order of questions, starting with ordinary software, that helps decide which of these concepts a problem deserves. Neither is a numbered lab; the canonical path continues with [Lab 08 — MCP](08-mcp/README.md), and then Milestone 9 (Evaluation).

## After Lab 08

Lab 08 keeps the Lab 07 agent and moves one capability behind an MCP server in a separate process. It adds no new agent: MCP standardizes how the application reaches a capability it does not own, and the application's allowlist — not the server's `tools/list` — decides what the runtime may use. Capstone 01 deliberately does not use MCP, because every capability in its scenario is local; the readiness assessment asks when a standardized external capability boundary is genuinely needed.

## How the sequence fits together

The labs follow [ROADMAP.md](../ROADMAP.md). Early labs are small on purpose. The first lab is a model call, and the handbook does not call that program an agent. The agent runtime — the first lab that earns the word — comes only after tools, knowledge, memory, and skills have each been introduced on their own.

Evaluation, observability, security, and deployment come after there is a system worth operating. They are part of the path, not an afterword.

The concepts those labs depend on are defined in [docs/mental-model.md](../docs/mental-model.md) and [docs/glossary.md](../docs/glossary.md).

## Labs

| Lab | Milestone |
| --- | --- |
| [`01-model-call`](01-model-call/README.md) | 1 — The Model |
| [`02-structured-output`](02-structured-output/README.md) | 2 — Structured Output |
| [`03-tool-calling`](03-tool-calling/README.md) | 3 — Tools |
| [`04-rag`](04-rag/README.md) | 4 — Knowledge / RAG |
| [`05-memory`](05-memory/README.md) | 5 — Memory |
| [`06-skills`](06-skills/README.md) | 6 — Skills |
| [`07-agent-runtime`](07-agent-runtime/README.md) | 7 — Agent Runtime |
| [`08-mcp`](08-mcp/README.md) | 8 — MCP |
| `09-evaluation` | 9 — Evaluation |
| `10-observability` | 10 — Observability |
| `11-security` | 11 — Security |
| `12-production` | 12 — Deployment |

Labs without links are reserved names for later milestones. Their directories do not exist yet. Multi-agent systems, A2A, and fine-tuning are not in this list. They are later topics in the roadmap.

## Running a lab

From the repository root, `./mvnw verify` (or `mvnw.cmd verify` on Windows) builds every lab and runs the tests. It does not call a model and does not need an API key.

Running a lab against the real provider needs an API key. Each lab's README shows its exact commands; for the first lab see [01-model-call](01-model-call/README.md).
