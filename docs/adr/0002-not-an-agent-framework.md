# ADR 0002: Not an agent framework

## Status

Accepted

## Context

New agent frameworks appear often. Each one adds its own runtime, abstractions, and vocabulary. This repository exists to explain the vocabulary developers already have, and to show it in runnable code.

A framework would become the product. The handbook would then spend its time on that framework's API instead of on the underlying concepts.

## Decision

This project will not implement another general-purpose agent framework.

It will use existing libraries where they are the clearest way to demonstrate a concept. Spring Boot and Spring AI are expected candidates in later milestones. They are not dependencies until a lab needs them.

The project will create only small abstractions that have a concrete educational or architectural purpose, such as keeping a provider SDK type from leaking through a lab's API. An abstraction that exists so the design looks complete is out of scope.

## Consequences

- There is no public agent API for other applications to embed.
- Contributors should not add a runtime, plugin system, or provider facade ahead of the milestone that needs it.
- When a library is used, the lab should show the library, not hide it behind a lookalike wrapper.
- Readers who want a framework should use an existing one. This repository can still help them decide which concepts that framework is implementing.
