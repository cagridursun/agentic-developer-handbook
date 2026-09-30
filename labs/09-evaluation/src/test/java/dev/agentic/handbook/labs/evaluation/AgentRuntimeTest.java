package dev.agentic.handbook.labs.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The system under evaluation is the Lab 07 runtime, so its contract is tested
 * here — deterministically, as a <em>test</em>: does this component obey its
 * contract? Whether the agent behaves well across cases is the evaluation's
 * job, not these tests'.
 */
class AgentRuntimeTest {

    private static AgentModel repeating(ModelDecision decision) {
        return new AgentModel() {
            @Override
            public ModelDecision start(String goal) {
                return decision;
            }

            @Override
            public ModelDecision observe(ToolExchange exchange) {
                return decision;
            }
        };
    }

    private static final ModelDecision.ToolRequest STATUS =
            new ModelDecision.ToolRequest("getServiceStatus", Map.of("serviceName", "notifications"));

    @Test
    void theStepBudgetEndsAModelThatNeverStops() {
        AgentRunResult result = new AgentRuntime(repeating(STATUS), 4).run("goal");
        assertEquals(StopReason.MAX_STEPS, result.stopReason());
        assertEquals(4, result.modelSteps());
        assertEquals(4, result.exchanges().size());
    }

    @Test
    void anUnlistedToolIsRejectedAndRecordedInTheTrace() {
        ModelDecision.ToolRequest rollback =
                new ModelDecision.ToolRequest("rollbackDeployment", Map.of("serviceName", "notifications"));
        AgentRunResult result = new AgentRuntime(repeating(rollback), 4).run("goal");
        assertEquals(StopReason.REJECTED_TOOL_CALL, result.stopReason());
        assertEquals(rollback, result.failure().orElseThrow().request());
        assertEquals(java.util.List.of(rollback), result.requests());
    }

    @Test
    void anUnknownServiceIsRejectedWithoutManufacturingStatusData() {
        AgentRunResult result = new AgentRuntime(repeating(
                new ModelDecision.ToolRequest("getServiceStatus", Map.of("serviceName", "payments"))), 4).run("goal");
        assertEquals(StopReason.REJECTED_TOOL_CALL, result.stopReason());
        assertTrue(result.exchanges().isEmpty());
        assertTrue(result.failure().orElseThrow().detail().contains("Unknown service 'payments'"));
    }

    @Test
    void malformedArgumentsAreRejected() {
        for (Map<String, Object> arguments : java.util.List.<Map<String, Object>>of(
                Map.of(), Map.of("serviceName", " "), Map.of("serviceName", 7),
                Map.of("serviceName", "billing", "extra", "x"))) {
            AgentRunResult result = new AgentRuntime(repeating(
                    new ModelDecision.ToolRequest("getServiceStatus", arguments)), 4).run("goal");
            assertEquals(StopReason.REJECTED_TOOL_CALL, result.stopReason(), arguments.toString());
        }
    }

    @Test
    void aFinalAnswerStopsTheRunAndCountsAsADecision() {
        AgentRunResult result = new AgentRuntime(repeating(new ModelDecision.FinalAnswer("done")), 4).run("goal");
        assertEquals(StopReason.FINAL_ANSWER, result.stopReason());
        assertEquals(1, result.modelSteps());
        assertEquals("done", result.answer().orElseThrow());
    }

    @Test
    void stepBudgetMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new AgentRuntime(repeating(STATUS), 0));
    }

    // --- Live adapter translation, exercised without any network ---

    @Test
    void geminiTextBecomesAFinalAnswerAndOneFunctionCallBecomesAToolRequest() {
        assertEquals("ok", ((ModelDecision.FinalAnswer) GeminiAgentModel.toDecision(
                responseWithParts(Part.fromText("ok")))).text());
        ModelDecision.ToolRequest request = (ModelDecision.ToolRequest) GeminiAgentModel.toDecision(
                responseWithParts(Part.fromFunctionCall("getServiceStatus", Map.of("serviceName", "billing"))));
        assertEquals("getServiceStatus", request.name());
        assertEquals("billing", request.arguments().get("serviceName"));
    }

    @Test
    void geminiParallelCallsAndEmptyResponsesAreRejectedInsteadOfGuessed() {
        assertThrows(IllegalStateException.class, () -> GeminiAgentModel.toDecision(responseWithParts(
                Part.fromFunctionCall("getServiceStatus", Map.of("serviceName", "a")),
                Part.fromFunctionCall("getRecentDeployment", Map.of("serviceName", "a")))));
        assertThrows(IllegalStateException.class,
                () -> GeminiAgentModel.toDecision(GenerateContentResponse.builder().build()));
    }

    // --- Configuration and the default run ---

    @Test
    void apiKeyAndModelResolutionMatchTheEarlierLabs() {
        assertEquals("primary", EvaluationExample.apiKey(Map.of(
                "GOOGLE_API_KEY", "primary", "GEMINI_API_KEY", "legacy")).orElseThrow());
        assertTrue(EvaluationExample.apiKey(Map.of()).isEmpty());
        assertEquals(EvaluationExample.DEFAULT_MODEL, EvaluationExample.model(Map.of()));
        assertEquals("custom", EvaluationExample.model(Map.of("GEMINI_MODEL", "custom")));
    }

    @Test
    void theDefaultRunNeedsNoKeyAndReportsTheRegression() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = EvaluationExample.run(new String[0], Map.of(),
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        String text = out.toString(StandardCharsets.UTF_8);
        assertEquals(0, exit);
        assertEquals("", err.toString(StandardCharsets.UTF_8));
        assertTrue(text.contains("Cases acceptable:  6 / 6"));
        assertTrue(text.contains("REGRESSIONS FOUND: 4 check(s) in 4 case(s)"));
    }

    @Test
    void liveModeWithoutAKeyStopsBeforeAnyModelCall() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = EvaluationExample.run(new String[] {"--live"}, Map.of(),
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        assertEquals(1, exit);
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("GOOGLE_API_KEY"));
    }

    private static GenerateContentResponse responseWithParts(Part... parts) {
        return GenerateContentResponse.builder()
                .candidates(Candidate.builder()
                        .content(Content.builder().role("model").parts(parts).build())
                        .build())
                .build();
    }
}
