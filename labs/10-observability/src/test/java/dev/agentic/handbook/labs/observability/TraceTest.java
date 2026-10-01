package dev.agentic.handbook.labs.observability;

import static dev.agentic.handbook.labs.observability.TestSupport.GOAL;
import static dev.agentic.handbook.labs.observability.TestSupport.repeating;
import static dev.agentic.handbook.labs.observability.TestSupport.telemetry;
import static dev.agentic.handbook.labs.observability.TestSupport.tool;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The trace model, tested through the real runtime. Nothing here reads a real
 * clock or compares an id's value: durations come from a stepping fake clock,
 * and ids are only compared with each other.
 */
class TraceTest {

    private static ObservedRun normalRun(Telemetry telemetry) {
        return new AgentRuntime(ScriptedModel.baseline(), 4, telemetry).run(GOAL);
    }

    private static ObservedRun repeatingRun(Telemetry telemetry) {
        return new AgentRuntime(ScriptedModel.repeatingDeploymentLookup(), 4, telemetry).run(GOAL);
    }

    // 1. Every run receives a trace id, and every span of the run carries it.
    @Test
    void everyRunHasATraceIdThatEverySpanShares() {
        Trace trace = normalRun(telemetry()).trace();
        assertFalse(trace.traceId().isBlank());
        assertTrue(trace.spans().stream().allMatch(span -> span.traceId().equals(trace.traceId())));
    }

    // 2. Nested spans keep their parent-child relationships.
    @Test
    void spansNestUnderTheirParents() {
        Trace trace = normalRun(telemetry()).trace();
        Span root = trace.root();
        assertTrue(root.parentSpanId().isEmpty());
        assertEquals(1, trace.spans().stream().filter(span -> span.parentSpanId().isEmpty()).count());

        for (Span span : trace.spans()) {
            if (span.parentSpanId().isEmpty()) {
                continue;
            }
            Span parent = trace.spans().stream()
                    .filter(candidate -> candidate.spanId().equals(span.parentSpanId().get()))
                    .findFirst().orElseThrow();
            switch (span.type()) {
                case MODEL_CALL, TOOL_CALL, FINAL_RESPONSE -> assertEquals(SpanType.AGENT_RUN, parent.type());
                case AGENT_DECISION -> assertEquals(SpanType.MODEL_CALL, parent.type());
                case TOOL_VALIDATION, TOOL_EXECUTION -> assertEquals(SpanType.TOOL_CALL, parent.type());
                case AGENT_RUN -> throw new AssertionError("only the root is an AGENT_RUN");
            }
            // A child lives inside its parent in time.
            assertFalse(span.start().isBefore(parent.start()));
            assertFalse(span.end().isAfter(parent.end()));
        }
    }

    // 3. Model spans are recorded: one per model decision, with the decision as a child.
    @Test
    void everyModelDecisionIsAModelSpanWithItsProposal() {
        ObservedRun run = normalRun(telemetry());
        List<Span> modelCalls = run.trace().ofType(SpanType.MODEL_CALL);
        assertEquals(run.result().modelSteps(), modelCalls.size());
        assertEquals("scripted", modelCalls.get(0).attribute("model.provider").orElseThrow());
        for (Span modelCall : modelCalls) {
            List<Span> decisions = run.trace().children(modelCall);
            assertEquals(1, decisions.size());
            assertEquals(SpanType.AGENT_DECISION, decisions.get(0).type());
            assertEquals(modelCall.attribute("outcome"), decisions.get(0).attribute("decision"));
        }
        assertEquals("final_answer", modelCalls.get(modelCalls.size() - 1).attribute("outcome").orElseThrow());
    }

    // 4. Tool calls are recorded, and the decision-authority boundary is three spans in order.
    @Test
    void everyToolCallRecordsProposalValidationAndExecution() {
        Trace trace = normalRun(telemetry()).trace();
        List<Span> calls = trace.ofType(SpanType.TOOL_CALL);
        assertEquals(List.of("getServiceStatus", "getRecentDeployment"),
                calls.stream().map(call -> call.attribute("tool").orElseThrow()).toList());
        for (Span call : calls) {
            List<Span> children = trace.children(call);
            assertEquals(List.of(SpanType.TOOL_VALIDATION, SpanType.TOOL_EXECUTION),
                    children.stream().map(Span::type).toList());
            assertEquals(Actor.APPLICATION, children.get(0).type().actor());
            assertEquals(Actor.TOOL, children.get(1).type().actor());
            // The tool call points back at the model decision that proposed it.
            Span proposal = trace.spans().stream()
                    .filter(span -> span.spanId().equals(call.attribute("proposal").orElseThrow()))
                    .findFirst().orElseThrow();
            assertEquals(SpanType.AGENT_DECISION, proposal.type());
            assertEquals(Actor.MODEL, proposal.type().actor());
        }
    }

