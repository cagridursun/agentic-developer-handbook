package dev.agentic.handbook.labs.evaluation;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lab 09: evaluate the Lab 07 agent — across a small versioned set of cases,
 * on both the final answer and the trajectory that produced it.
 *
 * <p>Default mode is fully deterministic: scripted behaviors drive the real
 * bounded runtime, so the harness and its report are observable with no key,
 * no network, and no model call. {@code --live} runs the same cases, the same
 * runtime, and the same checks with Gemini as the system under evaluation.
 *
 * <p>The scripted behaviors make the harness inspectable. They are not a real
 * model evaluation: live-model evaluation is the probabilistic use case of the
 * same harness.
 */
public final class EvaluationExample {

    /** Same default and override rules as the earlier labs. */
    static final String DEFAULT_MODEL = "gemini-3.8-flash";

    private EvaluationExample() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.getenv(), System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /** The whole program, with its environment and streams passed in so tests can drive it. */
    static int run(String[] args, Map<String, String> env, PrintStream out, PrintStream err) {
        boolean live = args.length > 0 && args[0].equals("--live");
        if (live) {
            Optional<String> apiKey = apiKey(env);
            if (apiKey.isEmpty()) {
                err.println("No API key found for --live mode.");
                err.println("Set the GOOGLE_API_KEY environment variable "
                        + "(create a key at https://aistudio.google.com/apikey).");
                return 1;
            }
            runLive(out, apiKey.get(), model(env));
        } else {
            runScripted(out);
        }
        return 0;
    }

    private static void runScripted(PrintStream out) {
        List<EvalCase> cases = HelioIncidentsV1.cases();

        banner(out, "AGENT EVALUATION");
        printSet(out, cases);

        banner(out, "1. BASELINE");
        SystemVersion baseline = ScriptedBehavior.baseline();
        out.println("Version: " + baseline.name() + " -- " + baseline.description());
        EvaluationReport baselineReport = EvaluationRunner.run(HelioIncidentsV1.NAME, cases, baseline);
        ReportPrinter.printCases(out, baselineReport);
        ReportPrinter.printSummary(out, baselineReport);

        banner(out, "2. CANDIDATE: THE SAME CASES, THE SAME CHECKS, A CHANGED SYSTEM");
        SystemVersion candidate = ScriptedBehavior.candidate();
        out.println("Version: " + candidate.name() + " -- " + candidate.description());
        out.println("Every run below still ends on a final answer inside the step budget.");
        EvaluationReport candidateReport = EvaluationRunner.run(HelioIncidentsV1.NAME, cases, candidate);
        ReportPrinter.printCases(out, candidateReport);
        ReportPrinter.printSummary(out, candidateReport);

        banner(out, "3. BASELINE VS CANDIDATE");
        ReportPrinter.printComparison(out, new Comparison(baselineReport, candidateReport));

        banner(out, "4. HUMAN REVIEW: ONE REGRESSED CASE");
        out.println("A person reads the input, the trajectory, and the answer, then decides.");
        candidateReport.cases().stream()
                .filter(report -> report.evalCase().id().equals("degraded-after-deployment"))
                .findFirst()
                .ifPresent(report -> ReportPrinter.printReview(out, report));

        out.println();
        out.println("The scripted behaviors make this harness deterministic and inspectable.");
        out.println("They are not a real model evaluation. Use --live for that.");
    }

    private static void runLive(PrintStream out, String apiKey, String model) {
        List<EvalCase> cases = HelioIncidentsV1.cases();
        banner(out, "LIVE EVALUATION: " + model);
        printSet(out, cases);
        out.println();
        out.println("Same cases, same runtime, same checks. Gemini is the system under evaluation.");

        SystemVersion live = new SystemVersion(model, "live model",
                () -> new GeminiAgentModel(apiKey, model));
        EvaluationReport report = EvaluationRunner.run(HelioIncidentsV1.NAME, cases, live);
        ReportPrinter.printCases(out, report);
        ReportPrinter.printSummary(out, report);

        banner(out, "FAILING CASES, FOR HUMAN REVIEW");
        if (report.failedCases().isEmpty()) {
            out.println("None in this run.");
        }
        report.failedCases().forEach(failed -> ReportPrinter.printReview(out, failed));

        out.println();
        out.println("This is one run of a probabilistic system, not a benchmark. Pass rates vary between");
        out.println("runs and models; a real evaluation repeats the set and reads the variance.");
    }

    private static void printSet(PrintStream out, List<EvalCase> cases) {
        out.println();
        out.println("Evaluation set: " + HelioIncidentsV1.NAME + " (" + cases.size() + " cases, step budget "
                + HelioIncidentsV1.MAX_STEPS + ")");
        for (EvalCase evalCase : cases) {
            out.println(String.format("  %-26s %-17s %s", evalCase.id(), evalCase.kind(), evalCase.goal()));
        }
    }

    private static void banner(PrintStream out, String title) {
        out.println();
        out.println("--------------------------------------------------");
        out.println(title);
        out.println("--------------------------------------------------");
    }

    /** GOOGLE_API_KEY first, legacy GEMINI_API_KEY second — the SDK's documented precedence. */
    static Optional<String> apiKey(Map<String, String> env) {
        return firstNonBlank(env.get("GOOGLE_API_KEY"), env.get("GEMINI_API_KEY"));
    }

    /** Model name from GEMINI_MODEL, or the lab default. */
    static String model(Map<String, String> env) {
        return firstNonBlank(env.get("GEMINI_MODEL")).orElse(DEFAULT_MODEL);
    }

    private static Optional<String> firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
