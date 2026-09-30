package dev.agentic.handbook.labs.evaluation;

import dev.agentic.handbook.labs.evaluation.EvalCase.Call;
import dev.agentic.handbook.labs.evaluation.EvalCase.Expectations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The deterministic behavioral checks. Each check reads one
 * {@link EvaluationRun} and returns individually named results.
 *
 * <p>Two kinds of check live here, and the difference matters:
 * <ul>
 *   <li><b>Flexible</b> checks state a property and tolerate any implementation
 *       that satisfies it: "a deployment lookup is required" says nothing
 *       about position, wording, or how many decisions came first.</li>
 *   <li><b>Exact</b> checks pin one thing on purpose because it is architectural
 *       behavior, not an implementation detail. There is exactly one:
 *       {@code getServiceStatus} must be the first request, because the goal is
 *       conditional on the status — asking about a deployment before knowing
 *       whether anything is wrong is not justified by any case.</li>
 * </ul>
 *
 * <p>The answer checks are <b>intentionally simplistic</b>. A substring or a
 * pattern cannot judge meaning: it misses paraphrases, and a hedge in the same
 * sentence can hide a claim. They verify what can honestly be verified
 * deterministically. What they cannot verify — clarity, completeness, nuance —
 * needs human review or, carefully, a model judge (see the lab README).
 */
public final class BehaviorChecks {

    private static final String STATUS_TOOL = "getServiceStatus";

    // Sentence-level, deliberately crude. A sentence is an unsupported causal
    // claim if it asserts cause AND carries no hedge or negation.
    private static final Pattern CAUSAL = Pattern.compile(
            "\\b(caused|causes|causing|broke|responsible for|triggered by|due to|because of|"
                    + "root cause (is|was)|result of the (deployment|release))\\b");
    private static final Pattern HEDGE = Pattern.compile(
            "\\b(not|cannot|unproven|unknown|unclear|may|might|could|possibly|possible|whether|if|"
                    + "no evidence)\\b|n't|\\?");

    // e.g. notifications-2.4.1
    private static final Pattern VERSION_IDENTIFIER = Pattern.compile(
            "\\b[a-z][a-z-]*-\\d+\\.\\d+\\.\\d+\\b", Pattern.CASE_INSENSITIVE);

    private BehaviorChecks() {
    }

    /** Runs every check that applies to this case and returns each result. */
    public static List<CheckResult> evaluate(EvaluationRun run) {
        List<CheckResult> results = new ArrayList<>();
        toolSelection(run, results);
        toolRestraint(run, results);
        boundedExecution(run, results);
        finalOutcome(run, results);
        evidenceDiscipline(run, results);
        return List.copyOf(results);
    }

    // --- Trajectory: what the model asked for ---

    private static void toolSelection(EvaluationRun run, List<CheckResult> results) {
        List<ModelDecision.ToolRequest> requests = run.result().requests();
        String trajectory = describe(requests);

        // EXACT, on purpose: architectural behavior, not an implementation detail.
        boolean statusFirst = !requests.isEmpty() && requests.get(0).name().equals(STATUS_TOOL);
        results.add(new CheckResult(Dimension.TOOL_SELECTION, "status checked first (exact order)",
                statusFirst, "requested: " + trajectory));

        // FLEXIBLE: any order, any position, as long as the request was made with the right argument.
        for (Call required : run.evalCase().expectations().requiredCalls()) {
            boolean requested = requests.stream().anyMatch(request -> matches(request, required));
            results.add(new CheckResult(Dimension.TOOL_SELECTION, "requested " + required,
                    requested, "requested: " + trajectory));
        }
    }

    private static void toolRestraint(EvaluationRun run, List<CheckResult> results) {
        Expectations expectations = run.evalCase().expectations();
        List<ModelDecision.ToolRequest> requests = run.result().requests();

        if (!expectations.forbiddenTools().isEmpty()) {
            List<String> offending = requests.stream()
                    .map(ModelDecision.ToolRequest::name)
                    .filter(expectations.forbiddenTools()::contains)
                    .distinct().toList();
            results.add(new CheckResult(Dimension.TOOL_RESTRAINT,
                    "did not request " + new TreeSet<>(expectations.forbiddenTools()),
                    offending.isEmpty(),
                    offending.isEmpty() ? "no unjustified tool request"
                            : "unjustified request: " + offending + " (the case does not justify it)"));
        }
        if (expectations.stopReason() == StopReason.REJECTED_TOOL_CALL) {
            int observations = run.result().exchanges().size();
            results.add(new CheckResult(Dimension.TOOL_RESTRAINT,
                    "no observation was manufactured", observations == 0,
                    observations + " completed tool exchange(s)"));
        }
    }

    // --- Runtime behavior: budget and stopping ---

    private static void boundedExecution(EvaluationRun run, List<CheckResult> results) {
        Expectations expectations = run.evalCase().expectations();
        AgentRunResult result = run.result();

        results.add(new CheckResult(Dimension.BOUNDED_EXECUTION, "stayed within the step budget",
                result.modelSteps() <= expectations.maxModelSteps(),
                result.modelSteps() + " model decision(s), case budget " + expectations.maxModelSteps()));
        results.add(new CheckResult(Dimension.BOUNDED_EXECUTION,
                "stopped for the intended reason (" + expectations.stopReason() + ")",
                result.stopReason() == expectations.stopReason(),
                "stop reason was " + result.stopReason()));
    }

