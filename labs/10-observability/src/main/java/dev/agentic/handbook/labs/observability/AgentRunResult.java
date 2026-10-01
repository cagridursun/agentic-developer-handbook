package dev.agentic.handbook.labs.observability;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The outcome of one bounded run, as in Lab 09: why the run stopped, the answer
 * when there is one, how many model decisions were made, and every completed
 * tool exchange.
 *
 * <p>This is what the caller gets. It says <em>that</em> a run stopped and
 * roughly how; it does not say what happened inside. That is the gap the trace
 * fills.
 *
 * @param failure the tool request that ended the run without becoming an
 *                observation (rejected, or failed while executing)
 * @param error   for {@link StopReason#ERROR}: the exception type and its
 *                redacted message
 */
public record AgentRunResult(
        StopReason stopReason,
        Optional<String> answer,
        int modelSteps,
        List<ToolExchange> exchanges,
        Optional<Failure> failure,
        Optional<String> error) {

    /** The request that ended the run, and the reason. It never became an observation. */
    public record Failure(ModelDecision.ToolRequest request, String detail) {
    }

    /** Every tool the model asked for, in order: completed exchanges, then the request that ended the run. */
    public List<ModelDecision.ToolRequest> requests() {
        List<ModelDecision.ToolRequest> requests = new ArrayList<>();
        for (ToolExchange exchange : exchanges) {
            requests.add(exchange.request());
        }
        failure.ifPresent(f -> requests.add(f.request()));
        return List.copyOf(requests);
    }
}
