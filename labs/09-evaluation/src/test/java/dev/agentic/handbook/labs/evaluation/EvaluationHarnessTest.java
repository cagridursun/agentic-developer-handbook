package dev.agentic.handbook.labs.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The harness as a whole: baseline, deliberate regression, comparison, and the
 * report. The point of the regression tests is the lesson of the lab — every
 * runtime-level guarantee still holds for the regressed candidate, and only
 * the behavioral checks notice.
 */
class EvaluationHarnessTest {

    private static final List<EvalCase> CASES = HelioIncidentsV1.cases();

    private static final EvaluationReport BASELINE =
            EvaluationRunner.run(HelioIncidentsV1.NAME, CASES, ScriptedBehavior.baseline());
    private static final EvaluationReport CANDIDATE =
            EvaluationRunner.run(HelioIncidentsV1.NAME, CASES, ScriptedBehavior.candidate());

    private static String printed(java.util.function.Consumer<PrintStream> action) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            action.accept(out);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    // --- Baseline ---

    @Test
    void baselineBehaviorPassesEveryCaseAndEveryCheck() {
        assertEquals(CASES.size(), BASELINE.casesAcceptable());
        assertEquals(BASELINE.checksTotal(), BASELINE.checksPassed());
        assertTrue(BASELINE.failedCases().isEmpty());
    }

    @Test
    void baselineTakesTheIntendedTrajectoryForEachCase() {
        Map<String, List<String>> trajectories = BASELINE.cases().stream().collect(Collectors.toMap(
                report -> report.evalCase().id(),
                report -> report.result().orElseThrow().requests().stream()
                        .map(ModelDecision.ToolRequest::name).toList()));
        assertEquals(List.of("getServiceStatus", "getRecentDeployment"), trajectories.get("degraded-after-deployment"));
        assertEquals(List.of("getServiceStatus"), trajectories.get("healthy-service"));
        assertEquals(List.of("getServiceStatus", "getRecentDeployment"), trajectories.get("degraded-no-deployment"));
        assertEquals(List.of("getServiceStatus"), trajectories.get("planned-maintenance"));
        assertEquals(List.of("getServiceStatus"), trajectories.get("unknown-service"));
    }

    // --- The deliberate regression ---

    @Test
    void candidateFailsExactlyTheIntendedCases() {
        Set<String> failing = CANDIDATE.failedCases().stream()
                .map(report -> report.evalCase().id()).collect(Collectors.toSet());
        assertEquals(Set.of("degraded-after-deployment", "leading-question",
                "healthy-service", "planned-maintenance"), failing);
    }

    @Test
    void theRegressionsAreCaughtByTheIntendedDimensionsAndNoOther() {
        Comparison comparison = new Comparison(BASELINE, CANDIDATE);
        Map<String, Dimension> byCase = comparison.regressions().stream().collect(
                Collectors.toMap(Comparison.Regression::caseId, Comparison.Regression::dimension));
        assertEquals(4, comparison.regressions().size());
        assertEquals(Dimension.EVIDENCE_DISCIPLINE, byCase.get("degraded-after-deployment"));
        assertEquals(Dimension.EVIDENCE_DISCIPLINE, byCase.get("leading-question"));
        assertEquals(Dimension.TOOL_RESTRAINT, byCase.get("healthy-service"));
        assertEquals(Dimension.TOOL_RESTRAINT, byCase.get("planned-maintenance"));
    }

    @Test
    void theCandidateStillSatisfiesEveryRuntimeLevelGuarantee() {
        // "All the tests pass while behavior gets worse": every regressed run is bounded,
        // ends on a final answer, and requests only allowlisted tools with valid arguments.
        for (CaseReport report : CANDIDATE.cases()) {
            AgentRunResult result = report.result().orElseThrow();
            if (report.evalCase().kind() == CaseKind.FAILURE) {
                assertEquals(StopReason.REJECTED_TOOL_CALL, result.stopReason());
            } else {
                assertEquals(StopReason.FINAL_ANSWER, result.stopReason(), report.evalCase().id());
            }
            assertTrue(result.modelSteps() <= HelioIncidentsV1.MAX_STEPS, report.evalCase().id());
        }
        assertEquals(BASELINE.byDimension().get(Dimension.BOUNDED_EXECUTION),
                CANDIDATE.byDimension().get(Dimension.BOUNDED_EXECUTION));
        assertEquals(BASELINE.byDimension().get(Dimension.TOOL_SELECTION),
                CANDIDATE.byDimension().get(Dimension.TOOL_SELECTION));
    }

    // --- Report and summary ---

    @Test
    void summaryCountsAreDerivedFromTheIndividualResults() {
        for (EvaluationReport report : List.of(BASELINE, CANDIDATE)) {
            int passedByCase = report.cases().stream().mapToInt(CaseReport::checksPassed).sum();
            int totalByCase = report.cases().stream().mapToInt(c -> c.checks().size()).sum();
            assertEquals(passedByCase, report.checksPassed());
            assertEquals(totalByCase, report.checksTotal());
            assertEquals(report.checksPassed(),
                    report.byDimension().values().stream().mapToInt(EvaluationReport.Tally::passed).sum());
            assertEquals(report.checksTotal(),
                    report.byDimension().values().stream().mapToInt(EvaluationReport.Tally::total).sum());
        }
        assertTrue(CANDIDATE.checksPassed() < CANDIDATE.checksTotal());
    }

    @Test
    void theReportPreservesEachIndividualFailureWithItsCaseAndCheck() {
        String report = printed(out -> ReportPrinter.printCases(out, CANDIDATE));
        assertTrue(report.contains("[FAIL] degraded-after-deployment"));
        assertTrue(report.contains("FAIL  no unsupported root-cause claim"));
        assertTrue(report.contains("caused the incident"), "the failing sentence is quoted as evidence");
        assertTrue(report.contains("FAIL  did not request [getRecentDeployment]"));
        assertTrue(report.contains("[PASS] unknown-service"));
        assertTrue(report.contains("Result: 10 / 11 checks passed"));
    }

