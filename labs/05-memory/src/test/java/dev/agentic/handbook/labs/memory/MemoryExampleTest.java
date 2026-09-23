package dev.agentic.handbook.labs.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Deterministic tests for the whole lesson: session memory semantics and the
 * three prompt-building strategies. No model call happens here; what the lab
 * teaches is observable in application-owned prompts.
 */
class MemoryExampleTest {

    private static final String QUESTION = "What programming language do I prefer?";
    private static final String FACT = "My preferred programming language is Java.";

    // --- SessionMemory semantics ---

    @Test
    void rememberStoresAValue() {
        SessionMemory memory = new SessionMemory();
        memory.remember("preferredProgrammingLanguage", "Java");
        assertEquals("Java", memory.get("preferredProgrammingLanguage").orElseThrow());
    }

    @Test
    void rememberingTheSameKeyUpdatesTheValue() {
        SessionMemory memory = new SessionMemory();
        memory.remember("preferredProgrammingLanguage", "Java");
        memory.remember("preferredProgrammingLanguage", "Kotlin");
        assertEquals("Kotlin", memory.get("preferredProgrammingLanguage").orElseThrow());
        assertEquals(1, memory.snapshot().size());
    }

    @Test
    void forgetRemovesAValue() {
        SessionMemory memory = new SessionMemory();
        memory.remember("preferredProgrammingLanguage", "Java");
        memory.forget("preferredProgrammingLanguage");
        assertTrue(memory.get("preferredProgrammingLanguage").isEmpty());
        assertTrue(memory.snapshot().isEmpty());
    }

    @Test
    void snapshotOrderIsInsertionOrderAndDeterministic() {
        SessionMemory memory = new SessionMemory();
        memory.remember("b", "2");
        memory.remember("a", "1");
        memory.remember("c", "3");
        assertEquals(List.of("b", "a", "c"), List.copyOf(memory.snapshot().keySet()));
    }

    @Test
    void blankKeysAndValuesAreRejected() {
        SessionMemory memory = new SessionMemory();
        assertThrows(IllegalArgumentException.class, () -> memory.remember("  ", "Java"));
        assertThrows(IllegalArgumentException.class, () -> memory.remember("key", "  "));
        assertThrows(IllegalArgumentException.class, () -> memory.remember(null, "Java"));
    }

    // --- Experience 1: no memory ---

    @Test
    void noMemoryPromptDoesNotContainTheEarlierFact() {
        String prompt = MemoryExample.buildNoMemoryPrompt(QUESTION);
        assertTrue(prompt.contains(QUESTION));
        assertFalse(prompt.contains("Java"));
    }

    // --- Experience 2: full history ---

    @Test
    void fullHistoryPromptContainsPreviousTurns() {
        List<ConversationTurn> history = List.of(
                new ConversationTurn(Role.USER, FACT),
                new ConversationTurn(Role.ASSISTANT, "Noted."));
        String prompt = MemoryExample.buildFullHistoryPrompt(history, QUESTION);
        assertTrue(prompt.contains(FACT));
        assertTrue(prompt.contains("Noted."));
        assertTrue(prompt.contains(QUESTION));
    }

    @Test
    void fullHistoryPromptGrowsWithEveryUnrelatedTurn() {
        List<ConversationTurn> shortHistory = List.of(
                new ConversationTurn(Role.USER, FACT));
        List<ConversationTurn> longHistory = MemoryExample.demoConversation();

        String shortPrompt = MemoryExample.buildFullHistoryPrompt(shortHistory, QUESTION);
        String longPrompt = MemoryExample.buildFullHistoryPrompt(longHistory, QUESTION);

        assertTrue(longHistory.size() > shortHistory.size());
        assertTrue(longPrompt.length() > shortPrompt.length());
        // The noise is really in there: unrelated turns travel with the question.
        assertTrue(longPrompt.contains("lunch"));
    }

    // --- Build: explicit memory ---

    @Test
    void memoryPromptContainsTheRememberedValue() {
        SessionMemory memory = new SessionMemory();
        memory.remember("preferredProgrammingLanguage", "Java");
        String prompt = MemoryExample.buildMemoryPrompt(memory, QUESTION);
        assertTrue(prompt.contains("<entry key=\"preferredProgrammingLanguage\">"));
        assertTrue(prompt.contains("Java"));
        assertTrue(prompt.contains(QUESTION));
    }

    @Test
    void memoryPromptDoesNotCarryTheTranscript() {
        SessionMemory memory = new SessionMemory();
        memory.remember("preferredProgrammingLanguage", "Java");
        String prompt = MemoryExample.buildMemoryPrompt(memory, QUESTION);
        // None of the unrelated demo conversation leaks into the memory prompt.
        assertFalse(prompt.contains("lunch"));
        assertFalse(prompt.contains("deployment"));
        assertFalse(prompt.contains(FACT));
    }

    @Test
    void memoryPromptIsSmallerThanTheFullHistoryPrompt() {
        SessionMemory memory = new SessionMemory();
        memory.remember("preferredProgrammingLanguage", "Java");
        String memoryPrompt = MemoryExample.buildMemoryPrompt(memory, QUESTION);
        String historyPrompt = MemoryExample.buildFullHistoryPrompt(
                MemoryExample.demoConversation(), QUESTION);
        assertTrue(memoryPrompt.length() < historyPrompt.length());
    }

    @Test
    void updatedMemoryReplacesThePreviousValueInThePrompt() {
        SessionMemory memory = new SessionMemory();
        memory.remember("preferredProgrammingLanguage", "Java");
        memory.remember("preferredProgrammingLanguage", "Kotlin");
        String prompt = MemoryExample.buildMemoryPrompt(memory, QUESTION);
        assertTrue(prompt.contains("Kotlin"));
        assertFalse(prompt.contains("Java"));
    }

    @Test
    void forgottenMemoryIsAbsentFromTheFuturePrompt() {
        SessionMemory memory = new SessionMemory();
        memory.remember("preferredProgrammingLanguage", "Java");
        memory.forget("preferredProgrammingLanguage");
        String prompt = MemoryExample.buildMemoryPrompt(memory, QUESTION);
        assertFalse(prompt.contains("Java"));
        assertTrue(prompt.contains(QUESTION));
    }

    @Test
    void theCurrentQuestionIsAlwaysPresent() {
        SessionMemory empty = new SessionMemory();
        assertTrue(MemoryExample.buildNoMemoryPrompt(QUESTION).contains(QUESTION));
        assertTrue(MemoryExample.buildFullHistoryPrompt(List.of(), QUESTION).contains(QUESTION));
        assertTrue(MemoryExample.buildMemoryPrompt(empty, QUESTION).contains(QUESTION));
    }

    // --- Configuration, same semantics as the earlier labs ---

    @Test
    void apiKeyAndModelResolution() {
        assertEquals("primary", MemoryExample.apiKey(Map.of(
                "GOOGLE_API_KEY", "primary",
                "GEMINI_API_KEY", "legacy")).orElseThrow());
        assertTrue(MemoryExample.apiKey(Map.of()).isEmpty());
        assertEquals(MemoryExample.DEFAULT_MODEL, MemoryExample.model(Map.of()));
        assertEquals("gemini-3.5-flash-lite",
                MemoryExample.model(Map.of("GEMINI_MODEL", "gemini-3.5-flash-lite")));
    }
}
