package dev.agentic.handbook.labs.security;

import java.util.List;
import java.util.Set;

/**
 * What the application knows about a tool, written by the application. The
 * capability and the operation type are classified by what the executor really
 * does, not by what a description says. {@code returnedFields} is an allowlist:
 * any other field the executor returns is dropped before the result goes on.
 */
public record ToolDefinition(
        String name,
        Capability capability,
        OperationType operationType,
        String resourceArgument,
        List<ArgumentRule> arguments,
        Set<String> returnedFields,
        ToolExecutor executor) {

    public ToolDefinition {
        arguments = List.copyOf(arguments);
        returnedFields = Set.copyOf(returnedFields);
    }
}
