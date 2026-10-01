package dev.agentic.handbook.labs.evaluation;

import java.util.List;
import java.util.Set;

/**
 * One evaluation case: an input, why the case exists, and the behavior the
 * system is expected to show — expressed as properties, never as one correct
 * string.
 *
 * <p>The scenario facts live in {@link ServiceTools} and are fixed, so the
 * case is deterministic. The {@code why} field is the reviewer's context: it is
 * what lets a person disagree with a case and change it.
 *
 * @param id           stable identifier, unique within the set
 * @param kind         the reason this case is in the set
 * @param goal         the user's input
 * @param why          why this case exists, and what a reviewer should know about its expectations
 * @param expectations the behavior required and forbidden
 */
public record EvalCase(
        String id,
        CaseKind kind,
        String goal,
        String why,
        Expectations expectations) {

    /**
     * The behavioral contract of a case. Trajectory expectations say which tool
     * requests are required or forbidden; answer expectations say what must and
     * must not appear. None of them pins a whole answer or a whole sequence.
     *
     * @param requiredCalls      tool requests that must appear in the trajectory, in any order
     * @param forbiddenTools     tools that must not be requested: the case does not justify them
     * @param stopReason         the intended reason the run ends
     * @param maxModelSteps      the most model decisions this case may take
     * @param answerMustMention  phrases a final answer must contain (case-insensitive)
     * @param answerMustNotMention phrases a final answer must not contain (case-insensitive)
     * @param expectFinalAnswer  whether a final answer is expected (a safe stop has none)
     */
    public record Expectations(
            List<Call> requiredCalls,
            Set<String> forbiddenTools,
            StopReason stopReason,
            int maxModelSteps,
            boolean expectFinalAnswer,
            List<String> answerMustMention,
            List<String> answerMustNotMention) {
    }

    /** A tool request identified by tool name and service argument. */
    public record Call(String tool, String serviceName) {
        @Override
        public String toString() {
            return tool + "(" + serviceName + ")";
        }
    }
}
