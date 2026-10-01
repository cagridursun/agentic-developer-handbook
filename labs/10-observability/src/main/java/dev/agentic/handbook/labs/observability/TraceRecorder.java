package dev.agentic.handbook.labs.observability;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Records the spans of one run under one trace id. Every span it creates
 * carries that id: that is the correlation. Every value it stores goes through
 * the {@link Redactor} first, so there is one place where "what may enter a
 * span" is enforced.
 */
public final class TraceRecorder {

    private final Telemetry telemetry;
    private final String traceId;
    private final List<Span> spans = new ArrayList<>();

    TraceRecorder(Telemetry telemetry, String traceId) {
        this.telemetry = telemetry;
        this.traceId = traceId;
    }

    public String traceId() {
        return traceId;
    }

    /** Starts a span. The parent is null only for the root. */
    public Span begin(Span parent, SpanType type, String name) {
        if (parent == null && !spans.isEmpty()) {
            throw new IllegalStateException("A trace has exactly one root span.");
        }
        Span span = new Span(traceId, telemetry.nextSpanId(),
                Optional.ofNullable(parent).map(Span::spanId), type,
                telemetry.redactor().text(name), telemetry.clock().instant());
        spans.add(span);
        return span;
    }

    public void put(Span span, String key, String value) {
        span.put(key, telemetry.redactor().value(key, value));
    }

    public void end(Span span, SpanStatus status) {
        span.finish(telemetry.clock().instant(), status);
    }

    /** Ends a span that failed or was refused, keeping the original exception's type and (redacted) message. */
    public void endWithError(Span span, SpanStatus status, String errorType, String errorMessage) {
        put(span, "error.type", errorType);
        put(span, "error.message", String.valueOf(errorMessage));
        end(span, status);
    }

    void log(StructuredLog.Level level, Span span, String event, String... fields) {
        telemetry.log().log(level, traceId, span.spanId(), event, fields);
    }

    /** The finished trace. Every span must have ended. */
    public Trace trace() {
        for (Span span : spans) {
            if (!span.finished()) {
                throw new IllegalStateException("Span " + span.spanId() + " (" + span.type() + ") never ended.");
            }
        }
        return new Trace(traceId, spans);
    }
}
