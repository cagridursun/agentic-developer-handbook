package dev.agentic.handbook.labs.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Security events in the trace, and sensitive values on the observable surfaces.
 * Observability records what the controls decided; these tests never rely on it to enforce anything.
 */
class ObservabilityTest {

    private static final String TOKEN = HelioPlatform.FAKE_API_TOKEN;
    private final World world = World.standard();

    private static ToolProposal restart(String service) {
        return ToolProposal.call("restartService", "x", "serviceName", service, "strategy", "ROLLING", "gracePeriodSeconds", 30);
    }

    // --- sensitive values ---

    @Test
    void theTokenIsNeverInAToolResultOrAFinalAnswerOrATrace() {
        Assistant.Run run = world.runAssistant(new SimulatedModel(), World.INCIDENT_RESPONDER, "Investigate notifications.");
        assertFalse(run.answer().contains(TOKEN));
        run.results().forEach(result -> {
            assertFalse(result.observation().contains(TOKEN));
            assertFalse(result.data().containsKey("monitoringApiToken"));
        });
        assertFalse(run.trace().allText().contains(TOKEN));
        // The platform really did return it: the control is the application's, not the platform's good behavior.
        assertEquals(TOKEN, world.platform.getServiceStatus("notifications").get("monitoringApiToken"));
    }

    @Test
    void droppingASensitiveFieldIsRecordedByNameAndNeverByValue() {
        World.Session session = world.session(World.INCIDENT_RESPONDER);
        world.gateway.invoke(session.context(), ToolProposal.call("getServiceStatus", "x", "serviceName", "notifications"));
        Trace trace = session.trace();
        Span redaction = trace.eventSpans(SecurityEvent.SENSITIVE_FIELD_REDACTED).get(0);
        assertEquals("monitoringApiToken", redaction.attribute("field").orElseThrow());
        assertFalse(trace.allText().contains(TOKEN));
    }

    @Test
    void aSecretInsideAnAllowedFieldIsScrubbedFromTheResultAndTheTrace() {
        ToolDefinition leaky = new ToolDefinition("getLeakyNote", Capability.READ_INCIDENT_NOTES, OperationType.READ,
                "serviceName", List.of(ArgumentRule.pattern("serviceName", HelioTools.SERVICE_NAME)),
                Set.of("serviceName", "note"),
                (principal, args) -> Map.of("serviceName", "notifications", "note", "Use " + TOKEN + " to log in",
                        "password", "hunter2"));
        World w = World.with(PolicyAuthorizer.helioPolicy(), new HelioPlatform(), Map.of("getLeakyNote", leaky));
        World.Session session = w.session(World.INCIDENT_RESPONDER);
        GatewayResult result = w.gateway.invoke(session.context(),
                ToolProposal.call("getLeakyNote", "x", "serviceName", "notifications"));
        assertFalse(result.observation().contains(TOKEN));
        assertTrue(result.data().get("note").toString().contains(Redactor.REDACTED));
        assertFalse(result.data().containsKey("password"));
        Trace trace = session.trace();
        assertFalse(trace.allText().contains(TOKEN));
        assertFalse(trace.allText().contains("hunter2"));
        assertEquals(2, trace.eventSpans(SecurityEvent.SENSITIVE_FIELD_REDACTED).size());
    }

    @Test
    void aSecretTheModelPutsInArgumentsDoesNotReachTheTrace() {
        World.Session session = world.session(World.INCIDENT_RESPONDER);
        world.gateway.invoke(session.context(), ToolProposal.call("getServiceStatus", "Here is the key: " + TOKEN,
                "serviceName", "notifications", "api_key", TOKEN));
        world.gateway.invoke(session.context(), ToolProposal.call("getServiceStatus", "x", "serviceName", "x " + TOKEN));
        assertFalse(session.trace().allText().contains(TOKEN));
    }

    @Test
    void aToolErrorMessageWithASecretIsRedactedEverywhere() {
        ToolDefinition failing = new ToolDefinition("getFailing", Capability.READ_SERVICE_STATUS, OperationType.READ,
                "serviceName", List.of(ArgumentRule.pattern("serviceName", HelioTools.SERVICE_NAME)),
                Set.of("serviceName"),
                (principal, args) -> {
                    throw new IllegalStateException("upstream said 503 for " + TOKEN + " with Authorization: Bearer abc.def");
                });
        World w = World.with(PolicyAuthorizer.helioPolicy(), new HelioPlatform(), Map.of("getFailing", failing));
        World.Session session = w.session(World.INCIDENT_RESPONDER);
        GatewayResult result = w.gateway.invoke(session.context(),
                ToolProposal.call("getFailing", "x", "serviceName", "notifications"));
        assertEquals(GatewayResult.Outcome.FAILED, result.outcome());
        assertFalse(result.reason().contains(TOKEN));
        assertFalse(result.reason().contains("abc.def"));
        assertFalse(session.trace().allText().contains(TOKEN));
        assertFalse(session.trace().allText().contains("abc.def"));
    }

