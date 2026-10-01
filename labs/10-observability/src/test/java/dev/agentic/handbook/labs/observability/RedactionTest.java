package dev.agentic.handbook.labs.observability;

import static dev.agentic.handbook.labs.observability.TestSupport.GOAL;
import static dev.agentic.handbook.labs.observability.TestSupport.repeating;
import static dev.agentic.handbook.labs.observability.TestSupport.tool;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The redactor is a heuristic over known shapes. These tests pin the shapes it
 * claims to handle, and that redaction happens at the point data enters a span
 * or a log line. They do not claim it finds every secret.
 */
class RedactionTest {

    private final Redactor redactor = new Redactor();

    @Test
    void knownSecretShapesInTextAreRedacted() {
        String googleKey = "AIza" + "x".repeat(35);
        String text = redactor.text("GET /v1?key=" + googleKey + " Authorization: Bearer abc.def-123 "
                + "password=hunter2 api_key: sk-demo client_secret=\"quoted secret\" token=abc123");
        for (String secret : List.of(googleKey, "abc.def-123", "hunter2", "sk-demo", "quoted secret", "abc123")) {
            assertFalse(text.contains(secret), secret + " leaked in: " + text);
        }
        assertTrue(text.contains(Redactor.REDACTED));
    }

    @Test
    void aBareBearerTokenAndAGoogleKeyAreRedactedWithoutAnyLabel() {
        assertEquals("sent Bearer " + Redactor.REDACTED, redactor.text("sent Bearer abcDEF123"));
        assertEquals("key " + Redactor.REDACTED, redactor.text("key AIza" + "y".repeat(35)));
    }

    @Test
    void secretNamedKeysAreRedactedWholesaleAndCountsAreNot() {
        assertEquals(Redactor.REDACTED, redactor.value("api_key", "anything"));
        assertEquals(Redactor.REDACTED, redactor.value("Authorization", "Basic xyz"));
        assertEquals(Redactor.REDACTED, redactor.value("password", "p"));
        assertEquals(Redactor.REDACTED, redactor.value("access_token", "t"));
        assertEquals("120", redactor.value("input_tokens", "120"));
        assertEquals("120", redactor.value("total_tokens", "120"));
    }

    @Test
    void describeRedactsSecretFieldsAndKeepsOrdinaryOnes() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("serviceName", "notifications");
        args.put("apiKey", "super-secret");
        assertEquals("serviceName=notifications, apiKey=" + Redactor.REDACTED, redactor.describe(args));
    }

    @Test
    void longValuesAreShortenedSoASummaryIsNotAPayloadDump() {
        String shortened = redactor.text("x".repeat(1000));
        assertEquals(Redactor.MAX_LENGTH, shortened.length());
        assertTrue(shortened.endsWith("..."));
    }

    @Test
    void ordinaryTextIsLeftAlone() {
        String text = "notifications is DEGRADED. Elevated delivery latency since 14:05 UTC.";
        assertEquals(text, redactor.text(text));
    }

    // Redaction is applied where data enters spans and logs, not left to each caller.

    @Test
    void secretsInAToolFailureNeverReachSpansLogsOrTheResult() {
        List<String> lines = new ArrayList<>();
        ToolBackend leaking = (tool, service) -> {
            throw new IllegalStateException("HTTP 503 for /v1/deployments?api_key=demo-not-a-real-key; "
                    + "request header Authorization: Bearer demo-token-1234567890");
        };
        ObservedRun run = new AgentRuntime(repeating(tool("getServiceStatus", "notifications")), 4,
                TestSupport.telemetry(lines), leaking).run(GOAL);

        StringBuilder everything = new StringBuilder(String.join("\n", lines));
        for (Span span : run.trace().spans()) {
            everything.append('\n').append(span.name()).append(span.attributes());
        }
        everything.append(run.result().error().orElseThrow());
        run.result().failure().ifPresent(failure -> everything.append(failure.detail()));

        assertFalse(everything.toString().contains("demo-not-a-real-key"));
        assertFalse(everything.toString().contains("demo-token-1234567890"));
        assertTrue(everything.toString().contains("HTTP 503"), "the diagnostic part of the message must survive");
        assertTrue(everything.toString().contains(Redactor.REDACTED));
    }

    @Test
    void secretsInToolArgumentsAreRedactedInTheSpanNameAndAttributes() {
        // Rejected (extra argument), but the proposal still appears in the trace, redacted.
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("serviceName", "notifications");
        args.put("password", "hunter2");
        List<String> lines = new ArrayList<>();
        ObservedRun run = new AgentRuntime(repeating(new ModelDecision.ToolRequest("getServiceStatus", args)), 4,
                TestSupport.telemetry(lines)).run(GOAL);

        String everything = String.join("\n", lines) + run.trace().spans().stream()
                .map(span -> span.name() + span.attributes()).toList();
        assertFalse(everything.contains("hunter2"));
        assertEquals(StopReason.REJECTED_TOOL_CALL, run.result().stopReason());
    }

    @Test
    void theRunnerDoesNotRedactWhatTheModelSeesOnlyWhatTelemetryRecords() {
        // The tool result goes to the model untouched; redaction is a telemetry concern, not a data change.
        ToolBackend withSecretField = (tool, service) -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("serviceName", service);
            result.put("status", "HEALTHY");
            result.put("api_key", "demo-not-a-real-key");
            return result;
        };
        ObservedRun run = new AgentRuntime(ScriptedModel.baseline(), 4, TestSupport.telemetry(), withSecretField)
                .run("Investigate the billing service");
        assertEquals("demo-not-a-real-key", run.result().exchanges().get(0).result().get("api_key"));
        String recorded = run.trace().ofType(SpanType.TOOL_EXECUTION).get(0).attribute("result").orElseThrow();
        assertFalse(recorded.contains("demo-not-a-real-key"));
        assertTrue(recorded.contains("api_key=" + Redactor.REDACTED));
    }
}
