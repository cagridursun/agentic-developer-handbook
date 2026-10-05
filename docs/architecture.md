# Architecture

This document records how the reference implementation will be built. It does not specify classes, packages, or a runtime. Those decisions will be written as ADRs when a milestone needs them.

The accepted decisions so far are in [adr/](adr/README.md).

## Java-first reference implementation

Java 27 is the language of the canonical labs. It is the current feature release rather than an LTS release; the handbook intentionally follows the current Java platform and does not use preview or incubator features. Conceptual documentation stays free of Java where the idea does not depend on it. Other languages are not promised as parallel implementations.

The root Maven project is a parent POM. It compiles with `--release 27` through a single `maven.compiler.release` property and uses UTF-8. Each lab is a child module; the first is [labs/01-model-call](../labs/01-model-call/README.md). See [ADR 0001](adr/0001-java-first.md) and [ADR 0005](adr/0005-upgrade-canonical-java-to-27.md).

## Progressive labs

Each lab adds one meaningful capability to the previous one. A reader should be able to run the lab and see what that capability changed. Labs are not created until the milestone that teaches them. See [ADR 0003](adr/0003-progressive-learning-model.md) and [labs/README.md](../labs/README.md).

## No unnecessary framework abstraction

The project will not grow its own general-purpose agent framework. Where an existing library is the clearest way to show a concept, the lab will use that library. Spring Boot and Spring AI are likely candidates later. They are not dependencies of this foundation.

A new abstraction has to earn its place by teaching something or by protecting a boundary. Hiding a simple call behind a premature interface does neither. See [ADR 0002](adr/0002-not-an-agent-framework.md).

## Framework APIs stay at the boundary

When a framework or a provider SDK appears, its types should not spread through the whole codebase. Application code that expresses the handbook's concepts should talk to those libraries at a small edge. That keeps a lab readable if the library changes, and it keeps provider SDK types out of the project's own APIs.

The labs draw these edges explicitly. In Lab 07, Gemini types stay inside `GeminiAgentModel`; the runtime sees only its own decision records. In Lab 08, MCP SDK types stay inside the application's MCP client and the server class; the runtime receives the same plain observation from a remote tool as from a local method.

## Examples stay runnable

A lab that cannot be built and run has not finished the concept. The default path must run without a paid API key, so a reader and CI can both execute it. A lab that demonstrates a live provider call will make that call opt-in.

## CI does not need paid API keys

GitHub Actions runs `./mvnw verify` on pull requests and on pushes to `main`. That job checks out the code, uses Java 27 (Temurin), caches Maven dependencies, and stops. It has no secrets.

External model-provider integration tests, when they exist, will be opt-in and will stay out of the default verify path.

## Evaluation is deterministic by default

Evaluation sits around the system, not inside it. [Lab 09](../labs/09-evaluation/README.md) runs the Lab 07 runtime against a small versioned set of cases and checks the trajectory and the final answer with deterministic code. The default run needs no key and makes no model call; scripted behaviors make the harness inspectable, and a live model is an opt-in system version evaluated by the same cases and checks. An evaluation trace is captured for a controlled run and judged against expected behavior. It is not an observability trace: the record of what one run actually did is the subject of the next section.

## Three planes, and the observability plane only observes

A model-backed system can be described as three planes. They are a way to think about responsibilities, not a layering every application must implement and not a package structure.

- **Control plane:** decision authority, validation, safety constraints, tool permissions. The application owns it. The allowlist and the step budget live here.
- **Execution plane:** model calls, tools, MCP, memory, and the observations that flow back. This is the work the run does.
- **Observability plane:** logs, metrics, and traces about the other two.

The observability plane **observes**; it never decides. Nothing the runtime branches on should come from telemetry, and the system must make the same decisions with recording switched off. [Lab 10](../labs/10-observability/README.md) instruments the Lab 07 runtime this way: a span records that the model proposed a tool, that the application validated it, and that the tool executed it, and recording the validation grants nothing. Keep it small: a trace id, a few spans, and a handful of counters teach the architecture, and a telemetry platform is not required to understand it. What may be recorded, and who may read it, is the next milestone's question.

## Security is the control plane at the execution boundary

The control plane above is where the application's decisions live, and [Lab 11](../labs/11-security/README.md) fills in the part of it that stands between a model's proposal and a tool. A proposal passes argument validation, an authorization decision that fails closed, and, for a state change, an approval bound to exactly that operation, and only then reaches the execution plane; what comes back is cut down to allowlisted fields and scrubbed. The decision uses inputs the model does not control. Its justification is recorded and never read by a control. Retrieved content and a remote provider's descriptions are untrusted input. A proposal that was steered by either is stopped by the same controls as any other.

This is a rule, not only a lab: [ADR 0006](adr/0006-application-owned-authorization-for-state-changing-tools.md) records that every state-changing tool invocation passes through application-owned authorization, independent of the model. The observability plane may record each decision as a security event, and still decides nothing. The lab is a teaching model in memory: it does not authenticate, run a policy engine, or operate an approval workflow. Running the application securely, with real credentials, configuration, and an identity source, is deployment, which [Lab 12](../labs/12-deployment/README.md) takes up.

## Deployment packages the boundary and does not redefine it

[Lab 12](../labs/12-deployment/README.md) puts the Lab 11 application boundary into one artifact and runs it. Deployment adds a way in (an HTTP door), a way to be configured (environment variables, validated before anything starts), a way to be probed (liveness and readiness), and a way to stop (graceful shutdown). It adds no authority. The door starts the same gateway the Lab 11 tests exercise: there is no endpoint that calls a tool directly, no setting that switches authorization off, and a startup self-check that refuses to start if the authorizer does not behave. A container, a network, or a loopback address is not an authorization boundary; the application's own decision is. Secrets are supplied at runtime and are never packaged into the artifact, the image, or a sample configuration. Observability keeps its place beside the application: the deployment's structured log reuses the Lab 11 redaction and records startup, readiness, requests, security decisions, and shutdown. Evaluation stays separate from the runtime. The lab is a teaching deployment: it records no decision beyond [ADR 0006](adr/0006-application-owned-authorization-for-state-changing-tools.md), and what production needs beyond it is the lab's production gap analysis.

## Simplest useful form first

A concept appears first in the smallest form that still teaches it. Structured output begins as "validate a response against a schema", not as a survey of every provider's JSON mode. RAG begins only when a lookup problem actually needs retrieval. Evaluation, observability, security, and deployment are covered by Labs 09, 10, 11, and 12. Deployment comes last because it is about operating the system, not about defining the first call.

## What is intentionally absent

There is still no Spring Boot, no Spring AI, no provider abstraction, and no database or broker. Lab 12 adds one Dockerfile and uses no container orchestrator. Lab 01 uses one provider SDK directly, without a wrapper, because one implementation does not justify an abstraction. Lab 08 uses the official MCP Java SDK the same way — directly, over STDIO, with no framework integration and no generic tool-plugin layer. Lab 09 evaluates with a plain-Java harness — no evaluation framework, no dataset format, no judge model, and no stored history. Lab 10 uses an in-memory, plain-Java trace model — no OpenTelemetry, no collector, no tracing or metrics backend, and no stored traces. Lab 11 adds plain-Java controls, simulated and in memory — no identity provider, no policy engine, no secrets manager, no approval platform, and no data loss prevention library. Lab 12 adds the JDK's built-in HTTP server and a Dockerfile: no web framework, no Kubernetes, no cloud service, no secrets platform, and no telemetry backend. Infrastructure arrives with the milestone that needs it.
