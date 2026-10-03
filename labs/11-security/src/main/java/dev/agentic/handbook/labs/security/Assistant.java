package dev.agentic.handbook.labs.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A minimal agent loop (Lab 07's shape, much smaller): ask the model, send each
 * proposal to the gateway, tell the model only what the gateway returned, and stop
 * on an answer or after a fixed number of steps. The loop has no authority: every
 * proposal goes through {@link ToolGateway}.
 */
public final class Assistant {

    public static final int MAX_STEPS = 6;

    /** The outcome of one run: the answer, every gateway result, and the trace. */
    public record Run(String answer, List<GatewayResult> results, Trace trace) {
    }

    private final AssistantModel model;
    private final ToolGateway gateway;

    public Assistant(AssistantModel model, ToolGateway gateway) {
        this.model = model;
        this.gateway = gateway;
    }

    public Run run(Optional<Principal> principal, String userRequest, TraceRecorder trace) {
        Span root = trace.begin(null, SpanType.AGENT_RUN, "assistant run");
        trace.put(root, "principal", principal.map(Principal::id).orElse("none"));
        List<Exchange> history = new ArrayList<>();
        List<GatewayResult> results = new ArrayList<>();
        String answer = null;
        String stopReason = "MAX_STEPS";
        for (int step = 0; step < MAX_STEPS; step++) {
            ModelStep modelStep = model.next(userRequest, List.copyOf(history));
            if (modelStep.proposal().isEmpty()) {
                answer = modelStep.answer();
                stopReason = "FINAL_ANSWER";
                break;
            }
            ToolProposal proposal = modelStep.proposal().get();
            Span decision = trace.begin(root, SpanType.AGENT_DECISION, "propose " + proposal.tool());
            trace.put(decision, "claimed_justification", String.valueOf(proposal.justification()));
            trace.end(decision, SpanStatus.OK);
            GatewayResult result = gateway.invoke(new RunContext(principal, trace, root), proposal);
            results.add(result);
            history.add(new Exchange(proposal, result.observation()));
        }
        trace.put(root, "stop_reason", stopReason);
        trace.end(root, SpanStatus.OK);
        return new Run(answer, results, trace.trace());
    }
}
