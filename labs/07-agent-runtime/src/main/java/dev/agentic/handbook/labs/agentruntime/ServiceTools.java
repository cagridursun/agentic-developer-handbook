package dev.agentic.handbook.labs.agentruntime;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The two read-only tools of this lab: plain, deterministic, local Java over a
 * fictional platform (the same invented "Helio" flavor as Lab 04, without any
 * dependency on it). No HTTP, no database, no MCP, no side effects.
 *
 * <p>The data is written so a model can observe correlation — a degraded
 * service and a deployment shortly before — without being handed a proven
 * root cause.
 */
public final class ServiceTools {

    private static final Map<String, Map<String, Object>> SERVICE_STATUS = Map.of(
            "notifications", Map.of(
                    "serviceName", "notifications",
                    "status", "DEGRADED",
                    "message", "Elevated delivery latency and retries since 14:05 UTC."),
            "billing", Map.of(
                    "serviceName", "billing",
                    "status", "HEALTHY",
                    "message", "All checks are passing."),
            "search", Map.of(
                    "serviceName", "search",
                    "status", "MAINTENANCE",
                    "message", "Planned index rebuild until 16:00 UTC."));

    private static final Map<String, Map<String, Object>> RECENT_DEPLOYMENTS = Map.of(
            "notifications", Map.of(
                    "serviceName", "notifications",
                    "version", "notifications-2.4.1",
                    "deployedAt", "2026-09-23T13:52:00Z",
                    "summary", "Retry policy adjustment for the email delivery worker."));

    private ServiceTools() {
    }

    /** The services this fictional platform knows. */
    public static Set<String> knownServices() {
        return SERVICE_STATUS.keySet();
    }

    /** Current operational status of a known service. */
    public static Map<String, Object> getServiceStatus(String serviceName) {
        return require(SERVICE_STATUS, serviceName);
    }

    /** The most recent deployment of a known service, if any. */
    public static Map<String, Object> getRecentDeployment(String serviceName) {
        String normalized = normalize(serviceName);
        requireKnown(normalized);
        Map<String, Object> deployment = RECENT_DEPLOYMENTS.get(normalized);
        if (deployment == null) {
            return Map.of(
                    "serviceName", normalized,
                    "recentDeployment", "none within the last 7 days");
        }
        return deployment;
    }

    private static Map<String, Object> require(Map<String, Map<String, Object>> data, String serviceName) {
        String normalized = normalize(serviceName);
        requireKnown(normalized);
        return data.get(normalized);
    }

    private static void requireKnown(String normalized) {
        if (!SERVICE_STATUS.containsKey(normalized)) {
            throw new IllegalArgumentException(
                    "Unknown service '" + normalized + "'. Known services: " + knownServices());
        }
    }

    private static String normalize(String serviceName) {
        return serviceName == null ? "" : serviceName.trim().toLowerCase(Locale.ROOT);
    }
}
