package dev.agentic.handbook.labs.observability;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything recorded for one run: one trace id and its spans, in the order
 * they started. The first span is the root. The parent ids make the nesting.
 */
public record Trace(String traceId, List<Span> spans) {

    public Trace {
        spans = List.copyOf(spans);
    }

    public Span root() {
        return spans.get(0);
    }

    public List<Span> children(Span parent) {
        return spans.stream()
                .filter(span -> span.parentSpanId().filter(parent.spanId()::equals).isPresent())
                .toList();
    }

    public List<Span> ofType(SpanType type) {
        return spans.stream().filter(span -> span.type() == type).toList();
    }

    public Duration duration() {
        return root().duration();
    }

    public Optional<String> stopReason() {
        return root().attribute("stop_reason");
    }

    /**
     * Tool calls the model proposed more than once with identical arguments,
     * with how many times. Reading a trace as data is the point: this question
     * ("did the run repeat itself?") is not answerable from a result.
     */
    public Map<String, Integer> repeatedToolCalls() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Span call : ofType(SpanType.TOOL_CALL)) {
            counts.merge(call.name(), 1, Integer::sum);
        }
        counts.values().removeIf(count -> count < 2);
        return counts;
    }

    /** Input and output tokens summed over the model spans that reported them; empty if none did. */
    public Optional<Long> reportedTokens(String attribute) {
        List<Long> values = new ArrayList<>();
        for (Span call : ofType(SpanType.MODEL_CALL)) {
            call.attribute(attribute).map(Long::parseLong).ifPresent(values::add);
        }
        return values.isEmpty() ? Optional.empty() : Optional.of(values.stream().mapToLong(Long::longValue).sum());
    }
}
