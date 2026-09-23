package dev.agentic.handbook.labs.agentruntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Deterministic tests for the loop, the stopping rules, and the trust
 * boundary, driven by scripted models. No network, no API key: the scripted
 * model is the proof that the runtime works; the live model is only a smoke
 * test.
 */
class AgentRuntimeTest {

    private static final ModelDecision.ToolRequest STATUS_NOTIFICATIONS =
            new ModelDecision.ToolRequest("getServiceStatus", Map.of("serviceName", "notifications"));
    private static final ModelDecision.ToolRequest DEPLOY_NOTIFICATIONS =
            new ModelDecision.ToolRequest("getRecentDeployment", Map.of("serviceName", "notifications"));

    // --- A. Normal multi-step run ---

    @Test
    void multiStepRunExecutesToolsInOrderAndStopsOnFinalAnswer() {
        ScriptedAgentModel model = ScriptedAgentModel.of(List.of(
                STATUS_NOTIFICATIONS,
                DEPLOY_NOTIFICATIONS,
                new ModelDecision.FinalAnswer("Summary: degraded, deployment correlated, cause unproven.")));

        AgentRunResult result = new AgentRuntime(model, 4).run("goal");

        assertEquals(StopReason.FINAL_ANSWER, result.stopReason());
        assertEquals(3, result.modelSteps());
        assertEquals(2, result.exchanges().size());
        assertEquals("getServiceStatus", result.exchanges().get(0).request().name());
        assertEquals("getRecentDeployment", result.exchanges().get(1).request().name());
        assertEquals("Summary: degraded, deployment correlated, cause unproven.",
                result.answer().orElseThrow());
        // The final answer stopped the run: exactly 3 decisions were requested.
        assertEquals(3, model.decisionsServed());
    }

    @Test
    void observationFromStepOneIsAvailableBeforeStepTwo() {
        List<ToolExchange> observed = new ArrayList<>();
        AgentModel recording = new AgentModel() {
            private int calls;

            @Override
            public ModelDecision start(String goal) {
                calls++;
                return STATUS_NOTIFICATIONS;
            }

            @Override
            public ModelDecision observe(ToolExchange exchange) {
                calls++;
                observed.add(exchange);
                return calls == 2 ? DEPLOY_NOTIFICATIONS : new ModelDecision.FinalAnswer("done");
            }
        };

        AgentRunResult result = new AgentRuntime(recording, 4).run("goal");

        assertEquals(StopReason.FINAL_ANSWER, result.stopReason());
        // The model saw the status observation before deciding on step 2.
        assertEquals("getServiceStatus", observed.get(0).request().name());
        assertEquals("DEGRADED", observed.get(0).result().get("status"));
        assertEquals("getRecentDeployment", observed.get(1).request().name());
    }

    // --- B. Immediate final answer ---

    @Test
    void immediateFinalAnswerExecutesNoTools() {
        ScriptedAgentModel model = ScriptedAgentModel.of(List.of(
                new ModelDecision.FinalAnswer("No investigation needed.")));

        AgentRunResult result = new AgentRuntime(model, 4).run("goal");

        assertEquals(StopReason.FINAL_ANSWER, result.stopReason());
        assertEquals(1, result.modelSteps());
        assertTrue(result.exchanges().isEmpty());
    }

    // --- C. Max steps: the endless model cannot loop forever ---

    @Test
    void stepBudgetStopsAModelThatNeverStops() {
        ScriptedAgentModel endless = ScriptedAgentModel.repeating(STATUS_NOTIFICATIONS);

        AgentRunResult result = new AgentRuntime(endless, 3).run("goal");

        assertEquals(StopReason.MAX_STEPS, result.stopReason());
        assertEquals(3, result.modelSteps());
        assertEquals(3, result.exchanges().size());
        assertTrue(result.answer().isEmpty());
        // No extra model call happened beyond the configured budget.
        assertEquals(3, endless.decisionsServed());
    }

    // --- D-G. The trust boundary ---

    @Test
    void unknownToolIsRejectedWithoutExecution() {
        ScriptedAgentModel model = ScriptedAgentModel.of(List.of(
                new ModelDecision.ToolRequest("deleteProductionDatabase", Map.of("serviceName", "billing"))));

        AgentRunResult result = new AgentRuntime(model, 4).run("goal");

        assertEquals(StopReason.REJECTED_TOOL_CALL, result.stopReason());
        assertTrue(result.exchanges().isEmpty());
        assertTrue(result.rejectionDetail().orElseThrow().contains("deleteProductionDatabase"));
    }

    @Test
    void missingArgumentIsRejected() {
        ScriptedAgentModel model = ScriptedAgentModel.of(List.of(
                new ModelDecision.ToolRequest("getServiceStatus", Map.of())));
        AgentRunResult result = new AgentRuntime(model, 4).run("goal");
        assertEquals(StopReason.REJECTED_TOOL_CALL, result.stopReason());
        assertTrue(result.exchanges().isEmpty());
    }

