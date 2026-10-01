package dev.agentic.handbook.labs.observability;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Shared helpers: a fake clock that makes every duration deterministic, and small model doubles. */
final class TestSupport {

    private TestSupport() {
    }

    /** Every read of the clock advances time by a fixed step, so no test depends on real time. */
    static final class SteppingClock implements InstantSource {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        private final long stepMillis;

        SteppingClock(long stepMillis) {
            this.stepMillis = stepMillis;
        }

        @Override
        public Instant instant() {
            Instant current = now;
            now = now.plusMillis(stepMillis);
            return current;
        }
    }

    static Telemetry telemetry(List<String> logLines) {
        return new Telemetry(new SteppingClock(10), logLines::add);
    }

    static Telemetry telemetry() {
        return telemetry(new ArrayList<>());
    }

    static final String GOAL = ObservabilityExample.GOAL;

    static AgentModel repeating(ModelDecision decision) {
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

    static ModelDecision.ToolRequest tool(String name, String service) {
        return new ModelDecision.ToolRequest(name, Map.of("serviceName", service));
    }

    /** A model that fails the way a provider call can. */
    static AgentModel throwing(RuntimeException failure) {
        return new AgentModel() {
            @Override
            public ModelDecision start(String goal) {
                throw failure;
            }

            @Override
            public ModelDecision observe(ToolExchange exchange) {
                throw failure;
            }
        };
    }

    static String printed(Consumer<PrintStream> action) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            action.accept(out);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
