package dev.agentic.handbook.labs.skills;

/**
 * A fully loaded skill: metadata plus the complete instruction body. A skill
 * is reusable procedure, not a capability — loading one gives the application
 * nothing to execute, only instructions to place into a model's context.
 */
public record Skill(
        String name,
        String description,
        String instructions) {
}
