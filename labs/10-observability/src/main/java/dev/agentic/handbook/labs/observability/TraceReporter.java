package dev.agentic.handbook.labs.observability;

import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a trace into text a person can read: the spans as a tree, in the
 * order they happened, with the actor, the status, and the duration of each.
 * The summary is derived from the trace; the metrics are printed from the
 * counters, so the two can be compared.
 */
public final class TraceReporter {

    // Shown in the span's name or in the header, so not repeated as details.
    private static final Set<String> NOT_REPEATED = Set.of(
            "tool", "arguments", "goal", "decision", "max_steps", "model.provider", "model.name");

    private TraceReporter() {
    }

    public static void printTree(PrintStream out, Trace trace) {
        out.println("TRACE " + trace.traceId());
        printSpan(out, trace, trace.root(), 0);
    }

    private static void printSpan(PrintStream out, Trace trace, Span span, int depth) {
        Duration sinceStart = Duration.between(trace.root().start(), span.start());
        out.println(String.format("%-7s %-9s %-15s %-12s %s%s  [%s %s]",
                "+" + sinceStart.toMillis() + "ms", span.spanId(), span.type(), span.type().actor(),
                "  ".repeat(depth), span.name(), span.status(), format(span.duration())));
        StringBuilder details = new StringBuilder();
        span.attribute("model.name").ifPresent(name -> details.append("model=")
                .append(span.attribute("model.provider").orElse("?")).append('/').append(name).append("  "));
        for (Map.Entry<String, String> attribute : span.attributes().entrySet()) {
            if (!NOT_REPEATED.contains(attribute.getKey())) {
                details.append(attribute.getKey()).append('=').append(attribute.getValue()).append("  ");
            }
        }
        if (!details.isEmpty()) {
            out.println(String.format("%-7s %-9s %-15s %-12s %s%s", "", "", "", "", "  ".repeat(depth + 1),
                    details.toString().stripTrailing()));
        }
        for (Span child : trace.children(span)) {
            printSpan(out, trace, child, depth + 1);
        }
    }

    public static void printSummary(PrintStream out, Trace trace) {
        List<Span> calls = trace.ofType(SpanType.TOOL_CALL);
        long rejected = calls.stream().filter(span -> span.status() == SpanStatus.REJECTED).count();
        long errors = calls.stream().filter(span -> span.status() == SpanStatus.ERROR).count();
        StopReason reason = StopReason.valueOf(trace.stopReason().orElseThrow());

        out.println("RUN SUMMARY " + trace.traceId());
        out.println("  stop reason:  " + reason + " -- " + explain(reason));
        out.println("  status:       " + trace.root().status());
        out.println("  duration:     " + format(trace.duration()));
        out.println("  model calls:  " + trace.ofType(SpanType.MODEL_CALL).size());
        out.println("  tool calls:   " + calls.size() + " (" + rejected + " rejected, " + errors + " failed)");
        out.println("  tokens:       " + tokens(trace));
    }

    public static void printMetrics(PrintStream out, Metrics metrics) {
        out.println("METRICS (in memory, across all runs above)");
        metrics.snapshot().forEach((name, value) -> out.println(String.format("  %-22s %d", name, value)));
    }

    static String explain(StopReason reason) {
        return switch (reason) {
            case FINAL_ANSWER -> "the model produced a final answer";
            case MAX_STEPS -> "the step budget was reached before a final answer";
            case REJECTED_TOOL_CALL -> "the application rejected the model's tool request";
            case ERROR -> "a model call or an allowed tool failed";
        };
    }

    private static String tokens(Trace trace) {
        var input = trace.reportedTokens("input_tokens");
        var output = trace.reportedTokens("output_tokens");
        if (input.isEmpty() && output.isEmpty()) {
            return "not reported by this model";
        }
        return "input=" + input.map(String::valueOf).orElse("n/a") + " output="
                + output.map(String::valueOf).orElse("n/a")
                + trace.reportedTokens("total_tokens").map(total -> " total=" + total).orElse("");
    }

    static String format(Duration duration) {
        if (duration.toMillis() == 0 && !duration.isZero()) {
            return "<1ms";
        }
        return duration.toMillis() + "ms";
    }
}
