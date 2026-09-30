package dev.agentic.handbook.labs.evaluation;

import java.util.List;
import java.util.Optional;

/**
 * Everything known about one case for one system version: the trace (absent
 * only if the run itself crashed) and every individual check result.
 */
public record CaseReport(EvalCase evalCase, Optional<AgentRunResult> result, List<CheckResult> checks) {

    /** A case is acceptable only if every one of its checks passed. */
    public boolean acceptable() {
        return checks.stream().allMatch(CheckResult::passed);
    }

    public int checksPassed() {
        return (int) checks.stream().filter(CheckResult::passed).count();
    }

    public List<CheckResult> failedChecks() {
        return checks.stream().filter(check -> !check.passed()).toList();
    }
}
