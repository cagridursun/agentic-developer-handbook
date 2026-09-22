# ADR 0004: Skills are a building block

## Status

Accepted

## Context

Agent Skills, often packaged as `SKILL.md`, tell an agent how to perform a task. That is a useful concept, and it is easy to inflate. A directory of skills can start to look like the product: a marketplace, a search index, a rating system, or an installer.

Those systems do not explain how a skill differs from a tool, from retrieved knowledge, or from memory. They also invite the project to execute untrusted third-party instructions.

## Decision

Agent Skills are part of the handbook. They are not the product.

A skill answers: how should the agent perform this task? A tool answers: what can the agent do? Knowledge answers: what information does the agent need? Memory answers: what should the agent remember?

The Skills milestone will explain `SKILL.md` and include a few example skills. It will not add a skill runtime in Milestone 0, and it will not add a marketplace, search engine, rating system, installation platform, or community registry.

Third-party skill text is untrusted input. The project will not automatically execute scripts bundled with skills.

## Consequences

- Documentation and labs must present skills as one block in the [mental model](../mental-model.md), beside tools, knowledge, and memory.
- Contributors should not add skill discovery, publishing, or ranking features.
- Example skills, when they exist, are teaching material for this repository.
- A skill file does not, by itself, make a program an agent. The agent runtime milestone is a separate step.
