# ADR 0003: Progressive learning model

## Status

Accepted

## Context

Agentic systems are usually introduced as a finished stack: model, tools, retrieval, memory, protocols, and several agents at once. That stack hides which problem each piece solves. It also encourages copying pieces the task does not need.

The repository needs a teaching order that stays buildable after every step.

## Decision

The project teaches agentic systems incrementally. Each lab adds one meaningful capability to the previous lab.

The main sequence is: model call, structured output, tools, knowledge / RAG, memory, skills, agent runtime, MCP, evaluation, observability, security, and deployment. Multi-agent systems, A2A, and fine-tuning are later topics. They are not required stops on the path.

A lab should answer what problem the new concept solves, when to use it, and when not to. The code diff should be explainable as that one addition.

## Consequences

- Labs appear when their milestone starts. Empty future lab directories are not created.
- A pull request that implements several future concepts at once is too large for this model.
- A reader may stop in the middle of the path. The documents must make that a valid outcome.
- Later labs may depend on earlier ones. They should not require a concept that has not been introduced yet.
- The roadmap, not a calendar, is the order of work. See [ROADMAP.md](../../ROADMAP.md).
