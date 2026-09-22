package dev.agentic.handbook.labs.structuredoutput;

/**
 * The kinds of work item this example understands. The response schema
 * restricts the model to exactly these names, and Jackson maps the JSON string
 * onto this enum. If the two ever disagree, deserialization fails loudly.
 */
public enum TaskType {
    FEATURE,
    BUG,
    TECHNICAL_TASK
}
