package dev.agentic.handbook.labs.mcp;

/**
 * The runtime's view of a model: given the goal or the latest observation,
 * what is the next decision? The same single seam as Lab 07 — deterministic
 * loop tests and provider translation kept out of loop semantics.
 *
 * <p>An implementation is stateful for one run. Create a new instance per run.
 */
public interface AgentModel {

    /** The first decision, given the user's goal. */
    ModelDecision start(String goal);

    /** The next decision, after the runtime executed a tool and observed the result. */
    ModelDecision observe(ToolExchange exchange);
}
