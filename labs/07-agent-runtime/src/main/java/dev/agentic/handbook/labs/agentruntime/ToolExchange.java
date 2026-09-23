package dev.agentic.handbook.labs.agentruntime;

import java.util.Map;

/**
 * One completed tool round: what the model requested and what the application
 * observed when it executed the request. The result is not the final answer —
 * it is an observation the model sees before its next decision.
 */
public record ToolExchange(
        ModelDecision.ToolRequest request,
        Map<String, Object> result) {
}
