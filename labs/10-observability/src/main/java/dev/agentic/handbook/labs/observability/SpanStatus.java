package dev.agentic.handbook.labs.observability;

/**
 * How a span ended. Three values, because "the application said no" and
 * "something broke" are different findings: a rejected proposal means the
 * application's controls worked; an error means something failed.
 */
public enum SpanStatus {
    OK,
    /** The application refused a proposal (not allowed, or invalid arguments). */
    REJECTED,
    /** Something failed, or the run did not reach its goal (for example, the step budget). */
    ERROR
}
