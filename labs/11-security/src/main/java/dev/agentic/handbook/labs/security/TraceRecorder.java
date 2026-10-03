package dev.agentic.handbook.labs.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Records the spans of one run under one trace id (Lab 10's recorder, trimmed).
 * Every value it stores goes through the {@link Redactor} first, so there is one
 * place where "what may enter a span" is enforced. Ids are sequential so the
 * lab's output is readable and repeatable.
 */
public final class TraceRecorder {

    private final String traceId;
    private final Redactor redactor;
    private final List<Span> spans = new ArrayList<>();

    public TraceRecorder(String traceId, Redactor redactor) {
        this.traceId = traceId;
        this.redactor = redactor;
    }

    public String traceId() {
        return traceId;
    }

    /** Starts a span. The parent is null only for the root. */
    public Span begin(Span parent, SpanType type, String name) {
        if (parent == null && !spans.isEmpty()) {
            throw new IllegalStateException("A trace has exactly one root span.");
        }
        Span span = new Span(traceId, String.format("span-%03d", spans.size() + 1),
                Optional.ofNullable(parent).map(Span::spanId), type, redactor.text(name));
        spans.add(span);
        return span;
    }

    public void put(Span span, String key, String value) {
        span.put(key, redactor.value(key, value));
    }

    public void end(Span span, SpanStatus status) {
        span.finish(status);
    }

    /** Records one security event as a finished span; {@code fields} alternate keys and values. */
    public Span event(Span parent, SecurityEvent event, SpanStatus status, String... fields) {
        if (fields.length % 2 != 0) {
            throw new IllegalArgumentException("fields must be key/value pairs");
        }
        Span span = begin(parent, SpanType.SECURITY_EVENT, event.name());
        for (int i = 0; i < fields.length; i += 2) {
            put(span, fields[i], fields[i + 1]);
        }
        end(span, status);
        return span;
    }

    /** The finished trace. Every span must have ended. */
    public Trace trace() {
        for (Span span : spans) {
            if (!span.finished()) {
                throw new IllegalStateException("Span " + span.spanId() + " never ended.");
            }
        }
        return new Trace(traceId, spans);
    }
}
