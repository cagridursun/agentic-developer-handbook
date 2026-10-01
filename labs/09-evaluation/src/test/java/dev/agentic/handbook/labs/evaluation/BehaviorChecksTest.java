package dev.agentic.handbook.labs.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Each check is proven on hand-built traces, independent of any scripted
 * behavior: a check that has never been seen to fail proves nothing.
 */
class BehaviorChecksTest {

    private static final ModelDecision.ToolRequest STATUS =
            new ModelDecision.ToolRequest("getServiceStatus", Map.of("serviceName", "notifications"));
    private static final ModelDecision.ToolRequest DEPLOYMENT =
            new ModelDecision.ToolRequest("getRecentDeployment", Map.of("serviceName", "notifications"));

    private static final EvalCase.Expectations EXPECT_BOTH = new EvalCase.Expectations(
            List.of(new EvalCase.Call("getServiceStatus", "notifications"),
                    new EvalCase.Call("getRecentDeployment", "notifications")),
            Set.of(), StopReason.FINAL_ANSWER, 4, true,
            List.of("DEGRADED", "notifications-2.4.1"), List.of());

    private static final EvalCase.Expectations EXPECT_STATUS_ONLY = new EvalCase.Expectations(
            List.of(new EvalCase.Call("getServiceStatus", "notifications")),
            Set.of("getRecentDeployment"), StopReason.FINAL_ANSWER, 4, true,
            List.of(), List.of("outage"));

    private static final String GOOD_ANSWER = "notifications is DEGRADED. The most recent deployment is "
            + "notifications-2.4.1. Whether it caused the degradation cannot be concluded.";

    private static ToolExchange exchange(ModelDecision.ToolRequest request, Map<String, Object> result) {
        return new ToolExchange(request, result);
    }

    private static final ToolExchange STATUS_EXCHANGE = exchange(STATUS,
            Map.of("serviceName", "notifications", "status", "DEGRADED", "message", "slow"));
    private static final ToolExchange DEPLOYMENT_EXCHANGE = exchange(DEPLOYMENT,
            Map.of("serviceName", "notifications", "version", "notifications-2.4.1"));

    private static AgentRunResult finished(int steps, String answer, ToolExchange... exchanges) {
        return new AgentRunResult(StopReason.FINAL_ANSWER, Optional.of(answer), steps,
                List.of(exchanges), Optional.empty());
    }

    private static CheckResult check(List<CheckResult> results, String namePart) {
        List<CheckResult> found = results.stream().filter(r -> r.name().contains(namePart)).toList();
        assertEquals(1, found.size(), "expected exactly one check containing '" + namePart + "': " + results);
        return found.get(0);
    }

    private static List<CheckResult> evaluate(EvalCase.Expectations expectations, AgentRunResult result) {
        return BehaviorChecks.evaluate(new EvaluationRun(
                new EvalCase("case", CaseKind.NORMAL, "goal", "why", expectations), result));
    }

    @Test
    void aCompliantTracePassesEveryCheck() {
        List<CheckResult> results = evaluate(EXPECT_BOTH, finished(3, GOOD_ANSWER, STATUS_EXCHANGE, DEPLOYMENT_EXCHANGE));
        assertTrue(results.stream().allMatch(CheckResult::passed), results.toString());
    }

    // --- Tool selection (trajectory) ---

    @Test
    void aMissingRequiredToolFailsToolSelection() {
        List<CheckResult> results = evaluate(EXPECT_BOTH, finished(2, GOOD_ANSWER, STATUS_EXCHANGE));
        CheckResult missing = check(results, "requested getRecentDeployment(notifications)");
        assertFalse(missing.passed());
        assertEquals(Dimension.TOOL_SELECTION, missing.dimension());
        assertTrue(check(results, "requested getServiceStatus(notifications)").passed());
    }

    @Test
    void aRequiredToolWithTheWrongArgumentDoesNotCount() {
        ToolExchange wrongService = exchange(
                new ModelDecision.ToolRequest("getServiceStatus", Map.of("serviceName", "billing")),
                Map.of("serviceName", "billing", "status", "HEALTHY", "message", "ok"));
        List<CheckResult> results = evaluate(EXPECT_STATUS_ONLY, finished(2, "billing is HEALTHY", wrongService));
        assertFalse(check(results, "requested getServiceStatus(notifications)").passed());
    }

