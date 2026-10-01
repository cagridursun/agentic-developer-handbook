package dev.agentic.handbook.labs.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One unit of work inside a run: what it was, which span it belongs to, when it
 * started and ended, how it ended, and a few attributes.
 *
 * <p>A span is only written to by the {@link TraceRecorder} that created it
 * (which redacts every value first) and is finished before the trace is read.
 */
public final class Span {

    private final String traceId;
    private final String spanId;
    private final Optional<String> parentSpanId;
    private final SpanType type;
    private final String name;
    private final Instant start;
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private Instant end;
    private SpanStatus status = SpanStatus.OK;

    Span(String traceId, String spanId, Optional<String> parentSpanId, SpanType type, String name, Instant start) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.type = type;
        this.name = name;
        this.start = start;
    }

    public String traceId() {
        return traceId;
    }

    public String spanId() {
        return spanId;
    }

    /** Empty only for the root span. */
    public Optional<String> parentSpanId() {
        return parentSpanId;
    }

    public SpanType type() {
        return type;
    }

    public String name() {
        return name;
    }

    public Instant start() {
        return start;
    }

    /** Only meaningful once the span is finished. */
    public Instant end() {
        return end;
    }

    public boolean finished() {
        return end != null;
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    public SpanStatus status() {
        return status;
    }

    public Map<String, String> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public Optional<String> attribute(String key) {
        return Optional.ofNullable(attributes.get(key));
    }

    void put(String key, String value) {
        attributes.put(key, value);
    }

    void finish(Instant end, SpanStatus status) {
        this.end = end;
        this.status = status;
    }
}
