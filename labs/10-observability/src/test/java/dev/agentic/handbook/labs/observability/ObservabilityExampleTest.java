package dev.agentic.handbook.labs.observability;

import static dev.agentic.handbook.labs.observability.TestSupport.GOAL;
import static dev.agentic.handbook.labs.observability.TestSupport.printed;
import static dev.agentic.handbook.labs.observability.TestSupport.repeating;
import static dev.agentic.handbook.labs.observability.TestSupport.telemetry;
import static dev.agentic.handbook.labs.observability.TestSupport.tool;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The runtime contract carried over from Lab 09, the live adapter's translation, and the default run. */
class ObservabilityExampleTest {

    // --- The Lab 09 runtime contract still holds with instrumentation around it ---

    @Test
    void theStepBudgetEndsAModelThatNeverStops() {
        AgentRunResult result = new AgentRuntime(repeating(tool("getServiceStatus", "notifications")), 4,
                telemetry()).run(GOAL).result();
        assertEquals(StopReason.MAX_STEPS, result.stopReason());
        assertEquals(4, result.modelSteps());
        assertEquals(4, result.exchanges().size());
    }

    @Test
    void anUnlistedToolAnUnknownServiceAndMalformedArgumentsAreRejected() {
        assertEquals(StopReason.REJECTED_TOOL_CALL, new AgentRuntime(
                repeating(tool("rollbackDeployment", "notifications")), 4, telemetry()).run(GOAL).result().stopReason());

        AgentRunResult unknown = new AgentRuntime(repeating(tool("getServiceStatus", "payments")), 4, telemetry())
                .run(GOAL).result();
        assertEquals(StopReason.REJECTED_TOOL_CALL, unknown.stopReason());
        assertTrue(unknown.exchanges().isEmpty());
        assertTrue(unknown.failure().orElseThrow().detail().contains("Unknown service 'payments'"));

        for (Map<String, Object> arguments : List.<Map<String, Object>>of(
                Map.of(), Map.of("serviceName", " "), Map.of("serviceName", 7),
                Map.of("serviceName", "billing", "extra", "x"))) {
            assertEquals(StopReason.REJECTED_TOOL_CALL, new AgentRuntime(
                    repeating(new ModelDecision.ToolRequest("getServiceStatus", arguments)), 4, telemetry())
                    .run(GOAL).result().stopReason(), arguments.toString());
        }
    }

    @Test
    void aFinalAnswerStopsTheRunAndCountsAsADecision() {
        AgentRunResult result = new AgentRuntime(repeating(new ModelDecision.FinalAnswer("done")), 4, telemetry())
                .run(GOAL).result();
        assertEquals(StopReason.FINAL_ANSWER, result.stopReason());
        assertEquals(1, result.modelSteps());
        assertEquals("done", result.answer().orElseThrow());
    }

    @Test
    void theNormalRunAnswersAsTheLab09BaselineDid() {
        String answer = new AgentRuntime(ScriptedModel.baseline(), 4, telemetry()).run(GOAL).result()
                .answer().orElseThrow();
        assertTrue(answer.contains("DEGRADED"));
        assertTrue(answer.contains("notifications-2.4.1"));
        assertTrue(answer.contains("correlation only"));
    }

