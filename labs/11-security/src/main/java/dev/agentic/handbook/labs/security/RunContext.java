package dev.agentic.handbook.labs.security;

import java.util.Optional;

/** Who is acting, and where to record it. The principal comes from the caller, never from the model. */
public record RunContext(Optional<Principal> principal, TraceRecorder trace, Span parent) {
}
