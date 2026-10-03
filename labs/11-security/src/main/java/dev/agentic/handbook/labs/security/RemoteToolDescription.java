package dev.agentic.handbook.labs.security;

/**
 * What a remote provider says about one of its tools (the shape of an MCP
 * {@code tools/list} entry, much reduced). Every field is the provider's claim.
 * The MCP specification says a client must treat tool annotations as untrusted
 * unless they come from a trusted server; this lab goes one step further and
 * lets no claim decide anything.
 */
public record RemoteToolDescription(String name, String description, boolean claimsReadOnly) {
}
