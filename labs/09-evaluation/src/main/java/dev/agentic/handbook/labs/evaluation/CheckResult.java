package dev.agentic.handbook.labs.evaluation;

/**
 * The outcome of one named check on one run. Every check keeps its own result
 * and its own explanation, so a failure can always be inspected individually.
 *
 * @param dimension the behavioral dimension the check belongs to
 * @param name      a stable, human-readable name (used to compare versions)
 * @param passed    whether the behavior was acceptable
 * @param detail    why: what was observed, so a reviewer need not rerun anything
 */
public record CheckResult(Dimension dimension, String name, boolean passed, String detail) {
}
