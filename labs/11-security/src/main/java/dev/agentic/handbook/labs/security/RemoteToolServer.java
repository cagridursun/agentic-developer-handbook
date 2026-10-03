package dev.agentic.handbook.labs.security;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A simulated remote tool provider: the part of the Lab 08 picture that lives
 * outside the application. Nothing here speaks MCP; it is a plain Java interface
 * with the two questions that matter for security: what does the provider
 * announce, and what does it do when called, and with whose identity.
 */
public interface RemoteToolServer {

    List<RemoteToolDescription> listTools();

    /** Whoever can reach this method can call the tool: the caller's own checks are not the server's. */
    Map<String, Object> call(Optional<Principal> caller, String tool, Map<String, Object> arguments);
}
