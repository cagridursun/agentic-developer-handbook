package dev.agentic.handbook.labs.evaluation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The per-case reports for one system version on one evaluation set, plus the
 * counts derived from them.
 *
 * <p>The counts are a summary, not a verdict. There is deliberately no single
 * score and no weighting: a failed check in one case can matter far more than
 * ten passed checks elsewhere, and a number cannot say which. Every count can
 * be traced back to a case and a check.
 */
public record EvaluationReport(String setName, String versionName, List<CaseReport> cases) {

    /** Passed and total checks for one dimension. */
    public record Tally(int passed, int total) {
    }

    public int casesAcceptable() {
        return (int) cases.stream().filter(CaseReport::acceptable).count();
    }

    public int checksTotal() {
        return cases.stream().mapToInt(report -> report.checks().size()).sum();
    }

    public int checksPassed() {
        return cases.stream().mapToInt(CaseReport::checksPassed).sum();
    }

    public List<CaseReport> failedCases() {
        return cases.stream().filter(report -> !report.acceptable()).toList();
    }

    /** Per-dimension tallies, in dimension order; a dimension with no checks is omitted. */
    public Map<Dimension, Tally> byDimension() {
        Map<Dimension, int[]> counts = new EnumMap<>(Dimension.class);
        for (CaseReport report : cases) {
            for (CheckResult check : report.checks()) {
                int[] tally = counts.computeIfAbsent(check.dimension(), d -> new int[2]);
                tally[1]++;
                if (check.passed()) {
                    tally[0]++;
                }
            }
        }
        Map<Dimension, Tally> result = new EnumMap<>(Dimension.class);
        counts.forEach((dimension, tally) -> result.put(dimension, new Tally(tally[0], tally[1])));
        return result;
    }
}
