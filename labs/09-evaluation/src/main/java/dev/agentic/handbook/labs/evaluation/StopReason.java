package dev.agentic.handbook.labs.evaluation;

/** Why a run ended. Stopping is a runtime responsibility, exactly as in Lab 07. */
public enum StopReason {
    /** The model produced an answer instead of a tool request. */
    FINAL_ANSWER,
    /** The application-defined budget of model decisions was exhausted. */
    MAX_STEPS,
    /** The model requested an unlisted tool, supplied invalid arguments, or asked about an unknown service. */
    REJECTED_TOOL_CALL
}
