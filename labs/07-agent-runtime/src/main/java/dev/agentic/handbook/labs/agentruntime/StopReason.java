package dev.agentic.handbook.labs.agentruntime;

/**
 * Why a run ended. Stopping is a runtime responsibility: the loop never relies
 * on the model eventually deciding to stop.
 */
public enum StopReason {
    /** The model produced an answer instead of a tool request. */
    FINAL_ANSWER,
    /** The application-defined budget of model decisions was exhausted. */
    MAX_STEPS,
    /** The model requested an unlisted tool or supplied invalid arguments. */
    REJECTED_TOOL_CALL
}
