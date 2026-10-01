package dev.agentic.handbook.labs.observability;

import java.io.PrintStream;
import java.time.Clock;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lab 10: observe the Lab 09 agent. One run that fails in a way the result
 * cannot explain, the same run as a trace, and what a trace lets you see.
 *
 * <p>Default mode is fully deterministic: scripted models drive the real
 * bounded runtime, so no key, no network, and no telemetry backend is needed.
 * {@code --live} runs the notifications goal once with Gemini and produces the
 * same kind of trace; only the model's behavior changes.
 */
public final class ObservabilityExample {

    /** Same default and override rules as the earlier labs. */
    static final String DEFAULT_MODEL = "gemini-3.8-flash";

    /** The Lab 07 goal, as in Lab 09's evaluation set. */
    static final String GOAL = "Investigate the notifications service. If it is degraded, check whether there "
            + "was a recent deployment and summarize what is known. Do not claim a root cause without evidence.";

    /** The step budget Lab 09 uses for every case. */
    static final int MAX_STEPS = 4;

    private ObservabilityExample() {
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
            runLive(out, apiKey.get(), model(env), Clock.systemUTC());
        } else {
            runScripted(out, Clock.systemUTC());
        }
        return 0;
    }

    static void runScripted(PrintStream out, InstantSource clock) {
        List<String> logLines = new ArrayList<>();
        Telemetry telemetry = new Telemetry(clock, logLines::add);

        banner(out, "OBSERVABILITY: ONE REQUEST, ONE TRACE");
        out.println("Goal: " + GOAL);
        out.println("Step budget: " + MAX_STEPS + " model decisions");

        banner(out, "1. A RUN THAT FAILED, SEEN FROM THE OUTSIDE");
        ObservedRun stuck = new AgentRuntime(ScriptedModel.repeatingDeploymentLookup(), MAX_STEPS, telemetry)
                .run(GOAL);
        out.println("What an operator would see in a plain log:");
        out.println("  WARN agent run did not complete (" + stuck.result().stopReason() + ")");
        out.println();
        out.println("What a Lab 09 style evaluation says about it:");
        printChecks(out, ScenarioChecks.evaluate(stuck.result(), MAX_STEPS));
        out.println();
        out.println("We know that it failed. We do not know what the run did, or why.");

        banner(out, "2. THE SAME RUN, OBSERVED");
        printLogs(out, logLines);
        out.println();
        TraceReporter.printTree(out, stuck.trace());
        out.println();
        TraceReporter.printSummary(out, stuck.trace());

        banner(out, "3. DIAGNOSIS: READING THE TRACE");
        diagnose(out, stuck.trace());

        banner(out, "4. A NORMAL RUN, FOR COMPARISON");
        ObservedRun normal = new AgentRuntime(ScriptedModel.baseline(), MAX_STEPS, telemetry).run(GOAL);
        printLogs(out, logLines);
        out.println();
        TraceReporter.printTree(out, normal.trace());
        out.println();
        TraceReporter.printSummary(out, normal.trace());

        banner(out, "5. A TOOL THAT FAILS: ERROR, AND WHAT MAY BE RECORDED");
        out.println("The deployment API (simulated) fails, and its error message echoes request details.");
        out.println("Secret-like values are redacted before they reach a span or a log line.");
        out.println();
        ObservedRun failing = new AgentRuntime(ScriptedModel.baseline(), MAX_STEPS, telemetry,
                failingDeploymentApi()).run(GOAL);
        printLogs(out, logLines);
        out.println();
        TraceReporter.printTree(out, failing.trace());
        out.println();
        TraceReporter.printSummary(out, failing.trace());

        banner(out, "6. METRICS: WHAT THE SAME THREE RUNS LOOK LIKE IN AGGREGATE");
        TraceReporter.printMetrics(out, telemetry.metrics());
        out.println();
        out.println("Logs tell you individual events. Metrics tell you aggregate behavior.");
        out.println("Traces tell you how the events belong to one run. The counters above say that runs");
        out.println("failed; only the traces say which one repeated a lookup, and which one hit a failing tool.");
        out.println();
        out.println("The scripted models are test doubles, not AIs. Use --live to observe a real model.");
    }

    private static void runLive(PrintStream out, String apiKey, String model, InstantSource clock) {
        List<String> logLines = new ArrayList<>();
        Telemetry telemetry = new Telemetry(clock, logLines::add);

        banner(out, "LIVE OBSERVED RUN: " + model);
        out.println("Goal: " + GOAL);
        out.println("Same runtime, same trace model. Gemini is the model; only its behavior differs.");
        out.println();

        ObservedRun run = new AgentRuntime(new GeminiAgentModel(apiKey, model), MAX_STEPS, telemetry).run(GOAL);
        printLogs(out, logLines);
        out.println();
        TraceReporter.printTree(out, run.trace());
        out.println();
        TraceReporter.printSummary(out, run.trace());
        out.println();
        TraceReporter.printMetrics(out, telemetry.metrics());
        out.println();
        out.println("This is one run of a probabilistic system. Token counts appear only if the provider reported them.");
    }

    /** Facts a developer can read off the trace of the repeating run, none of which the result contains. */
    private static void diagnose(PrintStream out, Trace trace) {
        out.println("Decision authority, as recorded (model proposes, application decides, tool executes):");
        long proposed = trace.ofType(SpanType.AGENT_DECISION).size();
        long allowed = trace.ofType(SpanType.TOOL_VALIDATION).stream()
                .filter(span -> span.status() == SpanStatus.OK).count();
        long executed = trace.ofType(SpanType.TOOL_EXECUTION).size();
        out.println("  MODEL proposed " + proposed + " times; APPLICATION allowed " + allowed
                + "; TOOL executed " + executed + ".");
        out.println("  Every proposal was valid, so the application let each one run: nothing was rejected.");
        out.println();

        Map<String, Integer> repeated = trace.repeatedToolCalls();
        if (repeated.isEmpty()) {
            out.println("No tool call was repeated.");
            return;
        }
        repeated.forEach((call, count) -> {
            Set<String> results = trace.ofType(SpanType.TOOL_CALL).stream()
                    .filter(span -> span.name().equals(call))
                    .flatMap(span -> trace.children(span).stream())
                    .filter(child -> child.type() == SpanType.TOOL_EXECUTION)
                    .map(child -> child.attribute("result").orElse(""))
                    .collect(Collectors.toSet());
            out.println("Repeated: " + call + " was requested " + count + " times"
                    + (results.size() == 1 ? ", and returned the same result every time." : "."));
        });
        out.println("Found: the model kept asking the same question and never accepted the answer.");
        out.println("The step budget, an application control, ended the run: stop reason "
                + trace.stopReason().orElse("?") + ".");
        out.println();
        out.println("The diagnosis is also a candidate evaluation case for Lab 09: 'a deployment lookup is");
        out.println("never requested twice with the same arguments'. Observability finds the failure;");
        out.println("evaluation keeps it from coming back.");
    }

    /** A deployment dependency that fails and echoes request details, the way a real HTTP error can. */
    private static ToolBackend failingDeploymentApi() {
        ToolBackend helio = ToolBackend.helio();
        return (tool, serviceName) -> {
            if (tool.equals("getRecentDeployment")) {
                throw new IllegalStateException("Deployment API returned HTTP 503 for GET "
                        + "/v1/deployments?service=" + serviceName + "&api_key=demo-not-a-real-key; "
                        + "request header Authorization: Bearer demo-token-1234567890");
            }
            return helio.execute(tool, serviceName);
        };
    }

    private static void printChecks(PrintStream out, List<ScenarioChecks.Check> checks) {
        for (ScenarioChecks.Check check : checks) {
            out.println(String.format("  [%s] %-48s %s", check.passed() ? "PASS" : "FAIL", check.name(),
                    check.detail()));
        }
    }

    /** Prints and clears the structured log lines collected so far. */
    private static void printLogs(PrintStream out, List<String> logLines) {
        out.println("LOG (structured, one line per event):");
        logLines.forEach(line -> out.println("  " + line));
        logLines.clear();
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