    @Test
    void stepBudgetMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentRuntime(repeating(tool("getServiceStatus", "a")), 0, telemetry()));
    }

    // --- Live adapter: translation and token usage, without any network ---

    @Test
    void geminiUsageIsReportedFieldByFieldAndOnlyWhenPresent() {
        GenerateContentResponse noUsage = responseWithParts(Part.fromText("ok"));
        assertTrue(GeminiAgentModel.usageOf(noUsage).isEmpty());

        GenerateContentResponse partial = GenerateContentResponse.builder()
                .usageMetadata(GenerateContentResponseUsageMetadata.builder()
                        .promptTokenCount(11).totalTokenCount(20).build())
                .build();
        AgentModel.TokenUsage usage = GeminiAgentModel.usageOf(partial).orElseThrow();
        assertEquals(11, usage.input().getAsInt());
        assertEquals(20, usage.total().getAsInt());
        assertTrue(usage.output().isEmpty(), "candidatesTokenCount was not reported, so output stays absent");
    }

    @Test
    void geminiTextBecomesAFinalAnswerAndOneFunctionCallBecomesAToolRequest() {
        assertEquals("ok", ((ModelDecision.FinalAnswer) GeminiAgentModel.toDecision(
                responseWithParts(Part.fromText("ok")))).text());
        ModelDecision.ToolRequest request = (ModelDecision.ToolRequest) GeminiAgentModel.toDecision(
                responseWithParts(Part.fromFunctionCall("getServiceStatus", Map.of("serviceName", "billing"))));
        assertEquals("getServiceStatus", request.name());
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
        assertEquals("primary", ObservabilityExample.apiKey(Map.of(
                "GOOGLE_API_KEY", "primary", "GEMINI_API_KEY", "legacy")).orElseThrow());
        assertTrue(ObservabilityExample.apiKey(Map.of()).isEmpty());
        assertEquals(ObservabilityExample.DEFAULT_MODEL, ObservabilityExample.model(Map.of()));
        assertEquals("custom", ObservabilityExample.model(Map.of("GEMINI_MODEL", "custom")));
    }

    // 13. The default run needs no key and no network: it is driven with an empty environment,
    // and the scripted models never construct the Gemini client (only the --live path does).
    @Test
    void theDefaultRunNeedsNoKeyAndShowsTheTraceTheLogsAndTheMetrics() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = ObservabilityExample.run(new String[0], Map.of(),
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        String text = out.toString(StandardCharsets.UTF_8);

        assertEquals(0, exit);
        assertEquals("", err.toString(StandardCharsets.UTF_8));
        // Structure, not timestamps or ids.
        for (String expected : List.of("TRACE run-001", "TRACE run-002", "TRACE run-003", "MODEL_CALL",
                "AGENT_DECISION", "TOOL_CALL", "TOOL_VALIDATION", "TOOL_EXECUTION", "FINAL_RESPONSE",
                "stop_reason=MAX_STEPS", "stop_reason=FINAL_ANSWER", "stop_reason=ERROR",
                "RUN SUMMARY run-001", "event=tool_call", "event=run_end",
                "METRICS", "agent.runs", "was requested 3 times", "tokens:       not reported by this model")) {
            assertTrue(text.contains(expected), "missing: " + expected);
        }
        assertTrue(text.contains("[FAIL] stopped for the intended reason"));
    }

    @Test
    void theDefaultRunPrintsNoSecretEvenThoughTheSimulatedFailureEchoedOne() {
        String text = printed(out -> ObservabilityExample.runScripted(out, java.time.Clock.systemUTC()));
        assertFalse(text.contains("demo-not-a-real-key"));
        assertFalse(text.contains("demo-token-1234567890"));
        assertTrue(text.contains("api_key=[REDACTED]"));
        assertTrue(text.contains("Authorization: [REDACTED]"));
    }

    @Test
    void theDefaultRunIsRepeatableApartFromTimes() {
        String first = stripTimes(printed(out -> ObservabilityExample.runScripted(out,
                new TestSupport.SteppingClock(10))));
        String second = stripTimes(printed(out -> ObservabilityExample.runScripted(out,
                new TestSupport.SteppingClock(10))));
        assertEquals(first, second, "with a fixed clock the whole report is identical");
    }

    private static String stripTimes(String text) {
        return text.replaceAll("\\d{4}-\\d\\d-\\d\\dT[\\d:.]+Z", "<time>");
    }

    @Test
    void liveModeWithoutAKeyStopsBeforeAnyModelCall() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = ObservabilityExample.run(new String[] {"--live"}, Map.of(),
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        assertEquals(1, exit);
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("GOOGLE_API_KEY"));
    }

    private static GenerateContentResponse responseWithParts(Part... parts) {
        return GenerateContentResponse.builder()
                .candidates(Candidate.builder()
                        .content(Content.builder().role("model").parts(parts).build()).build())
                .build();
    }
}
