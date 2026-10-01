package dev.agentic.handbook.labs.observability;

/** What one observed run produces: the result the caller gets, and the trace that explains it. */
public record ObservedRun(AgentRunResult result, Trace trace) {
}
