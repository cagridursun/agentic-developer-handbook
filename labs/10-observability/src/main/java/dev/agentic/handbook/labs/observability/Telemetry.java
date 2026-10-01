package dev.agentic.handbook.labs.observability;

import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * The observability plane for one process: it hands out trace ids and span ids,
 * owns the clock, the redactor, the structured log, and the metrics.
 *
 * <p>It observes the runtime and never decides anything for it: no method here
 * returns something the runtime branches on.
 *
 * <p>Ids are sequential ({@code run-001}, {@code span-001}) so the lab's output
 * is easy to read and compare. They are unique within one Telemetry instance,
 * which is all this single-process lab needs; a real system generates random
 * ids so independent processes cannot collide.
 */
public final class Telemetry {

    private final InstantSource clock;
    private final Redactor redactor = new Redactor();
    private final StructuredLog log;
    private final Metrics metrics = new Metrics();
    private final AtomicInteger traces = new AtomicInteger();
    private final AtomicInteger spans = new AtomicInteger();

    /**
     * @param clock   the only source of time: inject a fake one in tests
     * @param logSink where structured log lines go
     */
    public Telemetry(InstantSource clock, Consumer<String> logSink) {
        this.clock = clock;
        this.log = new StructuredLog(clock, redactor, logSink);
    }

    public TraceRecorder newRecorder() {
        return new TraceRecorder(this, String.format("run-%03d", traces.incrementAndGet()));
    }

    public Metrics metrics() {
        return metrics;
    }

    public Redactor redactor() {
        return redactor;
    }

    InstantSource clock() {
        return clock;
    }

    StructuredLog log() {
        return log;
    }

    String nextSpanId() {
        return String.format("span-%03d", spans.incrementAndGet());
    }
}
