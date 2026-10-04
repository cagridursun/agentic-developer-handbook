# ADR 0006: State-changing tool invocations pass through application-owned authorization

## Status

Accepted

## Context

The project principles already say "the LLM is never the authorization layer" and "validate tool inputs at application boundaries". Until Milestone 11 every runnable tool in the handbook was read-only, so the principle was a statement of intent: nothing in the code needed it to be true.

Milestone 11 introduces tools that change state. For those, the principle has to become a rule that code and later work can be checked against. A model's output is text, and text is what prompt injection controls. A rule that depends on the model behaving, such as a prompt that says not to, is not a rule an application can rely on. The same gap appears when a tool is reached through a protocol: a remote provider's description of a tool is a claim, not a classification.

## Decision

Every invocation of a tool that changes state passes through authorization owned and enforced by the application, and that authorization is independent of the model's output.

- A model proposal is an input to the decision, never the decision. Its justification is not read by any control.
- The application classifies a tool as read-only or state-changing from what the tool does, in its own code. A description, annotation, or claim from a model or a remote provider does not classify it.
- The decision uses inputs the model does not control: the principal the caller supplies, the capability the tool needs, the validated target, the operation type, and the application's own approval record.
- The check fails closed: a missing principal, a missing or broken policy, or an error in the check is a denial.
- A state change that needs human approval is approved for one exact operation (tool, target, arguments, requester), is invalidated if any of those change, and is checked again immediately before execution.
- The check is enforced where the tool is executed. A control that only records, or that only runs on the client side of a remote tool, does not satisfy this decision.

[Lab 11](../../labs/11-security/README.md) is the reference implementation of this rule at teaching scale. It is not an identity system, a policy engine, or an approval platform, and this ADR does not require one.

## Consequences

- Later labs, the capstone reference, and the future reference application must route every state-changing tool through an application-owned authorization step. A read-only tool may rely on validation and the allowlist alone, if the data it returns is not sensitive.
- A new state-changing capability needs a stated capability, scope, and operation type, and a test that a denied proposal never reaches the tool.
- Observability is not an enforcement mechanism: a trace may record a denial, and the denial must happen whether or not the trace is read.
- Remote tool providers are untrusted until the application classifies each tool itself, and a server that executes a state change enforces authorization for itself in addition to the application's check.
- What counts as a real identity, a real policy engine, or a real approval workflow is deliberately left open; the decision binds the boundary, not the technology.
