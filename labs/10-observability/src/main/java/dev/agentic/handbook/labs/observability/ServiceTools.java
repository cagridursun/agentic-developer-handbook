package dev.agentic.handbook.labs.observability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The two read-only tools, over the same fictional "Helio" platform as Labs 07
 * to 09 — plain, deterministic, local Java. Observability does not need a
 * remote capability to teach its lesson; the same spans would wrap an MCP-backed
 * call (see the lab README).
 *
 * <p>The scenario data is fixed on purpose, so a trace in the README can be
 * compared with the trace you produce. The data is identical to Lab 09.
 */
public final class ServiceTools {

    private static final Map<String, Map<String, Object>> SERVICE_STATUS = Map.of(
            "notifications", status("notifications", "DEGRADED",
                    "Elevated delivery latency and retries since 14:05 UTC."),
            "billing", status("billing", "HEALTHY", "All checks are passing."),
            "search", status("search", "MAINTENANCE", "Planned index rebuild until 16:00 UTC."),
            "checkout", status("checkout", "DEGRADED",
                    "Elevated request latency since 15:20 UTC."));

    private static final Map<String, Map<String, Object>> RECENT_DEPLOYMENTS = Map.of(
            "notifications", deployment("notifications", "notifications-2.4.1",
                    "2026-09-23T13:52:00Z", "Retry policy adjustment for the email delivery worker."));

    private ServiceTools() {
    }

    /** The services this fictional platform knows, in a stable order. */
    public static Set<String> knownServices() {
        return new TreeSet<>(SERVICE_STATUS.keySet());
    }

    /** Current operational status of a known service. */
    public static Map<String, Object> getServiceStatus(String serviceName) {
        return SERVICE_STATUS.get(requireKnown(serviceName));
    }

    /** The most recent deployment of a known service, if any. */
    public static Map<String, Object> getRecentDeployment(String serviceName) {
        String normalized = requireKnown(serviceName);
        Map<String, Object> deployment = RECENT_DEPLOYMENTS.get(normalized);
        if (deployment == null) {
            Map<String, Object> none = new LinkedHashMap<>();
            none.put("serviceName", normalized);
            none.put("recentDeployment", "none within the last 7 days");
            return Collections.unmodifiableMap(none);
        }
        return deployment;
    }

    private static String requireKnown(String serviceName) {
        String normalized = serviceName == null ? "" : serviceName.trim().toLowerCase(Locale.ROOT);
        if (!SERVICE_STATUS.containsKey(normalized)) {
            throw new IllegalArgumentException(
                    "Unknown service '" + normalized + "'. Known services: " + knownServices());
        }
        return normalized;
    }

    // Insertion-ordered so printed observations are identical on every run.
    private static Map<String, Object> status(String serviceName, String status, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serviceName", serviceName);
        result.put("status", status);
        result.put("message", message);
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Object> deployment(String serviceName, String version,
            String deployedAt, String summary) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serviceName", serviceName);
        result.put("version", version);
        result.put("deployedAt", deployedAt);
        result.put("summary", summary);
        return Collections.unmodifiableMap(result);
    }
}
