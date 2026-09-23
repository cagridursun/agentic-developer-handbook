package dev.agentic.handbook.labs.skills;

/**
 * The first stage of progressive disclosure: only name and description. This
 * is what an application (or later, a runtime) sees when deciding whether a
 * skill is relevant, before spending context on the full instruction body.
 */
public record SkillMetadata(
        String name,
        String description) {
}
