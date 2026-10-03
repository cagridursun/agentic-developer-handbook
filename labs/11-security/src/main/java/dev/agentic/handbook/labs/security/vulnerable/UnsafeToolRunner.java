package dev.agentic.handbook.labs.security.vulnerable;

import dev.agentic.handbook.labs.security.HelioPlatform;
import dev.agentic.handbook.labs.security.ToolProposal;
import java.util.Map;

/**
 * <b>INTENTIONALLY VULNERABLE. Educational and simulated. In memory only.</b>
 *
 * <p>Runs whatever the model proposes: no allowlist beyond a switch, no
 * validation, no principal, no authorization, no approval, no result filtering, no
 * trace. It exists so a reader can see what the controls of Lab 11 are for. It is
 * never used by the secure path, and nothing in the secure path refers to it.
 */
final class UnsafeToolRunner {

    private UnsafeToolRunner() {
    }

    static Map<String, Object> run(HelioPlatform platform, ToolProposal proposal) {
        Map<String, Object> args = proposal.arguments();
        return switch (proposal.tool()) {
            case "getServiceStatus" -> platform.getServiceStatus(String.valueOf(args.get("serviceName")));
            case "getIncidentNote" -> platform.getIncidentNote(String.valueOf(args.get("serviceName")),
                    String.valueOf(args.get("incidentId")));
            case "restartService" -> platform.restartService(String.valueOf(args.get("serviceName")),
                    String.valueOf(args.get("strategy")), Integer.parseInt(String.valueOf(args.get("gracePeriodSeconds"))));
            case "rollbackDeployment" -> platform.rollbackDeployment(String.valueOf(args.get("serviceName")),
                    String.valueOf(args.get("targetVersion")));
            default -> Map.of("error", "unknown tool");
        };
    }
}
