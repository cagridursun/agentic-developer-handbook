package dev.agentic.handbook.labs.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Argument validation, independent of authorization. Every declared argument is
 * required; an argument the tool does not declare is a violation (so a proposal
 * cannot smuggle in a field such as {@code approved} or {@code principal}); and
 * each value must satisfy its rule.
 */
public final class ArgumentValidator {

    /** The result of validation: the violations, and the arguments in a stable order if there were none. */
    public record Result(List<String> violations, Map<String, Object> arguments) {

        public Result {
            violations = List.copyOf(violations);
            arguments = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(arguments));
        }

        public boolean valid() {
            return violations.isEmpty();
        }
    }

    private ArgumentValidator() {
    }

    public static Result validate(ToolDefinition tool, Map<String, Object> arguments) {
        List<String> violations = new ArrayList<>();
        Set<String> declared = new TreeSet<>();
        for (ArgumentRule rule : tool.arguments()) {
            declared.add(rule.name());
        }
        for (ArgumentRule rule : tool.arguments()) {
            if (!arguments.containsKey(rule.name())) {
                violations.add(rule.name() + " is required");
            } else {
                rule.violation(arguments.get(rule.name())).ifPresent(violations::add);
            }
        }
        for (String name : new TreeSet<>(arguments.keySet())) {
            if (!declared.contains(name)) {
                violations.add("unexpected argument " + name);
            }
        }
        return new Result(violations, violations.isEmpty() ? arguments : Map.of());
    }
}
