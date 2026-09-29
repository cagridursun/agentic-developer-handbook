package dev.agentic.handbook.labs.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The application's MCP client against real server processes: the lab's own
 * server, and test servers that misbehave the way a server owned by someone
 * else might. Discovery is tested as information; the allowlist is tested as
 * the decision.
 */
class DeploymentMcpClientTest {

    private static final String MISBEHAVING =
            "dev.agentic.handbook.labs.mcp.server.MisbehavingDeploymentServer";

    // --- Initialization, discovery, allowlist ---

    @Test
    void connectsNegotiatesAndApprovesTheAllowlistedTool() {
        try (DeploymentMcpClient deployments = DeploymentMcpClient.launch()) {
            DeploymentMcpClient.Discovery discovery = deployments.discovery();
            assertEquals("helio-deployments", discovery.serverName());
            assertEquals("2025-11-25", discovery.protocolVersion());
            assertEquals(List.of("getRecentDeployment"), discovery.announced());
            assertEquals(List.of("getRecentDeployment"), discovery.approved());
            assertEquals(List.of(), discovery.ignored());
        }
    }

    @Test
    void announcedButUnapprovedToolsAreNeverExposed() {
        try (DeploymentMcpClient deployments = DeploymentMcpClient.launch(MISBEHAVING, "extra-tool")) {
            DeploymentMcpClient.Discovery discovery = deployments.discovery();
            assertEquals(List.of("getRecentDeployment", "rollbackDeployment"), discovery.announced());
            // Discovery is not permission: the server offers two tools, the
            // application exposes one.
            assertEquals(List.of("getRecentDeployment"), discovery.approved());
            assertEquals(List.of("rollbackDeployment"), discovery.ignored());
            // The approved tool still works against this server.
            assertEquals("notifications-2.4.1",
                    deployments.getRecentDeployment("notifications").get("version"));
        }
    }

    @Test
    void allowlistIsFixedByTheApplication() {
        assertEquals(Set.of("getRecentDeployment"), DeploymentMcpClient.APPROVED_TOOLS);
    }

    @Test
    void missingApprovedToolFailsAtStartup() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DeploymentMcpClient.launch(MISBEHAVING, "missing-tool"));
        assertTrue(failure.getMessage().contains("does not announce the approved tool 'getRecentDeployment'"),
                failure.getMessage());
    }

    @Test
    void changedContractFailsAtStartup() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DeploymentMcpClient.launch(MISBEHAVING, "changed-contract"));
        assertTrue(failure.getMessage().contains("contract of 'getRecentDeployment' changed"),
                failure.getMessage());
    }

    // --- Calling the approved tool ---

    @Test
    void remoteResultBecomesAnOrdinaryObservation() {
        try (DeploymentMcpClient deployments = DeploymentMcpClient.launch()) {
            Map<String, Object> observation = deployments.getRecentDeployment("notifications");
            assertEquals(Map.of(
                    "serviceName", "notifications",
                    "version", "notifications-2.4.1",
                    "deployedAt", "2026-09-23T13:52:00Z",
                    "summary", "Retry policy adjustment for the email delivery worker."), observation);
            assertEquals(1, deployments.toolCallsSent());
        }
    }

    // --- Failures stay failures ---

    @Test
    void toolExecutionErrorIsNotDowngradedToSuccess() {
        try (DeploymentMcpClient deployments = DeploymentMcpClient.launch()) {
            RemoteToolException failure = assertThrows(RemoteToolException.class,
                    () -> deployments.getRecentDeployment("payments"));
            assertTrue(failure.getMessage().startsWith("MCP tool error from 'helio-deployments'"),
                    failure.getMessage());
            assertTrue(failure.getMessage().contains("Unknown service 'payments'"), failure.getMessage());
        }
    }

    @Test
    void protocolErrorSurfacesClearly() {
        try (DeploymentMcpClient deployments = DeploymentMcpClient.launch(MISBEHAVING, "crashing-tool")) {
            RemoteToolException failure = assertThrows(RemoteToolException.class,
                    () -> deployments.getRecentDeployment("notifications"));
            assertTrue(failure.getMessage().startsWith("MCP protocol error from 'misbehaving-deployments'"),
                    failure.getMessage());
            assertTrue(failure.getMessage().contains("deployment database unavailable"), failure.getMessage());
        }
    }

    @Test
    void successWithoutStructuredDataIsNotAnObservation() {
        try (DeploymentMcpClient deployments = DeploymentMcpClient.launch(MISBEHAVING, "text-only")) {
            RemoteToolException failure = assertThrows(RemoteToolException.class,
                    () -> deployments.getRecentDeployment("notifications"));
            assertTrue(failure.getMessage().contains("returned no structured result"), failure.getMessage());
        }
    }
}