    @Test
    void theSummaryNamesTheFailingCasesInsteadOfHidingThemInATotal() {
        String summary = printed(out -> ReportPrinter.printSummary(out, CANDIDATE));
        assertTrue(summary.contains("Cases acceptable:  2 / 6"));
        assertTrue(summary.contains("healthy-service"));
        assertTrue(summary.contains("Evidence discipline"));
        assertFalse(summary.toLowerCase().contains("score"), "there is deliberately no magic score");
    }

    @Test
    void theComparisonListsEveryRegressionAndCountsThem() {
        String comparison = printed(out -> ReportPrinter.printComparison(out, new Comparison(BASELINE, CANDIDATE)));
        assertTrue(comparison.contains("REGRESSIONS FOUND: 4 check(s) in 4 case(s)"));
        assertTrue(comparison.contains("leading-question -> [Evidence discipline]"));
        assertTrue(comparison.contains("planned-maintenance -> [Tool restraint]"));
    }

    @Test
    void comparingAVersionWithItselfFindsNoRegression() {
        Comparison same = new Comparison(BASELINE, BASELINE);
        assertTrue(same.regressions().isEmpty());
        assertTrue(same.regressedCaseIds().isEmpty());
    }

    @Test
    void theHumanReviewShowsInputTraceAnswerAndChecks() {
        CaseReport regressed = CANDIDATE.cases().get(0);
        String review = printed(out -> ReportPrinter.printReview(out, regressed));
        assertTrue(review.contains("Goal:"));
        assertTrue(review.contains("observed: {serviceName=notifications, status=DEGRADED"));
        assertTrue(review.contains("Stop:   FINAL_ANSWER after 3 model decision(s)"));
        assertTrue(review.contains("Answer: notifications is DEGRADED"));
        assertTrue(review.contains("failing: [no unsupported root-cause claim]"));
        assertTrue(review.contains("Would you accept this behavior?"));
    }

    // --- Runner robustness ---

    @Test
    void aRunThatCrashesIsAFailedCheckAndOtherCasesStillRun() {
        SystemVersion crashing = new SystemVersion("crashing", "throws on the first decision", () -> new AgentModel() {
            @Override
            public ModelDecision start(String goal) {
                throw new IllegalStateException("provider unavailable");
            }

            @Override
            public ModelDecision observe(ToolExchange exchange) {
                throw new IllegalStateException("provider unavailable");
            }
        });
        EvaluationReport report = EvaluationRunner.run("set", CASES, crashing);
        assertEquals(CASES.size(), report.cases().size());
        for (CaseReport caseReport : report.cases()) {
            assertTrue(caseReport.result().isEmpty());
            assertFalse(caseReport.acceptable());
            assertTrue(caseReport.checks().get(0).detail().contains("provider unavailable"));
        }
    }

    private static SystemVersion crashing() {
        return new SystemVersion("crashing", "throws on the first decision", () -> new AgentModel() {
            @Override
            public ModelDecision start(String goal) {
                throw new IllegalStateException("provider unavailable");
            }

            @Override
            public ModelDecision observe(ToolExchange exchange) {
                throw new IllegalStateException("provider unavailable");
            }
        });
    }

    @Test
    void aCandidateThatCrashesIsReportedAsRegressionsNotJustCountedAsRegressedCases() {
        EvaluationReport crashed = EvaluationRunner.run(HelioIncidentsV1.NAME, CASES, crashing());
        Comparison comparison = new Comparison(BASELINE, crashed);

        assertEquals(CASES.size(), comparison.regressedCaseIds().size());
        Set<String> reported = comparison.regressions().stream()
                .map(Comparison.Regression::caseId).collect(Collectors.toSet());
        assertEquals(Set.copyOf(comparison.regressedCaseIds()), reported,
                "every regressed case has at least one listed regression");
        assertTrue(comparison.regressions().stream().allMatch(r -> r.checkName().equals("the run completed")));
        String text = printed(out -> ReportPrinter.printComparison(out, comparison));
        assertTrue(text.contains("REGRESSIONS FOUND: " + CASES.size() + " check(s) in " + CASES.size() + " case(s)"));
    }

    @Test
    void aCheckThatAlreadyFailedForTheBaselineIsNotARegression() {
        EvaluationReport crashed = EvaluationRunner.run(HelioIncidentsV1.NAME, CASES, crashing());
        Comparison comparison = new Comparison(crashed, crashed);
        assertTrue(comparison.regressions().isEmpty());
        assertTrue(comparison.regressedCaseIds().isEmpty());
    }

    @Test
    void theComparisonTableShowsADimensionThatOnlyTheCandidateHasChecksFor() {
        // The crashed baseline only has bounded-execution checks; the candidate has all five dimensions.
        EvaluationReport crashed = EvaluationRunner.run(HelioIncidentsV1.NAME, CASES, crashing());
        String text = printed(out -> ReportPrinter.printComparison(out, new Comparison(crashed, CANDIDATE)));
        assertTrue(text.contains("Evidence discipline"), text);
        assertTrue(text.contains("Tool restraint"), text);
    }

    @Test
    void aModelGetsAFreshInstancePerCase() {
        int[] created = {0};
        SystemVersion counting = new SystemVersion("counting", "counts instances", () -> {
            created[0]++;
            return ScriptedBehavior.baseline().models().get();
        });
        EvaluationRunner.run("set", CASES, counting);
        assertEquals(CASES.size(), created[0]);
    }
}
