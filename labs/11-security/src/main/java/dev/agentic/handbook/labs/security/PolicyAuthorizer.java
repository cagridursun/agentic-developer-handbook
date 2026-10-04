package dev.agentic.handbook.labs.security;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A small deterministic policy: a table of grants, and four rules applied in
 * order. It is a teaching policy, not an identity or RBAC system.
 *
 * <ol>
 *   <li>No principal, no grant for the principal, or any missing input: deny.</li>
 *   <li>The capability was not granted: deny.</li>
 *   <li>The resource is not in the principal's scope: deny.</li>
 *   <li>A state-changing operation without a valid approval: require approval.</li>
 *   <li>Otherwise: allow.</li>
 * </ol>
 */
public final class PolicyAuthorizer implements Authorizer {

    private final Map<String, Grant> grants;

    /** A null or empty table denies everything: missing configuration fails closed. */
    public PolicyAuthorizer(Map<String, Grant> grants) {
        this.grants = grants == null ? Map.of() : Map.copyOf(grants);
    }

    /**
     * The principals of the lab's scenario.
     * {@code triage-assistant}: reads status and incident notes for notifications only.
     * {@code incident-responder}: also proposes restarts and rollbacks, for notifications and billing.
     * {@code incident-commander}: approves operations for notifications and billing.
     */
    public static PolicyAuthorizer helioPolicy() {
        return new PolicyAuthorizer(Map.of(
                "triage-assistant", new Grant(
                        Set.of(Capability.READ_SERVICE_STATUS, Capability.READ_INCIDENT_NOTES, Capability.READ_DEPLOYMENTS),
                        Set.of("notifications")),
                "incident-responder", new Grant(
                        Set.of(Capability.READ_SERVICE_STATUS, Capability.READ_INCIDENT_NOTES, Capability.READ_DEPLOYMENTS,
                                Capability.RESTART_SERVICE, Capability.ROLLBACK_DEPLOYMENT,
                                Capability.MODIFY_DEPLOYMENT_CACHE),
                        Set.of("notifications", "billing")),
                "incident-commander", new Grant(
                        Set.of(Capability.APPROVE_OPERATIONS),
                        Set.of("notifications", "billing"))));
    }

    @Override
    public AuthorizationDecision authorize(AuthorizationRequest request) {
        if (request == null || request.principal() == null || request.capability() == null
                || request.resource() == null || request.operationType() == null
                || request.approvalState() == null) {
            return AuthorizationDecision.deny("INCOMPLETE_REQUEST");
        }
        Optional<Principal> principal = request.principal();
        if (principal.isEmpty() || principal.get().id() == null) {
            return AuthorizationDecision.deny("NO_PRINCIPAL");
        }
        Grant grant = grants.get(principal.get().id());
        if (grant == null) {
            return AuthorizationDecision.deny("UNKNOWN_PRINCIPAL");
        }
        if (!grant.capabilities().contains(request.capability())) {
            return AuthorizationDecision.deny("CAPABILITY_NOT_GRANTED");
        }
        if (!grant.services().contains(request.resource())) {
            return AuthorizationDecision.deny("RESOURCE_OUT_OF_SCOPE");
        }
        if (request.operationType() == OperationType.STATE_CHANGING
                && request.approvalState() != ApprovalState.GRANTED) {
            return AuthorizationDecision.requireApproval();
        }
        return AuthorizationDecision.allow();
    }
}
