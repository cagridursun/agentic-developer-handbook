package dev.agentic.handbook.labs.agentruntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The bounded agent runtime: the smallest loop this handbook is willing to
 * call an agent.
 *
 * <p>The model suggests the next step. The runtime owns execution and
 * stopping. Each iteration is one model decision — either a final answer,
 * which ends the run, or one tool request, which the runtime validates
 * against an explicit allowlist, executes as plain Java, and feeds back as an
 * observation. The for-loop bound is the step budget: the run can never make
 * more model decisions than the application configured, and the final answer
 * counts as a decision.
 *
 * <p>Autonomy is bounded by application controls: the model cannot add tools,
 * exceed the budget, or execute anything itself.
 */
public final class AgentRuntime {

    private final AgentModel model;
    private final int maxSteps;

    public AgentRuntime(AgentModel model, int maxSteps) {
        if (maxSteps < 1) {
            throw new IllegalArgumentException("maxSteps must be at least 1.");
        }
        this.model = model;
        this.maxSteps = maxSteps;
    }

    /** Runs the loop for one goal, always terminating within maxSteps model decisions. */
    public AgentRunResult run(String goal) {
        List<ToolExchange> exchanges = new ArrayList<>();
        ToolExchange lastExchange = null;

        // One iteration = one model decision. The bound is visible right here.
        for (int step = 1; step <= maxSteps; step++) {
            ModelDecision decision = lastExchange == null
                    ? model.start(goal)
                    : model.observe(lastExchange);

            if (decision instanceof ModelDecision.FinalAnswer answer) {
                return new AgentRunResult(StopReason.FINAL_ANSWER,
                        Optional.of(answer.text()), step, List.copyOf(exchanges), Optional.empty());
            }

            ModelDecision.ToolRequest request = (ModelDecision.ToolRequest) decision;
            Map<String, Object> result;
            try {
                result = executeTool(request);
            } catch (IllegalArgumentException rejected) {
                // The model proposed something the application does not allow.
                // Stop safely instead of guessing what was meant.
                return new AgentRunResult(StopReason.REJECTED_TOOL_CALL,
                        Optional.empty(), step, List.copyOf(exchanges),
                        Optional.of(rejected.getMessage()));
            }

            lastExchange = new ToolExchange(request, result);
            exchanges.add(lastExchange);
        }

        // The budget is exhausted. No further model call happens.
        return new AgentRunResult(StopReason.MAX_STEPS,
                Optional.empty(), maxSteps, List.copyOf(exchanges), Optional.empty());
    }

    /**
     * The authorization boundary. Model-generated tool names and arguments are
     * untrusted input: only the tools listed in this switch can run, arguments
     * are validated before execution, and nothing is ever resolved through
     * reflection.
     */
    private static Map<String, Object> executeTool(ModelDecision.ToolRequest request) {
        String serviceName = requiredServiceName(request.arguments());
        return switch (request.name()) {
            case "getServiceStatus" -> ServiceTools.getServiceStatus(serviceName);
            case "getRecentDeployment" -> ServiceTools.getRecentDeployment(serviceName);
            default -> throw new IllegalArgumentException(
                    "Tool '" + request.name() + "' is not on this application's allowlist.");
        };
    }

    private static String requiredServiceName(Map<String, Object> arguments) {
        if (arguments == null || !arguments.containsKey("serviceName")) {
            throw new IllegalArgumentException("Required argument 'serviceName' is missing.");
        }
        if (arguments.size() > 1) {
            throw new IllegalArgumentException(
                    "Unexpected arguments beyond 'serviceName': " + arguments.keySet());
        }
        if (!(arguments.get("serviceName") instanceof String serviceName) || serviceName.isBlank()) {
            throw new IllegalArgumentException("'serviceName' must be a non-blank string.");
        }
        return serviceName;
    }
}
