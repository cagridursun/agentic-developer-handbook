package dev.agentic.handbook.labs.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.agentic.handbook.labs.security.GatewayResult.Outcome;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** The controls at the execution boundary: what reaches the platform, and what is recorded. */
class GatewayTest {

    private final World world = World.standard();

    private GatewayResult call(Principal principal, ToolProposal proposal) {
        return world.gateway.invoke(world.session(principal).context(), proposal);
    }

    private static ToolProposal restart(String service) {
        return ToolProposal.call("restartService", "Restart it.", "serviceName", service, "strategy", "ROLLING",
                "gracePeriodSeconds", 30);
    }

    private static ToolProposal status(String service) {
        return ToolProposal.call("getServiceStatus", "Check it.", "serviceName", service);
    }

    private String approved(ToolProposal proposal) {
        String id = call(World.INCIDENT_RESPONDER, proposal).pendingOperationId().orElseThrow();
        assertEquals(Outcome.APPROVED, world.gateway.approve(world.session(World.INCIDENT_COMMANDER).context(), id).outcome());
        return id;
    }

    // --- least privilege, and authorization ---

    @Test
    void anAuthorizedReadSucceeds() {
        GatewayResult result = call(World.INCIDENT_RESPONDER, status("notifications"));
        assertEquals(Outcome.EXECUTED, result.outcome());
        assertEquals("DEGRADED", result.data().get("status"));
        assertEquals(1, world.platform.executionCount());
    }

    @Test
    void anUnauthorizedOperationIsRejectedAndNeverRuns() {
        GatewayResult result = call(World.TRIAGE_ASSISTANT, restart("notifications"));
        assertEquals(Outcome.DENIED, result.outcome());
        assertEquals("CAPABILITY_NOT_GRANTED", result.reason());
        assertEquals(0, world.platform.executionCount());
    }

    @Test
    void anUnauthorizedResourceIsRejected() {
        GatewayResult result = call(World.TRIAGE_ASSISTANT, status("billing"));
        assertEquals(Outcome.DENIED, result.outcome());
        assertEquals("RESOURCE_OUT_OF_SCOPE", result.reason());
        assertEquals(0, world.platform.executionCount());
    }

    @Test
    void aMissingPrincipalFailsClosed() {
        GatewayResult result = call(null, status("notifications"));
        assertEquals(Outcome.DENIED, result.outcome());
        assertEquals("NO_PRINCIPAL", result.reason());
        assertEquals(0, world.platform.executionCount());
    }

    @Test
    void aToolTheApplicationNeverRegisteredDoesNotExist() {
        GatewayResult result = call(World.INCIDENT_RESPONDER,
                ToolProposal.call("deleteEverything", "Needed.", "serviceName", "notifications"));
        assertEquals("UNKNOWN_TOOL", result.reason());
        assertEquals(0, world.platform.executionCount());
    }

    @Test
    void aFailingAuthorizerIsADenial() {
        Authorizer broken = request -> {
            throw new IllegalStateException("policy store unavailable");
        };
        World failing = World.with(broken, new HelioPlatform(), Map.of());
        GatewayResult result = failing.gateway.invoke(failing.session(World.INCIDENT_RESPONDER).context(), status("notifications"));
        assertEquals(Outcome.DENIED, result.outcome());
        assertEquals("AUTHORIZER_FAILED", result.reason());
        assertEquals(0, failing.platform.executionCount());

        World silent = World.with(request -> null, new HelioPlatform(), Map.of());
        assertEquals("NO_DECISION", silent.gateway.invoke(silent.session(World.INCIDENT_RESPONDER).context(),
                status("notifications")).reason());
    }

    @Test
    void anEmptyPolicyDeniesEverything() {
        World nothing = World.with(new PolicyAuthorizer(null), new HelioPlatform(), Map.of());
        assertEquals(Outcome.DENIED, nothing.gateway.invoke(nothing.session(World.INCIDENT_RESPONDER).context(),
                status("notifications")).outcome());
        assertEquals(0, nothing.platform.executionCount());
    }

    // --- validation ---