    @Test
    void argumentMatchingToleratesCaseAndWhitespaceBecauseTheToolDoes() {
        ToolExchange spaced = exchange(
                new ModelDecision.ToolRequest("getServiceStatus", Map.of("serviceName", " Notifications ")),
                Map.of("serviceName", "notifications", "status", "DEGRADED", "message", "slow"));
        List<CheckResult> results = evaluate(EXPECT_STATUS_ONLY,
                finished(2, "notifications is DEGRADED", spaced));
        assertTrue(check(results, "requested getServiceStatus(notifications)").passed());
    }

    @Test
    void statusMustBeCheckedFirstAndThatOneIsExactAboutOrder() {
        List<CheckResult> results = evaluate(EXPECT_BOTH,
                finished(3, GOOD_ANSWER, DEPLOYMENT_EXCHANGE, STATUS_EXCHANGE));
        assertFalse(check(results, "status checked first").passed());
        // The flexible checks still pass: both tools were requested, in any order.
        assertTrue(check(results, "requested getRecentDeployment(notifications)").passed());
        assertTrue(check(results, "requested getServiceStatus(notifications)").passed());
    }

    @Test
    void extraDecisionsBeforeTheRequiredToolDoNotFailFlexibleChecks() {
        // Implementation details may change; the contract is "the tool was requested".
        List<CheckResult> results = evaluate(EXPECT_BOTH,
                finished(4, GOOD_ANSWER, STATUS_EXCHANGE, STATUS_EXCHANGE, DEPLOYMENT_EXCHANGE));
        assertTrue(check(results, "requested getRecentDeployment(notifications)").passed());
    }

    // --- Tool restraint (unnecessary actions) ---

    @Test
    void aForbiddenToolRequestFailsRestraint() {
        List<CheckResult> results = evaluate(EXPECT_STATUS_ONLY,
                finished(3, "notifications is DEGRADED", STATUS_EXCHANGE, DEPLOYMENT_EXCHANGE));
        CheckResult restraint = check(results, "did not request");
        assertFalse(restraint.passed());
        assertEquals(Dimension.TOOL_RESTRAINT, restraint.dimension());
        assertTrue(restraint.detail().contains("getRecentDeployment"));
    }

    @Test
    void aForbiddenToolThatWasRejectedStillCountsBecauseTheTrajectoryIsWhatWasProposed() {
        AgentRunResult rejected = new AgentRunResult(StopReason.REJECTED_TOOL_CALL, Optional.empty(), 2,
                List.of(STATUS_EXCHANGE), Optional.of(new AgentRunResult.Failure(DEPLOYMENT, "rejected")));
        List<CheckResult> results = evaluate(EXPECT_STATUS_ONLY, rejected);
        assertFalse(check(results, "did not request").passed());
    }

    @Test
    void aSafeStopMustNotManufactureObservationsOrAnAnswer() {
        EvalCase.Expectations unknown = new EvalCase.Expectations(
                List.of(new EvalCase.Call("getServiceStatus", "notifications")),
                Set.of(), StopReason.REJECTED_TOOL_CALL, 4, false, List.of(), List.of());
        AgentRunResult manufactured = new AgentRunResult(StopReason.REJECTED_TOOL_CALL, Optional.of("invented"),
                1, List.of(STATUS_EXCHANGE), Optional.of(new AgentRunResult.Failure(STATUS, "rejected")));
        List<CheckResult> results = evaluate(unknown, manufactured);
        assertFalse(check(results, "no observation was manufactured").passed());
        assertFalse(check(results, "no final answer was fabricated").passed());
    }

    // --- Bounded execution ---

    @Test
    void exceedingTheStepBudgetFails() {
        List<CheckResult> results = evaluate(EXPECT_BOTH, finished(5, GOOD_ANSWER, STATUS_EXCHANGE, DEPLOYMENT_EXCHANGE));
        CheckResult budget = check(results, "step budget");
        assertFalse(budget.passed());
        assertEquals(Dimension.BOUNDED_EXECUTION, budget.dimension());
    }

