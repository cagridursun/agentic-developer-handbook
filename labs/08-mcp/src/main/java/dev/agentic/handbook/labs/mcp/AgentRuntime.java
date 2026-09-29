package dev.agentic.handbook.labs.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Lab 07 bounded agent runtime, with one capability moved behind MCP.
 *
 * <p>The loop is unchanged: one iteration is one model decision, the for-loop
 * bound is the step budget, the final answer counts as a decision, and the
 * model never executes anything. What changed is one line of the allowlist:
 * {@code getRecentDeployment} is now routed to the application's MCP client
 * instead of a local method — and a remote call can fail in ways a local
 * method did not, so there is one new stop reason.
 *
 * <p>MCP did not create this agent, and it does not replace this runtime. The
 * runtime still owns which tools exist for the model, validation, execution,
 * the budget, and stopping.
 */
public final class AgentRuntime {

    private final AgentModel model;
    private final int maxSteps;
    private final DeploymentMcpClient deployments;

    public AgentRuntime(AgentModel model, int maxSteps, DeploymentMcpClient deployments) {
        if (maxSteps < 1) {
            throw new IllegalArgumentException("maxSteps must be at least 1.");
        }
        this.model = model;
        this.maxSteps = maxSteps;
        this.deployments = deployments;
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
            try {
                lastExchange = executeTool(request);
            } catch (IllegalArgumentException rejected) {
                // The model proposed something the application does not allow.
                // Stop safely instead of guessing what was meant.
                return stop(StopReason.REJECTED_TOOL_CALL, step, exchanges, request, rejected.getMessage());
            } catch (RemoteToolException failed) {
                // An allowed request crossed the MCP boundary and failed there.
                // No retry, no invented result: the failure is the outcome.
                return stop(StopReason.TOOL_FAILED, step, exchanges, request, failed.getMessage());
            }
            exchanges.add(lastExchange);
        }

        // The budget is exhausted. No further model call happens.
        return new AgentRunResult(StopReason.MAX_STEPS,
                Optional.empty(), maxSteps, List.copyOf(exchanges), Optional.empty());
    }

    /**
     * The application tool boundary. Model-generated tool names and arguments
     * are untrusted input: the name must be on this switch, the arguments must
     * validate, and only then does the application decide where the call runs.
     * The model's request looks the same either way.
     */
    private ToolExchange executeTool(ModelDecision.ToolRequest request) {
        return switch (request.name()) {
            case "getServiceStatus" -> new ToolExchange(request, ToolExchange.Route.LOCAL,
                    ServiceStatusTool.getServiceStatus(requiredServiceName(request.arguments())));
            case "getRecentDeployment" -> new ToolExchange(request, ToolExchange.Route.MCP,
                    deployments.getRecentDeployment(requiredServiceName(request.arguments())));
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

    private static AgentRunResult stop(StopReason reason, int step, List<ToolExchange> exchanges,
            ModelDecision.ToolRequest request, String detail) {
        return new AgentRunResult(reason, Optional.empty(), step, List.copyOf(exchanges),
                Optional.of(new AgentRunResult.Failure(request, detail)));
    }
}
