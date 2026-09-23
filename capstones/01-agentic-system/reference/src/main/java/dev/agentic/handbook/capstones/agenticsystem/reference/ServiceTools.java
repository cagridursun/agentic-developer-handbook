package dev.agentic.handbook.capstones.agenticsystem.reference;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Tools decision of this reference: current facts come from authoritative,
 * deterministic, read-only application code — never from model recall. Same
 * fictional data as the starter fixtures.
 */
public final class ServiceTools {

    private static final Map<String, Map<String, Object>> SERVICE_STATUS = Map.of(
            "notifications", Map.of(
                    "serviceName", "notifications",
                    "status", "DEGRADED",
                    "message", "p99 delivery latency at 240 seconds; retry rate 9 percent since 14:05 UTC."),
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

    public static Set<String> knownServices() {
        return SERVICE_STATUS.keySet();
    }

    public static Map<String, Object> getServiceStatus(String serviceName) {
        String normalized = normalize(serviceName);
        requireKnown(normalized);
        return SERVICE_STATUS.get(normalized);
    }

    public static Map<String, Object> getRecentDeployment(String serviceName) {
        String normalized = normalize(serviceName);
        requireKnown(normalized);
        Map<String, Object> deployment = RECENT_DEPLOYMENTS.get(normalized);
        return deployment != null ? deployment
                : Map.of("serviceName", normalized, "recentDeployment", "none within the last 7 days");
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
