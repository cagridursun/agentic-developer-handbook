package dev.agentic.handbook.labs.security;

import java.util.Optional;

/** What the model produces: a proposal, or a final answer. Never an execution. */
public record ModelStep(Optional<ToolProposal> proposal, String answer) {

    static ModelStep propose(ToolProposal proposal) {
        return new ModelStep(Optional.of(proposal), null);
    }

    static ModelStep answer(String answer) {
        return new ModelStep(Optional.empty(), answer);
    }
}
