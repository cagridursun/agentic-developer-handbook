package dev.agentic.handbook.labs.security;

/**
 * Who a request acts for. Lab 11 does not authenticate anyone: the caller of the
 * gateway supplies the principal, and in a real system that value comes from a
 * verified identity (a session, a token), never from the model and never from
 * text in a prompt. A principal that the model merely names is not a principal.
 */
public record Principal(String id) {
}
