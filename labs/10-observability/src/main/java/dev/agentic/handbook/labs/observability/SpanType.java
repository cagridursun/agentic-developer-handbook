package dev.agentic.handbook.labs.observability;

/**
 * The kinds of span this lab records, and who acts in each. Deliberately few:
 * enough to answer "what happened between the request and the answer?".
 *
 * <pre>
 * AGENT_RUN                      the whole run (the root)
 *   MODEL_CALL                   one call to the model
 *     AGENT_DECISION             what the model proposed
 *   TOOL_CALL                    one proposed tool call, as the application handled it
 *     TOOL_VALIDATION            the application: allowed? valid arguments?
 *     TOOL_EXECUTION             the tool ran; its result summary
 *   FINAL_RESPONSE               the answer the run returned
 * </pre>
 */
public enum SpanType {
    AGENT_RUN(Actor.APPLICATION),
    MODEL_CALL(Actor.MODEL),
    AGENT_DECISION(Actor.MODEL),
    TOOL_CALL(Actor.APPLICATION),
    TOOL_VALIDATION(Actor.APPLICATION),
    TOOL_EXECUTION(Actor.TOOL),
    FINAL_RESPONSE(Actor.APPLICATION);

    private final Actor actor;

    SpanType(Actor actor) {
        this.actor = actor;
    }

    public Actor actor() {
        return actor;
    }
}
