package dev.agentic.handbook.labs.security;

/**
 * The span kinds of this lab: Lab 10's trace model, trimmed to what the security
 * question needs, plus one new kind.
 *
 * <pre>
 * AGENT_RUN                       the whole run (the root)
 *   AGENT_DECISION                what the model proposed (the model's claim, not a fact)
 *   TOOL_CALL                     one proposal, as the application handled it
 *     SECURITY_EVENT              one named decision or step of the application
 * </pre>
 *
 * Lab 10 had a {@code TOOL_VALIDATION} and a {@code TOOL_EXECUTION} span. Here the
 * finer stages (validation, authorization, approval, execution, redaction) are
 * each a named {@link SecurityEvent}, recorded as a {@code SECURITY_EVENT} span.
 */
public enum SpanType {
    AGENT_RUN,
    AGENT_DECISION,
    TOOL_CALL,
    SECURITY_EVENT
}
