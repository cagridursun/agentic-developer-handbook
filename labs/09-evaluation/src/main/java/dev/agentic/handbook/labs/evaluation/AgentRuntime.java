package dev.agentic.handbook.labs.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Lab 07 bounded agent runtime, carried over as the system under
 * evaluation. The loop, the step budget, and the allowlist are unchanged; the
 * only addition is that a rejected request is recorded in the result, so the
 * trace shows what the model proposed.
 *
 * <p>Educational duplication: this lab does not import Lab 07, so it stays
 * readable on its own. Evaluation sits <em>around</em> a system; it does not
 * change how the system works.
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

        for (int step = 1; step <= maxSteps; step++) {
            ModelDecision decision = lastExchange == null
                    ? model.start(goal)
                    : model.observe(lastExchange);

            if (decision instanceof ModelDecision.FinalAnswer answer) {
                return new AgentRunResult(StopReason.FINAL_ANSWER,
                        Optional.of(answer.text()), step, List.copyOf(exchanges), Optional.empty());
            }

            ModelDecision.ToolRequest request = (ModelDecision.ToolRequest) decision;
            try {
                lastExchange = new ToolExchange(request, executeTool(request));
            } catch (IllegalArgumentException rejected) {
                // Not on the allowlist, malformed arguments, or an unknown
                // service: stop safely instead of guessing what was meant.
                return new AgentRunResult(StopReason.REJECTED_TOOL_CALL, Optional.empty(), step,
                        List.copyOf(exchanges),
                        Optional.of(new AgentRunResult.Failure(request, rejected.getMessage())));
            }
            exchanges.add(lastExchange);
        }

        return new AgentRunResult(StopReason.MAX_STEPS,
                Optional.empty(), maxSteps, List.copyOf(exchanges), Optional.empty());
    }

    /** The authorization boundary: only these tools run, and arguments are validated first. */
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
