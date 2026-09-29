package dev.agentic.handbook.labs.mcp;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lab 08: the Lab 07 agent, with one capability owned outside the process.
 *
 * <p>Default mode is deterministic: a scripted model drives the real runtime,
 * and the runtime reaches a real MCP server — a separate JVM process started
 * by the official MCP Java SDK over STDIO. No key, no network, no hosted
 * server. {@code --live} runs the same runtime and the same MCP connection
 * with Gemini proposing the steps.
 *
 * <p>MCP does not create the agent. It standardizes how the application
 * reaches a capability it does not own.
 */
public final class McpExample {

    /** Same default and override rules as the earlier labs. */
    static final String DEFAULT_MODEL = "gemini-3.8-flash";

    /** The step budget: the maximum number of model decisions per run. */
    static final int MAX_STEPS = 4;

    static final String GOAL = "Investigate the notifications service. If it is degraded, "
            + "check whether there was a recent deployment and summarize what is known. "
            + "Do not claim a root cause without evidence.";

    private McpExample() {
    }

    public static void main(String[] args) {
        if (McpExample.class.getClassLoader() != ClassLoader.getSystemClassLoader()) {
            // Inside exec:java the MCP server could not be started with this
            // application's classpath. Say so instead of timing out.
            throw new IllegalStateException("Run this lab with exec:exec, not exec:java "
                    + "(./mvnw -pl labs/08-mcp compile exec:exec). It launches the MCP server "
                    + "as a separate process with this JVM's classpath.");
        }
        boolean live = args.length > 0 && args[0].equals("--live");
        Optional<String> apiKey = apiKey(System.getenv());
        if (live && apiKey.isEmpty()) {
            System.err.println("No API key found for --live mode.");
            System.err.println("Set the GOOGLE_API_KEY environment variable "
                    + "(create a key at https://aistudio.google.com/apikey).");
            System.exit(1);
        }

        banner("1. CONNECT: THE CAPABILITY NOW LIVES IN ANOTHER PROCESS");
        System.out.println("Launching the deployment team's MCP server as a child process (STDIO)...");
        try (DeploymentMcpClient deployments = DeploymentMcpClient.launch()) {
            printDiscovery(deployments.discovery());
            if (live) {
                runLive(deployments, apiKey.get(), model(System.getenv()));
            } else {
                runScripted(deployments);
            }
            System.out.println();
            System.out.println("Closing the MCP client. The STDIO transport stops the server process.");
        }
    }

    private static void printDiscovery(DeploymentMcpClient.Discovery discovery) {
        System.out.println();
        System.out.println("MCP server:              " + discovery.serverName() + " " + discovery.serverVersion());
        System.out.println("Protocol version:        " + discovery.protocolVersion()
                + " (negotiated by the initialize handshake)");
        System.out.println("Announced (tools/list):  " + discovery.announced());
        System.out.println("Application allowlist:   " + DeploymentMcpClient.APPROVED_TOOLS);
        System.out.println("Exposed to the runtime:  " + discovery.approved());
        System.out.println("Announced but ignored:   "
                + (discovery.ignored().isEmpty() ? "(none)" : discovery.ignored()));
        System.out.println();
        System.out.println("Discovery tells the application what the server offers.");
        System.out.println("The application's allowlist decides what this runtime may use.");
    }

