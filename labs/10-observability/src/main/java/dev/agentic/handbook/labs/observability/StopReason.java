package dev.agentic.handbook.labs.observability;

/**
 * Why a run ended. The first three are exactly the Lab 07 to 09 reasons, with
 * the same names; {@link #ERROR} is the one addition. Stopping is a runtime
 * responsibility, and this lab makes the reason visible in the trace.
 */
public enum StopReason {
    /** The model produced an answer instead of a tool request. */
    FINAL_ANSWER,
    /** The application-defined budget of model decisions was exhausted: the "step limit". */
    MAX_STEPS,
    /** The model requested an unlisted tool, supplied invalid arguments, or asked about an unknown service. */
    REJECTED_TOOL_CALL,
    /**
     * Something failed that is not a rejected proposal: the model call threw,
     * or an allowed tool failed while executing. Earlier labs let such an
     * exception escape the runtime; here the run stops and the trace records it.
     */
    ERROR
}
