package dev.agentic.handbook.labs.security;

import java.util.Set;

/** What one principal may do, and on which services. Least privilege is a short grant. */
public record Grant(Set<Capability> capabilities, Set<String> services) {

    public Grant {
        capabilities = Set.copyOf(capabilities);
        services = Set.copyOf(services);
    }
}
