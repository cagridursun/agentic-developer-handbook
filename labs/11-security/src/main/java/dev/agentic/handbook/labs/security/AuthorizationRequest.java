package dev.agentic.handbook.labs.security;

import java.util.Optional;

/**
 * Everything the policy decides on. None of it comes from the model's text: the
 * principal is supplied by the caller, the capability and operation type come
 * from the application's tool definition, the resource from validated arguments,
 * and the approval state from the application's own approval records.
 */
public record AuthorizationRequest(
        Optional<Principal> principal,
        Capability capability,
        String resource,
        OperationType operationType,
        ApprovalState approvalState) {
}