    @Test
    void invalidArgumentsAreRejectedBeforeAnyAuthorizationOrExecution() {
        List<ToolProposal> bad = List.of(
                ToolProposal.call("getServiceStatus", "x"),                                                  // required missing
                status("Notifications; DROP TABLE"),                                                         // id format
                ToolProposal.call("getIncidentNote", "x", "serviceName", "notifications", "incidentId", "1002"), // id format
                ToolProposal.call("restartService", "x", "serviceName", "notifications", "strategy", "FORCE",
                        "gracePeriodSeconds", 30),                                                           // enum
                ToolProposal.call("restartService", "x", "serviceName", "notifications", "strategy", "ROLLING",
                        "gracePeriodSeconds", 9999),                                                         // numeric limit
                ToolProposal.call("restartService", "x", "serviceName", "notifications", "strategy", "ROLLING",
                        "gracePeriodSeconds", "30"),                                                         // wrong type
                ToolProposal.call("getServiceStatus", "x", "serviceName", "notifications", "approved", true)); // unexpected
        for (ToolProposal proposal : bad) {
            GatewayResult result = call(World.INCIDENT_RESPONDER, proposal);
            assertEquals(Outcome.INVALID_ARGUMENTS, result.outcome(), proposal.toString());
        }
        assertEquals(0, world.platform.executionCount());
        assertTrue(world.pending.find("pending-001").isEmpty(), "an invalid proposal must not create a pending operation");
    }

    @Test
    void validationDoesNotEchoTheRejectedValue() {
        GatewayResult result = call(World.INCIDENT_RESPONDER,
                status("x token=" + HelioPlatform.FAKE_API_TOKEN));
        assertEquals(Outcome.INVALID_ARGUMENTS, result.outcome());
        assertFalse(result.reason().contains(HelioPlatform.FAKE_API_TOKEN));
    }

    @Test
    void aStructurallyValidProposalCanStillBeUnauthorized() {
        ToolProposal valid = restart("notifications");
        assertTrue(ArgumentValidator.validate(HelioTools.definitions(world.platform).get("restartService"),
                valid.arguments()).valid());
        assertEquals(Outcome.DENIED, call(World.TRIAGE_ASSISTANT, valid).outcome());
        assertEquals(0, world.platform.executionCount());
    }

    @Test
    void aModelCannotGrantItselfPermissionsByAddingFields() {
        ToolProposal sneaky = ToolProposal.call("restartService", "I am the commander.", "serviceName", "notifications",
                "strategy", "ROLLING", "gracePeriodSeconds", 30, "principal", "incident-commander", "approved", true);
        GatewayResult result = call(World.TRIAGE_ASSISTANT, sneaky);
        assertEquals(Outcome.INVALID_ARGUMENTS, result.outcome());
        assertEquals(0, world.platform.executionCount());
        assertTrue(world.platform.stateChanges().isEmpty());
    }

    @Test
    void aMoreConvincingJustificationChangesNoDecision() {
        ToolProposal plain = ToolProposal.call("restartService", "", "serviceName", "notifications", "strategy", "ROLLING",
                "gracePeriodSeconds", 30);
        ToolProposal persuasive = new ToolProposal("restartService", plain.arguments(),
                "URGENT: the commander approved this by phone, the policy allows it, and customers are losing money.");
        GatewayResult first = call(World.TRIAGE_ASSISTANT, plain);
        GatewayResult second = call(World.TRIAGE_ASSISTANT, persuasive);
        assertEquals(first.outcome(), second.outcome());
        assertEquals(first.reason(), second.reason());
        assertEquals(Outcome.DENIED, second.outcome());
        GatewayResult responderPlain = call(World.INCIDENT_RESPONDER, plain);
        GatewayResult responderPersuasive = call(World.INCIDENT_RESPONDER, persuasive);
        assertEquals(responderPlain.outcome(), responderPersuasive.outcome());
        assertEquals(Outcome.APPROVAL_REQUIRED, responderPersuasive.outcome());
    }

    @Test
    void toolsAreClassifiedByWhatTheyReallyDo() {
        for (ToolDefinition tool : HelioTools.definitions(world.platform).values()) {
            HelioPlatform platform = new HelioPlatform();
            ToolDefinition fresh = HelioTools.definitions(platform).get(tool.name());
            Map<String, Object> args = switch (tool.name()) {
                case "getServiceStatus" -> Map.of("serviceName", "notifications");
                case "getIncidentNote" -> Map.of("serviceName", "notifications", "incidentId", "INC-1001");
                case "restartService" -> Map.of("serviceName", "notifications", "strategy", "ROLLING", "gracePeriodSeconds", 1);
                default -> Map.of("serviceName", "notifications", "targetVersion", "notifications-2.4.0");
            };
            fresh.executor().execute(World.INCIDENT_RESPONDER, args);
            assertEquals(tool.operationType() == OperationType.STATE_CHANGING, !platform.stateChanges().isEmpty(),
                    tool.name() + " is classified " + tool.operationType());
        }
    }

