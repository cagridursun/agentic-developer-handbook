package dev.agentic.handbook.labs.security;

import java.util.Map;

/**
 * The execution layer of one tool: it receives only arguments that already passed every control, and the
 * principal the request acts for, so a downstream system can be called as that principal and not as a
 * more powerful service identity.
 */
@FunctionalInterface
public interface ToolExecutor {

    Map<String, Object> execute(Principal principal, Map<String, Object> validatedArguments);
}
