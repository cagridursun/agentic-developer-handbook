package dev.agentic.handbook.labs.mcp;

import java.util.Map;

/**
 * Everything a model may decide in this runtime: give the final answer, or
 * request exactly one tool. Unchanged from Lab 07.
 *
 * <p>Note what is missing: there is no "local" or "remote" in a tool request.
 * The model names a capability; where that capability lives is an
 * infrastructure decision the application makes after validation.
 */
public sealed interface ModelDecision permits ModelDecision.FinalAnswer, ModelDecision.ToolRequest {

    /** The model answered the goal. The run stops. */
    record FinalAnswer(String text) implements ModelDecision {
    }

    /**
     * The model proposes one tool call. A proposal, not an invocation: the
     * runtime decides whether it is allowed, valid, and executable — and, in
     * this lab, whether it runs locally or through MCP.
     */
    record ToolRequest(String name, Map<String, Object> arguments) implements ModelDecision {
    }
}
