package dev.agentic.handbook.capstones.agenticsystem.reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Agent Runtime decision of this reference: the Lab 07 bounded loop,
 * unchanged in spirit. The model proposes a final answer or one allowlisted
 * read-only tool call; the runtime validates, executes, observes, and stops —
 * on a final answer, on the step budget, or on a rejected proposal.
 *
 * <p>Kept in one file here because the capstone composes concepts rather than
 * introducing them; see Lab 07 for the concept taught type by type.
 */
public final class Agent {

    /** The model's whole decision space: answer, or one tool request. */
    public sealed interface Decision permits FinalAnswer, ToolRequest {
    }

    public record FinalAnswer(String text) implements Decision {
    }

    public record ToolRequest(String name, Map<String, Object> arguments) implements Decision {
    }

    /** One executed tool round: proposal plus observation. */
    public record ToolExchange(ToolRequest request, Map<String, Object> result) {
    }

    public enum StopReason {
        FINAL_ANSWER,
        MAX_STEPS,
        REJECTED_TOOL_CALL
    }

    /** The outcome of one bounded run. */
    public record RunResult(
            StopReason stopReason,
            Optional<String> answer,
            int modelSteps,
            List<ToolExchange> exchanges,
            Optional<String> rejectionDetail) {
    }

    /**
     * The one seam between loop semantics and provider payloads, established
     * in Lab 07. Implementations are stateful for a single run.
     */
    public interface Model {
        Decision start(String initialContext);

        Decision observe(ToolExchange exchange);
    }

    private final Model model;
    private final int maxSteps;

    public Agent(Model model, int maxSteps) {
        if (maxSteps < 1) {
            throw new IllegalArgumentException("maxSteps must be at least 1.");
        }
        this.model = model;
        this.maxSteps = maxSteps;
    }

    /** Runs the loop. One iteration = one model decision; the bound is visible. */
    public RunResult run(String initialContext) {
        List<ToolExchange> exchanges = new ArrayList<>();
        ToolExchange lastExchange = null;

        for (int step = 1; step <= maxSteps; step++) {
            Decision decision = lastExchange == null
                    ? model.start(initialContext)
                    : model.observe(lastExchange);

            if (decision instanceof FinalAnswer answer) {
                return new RunResult(StopReason.FINAL_ANSWER,
                        Optional.of(answer.text()), step, List.copyOf(exchanges), Optional.empty());
            }

            ToolRequest request = (ToolRequest) decision;
            Map<String, Object> result;
            try {
                result = executeTool(request);
            } catch (IllegalArgumentException rejected) {
                return new RunResult(StopReason.REJECTED_TOOL_CALL,
                        Optional.empty(), step, List.copyOf(exchanges),
                        Optional.of(rejected.getMessage()));
            }

            lastExchange = new ToolExchange(request, result);
            exchanges.add(lastExchange);
        }

        return new RunResult(StopReason.MAX_STEPS,
                Optional.empty(), maxSteps, List.copyOf(exchanges), Optional.empty());
    }

    /**
     * The authorization boundary: an explicit allowlist over the two read-only
     * tools, argument validation before execution, no reflection.
     */
    private static Map<String, Object> executeTool(ToolRequest request) {
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
