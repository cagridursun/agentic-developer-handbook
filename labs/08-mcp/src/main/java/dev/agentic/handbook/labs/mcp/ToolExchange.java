package dev.agentic.handbook.labs.mcp;

import java.util.Map;

/**
 * One completed tool round: what the model requested, where the application
 * routed it, and what the application observed.
 *
 * <p>The route is new in this lab, and it is recorded by the runtime, never
 * requested by the model. The model sees the same observation whether the
 * capability ran in this process or behind an MCP server.
 */
public record ToolExchange(
        ModelDecision.ToolRequest request,
        Route route,
        Map<String, Object> result) {

    /** Where the application executed a validated tool request. */
    public enum Route {
        /** A plain Java method in this process, exactly as in Lab 07. */
        LOCAL,
        /** A tools/call request through the MCP client to a separate server process. */
        MCP
    }
}