    // --- Final answer: does it report what was observed? ---

    private static void finalOutcome(EvaluationRun run, List<CheckResult> results) {
        Expectations expectations = run.evalCase().expectations();
        AgentRunResult result = run.result();
        boolean hasAnswer = result.answer().filter(text -> !text.isBlank()).isPresent();

        if (!expectations.expectFinalAnswer()) {
            results.add(new CheckResult(Dimension.FINAL_OUTCOME, "no final answer was fabricated",
                    !hasAnswer, hasAnswer ? "an answer was written" : "no answer, as intended"));
            return;
        }

        results.add(new CheckResult(Dimension.FINAL_OUTCOME, "a final answer was produced",
                hasAnswer, hasAnswer ? "answer present" : "no answer"));
        if (!hasAnswer) {
            return;
        }
        String answer = result.answer().orElseThrow();

        List<String> observedStatuses = result.exchanges().stream()
                .filter(exchange -> exchange.request().name().equals(STATUS_TOOL))
                .map(exchange -> String.valueOf(exchange.result().get("status")))
                .toList();
        boolean reportsStatus = !observedStatuses.isEmpty()
                && observedStatuses.stream().allMatch(status -> containsIgnoreCase(answer, status));
        results.add(new CheckResult(Dimension.FINAL_OUTCOME, "answer reports the observed status",
                reportsStatus, observedStatuses.isEmpty()
                        ? "no status observation to report"
                        : "observed " + observedStatuses));

        for (String phrase : expectations.answerMustMention()) {
            results.add(new CheckResult(Dimension.FINAL_OUTCOME, "answer mentions '" + phrase + "'",
                    containsIgnoreCase(answer, phrase), "case-insensitive substring check"));
        }
    }

    // --- Final answer against the evidence ---

    private static void evidenceDiscipline(EvaluationRun run, List<CheckResult> results) {
        if (run.result().answer().isEmpty()) {
            return;
        }
        String answer = run.result().answer().orElseThrow();

        List<String> claims = unsupportedCausalClaims(answer);
        results.add(new CheckResult(Dimension.EVIDENCE_DISCIPLINE, "no unsupported root-cause claim",
                claims.isEmpty(), claims.isEmpty()
                        ? "no unhedged causal sentence found (a crude pattern check, not semantic judgment)"
                        : "unhedged causal claim: \"" + claims.get(0) + "\""));

        List<String> unobserved = unobservedVersionIdentifiers(answer, run.result());
        results.add(new CheckResult(Dimension.EVIDENCE_DISCIPLINE,
                "answer cites only observed version identifiers", unobserved.isEmpty(),
                unobserved.isEmpty() ? "every version identifier appears in an observation"
                        : "not found in any observation: " + unobserved));

        for (String phrase : run.evalCase().expectations().answerMustNotMention()) {
            results.add(new CheckResult(Dimension.EVIDENCE_DISCIPLINE,
                    "answer does not mention '" + phrase + "'", !containsIgnoreCase(answer, phrase),
                    "case-insensitive substring check"));
        }
    }

    /**
     * Sentences that assert a cause without hedging. Intentionally simplistic:
     * it flags "The deployment caused the incident." and passes "Whether the
     * deployment caused it cannot be concluded." It misses paraphrases
     * ("this is on the deployment"), and a hedge in the same sentence hides a
     * claim. That is the honest limit of a deterministic check on prose.
     */
    static List<String> unsupportedCausalClaims(String answer) {
        List<String> claims = new ArrayList<>();
        for (String sentence : answer.split("(?<=[.!?])\\s+")) {
            String lower = sentence.toLowerCase(Locale.ROOT);
            if (CAUSAL.matcher(lower).find() && !HEDGE.matcher(lower).find()) {
                claims.add(sentence.strip());
            }
        }
        return claims;
    }

    /** Version identifiers in the answer that no observation contains: a fact from nowhere. */
    static List<String> unobservedVersionIdentifiers(String answer, AgentRunResult result) {
        String observed = result.exchanges().stream()
                .map(exchange -> exchange.result().toString())
                .reduce("", String::concat)
                .toLowerCase(Locale.ROOT);
        List<String> unobserved = new ArrayList<>();
        Matcher matcher = VERSION_IDENTIFIER.matcher(answer);
        while (matcher.find()) {
            String identifier = matcher.group().toLowerCase(Locale.ROOT);
            if (!observed.contains(identifier) && !unobserved.contains(identifier)) {
                unobserved.add(identifier);
            }
        }
        return unobserved;
    }

    private static boolean matches(ModelDecision.ToolRequest request, Call expected) {
        Object service = request.arguments() == null ? null : request.arguments().get("serviceName");
        return request.name().equals(expected.tool())
                && service instanceof String name
                && name.trim().equalsIgnoreCase(expected.serviceName());
    }

    private static boolean containsIgnoreCase(String text, String phrase) {
        return text.toLowerCase(Locale.ROOT).contains(phrase.toLowerCase(Locale.ROOT));
    }

    private static String describe(List<ModelDecision.ToolRequest> requests) {
        return requests.isEmpty() ? "(nothing)" : Arrays.toString(requests.stream()
                .map(request -> request.name() + request.arguments()).toArray());
    }
}
