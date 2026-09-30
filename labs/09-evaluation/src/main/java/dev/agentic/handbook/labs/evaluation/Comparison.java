package dev.agentic.handbook.labs.evaluation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The same cases and the same checks, run against two versions of the system.
 * Comparing them is what turns "I changed the prompt" into a reviewable
 * behavioral change: a regression is a check that fails now and did not fail before.
 */
public record Comparison(EvaluationReport baseline, EvaluationReport candidate) {

    /** One check that fails for the candidate and did not already fail for the baseline. */
    public record Regression(String caseId, Dimension dimension, String checkName, String detail) {
    }

    /**
     * Check-level regressions, in case order: a check that fails for the
     * candidate and did not already fail for the baseline. That includes a
     * check the baseline never ran — a candidate that crashes, or that
     * produces an answer where the baseline stopped, fails checks the baseline
     * has no counterpart for, and that is still a change for the worse. A
     * check that already failed for the baseline is not a regression.
     */
    public List<Regression> regressions() {
        Map<String, Boolean> baselineResults = new HashMap<>();
        for (CaseReport report : baseline.cases()) {
            for (CheckResult check : report.checks()) {
                baselineResults.put(key(report.evalCase().id(), check.name()), check.passed());
            }
        }
        List<Regression> regressions = new ArrayList<>();
        for (CaseReport report : candidate.cases()) {
            for (CheckResult check : report.failedChecks()) {
                if (!Boolean.FALSE.equals(baselineResults.get(key(report.evalCase().id(), check.name())))) {
                    regressions.add(new Regression(report.evalCase().id(), check.dimension(),
                            check.name(), check.detail()));
                }
            }
        }
        return List.copyOf(regressions);
    }

    /** Cases that were acceptable for the baseline and are not for the candidate. */
    public List<String> regressedCaseIds() {
        List<String> ids = new ArrayList<>();
        for (CaseReport report : candidate.cases()) {
            boolean baselineAcceptable = baseline.cases().stream()
                    .anyMatch(b -> b.evalCase().id().equals(report.evalCase().id()) && b.acceptable());
            if (baselineAcceptable && !report.acceptable()) {
                ids.add(report.evalCase().id());
            }
        }
        return List.copyOf(ids);
    }

    private static String key(String caseId, String checkName) {
        return caseId + "|" + checkName;
    }
}
