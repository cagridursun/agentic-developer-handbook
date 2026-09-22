package dev.agentic.handbook.labs.structuredoutput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Deterministic local behavior: JSON-to-record deserialization, enum handling,
 * application validation, and configuration resolution. The real Gemini call is
 * a manual smoke test, so normal CI never needs an API key or a network.
 */
class StructuredOutputExampleTest {

    @Test
    void parsesARepresentativeStructuredResponse() {
        // JSON in exactly the shape the response schema constrains Gemini to.
        String json = """
                {
                  "title": "Password reset from login page",
                  "summary": "Allow users to reset their password via an emailed temporary link.",
                  "type": "FEATURE",
                  "acceptanceCriteria": [
                    "Reset link is sent by email",
                    "Link expires after 15 minutes"
                  ]
                }
                """;

        DevelopmentTask task = StructuredOutputExample.parse(json);

        assertEquals("Password reset from login page", task.title());
        assertEquals(TaskType.FEATURE, task.type());
        assertEquals(2, task.acceptanceCriteria().size());
        assertEquals("Link expires after 15 minutes", task.acceptanceCriteria().get(1));
    }

    @Test
    void mapsEveryEnumValue() {
        for (TaskType type : TaskType.values()) {
            String json = """
                    {"title":"t","summary":"s","type":"%s","acceptanceCriteria":["a"]}
                    """.formatted(type.name());
            assertEquals(type, StructuredOutputExample.parse(json).type());
        }
    }

    @Test
    void rejectsAnUnknownEnumValue() {
        String json = """
                {"title":"t","summary":"s","type":"EPIC","acceptanceCriteria":["a"]}
                """;
        assertThrows(IllegalStateException.class, () -> StructuredOutputExample.parse(json));
    }

    @Test
    void rejectsTextThatIsNotJson() {
        assertThrows(IllegalStateException.class,
                () -> StructuredOutputExample.parse("Sure! Here is your task: ..."));
    }

    @Test
    void validTaskHasNoProblems() {
        DevelopmentTask task = new DevelopmentTask(
                "Title", "Summary", TaskType.BUG, List.of("One criterion"));
        assertTrue(task.problems().isEmpty());
    }

    @Test
    void schemaValidJsonCanStillFailApplicationValidation() {
        // Structurally fine, semantically useless: this is the boundary the lab
        // teaches. The schema cannot know that a blank title is worthless.
        DevelopmentTask task = StructuredOutputExample.parse("""
                {"title":"  ","summary":"s","type":"BUG","acceptanceCriteria":[]}
                """);

        List<String> problems = task.problems();
        assertEquals(2, problems.size());
        assertTrue(problems.get(0).contains("title"));
        assertTrue(problems.get(1).contains("acceptance criterion"));
    }

    @Test
    void missingFieldsAreCaughtByApplicationValidation() {
        DevelopmentTask task = StructuredOutputExample.parse("""
                {"type":"FEATURE","acceptanceCriteria":["a"]}
                """);
        assertTrue(task.problems().stream().anyMatch(p -> p.contains("title")));
        assertTrue(task.problems().stream().anyMatch(p -> p.contains("summary")));
    }

    @Test
    void apiKeyResolutionMatchesLab01Semantics() {
        assertEquals("primary", StructuredOutputExample.apiKey(Map.of(
                "GOOGLE_API_KEY", "primary",
                "GEMINI_API_KEY", "legacy")).orElseThrow());
        assertTrue(StructuredOutputExample.apiKey(Map.of()).isEmpty());
    }

    @Test
    void modelDefaultsAndOverrides() {
        assertEquals(StructuredOutputExample.DEFAULT_MODEL, StructuredOutputExample.model(Map.of()));
        assertEquals("gemini-3.5-flash-lite",
                StructuredOutputExample.model(Map.of("GEMINI_MODEL", "gemini-3.5-flash-lite")));
    }
}
