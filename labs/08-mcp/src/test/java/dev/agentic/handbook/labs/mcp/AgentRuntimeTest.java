package dev.agentic.handbook.labs.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The bounded runtime with one local tool and one MCP-backed tool. Scripted
 * models drive it; the MCP-backed tool is the real server in a real process,
 * shared by the tests in this class. No key, no network.
 */
class AgentRuntimeTest {

    private static final ModelDecision.ToolRequest STATUS_NOTIFICATIONS =
            new ModelDecision.ToolRequest("getServiceStatus", Map.of("serviceName", "notifications"));
    private static final ModelDecision.ToolRequest DEPLOY_NOTIFICATIONS =
            new ModelDecision.ToolRequest("getRecentDeployment", Map.of("serviceName", "notifications"));

    private static DeploymentMcpClient deployments;

    @BeforeAll
    static void connect() {
        deployments = DeploymentMcpClient.launch();
    }

    @AfterAll
    static void disconnect() {
        deployments.close();
    }

    private static AgentRunResult run(AgentModel model, int maxSteps) {
        return new AgentRuntime(model, maxSteps, deployments).run("goal");
    }

    // --- Routing: same agent, same loop, one capability now remote ---

    @Test
    void runtimeRoutesLocalAndMcpToolsAndStopsOnFinalAnswer() {
        ScriptedAgentModel model = ScriptedAgentModel.of(List.of(
                STATUS_NOTIFICATIONS,
                DEPLOY_NOTIFICATIONS,
                new ModelDecision.FinalAnswer("Degraded; deployment correlated; cause unproven.")));
        int before = deployments.toolCallsSent();

        AgentRunResult result = run(model, 4);

        assertEquals(StopReason.FINAL_ANSWER, result.stopReason());
        assertEquals(3, result.modelSteps());
        assertEquals(List.of(ToolExchange.Route.LOCAL, ToolExchange.Route.MCP),
                result.exchanges().stream().map(ToolExchange::route).toList());
        assertEquals("DEGRADED", result.exchanges().get(0).result().get("status"));
        assertEquals("notifications-2.4.1", result.exchanges().get(1).result().get("version"));
        assertEquals(1, deployments.toolCallsSent() - before);
        assertEquals(3, model.decisionsServed());
    }

    @Test
    void modelObservesTheRemoteResultLikeAnyOtherObservation() {
        List<ToolExchange> observed = new ArrayList<>();
        AgentModel recording = new AgentModel() {
            @Override
            public ModelDecision start(String goal) {
                return DEPLOY_NOTIFICATIONS;
            }

            @Override
            public ModelDecision observe(ToolExchange exchange) {
                observed.add(exchange);
                return new ModelDecision.FinalAnswer("done");
            }
        };

        run(recording, 4);

        assertEquals(1, observed.size());
        // A plain map with string keys: no MCP types reach the model side.
        assertEquals("2026-09-23T13:52:00Z", observed.get(0).result().get("deployedAt"));
    }

    @Test
    void immediateFinalAnswerSendsNoMcpRequest() {
        int before = deployments.toolCallsSent();
        AgentRunResult result = run(ScriptedAgentModel.of(List.of(new ModelDecision.FinalAnswer("Nothing to do."))), 4);
        assertEquals(StopReason.FINAL_ANSWER, result.stopReason());
        assertTrue(result.exchanges().isEmpty());
        assertEquals(0, deployments.toolCallsSent() - before);
    }

    // --- The bound holds across the process boundary ---

    @Test
    void stepBudgetStopsAModelThatKeepsCallingTheRemoteTool() {
        ScriptedAgentModel endless = ScriptedAgentModel.repeating(DEPLOY_NOTIFICATIONS);
        int before = deployments.toolCallsSent();

        AgentRunResult result = run(endless, 3);

        assertEquals(StopReason.MAX_STEPS, result.stopReason());
        assertEquals(3, result.modelSteps());
        assertEquals(3, endless.decisionsServed());
        // Exactly as many MCP requests as the budget allowed, and not one more.
        assertEquals(3, deployments.toolCallsSent() - before);
    }

    // --- Rejections happen before anything crosses the boundary ---

    @Test
    void unapprovedToolIsRejectedBeforeAnyMcpRequest() {
        int before = deployments.toolCallsSent();
        AgentRunResult result = run(ScriptedAgentModel.of(List.of(
                new ModelDecision.ToolRequest("rollbackDeployment", Map.of("serviceName", "notifications")))), 4);

        assertEquals(StopReason.REJECTED_TOOL_CALL, result.stopReason());
        AgentRunResult.Failure failure = result.failure().orElseThrow();
        assertEquals("rollbackDeployment", failure.request().name());
        assertTrue(failure.detail().contains("not on this application's allowlist"));
        assertEquals(0, deployments.toolCallsSent() - before);
    }

