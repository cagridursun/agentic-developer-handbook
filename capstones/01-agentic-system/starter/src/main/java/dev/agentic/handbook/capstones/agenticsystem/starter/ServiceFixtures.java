package dev.agentic.handbook.capstones.agenticsystem.starter;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic fictional platform data for the learner's tools. Read-only by
 * design: the capstone allows no side-effecting actions.
 *
 * <p>How these fixtures are exposed to a model — as tools, as pre-fetched
 * context, or not at all — is an architecture decision that belongs in
 * DECISIONS.md, not in this class.
 */
public final class ServiceFixtures {

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

    private ServiceFixtures() {
    }

    public static Set<String> knownServices() {
        return SERVICE_STATUS.keySet();
    }

    public static Optional<Map<String, Object>> serviceStatus(String serviceName) {
        return Optional.ofNullable(SERVICE_STATUS.get(normalize(serviceName)));
    }

    public static Optional<Map<String, Object>> recentDeployment(String serviceName) {
        return Optional.ofNullable(RECENT_DEPLOYMENTS.get(normalize(serviceName)));
    }

    private static String normalize(String serviceName) {
        return serviceName == null ? "" : serviceName.trim().toLowerCase(Locale.ROOT);
    }
}
