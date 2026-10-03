package dev.agentic.handbook.labs.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One unit of work in a run: Lab 10's span without timestamps, because this lab
 * is about what was decided, not how long it took. Only the {@link TraceRecorder}
 * that created it writes to it, and it redacts every value first.
 */
public final class Span {

    private final String traceId;
    private final String spanId;
    private final Optional<String> parentSpanId;
    private final SpanType type;
    private final String name;
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private SpanStatus status = SpanStatus.OK;
    private boolean finished;

    Span(String traceId, String spanId, Optional<String> parentSpanId, SpanType type, String name) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.type = type;
        this.name = name;
    }

    public String traceId() {
        return traceId;
    }

    public String spanId() {
        return spanId;
    }

    public Optional<String> parentSpanId() {
        return parentSpanId;
    }

    public SpanType type() {
        return type;
    }

    public String name() {
        return name;
    }

    public SpanStatus status() {
        return status;
    }

    public boolean finished() {
        return finished;
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

    void finish(SpanStatus status) {
        this.status = status;
        this.finished = true;
    }
}
