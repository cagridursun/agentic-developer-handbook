package dev.agentic.handbook.labs.security;

import java.util.List;

/** Everything recorded for one run: one trace id and its spans, in the order they started. */
public record Trace(String traceId, List<Span> spans) {

    public Trace {
        spans = List.copyOf(spans);
    }

    public Span root() {
        return spans.get(0);
    }

    public List<Span> ofType(SpanType type) {
        return spans.stream().filter(span -> span.type() == type).toList();
    }

    /** The security events recorded, in order. */
    public List<SecurityEvent> events() {
        return ofType(SpanType.SECURITY_EVENT).stream().map(span -> SecurityEvent.valueOf(span.name())).toList();
    }

    public List<Span> eventSpans(SecurityEvent event) {
        return ofType(SpanType.SECURITY_EVENT).stream().filter(span -> span.name().equals(event.name())).toList();
    }

    public boolean has(SecurityEvent event) {
        return !eventSpans(event).isEmpty();
    }

    /** Every name and attribute value, joined: what a person reading this trace could learn. */
    public String allText() {
        StringBuilder text = new StringBuilder();
        for (Span span : spans) {
            text.append(span.name()).append('\n');
            span.attributes().forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
        }
        return text.toString();
    }
}
