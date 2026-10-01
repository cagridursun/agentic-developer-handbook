package dev.agentic.handbook.labs.observability;

import java.util.Map;

/**
 * One completed tool round: what the model requested and what the application
 * observed. The observation is the evidence a final answer may be grounded in,
 * which is why the evaluation checks read it.
 */
public record ToolExchange(
        ModelDecision.ToolRequest request,
        Map<String, Object> result) {
}
