package dev.agentic.handbook.labs.evaluation;

/**
 * One case run against one system version: the case, and the trace the
 * runtime produced. This is the input to every check.
 *
 * <p>An <em>evaluation trace</em> is captured for a controlled run and judged
 * against expected behavior. It is not an observability trace, which is runtime
 * telemetry from a production system and belongs to Milestone 10.
 */
public record EvaluationRun(EvalCase evalCase, AgentRunResult result) {
}
