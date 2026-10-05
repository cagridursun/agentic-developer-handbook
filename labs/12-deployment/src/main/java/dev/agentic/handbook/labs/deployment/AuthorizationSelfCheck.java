package dev.agentic.handbook.labs.deployment;

import dev.agentic.handbook.labs.security.ApprovalState;
import dev.agentic.handbook.labs.security.AuthorizationDecision.Effect;
import dev.agentic.handbook.labs.security.AuthorizationRequest;
import dev.agentic.handbook.labs.security.Authorizer;
import dev.agentic.handbook.labs.security.Capability;
import dev.agentic.handbook.labs.security.OperationType;
import dev.agentic.handbook.labs.security.Principal;
import java.util.Optional;

/**
 * A deployment-time guard: before the application says it is ready, ask its
 * authorizer two questions whose answers must never change.
 *
 * <ol>
 *   <li>Does an unknown principal get <b>denied</b>? An authorizer that allows
 *       everyone has been replaced or switched off.</li>
 *   <li>Does a state change by a principal that may propose it, with no
 *       approval, <b>require approval</b> rather than run?</li>
 * </ol>
 *
 * <p>This does not replace the gateway's own checks or the tests. It catches one
 * accident that tests of the source cannot: a deployment that wired in a
 * different authorizer. The check asks, it never executes a tool.
 */
final class AuthorizationSelfCheck {

    private AuthorizationSelfCheck() {
    }

    static boolean passes(Authorizer authorizer) {
        try {
            var unknown = authorizer.authorize(new AuthorizationRequest(Optional.of(new Principal("self-check-nobody")),
                    Capability.READ_SERVICE_STATUS, "billing", OperationType.READ, ApprovalState.NOT_REQUESTED));
            var unapprovedChange = authorizer.authorize(new AuthorizationRequest(
                    Optional.of(new Principal("incident-responder")), Capability.RESTART_SERVICE, "billing",
                    OperationType.STATE_CHANGING, ApprovalState.NOT_REQUESTED));
            return unknown != null && unknown.effect() == Effect.DENY
                    && unapprovedChange != null && unapprovedChange.effect() == Effect.REQUIRE_APPROVAL;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