    @Test
    void blankArgumentIsRejected() {
        ScriptedAgentModel model = ScriptedAgentModel.of(List.of(
                new ModelDecision.ToolRequest("getServiceStatus", Map.of("serviceName", "  "))));
        assertEquals(StopReason.REJECTED_TOOL_CALL,
                new AgentRuntime(model, 4).run("goal").stopReason());
    }

    @Test
    void unexpectedExtraArgumentsAreRejected() {
        ScriptedAgentModel model = ScriptedAgentModel.of(List.of(
                new ModelDecision.ToolRequest("getServiceStatus",
                        Map.of("serviceName", "billing", "dropTables", true))));
        assertEquals(StopReason.REJECTED_TOOL_CALL,
                new AgentRuntime(model, 4).run("goal").stopReason());
    }

    @Test
    void unknownServiceIsRejectedByToolValidation() {
        ScriptedAgentModel model = ScriptedAgentModel.of(List.of(
                new ModelDecision.ToolRequest("getServiceStatus", Map.of("serviceName", "payments"))));
        AgentRunResult result = new AgentRuntime(model, 4).run("goal");
        assertEquals(StopReason.REJECTED_TOOL_CALL, result.stopReason());
        assertTrue(result.rejectionDetail().orElseThrow().contains("payments"));
    }

    // --- The tools themselves: deterministic fictional data ---

    @Test
    void serviceToolsReturnTheFictionalData() {
        assertEquals("DEGRADED", ServiceTools.getServiceStatus("notifications").get("status"));
        assertEquals("HEALTHY", ServiceTools.getServiceStatus("billing").get("status"));
        assertEquals("notifications-2.4.1",
                ServiceTools.getRecentDeployment("notifications").get("version"));
        assertEquals("none within the last 7 days",
                ServiceTools.getRecentDeployment("billing").get("recentDeployment"));
        assertThrows(IllegalArgumentException.class,
                () -> ServiceTools.getServiceStatus("payments"));
    }

    // --- H. Provider decision translation, locally constructed ---

    @Test
    void textResponseBecomesFinalAnswer() {
        GenerateContentResponse response = responseWithParts(Part.fromText("The service is degraded."));
        ModelDecision decision = GeminiAgentModel.toDecision(response);
        assertEquals("The service is degraded.",
                ((ModelDecision.FinalAnswer) decision).text());
    }

    @Test
    void singleFunctionCallBecomesToolRequest() {
        GenerateContentResponse response = responseWithParts(
                Part.fromFunctionCall("getServiceStatus", Map.of("serviceName", "notifications")));
        ModelDecision decision = GeminiAgentModel.toDecision(response);
        ModelDecision.ToolRequest request = (ModelDecision.ToolRequest) decision;
        assertEquals("getServiceStatus", request.name());
        assertEquals("notifications", request.arguments().get("serviceName"));
    }

    @Test
    void multipleFunctionCallsInOneTurnAreUnsupported() {
        GenerateContentResponse response = responseWithParts(
                Part.fromFunctionCall("getServiceStatus", Map.of("serviceName", "notifications")),
                Part.fromFunctionCall("getRecentDeployment", Map.of("serviceName", "notifications")));
        assertThrows(IllegalStateException.class, () -> GeminiAgentModel.toDecision(response));
    }

    @Test
    void emptyResponseIsRejectedInsteadOfGuessed() {
        GenerateContentResponse response = GenerateContentResponse.builder().build();
        assertThrows(IllegalStateException.class, () -> GeminiAgentModel.toDecision(response));
    }

    // --- Runtime configuration guard ---

    @Test
    void stepBudgetMustBePositive() {
        ScriptedAgentModel model = ScriptedAgentModel.of(List.of());
        assertThrows(IllegalArgumentException.class, () -> new AgentRuntime(model, 0));
    }

    // --- Configuration, same semantics as the earlier labs ---

    @Test
    void apiKeyAndModelResolution() {
        assertEquals("primary", AgentRuntimeExample.apiKey(Map.of(
                "GOOGLE_API_KEY", "primary",
                "GEMINI_API_KEY", "legacy")).orElseThrow());
        assertTrue(AgentRuntimeExample.apiKey(Map.of()).isEmpty());
        assertEquals(AgentRuntimeExample.DEFAULT_MODEL, AgentRuntimeExample.model(Map.of()));
    }

    private static GenerateContentResponse responseWithParts(Part... parts) {
        return GenerateContentResponse.builder()
                .candidates(Candidate.builder()
                        .content(Content.builder().role("model").parts(parts).build())
                        .build())
                .build();
    }
}
