package dev.agentic.handbook.labs.evaluation;

/**
 * The runtime's view of a model: given the goal or the latest observation,
 * what is the next decision? The same single seam as Labs 07 and 08.
 *
 * <p>An implementation is stateful for one run. Create a new instance per run —
 * the evaluation harness does exactly that for every case.
 */
public interface AgentModel {

    /** The first decision, given the user's goal. */
    ModelDecision start(String goal);

    /** The next decision, after the runtime executed a tool and observed the result. */
    ModelDecision observe(ToolExchange exchange);
}