    @Test
    void stoppingForTheWrongReasonFails() {
        AgentRunResult exhausted = new AgentRunResult(StopReason.MAX_STEPS, Optional.empty(), 4,
                List.of(STATUS_EXCHANGE, STATUS_EXCHANGE, STATUS_EXCHANGE, STATUS_EXCHANGE), Optional.empty());
        List<CheckResult> results = evaluate(EXPECT_STATUS_ONLY, exhausted);
        assertFalse(check(results, "stopped for the intended reason").passed());
        assertTrue(check(results, "step budget").passed(), "4 decisions is within a budget of 4");
        assertFalse(check(results, "final answer was produced").passed());
    }

    // --- Final outcome and evidence discipline (the answer) ---

    @Test
    void anAnswerThatOmitsTheObservedStatusOrRequiredFactFails() {
        List<CheckResult> results = evaluate(EXPECT_BOTH,
                finished(3, "Something happened.", STATUS_EXCHANGE, DEPLOYMENT_EXCHANGE));
        assertFalse(check(results, "reports the observed status").passed());
        assertFalse(check(results, "mentions 'notifications-2.4.1'").passed());
        assertEquals(Dimension.FINAL_OUTCOME, check(results, "mentions 'DEGRADED'").dimension());
    }

    @Test
    void aForbiddenPhraseInTheAnswerFailsEvidenceDiscipline() {
        List<CheckResult> results = evaluate(EXPECT_STATUS_ONLY,
                finished(2, "notifications is DEGRADED and there is a major outage.", STATUS_EXCHANGE));
        CheckResult forbidden = check(results, "does not mention 'outage'");
        assertFalse(forbidden.passed());
        assertEquals(Dimension.EVIDENCE_DISCIPLINE, forbidden.dimension());
    }

    @Test
    void aVersionIdentifierNoObservationContainsIsAFactFromNowhere() {
        List<CheckResult> results = evaluate(EXPECT_STATUS_ONLY,
                finished(2, "notifications is DEGRADED after deploying notifications-9.9.9.", STATUS_EXCHANGE));
        CheckResult grounded = check(results, "only observed version identifiers");
        assertFalse(grounded.passed());
        assertTrue(grounded.detail().contains("notifications-9.9.9"));
    }

    @Test
    void anObservedVersionIdentifierIsGrounded() {
        assertEquals(List.of(), BehaviorChecks.unobservedVersionIdentifiers(
                "It is notifications-2.4.1.", finished(3, "", STATUS_EXCHANGE, DEPLOYMENT_EXCHANGE)));
    }

    @Test
    void theRootCauseCheckCatchesTheDeliberateRegression() {
        List<CheckResult> results = evaluate(EXPECT_BOTH, finished(3,
                "notifications is DEGRADED. The deployment notifications-2.4.1 caused the incident.",
                STATUS_EXCHANGE, DEPLOYMENT_EXCHANGE));
        CheckResult cause = check(results, "no unsupported root-cause claim");
        assertFalse(cause.passed());
        assertEquals(Dimension.EVIDENCE_DISCIPLINE, cause.dimension());
        assertTrue(cause.detail().contains("caused the incident"));
    }

    @Test
    void theRootCauseCheckAcceptsHedgedAndNegatedSentences() {
        for (String honest : List.of(
                "The root cause is not proven.",
                "Whether the deployment caused it cannot be concluded.",
                "The deployment may have caused this, but that is unproven.",
                "There is no evidence that the release broke anything.",
                "Was it the deployment that caused it?",
                "The service is DEGRADED and the deployment is notifications-2.4.1.")) {
            assertEquals(List.of(), BehaviorChecks.unsupportedCausalClaims(honest), honest);
        }
    }

    @Test
    void theRootCauseCheckFlagsPlainAssertionsOfCause() {
        for (String blame : List.of(
                "The deployment caused the incident.",
                "The root cause is the retry policy change.",
                "The outage was due to the new release.",
                "It broke because of notifications-2.4.1.")) {
            assertEquals(1, BehaviorChecks.unsupportedCausalClaims(blame).size(), blame);
        }
    }

    @Test
    void theRootCauseCheckIsSimplisticAndItsLimitIsKnown() {
        // Documented limit: a paraphrase evades it, and a hedge hides a claim in the same sentence.
        assertEquals(List.of(), BehaviorChecks.unsupportedCausalClaims("This is on the deployment."));
        assertEquals(List.of(), BehaviorChecks.unsupportedCausalClaims(
                "The deployment caused the incident, although I could be wrong."));
    }
}