    // --- denials are visible; a denied action is never recorded as executed ---

    @Test
    void anAuthorizationDenialIsVisibleInTheTraceWithItsReason() {
        World.Session session = world.session(World.TRIAGE_ASSISTANT);
        world.gateway.invoke(session.context(), restart("notifications"));
        Trace trace = session.trace();
        assertEquals(List.of(SecurityEvent.AUTHORIZATION_DENIED), trace.events());
        Span denial = trace.eventSpans(SecurityEvent.AUTHORIZATION_DENIED).get(0);
        assertEquals("CAPABILITY_NOT_GRANTED", denial.attribute("reason").orElseThrow());
        assertEquals(SpanStatus.REJECTED, denial.status());
        assertEquals(SpanStatus.REJECTED, trace.ofType(SpanType.TOOL_CALL).get(0).status());
    }

    @Test
    void aValidationFailureAndAnApprovalHoldAreVisible() {
        World.Session session = world.session(World.INCIDENT_RESPONDER);
        world.gateway.invoke(session.context(), ToolProposal.call("getServiceStatus", "x", "serviceName", "BAD"));
        world.gateway.invoke(session.context(), restart("notifications"));
        Trace trace = session.trace();
        assertEquals(List.of(SecurityEvent.ARGUMENT_VALIDATION_FAILED, SecurityEvent.APPROVAL_REQUIRED), trace.events());
    }

    @Test
    void aGrantedApprovalIsVisible() {
        String id = world.gateway.invoke(world.session(World.INCIDENT_RESPONDER).context(), restart("notifications"))
                .pendingOperationId().orElseThrow();
        World.Session session = world.session(World.INCIDENT_COMMANDER);
        world.gateway.approve(session.context(), id);
        Span granted = session.trace().eventSpans(SecurityEvent.APPROVAL_GRANTED).get(0);
        assertEquals("incident-commander", granted.attribute("approver").orElseThrow());
        assertEquals(id, granted.attribute("pending").orElseThrow());
    }

    @Test
    void aDeniedOrHeldActionNeverEmitsAnExecutionEvent() {
        World.Session session = world.session(World.TRIAGE_ASSISTANT);
        world.gateway.invoke(session.context(), restart("notifications"));                       // denied
        world.gateway.invoke(session.context(), ToolProposal.call("getServiceStatus", "x"));    // invalid
        world.gateway.invoke(world.session(null).context(), restart("notifications"));          // no principal
        World.Session responder = world.session(World.INCIDENT_RESPONDER);
        world.gateway.invoke(responder.context(), restart("billing"));                           // held
        for (Trace trace : List.of(session.trace(), responder.trace())) {
            assertFalse(trace.has(SecurityEvent.TOOL_EXECUTION_STARTED));
            assertFalse(trace.has(SecurityEvent.TOOL_EXECUTION_COMPLETED));
        }
        assertEquals(0, world.platform.executionCount());
    }

    @Test
    void anExecutedActionRecordsAllowedStartedCompletedInOrder() {
        World.Session session = world.session(World.INCIDENT_RESPONDER);
        world.gateway.invoke(session.context(), ToolProposal.call("getIncidentNote", "x", "serviceName", "notifications",
                "incidentId", "INC-1001"));
        assertEquals(List.of(SecurityEvent.AUTHORIZATION_ALLOWED, SecurityEvent.TOOL_EXECUTION_STARTED,
                SecurityEvent.TOOL_EXECUTION_COMPLETED), session.trace().events());
    }

    @Test
    void everySecurityEventBelongsToAToolCallInOneTrace() {
        Assistant.Run run = world.runAssistant(new SimulatedModel(), World.INCIDENT_RESPONDER,
                "Summarize incident INC-1002 on notifications.");
        Trace trace = run.trace();
        Set<String> toolCalls = new java.util.HashSet<>();
        trace.ofType(SpanType.TOOL_CALL).forEach(span -> toolCalls.add(span.spanId()));
        for (Span event : trace.ofType(SpanType.SECURITY_EVENT)) {
            assertTrue(toolCalls.contains(event.parentSpanId().orElseThrow()));
            assertEquals(trace.traceId(), event.traceId());
        }
        assertEquals(trace.traceId(), trace.root().traceId());
    }

    @Test
    void recordingChangesNoDecision() {
        // The same proposal, with and without anyone reading the trace, gets the same outcome:
        // the trace is an output of the gateway, never an input.
        GatewayResult a = world.gateway.invoke(world.session(World.TRIAGE_ASSISTANT).context(), restart("notifications"));
        World other = World.standard();
        GatewayResult b = other.gateway.invoke(other.session(World.TRIAGE_ASSISTANT).context(), restart("notifications"));
        assertEquals(a.outcome(), b.outcome());
        assertEquals(a.reason(), b.reason());
    }
}
