package dev.agentic.handbook.labs.security;

/** How a span ended, as in Lab 10: {@code REJECTED} means the application refused or held a proposal. */
public enum SpanStatus {
    OK,
    /** The application refused a proposal, or held it (for example, until it is approved). */
    REJECTED,
    /** Something failed while executing. */
    ERROR
}
