package dev.agentic.handbook.labs.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The application's tool table for the Helio incident scenario. Each tool is
 * classified by what its executor really does. {@code getServiceStatus} and
 * {@code getIncidentNote} change nothing; {@code restartService} and
 * {@code rollbackDeployment} change the platform.
 */
public final class HelioTools {

    static final String SERVICE_NAME = "[a-z][a-z0-9-]{1,29}";
    static final String INCIDENT_ID = "INC-[0-9]{4}";
    static final String VERSION = "[a-z]+-[0-9]+\\.[0-9]+\\.[0-9]+";

    private HelioTools() {
    }

    public static Map<String, ToolDefinition> definitions(HelioPlatform platform) {
        Map<String, ToolDefinition> tools = new LinkedHashMap<>();
        add(tools, new ToolDefinition("getServiceStatus", Capability.READ_SERVICE_STATUS, OperationType.READ,
                "serviceName", List.of(ArgumentRule.pattern("serviceName", SERVICE_NAME)),
                Set.of("serviceName", "status", "message"),
                (principal, args) -> platform.getServiceStatus((String) args.get("serviceName"))));
        add(tools, new ToolDefinition("getIncidentNote", Capability.READ_INCIDENT_NOTES, OperationType.READ,
                "serviceName", List.of(ArgumentRule.pattern("serviceName", SERVICE_NAME),
                        ArgumentRule.pattern("incidentId", INCIDENT_ID)),
                Set.of("incidentId", "serviceName", "summary", "note"),
                (principal, args) -> platform.getIncidentNote((String) args.get("serviceName"), (String) args.get("incidentId"))));
        add(tools, new ToolDefinition("restartService", Capability.RESTART_SERVICE, OperationType.STATE_CHANGING,
                "serviceName", List.of(ArgumentRule.pattern("serviceName", SERVICE_NAME),
                        ArgumentRule.oneOf("strategy", "ROLLING", "IMMEDIATE"),
                        ArgumentRule.integer("gracePeriodSeconds", 0, 300)),
                Set.of("serviceName", "result"),
                (principal, args) -> platform.restartService((String) args.get("serviceName"), (String) args.get("strategy"),
                        ((Number) args.get("gracePeriodSeconds")).intValue())));
        add(tools, new ToolDefinition("rollbackDeployment", Capability.ROLLBACK_DEPLOYMENT,
                OperationType.STATE_CHANGING, "serviceName",
                List.of(ArgumentRule.pattern("serviceName", SERVICE_NAME), ArgumentRule.pattern("targetVersion", VERSION)),
                Set.of("serviceName", "result"),
                (principal, args) -> platform.rollbackDeployment((String) args.get("serviceName"), (String) args.get("targetVersion"))));
        return tools;
    }

    private static void add(Map<String, ToolDefinition> tools, ToolDefinition tool) {
        tools.put(tool.name(), tool);
    }
}
