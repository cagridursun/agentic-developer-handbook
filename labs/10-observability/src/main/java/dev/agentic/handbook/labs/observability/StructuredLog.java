package dev.agentic.handbook.labs.observability;

import java.time.InstantSource;
import java.util.function.Consumer;

/**
 * Structured log lines: a timestamp, a level, the trace and span they belong
 * to, an event name, and key=value fields. The trace and span ids are what turn
 * a line from "something happened" into "something happened in this run".
 *
 * <p>Few lines on purpose: one per completed tool call, one per failure, one
 * at the end of the run. Every field value goes through the {@link Redactor}.
 */
public final class StructuredLog {

    public enum Level { INFO, WARN, ERROR }

    private final InstantSource clock;
    private final Redactor redactor;
    private final Consumer<String> sink;

    StructuredLog(InstantSource clock, Redactor redactor, Consumer<String> sink) {
        this.clock = clock;
        this.redactor = redactor;
        this.sink = sink;
    }

    /** @param fields alternating keys and values */
    void log(Level level, String traceId, String spanId, String event, String... fields) {
        if (fields.length % 2 != 0) {
            throw new IllegalArgumentException("fields must be key/value pairs");
        }
        StringBuilder line = new StringBuilder()
                .append(clock.instant()).append(' ').append(level)
                .append(" trace=").append(traceId)
                .append(" span=").append(spanId)
                .append(" event=").append(event);
        for (int i = 0; i < fields.length; i += 2) {
            line.append(' ').append(fields[i]).append('=').append(quoted(redactor.value(fields[i], fields[i + 1])));
        }
        sink.accept(line.toString());
    }

    private static String quoted(String value) {
        if (value.isEmpty() || value.chars().anyMatch(c -> Character.isWhitespace(c) || c == '"' || c == '=')) {
            return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }
        return value;
    }
}
