package dev.agentic.handbook.labs.evaluation;

import java.util.List;
import java.util.Optional;

/**
 * The outcome of one bounded run. It doubles as the <em>evaluation trace</em>:
 * why the run stopped, the answer when there is one, how many model decisions
 * were made, every completed tool exchange in order, and — when the run
 * stopped on a rejected request — which request it was and why.
 *
 * <p>Nothing here is telemetry. It is the same plain record shape as Labs 07
 * and 08, captured for a controlled run and judged afterwards. Observability
 * is Milestone 10.
 */
public record AgentRunResult(
        StopReason stopReason,
        Optional<String> answer,
        int modelSteps,
        List<ToolExchange> exchanges,
        Optional<Failure> failure) {

    /** The request that ended the run, and the reason. It never became an observation. */
    public record Failure(ModelDecision.ToolRequest request, String detail) {
    }

    /**
     * Every tool the model asked for, in order: the completed exchanges, then
     * the rejected request that ended the run, if there was one. A trajectory
     * is what the model <em>proposed</em>, not only what was executed.
     */
    public List<ModelDecision.ToolRequest> requests() {
        List<ModelDecision.ToolRequest> requests = new java.util.ArrayList<>();
        for (ToolExchange exchange : exchanges) {
            requests.add(exchange.request());
        }
        failure.ifPresent(f -> requests.add(f.request()));
        return List.copyOf(requests);
    }
}
