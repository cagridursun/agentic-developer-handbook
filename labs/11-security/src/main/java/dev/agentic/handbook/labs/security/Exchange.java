package dev.agentic.handbook.labs.security;

/** One proposal and what the model was told about it afterwards. */
public record Exchange(ToolProposal proposal, String observation) {
}
