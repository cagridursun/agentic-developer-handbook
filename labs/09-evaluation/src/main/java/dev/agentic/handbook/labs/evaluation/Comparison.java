package dev.agentic.handbook.labs.evaluation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The same cases and the same checks, run against two versions of the system.
 * Comparing them is what turns "I changed the prompt" into a reviewable
 * behavioral change: a regression is a check that passed before and fails now.
 */
public record Comparison(EvaluationReport baseline, EvaluationReport candidate) {

    /** One check that passed for the baseline and fails for the candidate. */
    public record Regression(String caseId, Dimension dimension, String checkName, String detail) {
    }

    /** Check-level regressions, in case order. A check the baseline did not run cannot regress. */
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
                if (Boolean.TRUE.equals(baselineResults.get(key(report.evalCase().id(), check.name())))) {
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
