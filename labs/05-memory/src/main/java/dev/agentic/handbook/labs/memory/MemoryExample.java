package dev.agentic.handbook.labs.memory;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lab 05: memory.
 *
 * <p>A scripted, deterministic demonstration of three ways to handle an
 * earlier interaction: carry nothing, carry everything, or keep explicit
 * application-controlled state. Only the final memory-backed step calls the
 * model; everything worth learning is visible in the prompts the application
 * builds.
 *
 * <p>Conversation history is not memory. Memory is application-controlled
 * state. Forgetting is part of memory design. And this is still not an agent.
 */
public final class MemoryExample {

    /** Same default and override rules as the earlier labs. */
    static final String DEFAULT_MODEL = "gemini-3.8-flash";

    private static final String EARLIER_FACT = "My preferred programming language is Java.";
    private static final String LATER_QUESTION = "What programming language do I prefer?";

    private MemoryExample() {
    }

    public static void main(String[] args) {
        banner("1. NO MEMORY");
        System.out.println("Earlier user message: \"" + EARLIER_FACT + "\"");
        System.out.println("Later question:       \"" + LATER_QUESTION + "\"");
        System.out.println();
        String noMemoryPrompt = buildNoMemoryPrompt(LATER_QUESTION);
        System.out.println(noMemoryPrompt);
        System.out.println("(" + noMemoryPrompt.length() + " characters)");
        System.out.println();
        System.out.println("The earlier fact is simply not in this prompt. The model is not");
        System.out.println("forgetful; the application never carried the fact forward. Every");
        System.out.println("request is bounded by the context supplied with it.");

        banner("2. NAIVE FIX: SEND THE ENTIRE HISTORY");
        List<ConversationTurn> conversation = demoConversation();
        String fullHistoryPrompt = buildFullHistoryPrompt(conversation, LATER_QUESTION);
        System.out.println("Conversation turns:  " + conversation.size());
        System.out.println("History characters:  " + fullHistoryPrompt.length());
        System.out.println();
        System.out.println("The preference is available again, because the transcript contains");
        System.out.println("it. But so is everything else: lunch plans, a build failure, meeting");
        System.out.println("notes. The prompt grows with every turn, forever, even though this");
        System.out.println("question needs exactly one small fact. Carrying a transcript is not");
        System.out.println("the same engineering decision as designing memory.");

        banner("3. EXPLICIT MEMORY");
        SessionMemory memory = new SessionMemory();
        // THE APPLICATION decides what becomes memory. Deliberate, deterministic,
        // and visible - no model extracts this automatically in this lab.
        memory.remember("preferredProgrammingLanguage", "Java");

        String memoryPrompt = buildMemoryPrompt(memory, LATER_QUESTION);
        System.out.println(memoryPrompt);
        System.out.println("(" + memoryPrompt.length() + " characters, vs "
                + fullHistoryPrompt.length() + " with the full transcript)");

        // The single real model call of this lab.
        Optional<String> apiKey = apiKey(System.getenv());
        if (apiKey.isPresent()) {
            String model = model(System.getenv());
            System.out.println();
            System.out.println("Model: " + model);
            Client client = Client.builder().apiKey(apiKey.get()).build();
            GenerateContentResponse response =
                    client.models.generateContent(model, memoryPrompt, null);
            System.out.println("Answer: " + response.text());
        } else {
            System.out.println();
            System.out.println("GOOGLE_API_KEY is not set, so the real generation step is skipped.");
            System.out.println("The demonstration above is complete without it.");
        }

        banner("4. UPDATE, THEN FORGET");
        memory.remember("preferredProgrammingLanguage", "Kotlin");
        System.out.println("After remembering \"Kotlin\" under the same key, the future prompt");
        System.out.println("contains the current value only:");
        System.out.println();
        System.out.println(indent(buildMemoryPrompt(memory, LATER_QUESTION)));

        memory.forget("preferredProgrammingLanguage");
        System.out.println("After forget(\"preferredProgrammingLanguage\"), the entry is gone:");
        System.out.println();
        System.out.println(indent(buildMemoryPrompt(memory, LATER_QUESTION)));
        System.out.println("A memory system that can remember but cannot forget is incomplete.");
    }

    /** Experience 1: only the current question. Nothing survived the earlier turn. */
    static String buildNoMemoryPrompt(String question) {
        return "You are answering the user's current question.\n\n"
                + "Current question:\n" + question + "\n";
    }

    /** Experience 2: the whole transcript travels with every request. */
    static String buildFullHistoryPrompt(List<ConversationTurn> history, String question) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are answering the user's current question.\n\n")
                .append("Conversation so far:\n");
        for (ConversationTurn turn : history) {
            prompt.append(turn.role()).append(": ").append(turn.content()).append("\n");
        }
        prompt.append("\nCurrent question:\n").append(question).append("\n");
        return prompt.toString();
    }

    /**
     * Build: the current question plus the explicit memory snapshot, and
     * nothing else. The entry tags are structure for readability, not a
     * security boundary — remembered text is still untrusted context, and a
     * remembered claim never grants privileges.
     */
    static String buildMemoryPrompt(SessionMemory memory, String question) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are answering the user's current question.\n\n")
                .append("The application has retained the following memory from earlier ")
                .append("interactions.\n\nMemory:\n");
        Map<String, String> snapshot = memory.snapshot();
        if (snapshot.isEmpty()) {
            prompt.append("(no entries)\n");
        } else {
            for (Map.Entry<String, String> entry : snapshot.entrySet()) {
                prompt.append("<entry key=\"").append(entry.getKey()).append("\">\n")
                        .append(entry.getValue())
                        .append("\n</entry>\n");
            }
        }
        prompt.append("\nCurrent question:\n").append(question).append("\n\n")
                .append("Use the memory only when it is relevant. If the memory does not ")
                .append("contain enough information, say so.\n");
        return prompt.toString();
    }

    /**
     * The demo transcript: one durable fact buried in unrelated turns. This is
     * what "just send the history" actually carries forward.
     */
    static List<ConversationTurn> demoConversation() {
        return List.of(
                new ConversationTurn(Role.USER, EARLIER_FACT),
                new ConversationTurn(Role.ASSISTANT, "Noted - Java it is."),
                new ConversationTurn(Role.USER, "The staging deployment failed twice this morning."),
                new ConversationTurn(Role.ASSISTANT, "The canary logs point at a migration ordering issue."),
                new ConversationTurn(Role.USER, "Where should we order lunch for the team today?"),
                new ConversationTurn(Role.ASSISTANT, "The usual place near the office has a Tuesday menu."),
                new ConversationTurn(Role.USER, "Meeting notes: retro moved to Thursday, demo stays Friday."),
                new ConversationTurn(Role.ASSISTANT, "Recorded. Anything else from the meeting?"));
    }

    private static void banner(String title) {
        System.out.println();
        System.out.println("--------------------------------------------------");
        System.out.println(title);
        System.out.println("--------------------------------------------------");
    }

    private static String indent(String text) {
        return "    " + text.replace("\n", "\n    ");
    }

    /** GOOGLE_API_KEY first, legacy GEMINI_API_KEY second — the SDK's documented precedence. */
    static Optional<String> apiKey(Map<String, String> env) {
        return firstNonBlank(env.get("GOOGLE_API_KEY"), env.get("GEMINI_API_KEY"));
    }

    /** Model name from GEMINI_MODEL, or the lab default. */
    static String model(Map<String, String> env) {
        return firstNonBlank(env.get("GEMINI_MODEL")).orElse(DEFAULT_MODEL);
    }

    private static Optional<String> firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
