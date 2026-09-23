package dev.agentic.handbook.labs.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Deterministic tests for skill loading, the drift demonstration, and prompt
 * construction. No model call: the lesson is provable from application-owned
 * data, and CI needs no API key and no network.
 */
class SkillsExampleTest {

    // --- Skill loading ---

    @Test
    void incidentHandoffSkillLoads() {
        Skill skill = SkillLoader.load("incident-handoff");
        assertEquals("incident-handoff", skill.name());
        assertFalse(skill.description().isBlank());
        assertTrue(skill.description().contains("Use when"),
                "description should say when to use the skill");
        assertTrue(skill.instructions().contains("Separate observed facts from hypotheses"));
    }

    @Test
    void metadataStageDoesNotExposeInstructions() {
        SkillMetadata metadata = SkillLoader.metadata("incident-handoff");
        assertEquals("incident-handoff", metadata.name());
        assertEquals(SkillLoader.load("incident-handoff").description(), metadata.description());
        // SkillMetadata has no instructions field at all: stage 1 is metadata only.
    }

    @Test
    void bothBundledSkillsAreLoadable() {
        for (String name : SkillLoader.AVAILABLE_SKILLS) {
            Skill skill = SkillLoader.load(name);
            assertEquals(name, skill.name());
        }
    }

    @Test
    void unknownSkillIsRejected() {
        assertThrows(IllegalStateException.class, () -> SkillLoader.load("no-such-skill"));
    }

    // --- Parser contract (the lab subset of the Agent Skills format) ---

    @Test
    void missingFrontmatterIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> SkillLoader.parse("x", "# Just markdown, no frontmatter\n"));
    }

    @Test
    void unclosedFrontmatterIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> SkillLoader.parse("x", "---\nname: x\ndescription: d\n# body\n"));
    }

    @Test
    void missingNameIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> SkillLoader.parse("x", "---\ndescription: d\n---\nbody\n"));
    }

    @Test
    void missingDescriptionIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> SkillLoader.parse("x", "---\nname: x\n---\nbody\n"));
    }

    @Test
    void nameMustMatchDirectory() {
        assertThrows(IllegalStateException.class,
                () -> SkillLoader.parse("other-dir", "---\nname: x\ndescription: d\n---\nbody\n"));
    }

    @Test
    void specNamingRulesAreEnforced() {
        // Uppercase, edge hyphens, and consecutive hyphens are all invalid.
        for (String bad : List.of("Bad-Name", "-x", "x-", "a--b")) {
            assertThrows(IllegalStateException.class,
                    () -> SkillLoader.parse(bad, "---\nname: " + bad + "\ndescription: d\n---\nbody\n"),
                    bad);
        }
    }

    @Test
    void emptyBodyIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> SkillLoader.parse("x", "---\nname: x\ndescription: d\n---\n\n"));
    }

    // --- Experience 1: the vague prompt ---

    @Test
    void vaguePromptContainsFactsButNoProcedure() {
        String prompt = SkillsExample.buildVaguePrompt(SkillsExample.INCIDENT_FACTS);
        assertTrue(prompt.contains("Checkout latency"));
        assertFalse(prompt.contains("Separate observed facts from hypotheses"));
    }

    // --- Naive fix and drift ---

    @Test
    void copiedProcedurePromptContainsEveryRule() {
        String prompt = SkillsExample.buildProcedurePrompt(
                SkillsExample.PROCEDURE_A, SkillsExample.INCIDENT_FACTS);
        for (String rule : SkillsExample.PROCEDURE_A) {
            assertTrue(prompt.contains(rule), rule);
        }
    }

    @Test
    void driftIsDeterministicallyVisible() {
        assertEquals(6, SkillsExample.PROCEDURE_A.size());
        assertEquals(5, SkillsExample.PROCEDURE_B.size());
        List<String> missing = SkillsExample.missingRules(
                SkillsExample.PROCEDURE_A, SkillsExample.PROCEDURE_B);
        assertEquals(List.of("Separate observed facts from hypotheses."), missing);
    }

    @Test
    void driftedPromptLacksTheLostRule() {
        String promptB = SkillsExample.buildProcedurePrompt(
                SkillsExample.PROCEDURE_B, SkillsExample.INCIDENT_FACTS);
        assertFalse(promptB.contains("Separate observed facts from hypotheses."));
        assertTrue(promptB.contains("Do not invent a root cause."));
    }

    // --- Build: the skill-backed prompt ---

    @Test
    void skillPromptContainsInstructionsAndFacts() {
        Skill skill = SkillLoader.load("incident-handoff");
        String prompt = SkillsExample.buildSkillPrompt(skill, SkillsExample.INCIDENT_FACTS);
        assertTrue(prompt.contains("<skill name=\"incident-handoff\">"));
        assertTrue(prompt.contains("Separate observed facts from hypotheses"));
        assertTrue(prompt.contains("Checkout latency"));
    }

    @Test
    void skillPromptDoesNotContainTheOtherSkillsInstructions() {
        // Progressive disclosure: selecting incident-handoff never loads the
        // release-summary body into the final prompt.
        Skill skill = SkillLoader.load("incident-handoff");
        String prompt = SkillsExample.buildSkillPrompt(skill, SkillsExample.INCIDENT_FACTS);
        Skill other = SkillLoader.load("release-summary");
        assertFalse(prompt.contains(other.instructions().lines().skip(2).findFirst().orElseThrow()));
        assertFalse(prompt.contains("release-summary"));
    }

    @Test
    void selectedSkillIdentityIsVisibleInThePrompt() {
        Skill skill = SkillLoader.load("incident-handoff");
        String prompt = SkillsExample.buildSkillPrompt(skill, SkillsExample.INCIDENT_FACTS);
        assertTrue(prompt.contains("name=\"incident-handoff\""));
    }

    // --- Configuration, same semantics as the earlier labs ---

    @Test
    void apiKeyAndModelResolution() {
        assertEquals("primary", SkillsExample.apiKey(Map.of(
                "GOOGLE_API_KEY", "primary",
                "GEMINI_API_KEY", "legacy")).orElseThrow());
        assertTrue(SkillsExample.apiKey(Map.of()).isEmpty());
        assertEquals(SkillsExample.DEFAULT_MODEL, SkillsExample.model(Map.of()));
        assertEquals("gemini-3.5-flash-lite",
                SkillsExample.model(Map.of("GEMINI_MODEL", "gemini-3.5-flash-lite")));
    }
}
