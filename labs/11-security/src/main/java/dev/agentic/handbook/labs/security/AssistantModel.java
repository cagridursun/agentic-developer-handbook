package dev.agentic.handbook.labs.security;

import java.util.List;

/** The model's side of the boundary: it sees text and proposes. Nothing else. */
@FunctionalInterface
public interface AssistantModel {

    ModelStep next(String userRequest, List<Exchange> history);
}
