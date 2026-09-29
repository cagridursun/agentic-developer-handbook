package dev.agentic.handbook.labs.mcp.server;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The deployment records owned by the (fictional) deployment team. In-memory,
 * deterministic, read-only: no database and no network.
 *
 * <p>Only the MCP server process reads this class. The agent application never
 * imports it; the only way the agent learns a deployment is by asking the
 * server over MCP.
 */
final class DeploymentRecords {

    private static final Set<String> KNOWN_SERVICES = Set.of("notifications", "billing", "search");

    private static final Map<String, Map<String, Object>> RECENT_DEPLOYMENTS = Map.of(
            "notifications", ordered(
                    "serviceName", "notifications",
                    "version", "notifications-2.4.1",
                    "deployedAt", "2026-09-23T13:52:00Z",
                    "summary", "Retry policy adjustment for the email delivery worker."));

    private DeploymentRecords() {
    }

    /** The services the deployment team tracks, in a stable order. */
    static Set<String> knownServices() {
        return new TreeSet<>(KNOWN_SERVICES);
    }

    /**
     * The most recent deployment of a known service, or an explicit "none"
     * record. An unknown service is an error, not an empty result.
     */
    static Map<String, Object> recentDeployment(String serviceName) {
        String normalized = serviceName.trim().toLowerCase(Locale.ROOT);
        if (!KNOWN_SERVICES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Unknown service '" + normalized + "'. Known services: " + knownServices());
        }
        Map<String, Object> deployment = RECENT_DEPLOYMENTS.get(normalized);
        if (deployment == null) {
            return ordered(
                    "serviceName", normalized,
                    "recentDeployment", "none within the last 7 days");
        }
        return deployment;
    }

    // Insertion-ordered so the JSON the server sends is identical on every run.
    private static Map<String, Object> ordered(String... keysAndValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            result.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return Collections.unmodifiableMap(result);
    }
}