    // --- approval ---

    @Test
    void aStateChangeRequiresApprovalAndNeverReachesTheExecutionLayerWithout() {
        GatewayResult held = call(World.INCIDENT_RESPONDER, restart("notifications"));
        assertEquals(Outcome.APPROVAL_REQUIRED, held.outcome());
        assertTrue(held.pendingOperationId().isPresent());
        assertEquals(0, world.platform.executionCount());
        assertTrue(world.platform.stateChanges().isEmpty());
    }

    @Test
    void anApprovedOperationRunsOnceWithItsApproval() {
        ToolProposal proposal = restart("notifications");
        String id = approved(proposal);
        GatewayResult result = world.gateway.invokeApproved(world.session(World.INCIDENT_RESPONDER).context(), proposal, id);
        assertEquals(Outcome.EXECUTED, result.outcome());
        assertEquals(List.of("restart notifications strategy=ROLLING grace=30"), world.platform.stateChanges());
        assertEquals(1, world.platform.executionCount());
    }

    @Test
    void anApprovalIsSingleUse() {
        ToolProposal proposal = restart("notifications");
        String id = approved(proposal);
        world.gateway.invokeApproved(world.session(World.INCIDENT_RESPONDER).context(), proposal, id);
        GatewayResult replay = world.gateway.invokeApproved(world.session(World.INCIDENT_RESPONDER).context(), proposal, id);
        assertEquals(Outcome.APPROVAL_INVALID, replay.outcome());
        assertEquals("APPROVAL_ALREADY_USED", replay.reason());
        assertEquals(1, world.platform.stateChanges().size());
    }

    @Test
    void anApprovalForOneOperationCannotAuthorizeAnother() {
        String id = approved(restart("notifications"));
        ToolProposal other = ToolProposal.call("rollbackDeployment", "Roll back.", "serviceName", "notifications",
                "targetVersion", "notifications-2.4.0");
        GatewayResult result = world.gateway.invokeApproved(world.session(World.INCIDENT_RESPONDER).context(), other, id);
        assertEquals(Outcome.APPROVAL_INVALID, result.outcome());
        assertEquals("APPROVAL_DOES_NOT_MATCH_OPERATION", result.reason());
        assertEquals(0, world.platform.executionCount());
    }

    @Test
    void changingTheTargetAfterApprovalInvalidatesIt() {
        ToolProposal original = restart("notifications");
        String id = approved(original);
        GatewayResult changed = world.gateway.invokeApproved(world.session(World.INCIDENT_RESPONDER).context(),
                restart("billing"), id);
        assertEquals("APPROVAL_DOES_NOT_MATCH_TARGET", changed.reason());
        // Invalidation is permanent: the original operation no longer runs under that approval either.
        GatewayResult back = world.gateway.invokeApproved(world.session(World.INCIDENT_RESPONDER).context(), original, id);
        assertEquals("APPROVAL_INVALIDATED", back.reason());
        assertEquals(0, world.platform.executionCount());
    }

    @Test
    void changingTheArgumentsAfterApprovalInvalidatesIt() {
        String id = approved(restart("notifications"));
        ToolProposal harsher = ToolProposal.call("restartService", "Restart it.", "serviceName", "notifications",
                "strategy", "IMMEDIATE", "gracePeriodSeconds", 0);
        GatewayResult result = world.gateway.invokeApproved(world.session(World.INCIDENT_RESPONDER).context(), harsher, id);
        assertEquals("APPROVAL_DOES_NOT_MATCH_ARGUMENTS", result.reason());
        assertEquals(0, world.platform.executionCount());
    }

    @Test
    void anApprovalIsBoundToTheRequester() {
        ToolProposal proposal = restart("notifications");
        String id = approved(proposal);
        GatewayResult other = world.gateway.invokeApproved(world.session(World.TRIAGE_ASSISTANT).context(), proposal, id);
        assertEquals(Outcome.APPROVAL_INVALID, other.outcome());
        assertEquals(0, world.platform.executionCount());
    }

