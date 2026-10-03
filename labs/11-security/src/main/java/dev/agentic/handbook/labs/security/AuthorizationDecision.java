package dev.agentic.handbook.labs.security;

/** The policy's answer, with a reason code a person can read. Three effects, not one boolean. */
public record AuthorizationDecision(Effect effect, String reason) {

    public enum Effect {
        ALLOW,
        DENY,
        /** The principal may do this, but only with a valid approval for exactly this operation. */
        REQUIRE_APPROVAL
    }

    static AuthorizationDecision allow() {
        return new AuthorizationDecision(Effect.ALLOW, "ALLOWED");
    }

    static AuthorizationDecision deny(String reason) {
        return new AuthorizationDecision(Effect.DENY, reason);
    }

    static AuthorizationDecision requireApproval() {
        return new AuthorizationDecision(Effect.REQUIRE_APPROVAL, "STATE_CHANGE_NEEDS_APPROVAL");
    }
}
