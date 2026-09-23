package dev.agentic.handbook.labs.agentruntime;

/**
 * The runtime's view of a model: given the goal or the latest observation,
 * what is the next decision?
 *
 * <p>This is the first interface in the canonical labs, and it exists because
 * a concrete need finally appeared: the execution loop must be testable
 * deterministically, without a network, and provider payload translation must
 * stay out of loop semantics. It is one seam, not a provider framework — there
 * is no factory, registry, or strategy behind it.
 *
 * <p>An implementation is stateful for one run: it owns whatever conversation
 * history its provider requires. Create a new instance per run.
 */
public interface AgentModel {

    /** The first decision, given the user's goal. */
    ModelDecision start(String goal);

    /** The next decision, after the runtime executed a tool and observed the result. */
    ModelDecision observe(ToolExchange exchange);
}
