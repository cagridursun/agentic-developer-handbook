package dev.agentic.handbook.labs.security;

/**
 * The authorization boundary: a deterministic decision made by application code.
 * An implementation must fail closed: anything missing or unexpected is a denial.
 */
@FunctionalInterface
public interface Authorizer {

    AuthorizationDecision authorize(AuthorizationRequest request);
}
