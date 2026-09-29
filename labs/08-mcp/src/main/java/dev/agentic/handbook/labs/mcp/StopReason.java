package dev.agentic.handbook.labs.mcp;

/**
 * Why a run ended. Stopping is a runtime responsibility: the loop never relies
 * on the model eventually deciding to stop.
 */
public enum StopReason {
    /** The model produced an answer instead of a tool request. */
    FINAL_ANSWER,
    /** The application-defined budget of model decisions was exhausted. */
    MAX_STEPS,
    /** The model requested a tool the application does not allow, or supplied invalid arguments. */
    REJECTED_TOOL_CALL,
    /**
     * New in this lab: an allowed, valid request reached the remote capability,
     * and the capability reported an error (or could not be reached). The run
     * stops with the error instead of retrying or inventing a result.
     */
    TOOL_FAILED
}