    // 5. A result belongs to the span of the tool call that produced it.
    @Test
    void eachResultIsAttachedToItsOwnToolCall() {
        Trace trace = normalRun(telemetry()).trace();
        List<Span> calls = trace.ofType(SpanType.TOOL_CALL);
        Span statusExecution = executionOf(trace, calls.get(0));
        Span deploymentExecution = executionOf(trace, calls.get(1));
        assertTrue(statusExecution.attribute("result").orElseThrow().contains("status=DEGRADED"));
        assertFalse(statusExecution.attribute("result").orElseThrow().contains("notifications-2.4.1"));
        assertTrue(deploymentExecution.attribute("result").orElseThrow().contains("notifications-2.4.1"));
    }

    private static Span executionOf(Trace trace, Span call) {
        return trace.children(call).stream().filter(span -> span.type() == SpanType.TOOL_EXECUTION)
                .findFirst().orElseThrow();
    }

    // 6. Failures are represented, without swallowing the original exception's information.
    @Test
    void aToolFailureIsAnErrorSpanWithTypeAndMessage() {
        ToolBackend failing = (tool, service) -> {
            throw new IllegalStateException("deployment API unavailable");
        };
        ObservedRun run = new AgentRuntime(repeating(tool("getServiceStatus", "notifications")), 4, telemetry(),
                failing).run(GOAL);
        Trace trace = run.trace();
        Span call = trace.ofType(SpanType.TOOL_CALL).get(0);
        Span execution = executionOf(trace, call);

        assertEquals(SpanStatus.ERROR, execution.status());
        assertEquals(SpanStatus.ERROR, call.status());
        assertEquals(SpanStatus.ERROR, trace.root().status());
        assertEquals("IllegalStateException", execution.attribute("error.type").orElseThrow());
        assertEquals("deployment API unavailable", execution.attribute("error.message").orElseThrow());
        assertEquals(StopReason.ERROR, run.result().stopReason());
        assertEquals("IllegalStateException: deployment API unavailable", run.result().error().orElseThrow());
        assertEquals("getServiceStatus", run.result().failure().orElseThrow().request().name());
    }

    @Test
    void aModelFailureIsAnErrorOnTheModelSpanAndStopsTheRun() {
        ObservedRun run = new AgentRuntime(TestSupport.throwing(new IllegalStateException("provider unavailable")),
                4, telemetry()).run(GOAL);
        Span modelCall = run.trace().ofType(SpanType.MODEL_CALL).get(0);
        assertEquals(SpanStatus.ERROR, modelCall.status());
        assertEquals("provider unavailable", modelCall.attribute("error.message").orElseThrow());
        assertEquals(StopReason.ERROR, run.result().stopReason());
        assertTrue(run.result().failure().isEmpty());
        assertTrue(run.trace().ofType(SpanType.TOOL_CALL).isEmpty());
    }

    @Test
    void aRejectedProposalIsRejectedNotAnError() {
        ObservedRun run = new AgentRuntime(repeating(tool("rollbackDeployment", "notifications")), 4, telemetry())
                .run(GOAL);
        Trace trace = run.trace();
        Span call = trace.ofType(SpanType.TOOL_CALL).get(0);
        Span validation = trace.children(call).get(0);

        assertEquals(SpanStatus.REJECTED, validation.status());
        assertEquals(SpanStatus.REJECTED, call.status());
        assertTrue(validation.attribute("error.message").orElseThrow().contains("not on this application's allowlist"));
        assertTrue(trace.ofType(SpanType.TOOL_EXECUTION).isEmpty(), "a rejected proposal must never execute");
        assertEquals(StopReason.REJECTED_TOOL_CALL, run.result().stopReason());
    }

