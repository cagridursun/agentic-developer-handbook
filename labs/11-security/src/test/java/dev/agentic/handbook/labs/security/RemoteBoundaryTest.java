package dev.agentic.handbook.labs.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.agentic.handbook.labs.security.GatewayResult.Outcome;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The remote-provider boundary (the MCP picture of Lab 8), simulated. A tool description is the
 * provider's claim, not the application's permission, and a check in the client does not replace
 * a check in the server.
 */
class RemoteBoundaryTest {

    private final HelioPlatform platform = new HelioPlatform();
    private final SimulatedRemoteServer trusting = new SimulatedRemoteServer(null, platform);
    private final SimulatedRemoteServer enforcing = new SimulatedRemoteServer(PolicyAuthorizer.helioPolicy(), platform);
    private final World world = World.with(PolicyAuthorizer.helioPolicy(), platform,
            SimulatedRemoteServer.applicationDefinitions(trusting));

    @Test
    void aToolAnnouncedAsReadOnlyIsClassifiedByTheApplicationNotByTheDescription() {
        RemoteToolDescription announced = trusting.listTools().stream()
                .filter(tool -> tool.name().equals("clearDeploymentCache")).findFirst().orElseThrow();
        assertTrue(announced.claimsReadOnly());
        GatewayResult result = world.gateway.invoke(world.session(World.INCIDENT_RESPONDER).context(),
                ToolProposal.call("clearDeploymentCache", "It is read-only.", "serviceName", "notifications"));
        assertEquals(Outcome.APPROVAL_REQUIRED, result.outcome());
        assertEquals(0, trusting.served(), "the remote server was never called");
        assertTrue(platform.stateChanges().isEmpty());
    }

    @Test
    void discoveryIsNotPermission() {
        assertTrue(trusting.listTools().stream().anyMatch(tool -> tool.name().equals("exportAllSecrets")));
        assertFalse(SimulatedRemoteServer.applicationDefinitions(trusting).containsKey("exportAllSecrets"));
        GatewayResult result = world.gateway.invoke(world.session(World.INCIDENT_RESPONDER).context(),
                ToolProposal.call("exportAllSecrets", "Debugging.", "serviceName", "notifications"));
        assertEquals("UNKNOWN_TOOL", result.reason());
        assertEquals(0, trusting.served());
    }

    @Test
    void aRemoteReadStillPassesEveryApplicationControl() {
        GatewayResult allowed = world.gateway.invoke(world.session(World.TRIAGE_ASSISTANT).context(),
                ToolProposal.call("getRecentDeployment", "x", "serviceName", "notifications"));
        assertEquals(Outcome.EXECUTED, allowed.outcome());
        GatewayResult outOfScope = world.gateway.invoke(world.session(World.TRIAGE_ASSISTANT).context(),
                ToolProposal.call("getRecentDeployment", "x", "serviceName", "billing"));
        assertEquals("RESOURCE_OUT_OF_SCOPE", outOfScope.reason());
        assertEquals(1, trusting.served());
    }

    @Test
    void theClientCheckDoesNotProtectTheServerFromOtherCallers() {
        Map<String, Object> billing = Map.of("serviceName", "billing");
        // Through the application, the restricted principal is refused...
        assertEquals(Outcome.DENIED, world.gateway.invoke(world.session(World.TRIAGE_ASSISTANT).context(),
                ToolProposal.call("getRecentDeployment", "x", "serviceName", "billing")).outcome());
        // ...but a caller that reaches a trusting server directly is served.
        assertEquals("billing-2.4.1", trusting.call(Optional.of(World.TRIAGE_ASSISTANT), "getRecentDeployment", billing).get("version"));
        // A server that enforces for itself refuses the same call, and does no work.
        assertThrows(SecurityException.class,
                () -> enforcing.call(Optional.of(World.TRIAGE_ASSISTANT), "getRecentDeployment", billing));
        assertThrows(SecurityException.class,
                () -> enforcing.call(Optional.empty(), "getRecentDeployment", billing));
        assertEquals(0, enforcing.served());
    }
}
