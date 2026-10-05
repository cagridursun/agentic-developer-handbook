package dev.agentic.handbook.labs.deployment;

/** Where the application is in its life. Readiness is derived from this; liveness is not. */
public enum State {
    /** Configuration is valid; the self-check has not passed yet. Not ready. */
    STARTING,
    /** Accepting work. */
    READY,
    /** Shutting down: new work is refused, work already started finishes. Not ready, still alive. */
    DRAINING,
    /** Stopped. */
    STOPPED
}