    // 7. Stop reasons are recorded, and distinguish an answer from a budget stop from an error.
    @Test
    void stopReasonsAreRecordedOnTheRootSpan() {
        Telemetry telemetry = telemetry();
        ObservedRun answered = normalRun(telemetry);
        ObservedRun budget = repeatingRun(telemetry);
        ObservedRun rejected = new AgentRuntime(repeating(tool("getServiceStatus", "payments")), 4, telemetry).run(GOAL);
        ObservedRun failed = new AgentRuntime(TestSupport.throwing(new IllegalStateException("x")), 4, telemetry)
                .run(GOAL);

        assertEquals(Optional.of("FINAL_ANSWER"), answered.trace().stopReason());
        assertEquals(Optional.of("MAX_STEPS"), budget.trace().stopReason());
        assertEquals(Optional.of("REJECTED_TOOL_CALL"), rejected.trace().stopReason());
        assertEquals(Optional.of("ERROR"), failed.trace().stopReason());
        for (ObservedRun run : List.of(answered, budget, rejected, failed)) {
            assertEquals(run.result().stopReason().name(), run.trace().stopReason().orElseThrow());
        }
        assertEquals(SpanStatus.OK, answered.trace().root().status());
        assertEquals(SpanStatus.ERROR, budget.trace().root().status());
        assertEquals(SpanStatus.REJECTED, rejected.trace().root().status());
        assertEquals(1, answered.trace().ofType(SpanType.FINAL_RESPONSE).size());
        assertTrue(budget.trace().ofType(SpanType.FINAL_RESPONSE).isEmpty());
        assertEquals("StepBudgetExhausted", budget.trace().root().attribute("error.type").orElseThrow());
    }

    // 8. Durations are non-negative and consistent. The fake clock makes them exact.
    @Test
    void durationsAreNonNegativeAndConsistent() {
        Trace trace = repeatingRun(telemetry()).trace();
        for (Span span : trace.spans()) {
            assertTrue(span.finished());
            assertFalse(span.duration().isNegative(), span.type() + " " + span.spanId());
            assertTrue(span.duration().compareTo(trace.duration()) <= 0);
        }
        // The stepping clock advances 10 ms per read, so the root outlasts any single child.
        assertTrue(trace.duration().compareTo(Duration.ofMillis(10)) > 0);
        long childrenOfRoot = trace.children(trace.root()).stream().map(Span::duration)
                .mapToLong(Duration::toMillis).sum();
        assertTrue(childrenOfRoot <= trace.duration().toMillis());
    }

    @Test
    void theRealClockNeverProducesANegativeDuration() {
        Telemetry real = new Telemetry(java.time.Clock.systemUTC(), line -> { });
        Trace trace = normalRun(real).trace();
        assertTrue(trace.spans().stream().noneMatch(span -> span.duration().isNegative()));
    }

    // 9. Metrics are derived correctly, and agree with the traces.
    @Test
    void metricsCountWhatTheTracesContain() {
        Telemetry telemetry = telemetry();
        ObservedRun budget = repeatingRun(telemetry);
        ObservedRun normal = normalRun(telemetry);
        ToolBackend failing = (tool, service) -> {
            throw new IllegalStateException("down");
        };
        ObservedRun failed = new AgentRuntime(ScriptedModel.baseline(), 4, telemetry, failing).run(GOAL);
        ObservedRun rejected = new AgentRuntime(repeating(tool("getServiceStatus", "payments")), 4, telemetry)
                .run(GOAL);
        List<Trace> traces = List.of(budget.trace(), normal.trace(), failed.trace(), rejected.trace());

        Metrics metrics = telemetry.metrics();
        assertEquals(4, metrics.get(Metrics.RUNS));
        assertEquals(3, metrics.get(Metrics.FAILURES));
        assertEquals(count(traces, SpanType.MODEL_CALL), metrics.get(Metrics.STEPS));
        assertEquals(count(traces, SpanType.TOOL_CALL), metrics.get(Metrics.TOOL_CALLS));
        assertEquals(1, metrics.get(Metrics.TOOL_ERRORS));
        assertEquals(1, metrics.get(Metrics.TOOL_REJECTED));
        assertEquals(traces.stream().mapToLong(trace -> trace.duration().toMillis()).sum(),
                metrics.get(Metrics.RUN_DURATION_MS));

        // The documented numbers, so a change to the counting rules is visible.
        assertEquals(4 + 3 + 1 + 1, metrics.get(Metrics.STEPS));
        assertEquals(4 + 2 + 1 + 1, metrics.get(Metrics.TOOL_CALLS));
    }

    private static long count(List<Trace> traces, SpanType type) {
        return traces.stream().mapToLong(trace -> trace.ofType(type).size()).sum();
    }

    // 10. Runs never share trace ids, and span ids are unique too.
    @Test
    void multipleRunsDoNotShareTraceOrSpanIds() {
        Telemetry telemetry = telemetry();
        List<Trace> traces = List.of(normalRun(telemetry).trace(), normalRun(telemetry).trace(),
                repeatingRun(telemetry).trace());
        assertEquals(3, traces.stream().map(Trace::traceId).distinct().count());
        Set<String> spanIds = new HashSet<>();
        for (Trace trace : traces) {
            for (Span span : trace.spans()) {
                assertTrue(spanIds.add(span.spanId()), "duplicate span id " + span.spanId());
            }
        }
    }

