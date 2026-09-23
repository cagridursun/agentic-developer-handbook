package dev.agentic.handbook.capstones.agenticsystem.reference;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Capstone 01 reference: one defensible composition, not the answer key.
 *
 * <p>Used: model, tools, knowledge, skill, bounded agent runtime.
 * Deliberately absent: structured output (a human reads the handoff), memory
 * (one bounded run, no cross-interaction state), MCP (every capability is
 * local Java). The full reasoning is in DECISIONS.md.
 */
public final class CapstoneExample {

    /** Same default and override rules as the canonical labs. */
    static final String DEFAULT_MODEL = "gemini-3.8-flash";

    /** The step budget: maximum model decisions per run. */
    static final int MAX_STEPS = 4;

    static final String GOAL = "The notifications service is slow after a release. "
            + "Investigate what is currently known and prepare a concise engineering handoff. "
            + "Do not claim a root cause without evidence.";

    private CapstoneExample() {
    }

    public static void main(String[] args) {
        boolean live = args.length > 0 && args[0].equals("--live");

        // 1. KNOWLEDGE: application-controlled retrieval, before the loop.
        List<Knowledge.Document> corpus = Knowledge.load();
        List<Knowledge.Retrieved> runbooks = Knowledge.retrieve(corpus, GOAL, Knowledge.TOP_K);
        System.out.println("Retrieved runbooks:");
        runbooks.forEach(doc -> System.out.println("- " + doc.source() + " (score: " + doc.score() + ")"));

        // 2. SKILL: one reviewable procedure, loaded, not copied into code.
        SkillLoader.Skill skill = SkillLoader.loadIncidentHandoff();
        System.out.println("Loaded skill: " + skill.name());

        // 3. INITIAL CONTEXT: goal + runbook context + procedure + what tools exist.
        String initialContext = buildInitialContext(GOAL, runbooks, skill);

        // 4. BOUNDED RUNTIME: the model decides the next step; the application
        //    validates, executes, and stops.
        Agent.Model model = live ? liveModel() : scriptedModel();
        Agent.RunResult result = new Agent(model, MAX_STEPS).run(initialContext);

        printTrace(result);
    }

    /**
     * The composition step: everything the model needs for its first decision,
     * clearly separated. Runbook text and skill text are structure-tagged
     * untrusted context, not a security boundary.
     */
    static String buildInitialContext(String goal, List<Knowledge.Retrieved> runbooks,
            SkillLoader.Skill skill) {
        StringBuilder context = new StringBuilder();
        context.append("You are investigating an incident on the fictional Helio platform.\n\n")
                .append("Goal:\n").append(goal).append("\n\n")
                .append("You may request the read-only tools getServiceStatus and ")
                .append("getRecentDeployment, one call at a time, or answer when you have ")
                .append("enough evidence.\n\n")
                .append("Relevant runbook context:\n");
        for (Knowledge.Retrieved doc : runbooks) {
            context.append("\n<document source=\"").append(doc.source()).append("\">\n")
                    .append(doc.document().content().strip())
                    .append("\n</document>\n");
        }
        context.append("\nFollow this procedure for the final handoff:\n\n")
                .append("<skill name=\"").append(skill.name()).append("\">\n")
                .append(skill.instructions())
                .append("\n</skill>\n\n")
                .append("Base observed facts only on tool results and the goal. ")
                .append("Never state an unproven root cause as fact.\n");
        return context.toString();
    }

    /** The deterministic default: a plausible investigation, replayed. */
    private static Agent.Model scriptedModel() {
        return ScriptedModel.of(List.of(
                new Agent.ToolRequest("getServiceStatus", Map.of("serviceName", "notifications")),
                new Agent.ToolRequest("getRecentDeployment", Map.of("serviceName", "notifications")),
                new Agent.FinalAnswer("""
                        **Summary**
                        The notifications service is degraded following a recent deployment; \
                        the timing correlates but the root cause is not established.

                        **Impact**
                        Notification delivery is slow: p99 latency at 240 seconds with a 9 \
                        percent retry rate since 14:05 UTC.

                        **Observed facts**
                        - getServiceStatus: notifications is DEGRADED (p99 240s, retries 9%, since 14:05 UTC)
                        - getRecentDeployment: notifications-2.4.1 deployed at 13:52 UTC (retry policy adjustment)

                        **Hypotheses** (unproven)
                        - The 13:52 UTC retry-policy deployment may be related to the 14:05 UTC degradation.

                        **Unknowns**
                        - The mechanism linking the deployment to the latency is not established.
                        - Behavior of the previous version under current load.

                        **Next actions**
                        - Compare delivery behavior between notifications-2.4.1 and the previous version (owner: not established)
                        - Decide whether rollback criteria from the deployment runbook are met (owner: on-call engineer)""")));
    }

    private static Agent.Model liveModel() {
        Optional<String> apiKey = apiKey(System.getenv());
        if (apiKey.isEmpty()) {
            System.err.println("No API key found for --live mode.");
            System.err.println("Set the GOOGLE_API_KEY environment variable "
                    + "(create a key at https://aistudio.google.com/apikey).");
            System.exit(1);
        }
        String model = model(System.getenv());
        System.out.println("Live mode, model: " + model + " (budget: " + MAX_STEPS + " steps)");
        return new GeminiModel(apiKey.get(), model);
    }

    private static void printTrace(Agent.RunResult result) {
        int step = 0;
        for (Agent.ToolExchange exchange : result.exchanges()) {
            step++;
            System.out.println();
            System.out.println("Step " + step + " / " + MAX_STEPS);
            System.out.println("Model decision: TOOL " + exchange.request().name()
                    + " " + exchange.request().arguments());
            System.out.println("Tool result:    " + exchange.result());
        }
        System.out.println();
        if (result.stopReason() == Agent.StopReason.FINAL_ANSWER) {
            System.out.println("Step " + result.modelSteps() + " / " + MAX_STEPS);
            System.out.println("Model decision: FINAL ANSWER");
            System.out.println();
            System.out.println(result.answer().orElse(""));
        }
        result.rejectionDetail().ifPresent(detail -> System.out.println("Rejected: " + detail));
        System.out.println();
        System.out.println("Stop reason: " + result.stopReason());
    }

    /** GOOGLE_API_KEY first, legacy GEMINI_API_KEY second — the SDK's documented precedence. */
    static Optional<String> apiKey(Map<String, String> env) {
        return firstNonBlank(env.get("GOOGLE_API_KEY"), env.get("GEMINI_API_KEY"));
    }

    /** Model name from GEMINI_MODEL, or the default. */
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
