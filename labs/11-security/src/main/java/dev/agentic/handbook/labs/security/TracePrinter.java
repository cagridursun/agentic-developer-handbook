package dev.agentic.handbook.labs.security;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

/** Prints a trace as an indented tree: what a person reads to see what the controls decided. */
public final class TracePrinter {

    private TracePrinter() {
    }

    public static void print(PrintStream out, Trace trace) {
        out.println("TRACE " + trace.traceId());
        Map<String, Integer> depth = new HashMap<>();
        for (Span span : trace.spans()) {
            int level = span.parentSpanId().map(parent -> depth.get(parent) + 1).orElse(0);
            depth.put(span.spanId(), level);
            out.println("  ".repeat(level) + span.spanId() + " " + span.type() + " " + span.name()
                    + "  [" + span.status() + "]");
            span.attributes().forEach((key, value) -> out.println("  ".repeat(level) + "      " + key + "=" + value));
        }
    }
}