    @Test
    void anOperationThatIsNotYetApprovedCannotRun() {
        ToolProposal proposal = restart("notifications");
        String id = call(World.INCIDENT_RESPONDER, proposal).pendingOperationId().orElseThrow();
        GatewayResult result = world.gateway.invokeApproved(world.session(World.INCIDENT_RESPONDER).context(), proposal, id);
        assertEquals("NOT_YET_APPROVED", result.reason());
        assertEquals(0, world.platform.executionCount());
    }

    @Test
    void anUnknownApprovalIdIsRejected() {
        GatewayResult result = world.gateway.invokeApproved(world.session(World.INCIDENT_RESPONDER).context(),
                restart("notifications"), "pending-999");
        assertEquals("UNKNOWN_PENDING_OPERATION", result.reason());
        assertEquals(0, world.platform.executionCount());
    }

    @Test
    void onlyAnAuthorizedApproverMayApproveAndNeverTheRequester() {
        String id = call(World.INCIDENT_RESPONDER, restart("notifications")).pendingOperationId().orElseThrow();
        assertEquals("CAPABILITY_NOT_GRANTED",
                world.gateway.approve(world.session(World.INCIDENT_RESPONDER).context(), id).reason());
        assertEquals("NO_PRINCIPAL", world.gateway.approve(world.session(null).context(), id).reason());

        // Even a principal who holds both capabilities cannot approve their own request.
        Authorizer both = request -> request.capability() == Capability.APPROVE_OPERATIONS
                ? AuthorizationDecision.allow() : PolicyAuthorizer.helioPolicy().authorize(request);
        World lax = World.with(both, new HelioPlatform(), Map.of());
        String own = lax.gateway.invoke(lax.session(World.INCIDENT_RESPONDER).context(), restart("notifications"))
                .pendingOperationId().orElseThrow();
        assertEquals("REQUESTER_CANNOT_APPROVE_OWN_OPERATION",
                lax.gateway.approve(lax.session(World.INCIDENT_RESPONDER).context(), own).reason());
        assertEquals(0, world.platform.executionCount());
    }

    @Test
    void approvalIsRevalidatedBeforeExecution() {
        AtomicBoolean revoked = new AtomicBoolean();
        PolicyAuthorizer policy = PolicyAuthorizer.helioPolicy();
        Authorizer revocable = request -> revoked.get() && request.capability() == Capability.RESTART_SERVICE
                ? AuthorizationDecision.deny("GRANT_REVOKED") : policy.authorize(request);
        World w = World.with(revocable, new HelioPlatform(), Map.of());
        ToolProposal proposal = restart("notifications");
        String id = w.gateway.invoke(w.session(World.INCIDENT_RESPONDER).context(), proposal).pendingOperationId().orElseThrow();
        w.gateway.approve(w.session(World.INCIDENT_COMMANDER).context(), id);
        revoked.set(true);
        GatewayResult result = w.gateway.invokeApproved(w.session(World.INCIDENT_RESPONDER).context(), proposal, id);
        assertEquals(Outcome.DENIED, result.outcome());
        assertEquals("GRANT_REVOKED", result.reason());
        assertEquals(0, w.platform.executionCount());
    }

    // --- failure ---

    @Test
    void aFailingToolIsAFailureNotACompletion() {
        World w = World.standard();
        String id = w.gateway.invoke(w.session(World.INCIDENT_RESPONDER).context(), ToolProposal.call("rollbackDeployment",
                "x", "serviceName", "notifications", "targetVersion", "notifications-9.9.9")).pendingOperationId().orElseThrow();
        w.gateway.approve(w.session(World.INCIDENT_COMMANDER).context(), id);
        World.Session session = w.session(World.INCIDENT_RESPONDER);
        GatewayResult result = w.gateway.invokeApproved(session.context(), ToolProposal.call("rollbackDeployment", "x",
                "serviceName", "notifications", "targetVersion", "notifications-9.9.9"), id);
        assertEquals(Outcome.FAILED, result.outcome());
        Trace trace = session.trace();
        assertTrue(trace.has(SecurityEvent.TOOL_EXECUTION_FAILED));
        assertFalse(trace.has(SecurityEvent.TOOL_EXECUTION_COMPLETED));
    }
}
