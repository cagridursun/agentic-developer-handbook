package dev.agentic.handbook.labs.observability;

import java.util.Map;

/**
 * Executes an already validated tool call. It is a seam so a test, or the
 * failure scenario, can make a tool fail the way a real dependency does; the
 * default is {@link #helio()}, the local Helio tools.
 */
@FunctionalInterface
public interface ToolBackend {

    Map<String, Object> execute(String tool, String serviceName);

    /** The two read-only Helio tools, as in Labs 07 to 09. */
    static ToolBackend helio() {
        return (tool, serviceName) -> switch (tool) {
            case "getServiceStatus" -> ServiceTools.getServiceStatus(serviceName);
            case "getRecentDeployment" -> ServiceTools.getRecentDeployment(serviceName);
            default -> throw new IllegalStateException("No implementation for allowlisted tool " + tool);
        };
    }
}