    private static void runScripted(DeploymentMcpClient deployments) {
        banner("2. THE SAME BOUNDED RUNTIME, ONE CAPABILITY NOW REMOTE");
        System.out.println("(A deterministic test double replays plausible decisions so the");
        System.out.println("boundary is observable. Use --live for real Gemini decisions.)");
        printGoal();

        AgentModel scripted = ScriptedAgentModel.of(List.of(
                new ModelDecision.ToolRequest("getServiceStatus", Map.of("serviceName", "notifications")),
                new ModelDecision.ToolRequest("getRecentDeployment", Map.of("serviceName", "notifications")),
                new ModelDecision.FinalAnswer(
                        "The notifications service is DEGRADED with elevated delivery latency "
                                + "and retries since 14:05 UTC. The deployment records report "
                                + "notifications-2.4.1 (a retry policy adjustment) finished at 13:52 UTC, "
                                + "shortly before the degradation began, so the timing may be relevant. "
                                + "The root cause is not proven; comparing behavior against the previous "
                                + "version is the next step.")));
        runAndPrint(scripted, deployments);

        banner("3. FAILURE: A TOOL THE APPLICATION NEVER APPROVED");
        System.out.println("The model proposes rollbackDeployment. A deployment server may well offer");
        System.out.println("such a tool to other clients; this application never approved it, so the");
        System.out.println("runtime refuses before any MCP request is sent.");
        runAndPrint(ScriptedAgentModel.of(List.of(
                new ModelDecision.ToolRequest("rollbackDeployment", Map.of("serviceName", "notifications")))),
                deployments);

        banner("4. FAILURE: THE REMOTE CAPABILITY REPORTS AN ERROR");
        System.out.println("The model proposes an allowed tool with a valid argument shape, for a service");
        System.out.println("only the deployment team can judge. The MCP server answers with a tool error.");
        runAndPrint(ScriptedAgentModel.of(List.of(
                new ModelDecision.ToolRequest("getRecentDeployment", Map.of("serviceName", "payments")))),
                deployments);
        System.out.println("The runtime did not retry, and it did not invent a deployment.");
    }

    private static void runLive(DeploymentMcpClient deployments, String apiKey, String model) {
        banner("2. LIVE RUN: GEMINI PROPOSES, THE RUNTIME DECIDES (budget: " + MAX_STEPS + " steps)");
        System.out.println("Model: " + model);
        printGoal();
        runAndPrint(new GeminiAgentModel(apiKey, model), deployments);
    }

    private static void runAndPrint(AgentModel model, DeploymentMcpClient deployments) {
        int mcpRequestsBefore = deployments.toolCallsSent();
        AgentRunResult result = new AgentRuntime(model, MAX_STEPS, deployments).run(GOAL);
        printTrace(result, deployments.discovery().serverName());
        System.out.println("MCP requests sent: " + (deployments.toolCallsSent() - mcpRequestsBefore));
    }

    private static void printGoal() {
        System.out.println();
        System.out.println("Goal:");
        System.out.println(GOAL);
    }

    static void printTrace(AgentRunResult result, String serverName) {
        int step = 0;
        for (ToolExchange exchange : result.exchanges()) {
            step++;
            System.out.println();
            System.out.println("Step " + step + " / " + MAX_STEPS);
            System.out.println("Model proposal:  " + describe(exchange.request()));
            System.out.println("Runtime route:   " + switch (exchange.route()) {
                case LOCAL -> "LOCAL  -> Java method in this process";
                case MCP -> "MCP    -> tools/call to " + serverName + " (separate process)";
            });
            System.out.println("Observation:     " + exchange.result());
        }

        System.out.println();
        if (result.stopReason() == StopReason.FINAL_ANSWER) {
            System.out.println("Step " + result.modelSteps() + " / " + MAX_STEPS);
            System.out.println("Model proposal:  FINAL ANSWER");
            System.out.println();
            System.out.println(result.answer().orElse(""));
            System.out.println();
        }
        result.failure().ifPresent(failure -> {
            System.out.println("Step " + result.modelSteps() + " / " + MAX_STEPS);
            System.out.println("Model proposal:  " + describe(failure.request()));
            if (result.stopReason() == StopReason.TOOL_FAILED) {
                // Only a call that crossed the MCP boundary can fail this way.
                System.out.println("Runtime route:   MCP    -> tools/call to " + serverName + " (separate process)");
                System.out.println("Tool failed:     " + failure.detail());
            } else {
                System.out.println("Rejected:        " + failure.detail());
            }
            System.out.println();
        });
        System.out.println("Stop reason:       " + result.stopReason());
    }

    private static String describe(ModelDecision.ToolRequest request) {
        return request.name() + " " + request.arguments();
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
