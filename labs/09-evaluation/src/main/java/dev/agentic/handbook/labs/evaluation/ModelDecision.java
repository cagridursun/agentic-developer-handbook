package dev.agentic.handbook.labs.evaluation;

import java.util.Map;

/**
 * Everything a model may decide in this runtime: give the final answer, or
 * request exactly one tool. The same decision space as Lab 07.
 */
public sealed interface ModelDecision permits ModelDecision.FinalAnswer, ModelDecision.ToolRequest {

    /** The model answered the goal. The run stops. */
    record FinalAnswer(String text) implements ModelDecision {
    }

    /** The model proposes one tool call. A proposal, not an invocation. */
    record ToolRequest(String name, Map<String, Object> arguments) implements ModelDecision {
    }
}
