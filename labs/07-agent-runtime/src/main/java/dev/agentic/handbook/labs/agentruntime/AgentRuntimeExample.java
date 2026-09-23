package dev.agentic.handbook.labs.agentruntime;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lab 07: a bounded agent runtime.
 *
 * <p>Default mode is fully deterministic: a scripted model drives the real
 * runtime, so the loop, the validation, and the stopping rules are observable
 * with no key and no network. {@code --live} runs the same runtime with
 * Gemini deciding the steps.
 *
 * <p>The model suggests the next step. The runtime owns execution and
 * stopping. This is an agent.
 */
public final class AgentRuntimeExample {

    /** Same default and override rules as the earlier labs. */
    static final String DEFAULT_MODEL = "gemini-3.8-flash";

    /** The step budget: the maximum number of model decisions per run. */
    static final int MAX_STEPS = 4;

    static final String GOAL = "Investigate the notifications service. If it is degraded, "
            + "check whether there was a recent deployment and summarize what is known. "
            + "Do not claim a root cause without evidence.";

    private AgentRuntimeExample() {
    }

    public static void main(String[] args) {
        boolean live = args.length > 0 && args[0].equals("--live");
        if (live) {
            runLive();
        } else {
            runScripted();
        }
    }

    private static void runScripted() {
        banner("1. THE BOUNDED RUNTIME, DRIVEN BY A SCRIPTED MODEL");
        System.out.println("(A deterministic test double replays plausible decisions so the");
        System.out.println("runtime itself is observable. Use --live for real Gemini decisions.)");

        AgentModel scripted = ScriptedAgentModel.of(List.of(
                new ModelDecision.ToolRequest("getServiceStatus", Map.of("serviceName", "notifications")),
                new ModelDecision.ToolRequest("getRecentDeployment", Map.of("serviceName", "notifications")),
                new ModelDecision.FinalAnswer(
                        "The notifications service is DEGRADED with elevated delivery latency "
                                + "and retries since 14:05 UTC. A deployment (notifications-2.4.1, "
                                + "a retry policy adjustment) finished at 13:52 UTC, shortly before "
                                + "the degradation began, so the timing may be relevant. The root "
                                + "cause is not proven; comparing behavior against the previous "
                                + "version is the next step.")));

        AgentRunResult result = new AgentRuntime(scripted, MAX_STEPS).run(GOAL);
        printTrace(result);

        banner("2. THE MODEL THAT NEVER STOPS");
        System.out.println("This scripted model requests getServiceStatus(notifications) forever.");
        System.out.println("A while(true) loop would never return. The runtime's step budget ends it:");
        System.out.println();

        ScriptedAgentModel endless = ScriptedAgentModel.repeating(
                new ModelDecision.ToolRequest("getServiceStatus", Map.of("serviceName", "notifications")));
        AgentRunResult bounded = new AgentRuntime(endless, MAX_STEPS).run(GOAL);
        System.out.println("Model decisions made: " + bounded.modelSteps() + " (budget: " + MAX_STEPS + ")");
        System.out.println("Tool calls executed:  " + bounded.exchanges().size());
        System.out.println("Stop reason:          " + bounded.stopReason());
        System.out.println();
        System.out.println("A loop is not enough. An agent runtime needs stopping rules.");
    }

    private static void runLive() {
        Optional<String> apiKey = apiKey(System.getenv());
        if (apiKey.isEmpty()) {
            System.err.println("No API key found for --live mode.");
            System.err.println("Set the GOOGLE_API_KEY environment variable "
                    + "(create a key at https://aistudio.google.com/apikey).");
            System.exit(1);
        }
        String model = model(System.getenv());

        banner("LIVE RUN: GEMINI DECIDES, THE RUNTIME EXECUTES (budget: " + MAX_STEPS + " steps)");
        System.out.println("Model: " + model);

        AgentModel gemini = new GeminiAgentModel(apiKey.get(), model);
        AgentRunResult result = new AgentRuntime(gemini, MAX_STEPS).run(GOAL);
        printTrace(result);
    }

    private static void printTrace(AgentRunResult result) {
        System.out.println();
        System.out.println("Goal:");
        System.out.println(GOAL);

        int step = 0;
        for (ToolExchange exchange : result.exchanges()) {
            step++;
            System.out.println();
            System.out.println("Step " + step + " / " + MAX_STEPS);
            System.out.println("Model decision: TOOL " + exchange.request().name()
                    + " " + exchange.request().arguments());
            System.out.println("Tool result:    " + exchange.result());
        }

        System.out.println();
        if (result.stopReason() == StopReason.FINAL_ANSWER) {
            System.out.println("Step " + result.modelSteps() + " / " + MAX_STEPS);
            System.out.println("Model decision: FINAL ANSWER");
            System.out.println();
            System.out.println(result.answer().orElse(""));
        }
        result.rejectionDetail().ifPresent(detail ->
                System.out.println("Rejected: " + detail));
        System.out.println();
        System.out.println("Stop reason: " + result.stopReason());
    }

    private static void banner(String title) {
        System.out.println();
        System.out.println("--------------------------------------------------");
        System.out.println(title);
        System.out.println("--------------------------------------------------");
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