    @Test
    void invalidArgumentsForTheRemoteToolAreRejectedLocally() {
        int before = deployments.toolCallsSent();
        for (Map<String, Object> invalid : List.<Map<String, Object>>of(
                Map.of(),
                Map.of("serviceName", 42),
                Map.of("serviceName", "  "),
                Map.of("serviceName", "billing", "force", true))) {
            AgentRunResult result = run(ScriptedAgentModel.of(List.of(
                    new ModelDecision.ToolRequest("getRecentDeployment", invalid))), 4);
            assertEquals(StopReason.REJECTED_TOOL_CALL, result.stopReason(), invalid.toString());
        }
        // The application boundary stopped all of them; the server never saw one.
        assertEquals(0, deployments.toolCallsSent() - before);
    }

    @Test
    void unknownServiceForTheLocalToolIsRejected() {
        AgentRunResult result = run(ScriptedAgentModel.of(List.of(
                new ModelDecision.ToolRequest("getServiceStatus", Map.of("serviceName", "payments")))), 4);
        assertEquals(StopReason.REJECTED_TOOL_CALL, result.stopReason());
        assertTrue(result.failure().orElseThrow().detail().contains("payments"));
    }

    // --- A remote failure is a failure ---

    @Test
    void remoteToolErrorStopsTheRunWithoutRetryOrInvention() {
        ScriptedAgentModel model = ScriptedAgentModel.of(List.of(
                new ModelDecision.ToolRequest("getRecentDeployment", Map.of("serviceName", "payments")),
                new ModelDecision.FinalAnswer("This must never be reached.")));
        int before = deployments.toolCallsSent();

        AgentRunResult result = run(model, 4);

        assertEquals(StopReason.TOOL_FAILED, result.stopReason());
        assertTrue(result.answer().isEmpty());
        assertTrue(result.exchanges().isEmpty());
        assertTrue(result.failure().orElseThrow().detail().startsWith("MCP tool error"));
        assertEquals(1, deployments.toolCallsSent() - before);
        assertEquals(1, model.decisionsServed());
    }

    @Test
    void stepBudgetMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentRuntime(ScriptedAgentModel.of(List.of()), 0, deployments));
    }

    // --- The model is told about allowed tools only, and not where they run ---

    @Test
    void liveModelIsDeclaredExactlyTheToolsTheApplicationAllows() {
        List<String> declared = GeminiAgentModel.TOOLS.functionDeclarations().orElseThrow().stream()
                .map(FunctionDeclaration::name)
                .map(name -> name.orElseThrow())
                .toList();
        assertEquals(List.of("getServiceStatus", "getRecentDeployment"), declared);
    }

    @Test
    void providerResponsesTranslateToDecisions() {
        assertEquals("Done.", ((ModelDecision.FinalAnswer) GeminiAgentModel.toDecision(
                response(Part.fromText("Done.")))).text());
        ModelDecision.ToolRequest request = (ModelDecision.ToolRequest) GeminiAgentModel.toDecision(
                response(Part.fromFunctionCall("getRecentDeployment", Map.of("serviceName", "notifications"))));
        assertEquals("getRecentDeployment", request.name());
        assertThrows(IllegalStateException.class, () -> GeminiAgentModel.toDecision(response(
                Part.fromFunctionCall("getServiceStatus", Map.of("serviceName", "notifications")),
                Part.fromFunctionCall("getRecentDeployment", Map.of("serviceName", "notifications")))));
    }

    @Test
    void apiKeyAndModelResolution() {
        assertEquals("primary", McpExample.apiKey(Map.of(
                "GOOGLE_API_KEY", "primary",
                "GEMINI_API_KEY", "legacy")).orElseThrow());
        assertTrue(McpExample.apiKey(Map.of()).isEmpty());
        assertEquals(McpExample.DEFAULT_MODEL, McpExample.model(Map.of()));
    }

    // --- The process boundary is also a code boundary ---

    @Test
    void applicationAndServerDoNotImportEachOther() throws IOException {
        Path root = Path.of("src", "main", "java", "dev", "agentic", "handbook", "labs", "mcp");
        for (Path file : javaFiles(root)) {
            String source = Files.readString(file);
            boolean serverSide = file.startsWith(root.resolve("server"));
            if (serverSide) {
                assertFalse(source.matches("(?s).*import dev\\.agentic\\.handbook\\.labs\\.mcp\\.[A-Z].*"),
                        file + " imports the agent application");
            } else {
                assertFalse(source.contains("import dev.agentic.handbook.labs.mcp.server."),
                        file + " imports the MCP server");
            }
        }
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static GenerateContentResponse response(Part... parts) {
        return GenerateContentResponse.builder()
                .candidates(Candidate.builder()
                        .content(Content.builder().role("model").parts(parts).build())
                        .build())
                .build();
    }
}
