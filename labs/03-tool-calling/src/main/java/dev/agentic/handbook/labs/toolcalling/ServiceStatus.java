package dev.agentic.handbook.labs.toolcalling;

/**
 * What the tool implementation returns: a normal Java value. Nothing in this
 * record knows that a model exists.
 */
public record ServiceStatus(
        String serviceName,
        ServiceState state,
        String message) {
}
