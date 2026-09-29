package dev.agentic.handbook.labs.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The capability that stays local: plain, deterministic Java over the same
 * fictional "Helio" platform as Lab 07. No protocol, no process boundary — a
 * method call in this JVM is still the right design for data this application
 * owns.
 *
 * <p>Lab 07's {@code getRecentDeployment} is gone from this class. That data
 * now belongs to the deployment team's MCP server, and this process does not
 * have it.
 */
public final class ServiceStatusTool {

    private static final Map<String, Map<String, Object>> SERVICE_STATUS = Map.of(
            "notifications", status("notifications", "DEGRADED",
                    "Elevated delivery latency and retries since 14:05 UTC."),
            "billing", status("billing", "HEALTHY", "All checks are passing."),
            "search", status("search", "MAINTENANCE", "Planned index rebuild until 16:00 UTC."));

    private ServiceStatusTool() {
    }

    /** The services this fictional platform knows, in a stable order. */
    public static Set<String> knownServices() {
        return new TreeSet<>(SERVICE_STATUS.keySet());
    }

    /** Current operational status of a known service. */
    public static Map<String, Object> getServiceStatus(String serviceName) {
        String normalized = serviceName == null ? "" : serviceName.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> status = SERVICE_STATUS.get(normalized);
        if (status == null) {
            throw new IllegalArgumentException(
                    "Unknown service '" + normalized + "'. Known services: " + knownServices());
        }
        return status;
    }

    // Insertion-ordered so the printed trace is identical on every run.
    private static Map<String, Object> status(String serviceName, String status, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serviceName", serviceName);
        result.put("status", status);
        result.put("message", message);
        return Collections.unmodifiableMap(result);
    }
}
