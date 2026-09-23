package dev.agentic.handbook.labs.agentruntime;

import java.util.List;
import java.util.Optional;

/**
 * The outcome of one bounded run: why it stopped, the answer when there is
 * one, how many model decisions were made, and every tool exchange that
 * happened, in order. Deliberately a plain record, not an event framework.
 */
public record AgentRunResult(
        StopReason stopReason,
        Optional<String> answer,
        int modelSteps,
        List<ToolExchange> exchanges,
        Optional<String> rejectionDetail) {
}
