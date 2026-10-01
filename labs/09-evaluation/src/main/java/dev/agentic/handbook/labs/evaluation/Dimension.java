package dev.agentic.handbook.labs.evaluation;

/**
 * The behavioral dimensions this lab evaluates. Five, on purpose: each one is a
 * different <em>kind</em> of check, and together they cover both the
 * trajectory and the final answer. They are reported side by side and never
 * combined into a single score.
 */
public enum Dimension {

    /** Trajectory: did the run request the tools the case justifies, with the right arguments? */
    TOOL_SELECTION("Tool selection"),

    /** Trajectory: did the run avoid tools and data the case does not justify? */
    TOOL_RESTRAINT("Tool restraint"),

    /** Runtime behavior: did the run stay in its budget and stop for the intended reason? */
    BOUNDED_EXECUTION("Bounded execution"),

    /** Final answer: does it report what was observed, and nothing that was not asked for? */
    FINAL_OUTCOME("Final outcome"),

    /** Final answer against evidence: no unsupported root cause, no facts absent from the observations. */
    EVIDENCE_DISCIPLINE("Evidence discipline");

    private final String label;

    Dimension(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
