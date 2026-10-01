package dev.agentic.handbook.labs.observability;

import java.util.List;

/**
 * Three checks borrowed from Lab 09's bounded-execution and final-outcome
 * dimensions, applied to one run. They exist to show that the same scenario
 * can produce an <em>evaluation result</em> and a <em>trace</em>, and that the
 * two answer different questions. They are not the evaluation harness: Lab 09
 * is, with its cases, its five dimensions, and its comparison of versions.
 */
public final class ScenarioChecks {

    /** One named check and what it observed. */
    public record Check(String name, boolean passed, String detail) {
    }

    private ScenarioChecks() {
    }

    public static List<Check> evaluate(AgentRunResult result, int stepBudget) {
        boolean answered = result.answer().filter(text -> !text.isBlank()).isPresent();
        return List.of(
                new Check("stopped for the intended reason (FINAL_ANSWER)",
                        result.stopReason() == StopReason.FINAL_ANSWER, "stop reason was " + result.stopReason()),
                new Check("a final answer was produced", answered, answered ? "answer present" : "no answer"),
                new Check("stayed within the step budget", result.modelSteps() <= stepBudget,
                        result.modelSteps() + " model decision(s), budget " + stepBudget));
    }
}
