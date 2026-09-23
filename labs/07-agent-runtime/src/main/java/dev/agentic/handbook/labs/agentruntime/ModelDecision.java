package dev.agentic.handbook.labs.agentruntime;

import java.util.Map;

/**
 * Everything a model may decide in this runtime: give the final answer, or
 * request exactly one tool. There is deliberately nothing else — no plans, no
 * task graphs, no parallel calls. The sealed hierarchy makes the whole
 * decision space visible at a glance.
 */
public sealed interface ModelDecision permits ModelDecision.FinalAnswer, ModelDecision.ToolRequest {

    /** The model answered the goal. The run stops. */
    record FinalAnswer(String text) implements ModelDecision {
    }

    /**
     * The model proposes one tool call. A proposal, not an invocation: the
     * runtime decides whether it is allowed, valid, and executable.
     */
    record ToolRequest(String name, Map<String, Object> arguments) implements ModelDecision {
    }
}
