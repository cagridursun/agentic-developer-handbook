package dev.agentic.handbook.labs.observability;

/**
 * Who acted. This is the decision-authority boundary made visible in the trace:
 * the model proposes, the application validates and decides, the tool executes.
 * The actor is a label on a span; it grants no authority.
 */
public enum Actor {
    MODEL,
    APPLICATION,
    TOOL
}
