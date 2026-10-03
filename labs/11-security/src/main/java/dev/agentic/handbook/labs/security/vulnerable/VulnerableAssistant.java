package dev.agentic.handbook.labs.security.vulnerable;

import dev.agentic.handbook.labs.security.Exchange;
import dev.agentic.handbook.labs.security.HelioPlatform;
import dev.agentic.handbook.labs.security.ModelStep;
import dev.agentic.handbook.labs.security.SimulatedModel;
import dev.agentic.handbook.labs.security.ToolProposal;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>INTENTIONALLY VULNERABLE. Educational and simulated. In memory only.</b>
 *
 * <p>The same agent loop and the same simulated model as the secure path, with the
 * model's proposal treated as a command. It shows the failure that Lab 11's
 * controls prevent: a retrieved note steers the model, and the application runs
 * what the model proposes, then hands the raw result (token included) back.
 */
public final class VulnerableAssistant {

    /** What happened: the answer, and every call the application ran on the model's say-so. */
    public record Run(String answer, List<String> executedCalls) {
    }

    private final HelioPlatform platform;

    public VulnerableAssistant(HelioPlatform platform) {
        this.platform = platform;
    }

    public Run run(String userRequest) {
        SimulatedModel model = new SimulatedModel();
        List<Exchange> history = new ArrayList<>();
        List<String> executed = new ArrayList<>();
        for (int step = 0; step < 6; step++) {
            ModelStep modelStep = model.next(userRequest, List.copyOf(history));
            if (modelStep.proposal().isEmpty()) {
                return new Run(modelStep.answer(), executed);
            }
            ToolProposal proposal = modelStep.proposal().get();
            executed.add(proposal.tool() + " " + proposal.arguments());
            history.add(new Exchange(proposal, String.valueOf(UnsafeToolRunner.run(platform, proposal))));
        }
        return new Run("(step limit reached)", executed);
    }
}
