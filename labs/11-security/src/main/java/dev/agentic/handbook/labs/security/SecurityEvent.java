package dev.agentic.handbook.labs.security;

/**
 * The named things the application records about a proposal. A denial and an
 * execution are different events, so a denied action can never look executed.
 * Recording an event grants nothing and prevents nothing: the controls decide,
 * and the trace only says what they decided.
 */
public enum SecurityEvent {
    ARGUMENT_VALIDATION_FAILED,
    AUTHORIZATION_ALLOWED,
    AUTHORIZATION_DENIED,
    APPROVAL_REQUIRED,
    APPROVAL_GRANTED,
    APPROVAL_INVALIDATED,
    TOOL_EXECUTION_STARTED,
    TOOL_EXECUTION_COMPLETED,
    TOOL_EXECUTION_FAILED,
    SENSITIVE_FIELD_REDACTED
}
