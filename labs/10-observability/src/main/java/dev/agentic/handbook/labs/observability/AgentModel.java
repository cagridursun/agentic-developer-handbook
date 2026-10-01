package dev.agentic.handbook.labs.observability;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * The runtime's view of a model: given the goal or the latest observation,
 * what is the next decision? The same single seam as Labs 07 to 09.
 *
 * <p>An implementation is stateful for one run. Create a new instance per run.
 *
 * <p>The two default methods are the only addition in this lab: they let the
 * runtime <em>report</em> what the model call is, and what it cost when the
 * provider says so. They never influence a decision.
 */
public interface AgentModel {

    /** The first decision, given the user's goal. */
    ModelDecision start(String goal);

    /** The next decision, after the runtime executed a tool and observed the result. */
    ModelDecision observe(ToolExchange exchange);

    /** Which model this is, for the trace. */
    default ModelInfo info() {
        return new ModelInfo("unknown", "unknown");
    }

    /**
     * Token usage the provider reported for the most recent call, if any. Empty
     * means "not reported", never "zero": a model that does not report usage
     * must not be given invented numbers.
     */
    default Optional<TokenUsage> lastUsage() {
        return Optional.empty();
    }

    /** Provider and model name, as recorded on a model span. */
    record ModelInfo(String provider, String name) {
    }

    /**
     * Token counts as reported by the provider. Each field is independently
     * optional: absent when the provider did not report it.
     */
    record TokenUsage(OptionalInt input, OptionalInt output, OptionalInt total) {
    }
}
