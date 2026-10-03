package dev.agentic.handbook.labs.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.agentic.handbook.labs.security.AuthorizationDecision.Effect;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The policy on its own, with no gateway and no model: a deterministic table of decisions. */
class PolicyAuthorizerTest {

    private final PolicyAuthorizer policy = PolicyAuthorizer.helioPolicy();

    private AuthorizationDecision decide(Principal principal, Capability capability, String resource,
            OperationType type, ApprovalState approval) {
        return policy.authorize(new AuthorizationRequest(Optional.ofNullable(principal), capability, resource, type, approval));
    }

    @Test
    void aGrantedReadInScopeIsAllowed() {
        assertEquals(Effect.ALLOW, decide(World.TRIAGE_ASSISTANT, Capability.READ_SERVICE_STATUS, "notifications",
                OperationType.READ, ApprovalState.NOT_REQUESTED).effect());
    }

    @Test
    void theRestrictedPrincipalHasLeastPrivilege() {
        assertEquals("CAPABILITY_NOT_GRANTED", decide(World.TRIAGE_ASSISTANT, Capability.RESTART_SERVICE, "notifications",
                OperationType.STATE_CHANGING, ApprovalState.GRANTED).reason());
        assertEquals("CAPABILITY_NOT_GRANTED", decide(World.TRIAGE_ASSISTANT, Capability.ROLLBACK_DEPLOYMENT, "notifications",
                OperationType.STATE_CHANGING, ApprovalState.GRANTED).reason());
        assertEquals("RESOURCE_OUT_OF_SCOPE", decide(World.TRIAGE_ASSISTANT, Capability.READ_SERVICE_STATUS, "billing",
                OperationType.READ, ApprovalState.NOT_REQUESTED).reason());
    }

    @Test
    void aStateChangeWithoutAnApprovalRequiresOne() {
        assertEquals(Effect.REQUIRE_APPROVAL, decide(World.INCIDENT_RESPONDER, Capability.RESTART_SERVICE, "billing",
                OperationType.STATE_CHANGING, ApprovalState.NOT_REQUESTED).effect());
        assertEquals(Effect.ALLOW, decide(World.INCIDENT_RESPONDER, Capability.RESTART_SERVICE, "billing",
                OperationType.STATE_CHANGING, ApprovalState.GRANTED).effect());
    }

    @Test
    void anApprovalNeverWidensWhatThePrincipalMayDo() {
        // Approval state GRANTED does not help a principal without the capability or the scope.
        assertEquals(Effect.DENY, decide(World.INCIDENT_RESPONDER, Capability.RESTART_SERVICE, "search",
                OperationType.STATE_CHANGING, ApprovalState.GRANTED).effect());
        assertEquals(Effect.DENY, decide(World.TRIAGE_ASSISTANT, Capability.RESTART_SERVICE, "notifications",
                OperationType.STATE_CHANGING, ApprovalState.GRANTED).effect());
    }

    @Test
    void aMissingPrincipalIsDenied() {
        assertEquals("NO_PRINCIPAL", decide(null, Capability.READ_SERVICE_STATUS, "notifications",
                OperationType.READ, ApprovalState.NOT_REQUESTED).reason());
    }

    @Test
    void anUnknownPrincipalIsDenied() {
        assertEquals("UNKNOWN_PRINCIPAL", decide(new Principal("someone-the-model-named"), Capability.READ_SERVICE_STATUS,
                "notifications", OperationType.READ, ApprovalState.NOT_REQUESTED).reason());
    }

    @Test
    void missingOrEmptyConfigurationFailsClosed() {
        for (PolicyAuthorizer empty : new PolicyAuthorizer[] {new PolicyAuthorizer(null), new PolicyAuthorizer(Map.of())}) {
            assertEquals("UNKNOWN_PRINCIPAL", empty.authorize(new AuthorizationRequest(Optional.of(World.INCIDENT_COMMANDER),
                    Capability.APPROVE_OPERATIONS, "notifications", OperationType.GRANT_APPROVAL,
                    ApprovalState.NOT_REQUESTED)).reason());
        }
    }

    @Test
    void anIncompleteRequestIsDenied() {
        assertEquals("INCOMPLETE_REQUEST", policy.authorize(null).reason());
        assertEquals("INCOMPLETE_REQUEST", policy.authorize(new AuthorizationRequest(Optional.of(World.TRIAGE_ASSISTANT),
                Capability.READ_SERVICE_STATUS, null, OperationType.READ, ApprovalState.NOT_REQUESTED)).reason());
        assertEquals("INCOMPLETE_REQUEST", policy.authorize(new AuthorizationRequest(null,
                Capability.READ_SERVICE_STATUS, "notifications", OperationType.READ, ApprovalState.NOT_REQUESTED)).reason());
    }

    @Test
    void aGrantIsImmutable() {
        Set<Capability> capabilities = new java.util.HashSet<>(Set.of(Capability.READ_SERVICE_STATUS));
        Grant grant = new Grant(capabilities, Set.of("notifications"));
        capabilities.add(Capability.RESTART_SERVICE);
        assertEquals(Set.of(Capability.READ_SERVICE_STATUS), grant.capabilities());
    }
}
