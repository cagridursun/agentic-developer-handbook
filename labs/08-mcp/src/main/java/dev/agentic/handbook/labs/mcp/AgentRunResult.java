package dev.agentic.handbook.labs.mcp;

import java.util.List;
import java.util.Optional;

/**
 * The outcome of one bounded run: why it stopped, the answer when there is
 * one, how many model decisions were made, every completed tool exchange in
 * order, and — when the run stopped on a rejected or failed tool call — which
 * request it was and why.
 */
public record AgentRunResult(
        StopReason stopReason,
        Optional<String> answer,
        int modelSteps,
        List<ToolExchange> exchanges,
        Optional<Failure> failure) {

    /** The request that ended the run, and the reason. It was never turned into an observation. */
    public record Failure(ModelDecision.ToolRequest request, String detail) {
    }
}
