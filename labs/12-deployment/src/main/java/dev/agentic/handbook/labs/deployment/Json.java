package dev.agentic.handbook.labs.deployment;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** Just enough JSON to write responses and log lines: maps, lists, strings, numbers, booleans. */
final class Json {

    private Json() {
    }

    static String write(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> quote(String.valueOf(entry.getKey())) + ":" + write(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Collection<?> items) {
            return items.stream().map(Json::write).collect(Collectors.joining(",", "[", "]"));
        }
        return quote(value.toString());
    }

    /** Escapes quotes, backslashes, and control characters, so a value can never break out of its string. */
    static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
