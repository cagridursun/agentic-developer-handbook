package dev.agentic.handbook.labs.security;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * One rule about one argument of one tool. This is validation: is the value
 * well formed for this tool? It never asks whether the caller may use it; that
 * is authorization, and a well-formed value can still be unauthorized. A
 * violation message names the rule and never echoes the value, so a bad value
 * cannot carry something sensitive into a message.
 */
public record ArgumentRule(String name, Kind kind, Pattern pattern, Set<String> allowed, int min, int max) {

    public enum Kind { TEXT_PATTERN, ONE_OF, INTEGER_RANGE }

    public static ArgumentRule pattern(String name, String regex) {
        return new ArgumentRule(name, Kind.TEXT_PATTERN, Pattern.compile(regex), Set.of(), 0, 0);
    }

    public static ArgumentRule oneOf(String name, String... values) {
        return new ArgumentRule(name, Kind.ONE_OF, null, Set.of(values), 0, 0);
    }

    public static ArgumentRule integer(String name, int min, int max) {
        return new ArgumentRule(name, Kind.INTEGER_RANGE, null, Set.of(), min, max);
    }

    /** Empty if the value satisfies the rule, else a message that does not repeat the value. */
    Optional<String> violation(Object value) {
        return switch (kind) {
            case TEXT_PATTERN -> value instanceof String text && pattern.matcher(text).matches()
                    ? Optional.empty()
                    : Optional.of(name + " must be text matching " + pattern.pattern());
            case ONE_OF -> value instanceof String text && allowed.contains(text)
                    ? Optional.empty()
                    : Optional.of(name + " must be one of " + List.copyOf(new TreeSet<>(allowed)));
            case INTEGER_RANGE -> isInteger(value) && ((Number) value).longValue() >= min
                    && ((Number) value).longValue() <= max
                    ? Optional.empty()
                    : Optional.of(name + " must be an integer between " + min + " and " + max);
        };
    }

    private static boolean isInteger(Object value) {
        return value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte;
    }
}
