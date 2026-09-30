package dev.agentic.handbook.labs.evaluation;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;

/**
 * Plain terminal output for the evaluation report. ASCII only, no colors, no
 * framework: the report is read by a person, and the layout is a means, not
 * the point. It is also friendly to pasting into a Markdown review comment.
 */
public final class ReportPrinter {

    private ReportPrinter() {
    }

    /** Every case, every check — failures are never folded into a total. */
    public static void printCases(PrintStream out, EvaluationReport report) {
        for (CaseReport caseReport : report.cases()) {
            out.println();
            out.println((caseReport.acceptable() ? "[PASS] " : "[FAIL] ") + caseReport.evalCase().id()
                    + "  (" + caseReport.evalCase().kind() + ")");
            for (CheckResult check : caseReport.checks()) {
                out.println("  " + (check.passed() ? "PASS  " : "FAIL  ") + check.name());
                if (!check.passed()) {
                    out.println("        " + check.detail());
                }
            }
            out.println("  Result: " + caseReport.checksPassed() + " / " + caseReport.checks().size()
                    + " checks passed");
        }
    }

    /** Counts, per dimension, with the failing cases named so the reader can drill back. */
    public static void printSummary(PrintStream out, EvaluationReport report) {
        out.println();
        out.println("Summary (" + report.versionName() + ")");
        out.println("  Cases acceptable:  " + report.casesAcceptable() + " / " + report.cases().size());
        out.println("  Checks passed:     " + report.checksPassed() + " / " + report.checksTotal());
        out.println("  Per dimension:");
        for (Map.Entry<Dimension, EvaluationReport.Tally> entry : report.byDimension().entrySet()) {
            out.println(String.format("    %-20s %d / %d", entry.getKey().label(),
                    entry.getValue().passed(), entry.getValue().total()));
        }
        List<CaseReport> failed = report.failedCases();
        if (failed.isEmpty()) {
            out.println("  Failing cases:     none");
        } else {
            out.println("  Failing cases:     " + failed.stream().map(c -> c.evalCase().id()).toList());
            out.println("  These counts are a summary. Read the failing checks above; they are the evidence.");
        }
    }

    /** Baseline and candidate side by side, then the individual regressions. */
    public static void printComparison(PrintStream out, Comparison comparison) {
        EvaluationReport baseline = comparison.baseline();
        EvaluationReport candidate = comparison.candidate();
        out.println();
        out.println(row("", baseline.versionName(), candidate.versionName()));
        out.println(row("Cases acceptable",
                baseline.casesAcceptable() + " / " + baseline.cases().size(),
                candidate.casesAcceptable() + " / " + candidate.cases().size()));
        out.println(row("Checks passed",
                baseline.checksPassed() + " / " + baseline.checksTotal(),
                candidate.checksPassed() + " / " + candidate.checksTotal()));
        Map<Dimension, EvaluationReport.Tally> before = baseline.byDimension();
        Map<Dimension, EvaluationReport.Tally> after = candidate.byDimension();
        for (Dimension dimension : before.keySet()) {
            EvaluationReport.Tally b = before.get(dimension);
            EvaluationReport.Tally c = after.getOrDefault(dimension, new EvaluationReport.Tally(0, 0));
            out.println(row(dimension.label(),
                    b.passed() + " / " + b.total(), c.passed() + " / " + c.total()));
        }

        List<Comparison.Regression> regressions = comparison.regressions();
        out.println();
        out.println("Passed for the baseline, fails for the candidate:");
        if (regressions.isEmpty()) {
            out.println("  (none)");
        }
        for (Comparison.Regression regression : regressions) {
            out.println("  " + regression.caseId() + " -> [" + regression.dimension().label() + "] "
                    + regression.checkName());
            out.println("      " + regression.detail());
        }
        out.println();
        out.println("REGRESSIONS FOUND: " + regressions.size() + " check(s) in "
                + comparison.regressedCaseIds().size() + " case(s)");
    }

    private static String row(String label, String baseline, String candidate) {
        return String.format("%-26s %-12s %-12s", label, baseline, candidate).stripTrailing();
    }

    /**
     * What a person needs to decide "would I accept this behavior?": the input,
     * the trajectory with its observations, the final answer, and the checks.
     * The deterministic checks inform the reviewer; they do not replace them.
     */
    public static void printReview(PrintStream out, CaseReport report) {
        EvalCase evalCase = report.evalCase();
        out.println();
        out.println("CASE:   " + evalCase.id() + "  (" + evalCase.kind() + ")");
        out.println("Why:    " + evalCase.why());
        out.println("Goal:   " + evalCase.goal());
        if (report.result().isEmpty()) {
            out.println("Trace:  the run did not complete");
        } else {
            AgentRunResult result = report.result().get();
            out.println("Trajectory:");
            int step = 0;
            for (ToolExchange exchange : result.exchanges()) {
                step++;
                out.println("  " + step + ". " + exchange.request().name() + " " + exchange.request().arguments());
                out.println("     observed: " + exchange.result());
            }
            result.failure().ifPresent(failure -> {
                out.println("  " + (result.exchanges().size() + 1) + ". " + failure.request().name() + " "
                        + failure.request().arguments());
                out.println("     rejected: " + failure.detail());
            });
            out.println("Stop:   " + result.stopReason() + " after " + result.modelSteps()
                    + " model decision(s), budget " + evalCase.expectations().maxModelSteps());
            out.println("Answer: " + result.answer().orElse("(none)"));
        }
        out.println("Checks: " + report.checksPassed() + " / " + report.checks().size() + " passed"
                + (report.acceptable() ? "" : " -- failing: "
                        + report.failedChecks().stream().map(CheckResult::name).toList()));
        out.println("Would you accept this behavior? The checks inform that decision; they do not make it.");
    }
}
