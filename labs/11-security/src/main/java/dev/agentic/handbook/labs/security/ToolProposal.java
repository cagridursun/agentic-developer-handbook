package dev.agentic.handbook.labs.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the model proposes: a tool name, arguments, and the model's own account
 * of why. The justification is recorded as a claim and never read by any
 * control: a more convincing justification changes no decision.
 */
public record ToolProposal(String tool, Map<String, Object> arguments, String justification) {

    public ToolProposal {
        // Insertion order is kept (Map.copyOf would reorder per JVM run), so output is repeatable.
        arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }

    /** A proposal with arguments given as alternating names and values, in that order. */
    public static ToolProposal call(String tool, String justification, Object... namesAndValues) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            arguments.put((String) namesAndValues[i], namesAndValues[i + 1]);
        }
        return new ToolProposal(tool, arguments, justification);
    }
}