    // 12. The deliberately failing scenario exposes the intended diagnostic information.
    @Test
    void theRepeatingRunTraceExposesTheRepeatedLookupAndTheBudgetStop() {
        ObservedRun run = repeatingRun(telemetry());
        Trace trace = run.trace();

        assertEquals(StopReason.MAX_STEPS, run.result().stopReason());
        assertTrue(run.result().answer().isEmpty());
        assertEquals(Map.of("getRecentDeployment(serviceName=notifications)", 3), trace.repeatedToolCalls());
        assertEquals(4, trace.ofType(SpanType.MODEL_CALL).size());
        assertEquals(4, trace.ofType(SpanType.TOOL_EXECUTION).size());
        assertEquals(Set.of(SpanStatus.OK),
                Set.copyOf(trace.ofType(SpanType.TOOL_VALIDATION).stream().map(Span::status).toList()),
                "every repeated proposal was valid and allowed; the budget is what stopped the run");
        // The same result every time: the model did not need a different answer, it just kept asking.
        assertEquals(1, trace.ofType(SpanType.TOOL_EXECUTION).stream().skip(1)
                .map(span -> span.attribute("result").orElseThrow()).distinct().count());
        assertTrue(trace.root().attribute("error.message").orElseThrow().contains("budget of 4"));
    }

    @Test
    void theNormalRunIsNotFlaggedAsRepeating() {
        assertTrue(normalRun(telemetry()).trace().repeatedToolCalls().isEmpty());
    }

    // Token usage: absent unless the model reported it. Never invented.
    @Test
    void aModelThatReportsNoUsageLeavesTokenFieldsAbsent() {
        Trace trace = normalRun(telemetry()).trace();
        for (Span modelCall : trace.ofType(SpanType.MODEL_CALL)) {
            assertTrue(modelCall.attribute("input_tokens").isEmpty());
            assertTrue(modelCall.attribute("output_tokens").isEmpty());
            assertTrue(modelCall.attribute("total_tokens").isEmpty());
        }
        assertTrue(trace.reportedTokens("input_tokens").isEmpty());
    }

    @Test
    void reportedUsageIsRecordedFieldByFieldAndNothingElse() {
        AgentModel reporting = new AgentModel() {
            @Override
            public ModelDecision start(String goal) {
                return new ModelDecision.FinalAnswer("done");
            }

            @Override
            public ModelDecision observe(ToolExchange exchange) {
                throw new IllegalStateException("unreachable");
            }

            @Override
            public Optional<TokenUsage> lastUsage() {
                // Input and total reported; output not reported.
                return Optional.of(new TokenUsage(OptionalInt.of(120), OptionalInt.empty(), OptionalInt.of(150)));
            }
        };
        Span modelCall = new AgentRuntime(reporting, 4, telemetry()).run(GOAL).trace()
                .ofType(SpanType.MODEL_CALL).get(0);
        assertEquals("120", modelCall.attribute("input_tokens").orElseThrow());
        assertEquals("150", modelCall.attribute("total_tokens").orElseThrow());
        assertTrue(modelCall.attribute("output_tokens").isEmpty());
    }

    // Structured logs: correlated and few.
    @Test
    void logLinesCarryTheTraceAndSpanTheyBelongTo() {
        List<String> lines = new java.util.ArrayList<>();
        ObservedRun run = repeatingRun(TestSupport.telemetry(lines));
        assertEquals(5, lines.size(), "one line per completed tool call, plus the end of the run");
        for (String line : lines) {
            assertTrue(line.contains("trace=" + run.trace().traceId()), line);
            assertTrue(line.matches(".* span=span-\\d+ event=\\w+.*"), line);
        }
        assertTrue(lines.get(4).contains("event=run_end stop_reason=MAX_STEPS"));
    }

    @Test
    void anUnfinishedSpanCannotBecomeATrace() {
        TraceRecorder recorder = telemetry().newRecorder();
        recorder.begin(null, SpanType.AGENT_RUN, "run");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, recorder::trace);
    }

    @Test
    void aTraceHasExactlyOneRoot() {
        TraceRecorder recorder = telemetry().newRecorder();
        recorder.begin(null, SpanType.AGENT_RUN, "run");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> recorder.begin(null, SpanType.AGENT_RUN, "second root"));
        assertNotEquals("", recorder.traceId());
    }
}
