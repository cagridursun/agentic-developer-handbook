# Vision

Agentic Developer Handbook is a Java-first, open-source guide to understanding and building agentic systems.

The long-term aim is a single path from a plain model call to a production-oriented application, with a reason for every step and a working Java example when the step is real.

## The problem

Developers meet a crowd of terms at once: agent, tool calling, RAG, memory, skills, MCP, A2A, fine-tuning, multi-agent systems. The terms are used in product pages, talks, and framework docs as if they were interchangeable.

They are not interchangeable. A model call, a retrieval query, a tool, a skill file, and a second agent solve different problems. Treating them as one stack produces systems that are harder to test, operate, and explain than the task requires.

The missing piece is a mental model: what each concept means, how it differs from the neighbors, when it belongs in an architecture, and when it does not.

## The goal

Give developers that mental model, then let them build it.

Explanation comes first. Runnable Java code comes next, so a concept can be executed, inspected, and changed. The code is a reference implementation for learning. It is not a framework other applications are expected to adopt.

## The approach

One concept at a time, on one architectural journey.

Each step starts from the system as it stood before and adds one meaningful capability. Readers should be able to point at the diff and say what new problem that diff solves.

The canonical code is Java 21. The conceptual chapters stay language-independent where the idea does not depend on Java. See [ADR 0001](docs/adr/0001-java-first.md) and [ADR 0003](docs/adr/0003-progressive-learning-model.md).

## What we will cover

The handbook will work through these topics. Covering a topic means explaining it and, when it earns a place in the path, implementing the smallest useful Java lab.

- Models and inference providers
- Structured output
- Tools and tool calling
- Knowledge and RAG
- Memory
- Skills
- Agents and an agent runtime
- MCP
- Multi-agent systems
- A2A
- Fine-tuning
- Evaluation
- Observability
- Security
- Deployment

Multi-agent systems, A2A, and fine-tuning are part of the map. They are not early milestones. Most of the learning path is one model, one runtime, and the capabilities that runtime actually needs.

## What we will not become

This project will not become:

- another general-purpose agent framework
- a skill marketplace, registry, or installation platform
- a prompt repository
- a tutorial for one vendor's SDK
- a wrapper around every AI library
- a collection of links with no runnable code

Libraries such as Spring Boot and Spring AI may be used later, where they are the clearest way to show a concept. They are not the subject of the book. See [ADR 0002](docs/adr/0002-not-an-agent-framework.md) and [ADR 0004](docs/adr/0004-skills-are-a-building-block.md).

## Design philosophy

Prefer boring software engineering over unnecessary AI complexity.

Use the smallest mechanism that solves the problem. A prompt and a validated response are enough for many tasks. A tool is enough for many others. Another agent, a vector database, a fine-tune, or a new protocol needs a reason that a simpler design cannot meet.

The practical form of that rule is in [docs/mental-model.md](docs/mental-model.md). The order of work is in [ROADMAP.md](ROADMAP.md).
