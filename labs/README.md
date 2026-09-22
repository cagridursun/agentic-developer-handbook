# Labs

Labs are the runnable half of the handbook. Each one adds a single concept to the previous lab and leaves a build that still works.

The first two labs are available: [01-model-call](01-model-call/README.md) and [02-structured-output](02-structured-output/README.md). Later labs will be added with their milestones. Empty lab directories are not created in advance.

## What every lab answers

1. What problem are we solving?
2. Why does this concept exist?
3. When should I use it?
4. When should I not use it?
5. What does the architecture look like?
6. How does the Java implementation work?
7. What changes in production?

A lab that cannot answer "when should I not use it" is incomplete. The point of the sequence is to know when to stop adding parts.

## How the sequence fits together

The labs follow [ROADMAP.md](../ROADMAP.md). Early labs are small on purpose. The first lab is a model call. The handbook will not call that program an agent. The agent runtime comes only after tools, knowledge, memory, and skills have each been introduced on their own.

Evaluation, observability, security, and deployment come after there is a system worth operating. They are part of the path, not an afterword.

The concepts those labs depend on are defined in [docs/mental-model.md](../docs/mental-model.md) and [docs/glossary.md](../docs/glossary.md).

## Labs

| Lab | Milestone |
| --- | --- |
| [`01-model-call`](01-model-call/README.md) | 1 — The Model |
| [`02-structured-output`](02-structured-output/README.md) | 2 — Structured Output |
| `03-tool-calling` | 3 — Tools |
| `04-rag` | 4 — Knowledge / RAG |
| `05-memory` | 5 — Memory |
| `06-skills` | 6 — Skills |
| `07-agent-runtime` | 7 — Agent Runtime |
| `08-mcp` | 8 — MCP |
| `09-evaluation` | 9 — Evaluation |
| `10-observability` | 10 — Observability |
| `11-security` | 11 — Security |
| `12-production` | 12 — Deployment |

Labs without links are reserved names for later milestones. Their directories do not exist yet. Multi-agent systems, A2A, and fine-tuning are not in this list. They are later topics in the roadmap.

## Running a lab

From the repository root, `./mvnw verify` (or `mvnw.cmd verify` on Windows) builds every lab and runs the tests. It does not call a model and does not need an API key.

Running a lab against the real provider needs an API key. Each lab's README shows its exact commands; for the first lab see [01-model-call](01-model-call/README.md).
