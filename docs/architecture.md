# Architecture

This document records how the reference implementation will be built. It does not specify classes, packages, or a runtime. Those decisions will be written as ADRs when a milestone needs them.

The accepted decisions so far are in [adr/](adr/README.md).

## Java-first reference implementation

Java 21 is the language of the canonical labs. Conceptual documentation stays free of Java where the idea does not depend on it. Other languages are not promised as parallel implementations.

The root Maven project is a parent POM. It targets Java 21, uses UTF-8, and has no modules yet, because there is no lab yet. The first lab will add the first module. See [ADR 0001](adr/0001-java-first.md).

## Progressive labs

Each lab adds one meaningful capability to the previous one. A reader should be able to run the lab and see what that capability changed. Labs are not created until the milestone that teaches them. See [ADR 0003](adr/0003-progressive-learning-model.md) and [labs/README.md](../labs/README.md).

## No unnecessary framework abstraction

The project will not grow its own general-purpose agent framework. Where an existing library is the clearest way to show a concept, the lab will use that library. Spring Boot and Spring AI are likely candidates later. They are not dependencies of this foundation.

A new abstraction has to earn its place by teaching something or by protecting a boundary. Hiding a simple call behind a premature interface does neither. See [ADR 0002](adr/0002-not-an-agent-framework.md).

## Framework APIs stay at the boundary

When a framework or a provider SDK appears, its types should not spread through the whole codebase. Application code that expresses the handbook's concepts should talk to those libraries at a small edge. That keeps a lab readable if the library changes, and it keeps provider SDK types out of the project's own APIs.

This is a constraint for later milestones. There is no framework edge to draw yet.

## Examples stay runnable

A lab that cannot be built and run has not finished the concept. The default path must run without a paid API key, so a reader and CI can both execute it. A lab that demonstrates a live provider call will make that call opt-in.

## CI does not need paid API keys

GitHub Actions runs `./mvnw verify` on pull requests and on pushes to `main`. That job checks out the code, uses Java 21, caches Maven dependencies, and stops. It has no secrets.

External model-provider integration tests, when they exist, will be opt-in and will stay out of the default verify path.

## Simplest useful form first

A concept appears first in the smallest form that still teaches it. Structured output begins as "validate a response against a schema", not as a survey of every provider's JSON mode. RAG begins only when a lookup problem actually needs retrieval. The production concerns — evaluation, observability, security, deployment — are later milestones because they are about operating the system, not about defining the first call.

## What is intentionally absent

Milestone 0 has no application code, no Spring Boot, no Spring AI, no provider SDK, and no database, broker, or container runtime. Adding them now would describe an architecture the repository does not have.
