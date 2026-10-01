package dev.agentic.handbook.labs.observability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A handful of in-memory counters, kept across runs. Metrics summarize: they
 * say how often, never why. They hold no relationship between events, which is
 * exactly what a trace adds.
 *
 * <p>Deliberately small. There is no registry, no labels, no histogram, and no
 * exporter: a metric here is a name and a number.
 */
public final class Metrics {

    /** Runs started. */
    public static final String RUNS = "agent.runs";
    /** Runs that did not end in a final answer, whatever the reason. */
    public static final String FAILURES = "agent.failures";
    /** Model decisions, the unit of the step budget; one model call each. */
    public static final String STEPS = "agent.steps";
    /** Tool calls the model proposed. */
    public static final String TOOL_CALLS = "agent.tool.calls";
    /** Tool calls the application refused. */
    public static final String TOOL_REJECTED = "agent.tool.rejected";
    /** Allowed tool calls that failed while executing. */
    public static final String TOOL_ERRORS = "agent.tool.errors";
    /** Total wall time of all runs, in milliseconds. */
    public static final String RUN_DURATION_MS = "agent.run.duration_ms";

    private final Map<String, Long> counters = new LinkedHashMap<>();

    public Metrics() {
        for (String name : new String[] {RUNS, FAILURES, STEPS, TOOL_CALLS, TOOL_REJECTED, TOOL_ERRORS,
                RUN_DURATION_MS}) {
            counters.put(name, 0L);
        }
    }

    void add(String name, long amount) {
        counters.merge(name, amount, Long::sum);
    }

    void increment(String name) {
        add(name, 1);
    }

    public long get(String name) {
        return counters.getOrDefault(name, 0L);
    }

    public Map<String, Long> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(counters));
    }
}
