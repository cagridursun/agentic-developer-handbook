package dev.agentic.handbook.labs.evaluation;

import java.util.function.Supplier;

/**
 * A version of the system under evaluation: a name, and a way to get a fresh
 * model for each case (models are stateful for one run). The runtime, the
 * cases, and the checks are the same for every version — that is what makes
 * two versions comparable.
 */
public record SystemVersion(String name, String description, Supplier<AgentModel> models) {
}
