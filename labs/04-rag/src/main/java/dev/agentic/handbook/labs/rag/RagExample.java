package dev.agentic.handbook.labs.rag;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lab 04: retrieve, augment, generate.
 *
 * <p>The application retrieves relevant local documentation, places it into
 * the prompt, and asks the model to answer from that context. Retrieval is
 * application code and runs before inference; the model never chooses what to
 * read.
 *
 * <p>RAG is not a vector database, and this is still not an agent.
 */
public final class RagExample {

    /** Same default and override rules as the earlier labs. */
    static final String DEFAULT_MODEL = "gemini-3.8-flash";

    /** How many documents the prompt may contain. Small on purpose. */
    static final int TOP_K = 2;

    /**
     * The corpus manifest. The files live on the classpath under /knowledge
     * so the lab runs identically from the repository root, CI, or an IDE.
     * The corpus is fictional; see the files themselves.
     */
    private static final List<String> KNOWLEDGE_SOURCES = List.of(
            "architecture.md",
            "authentication.md",
            "coding-guidelines.md",
            "deployment.md",
            "services.md");

    private RagExample() {
    }

    public static void main(String[] args) {
        Map<String, String> env = System.getenv();

        String question = args.length > 0
                ? String.join(" ", args)
                : "How does authentication work?";

        System.out.println("Question: " + question);
        System.out.println();

        // 1. RETRIEVE. Load the local corpus and select relevant documents.
        //    This is plain application code; no model, no API key involved.
        List<KnowledgeDocument> knowledge = loadKnowledge();
        List<RetrievedDocument> retrieved = LocalRetriever.retrieve(knowledge, question, TOP_K);

        // The source list is application data, printed before any model call.
        if (retrieved.isEmpty()) {
            System.out.println("No relevant project documentation was found for this question.");
            System.out.println("Not calling the model: there is nothing to ground an answer in.");
            return;
        }
        System.out.println("Retrieved sources:");
        for (RetrievedDocument doc : retrieved) {
            System.out.println("- " + doc.source() + " (score: " + doc.score() + ")");
        }
        System.out.println();

        // 2. AUGMENT. Build one explicit prompt: instructions, the question,
        //    and only the selected documents, each labeled with its source.
        String prompt = buildPrompt(question, retrieved);

        // 3. GENERATE. Only now does the model matter, so only now is the API
        //    key required. One ordinary call, exactly like Lab 01 — the only
        //    difference is what the prompt contains.
        Optional<String> apiKey = apiKey(env);
        if (apiKey.isEmpty()) {
            System.err.println("No API key found.");
            System.err.println("Set the GOOGLE_API_KEY environment variable "
                    + "(create a key at https://aistudio.google.com/apikey).");
            System.exit(1);
        }
        String model = model(env);
        System.out.println("Model: " + model);
        System.out.println();

        Client client = Client.builder().apiKey(apiKey.get()).build();
        GenerateContentResponse response = client.models.generateContent(model, prompt, null);

        System.out.println("Answer:");
        System.out.println(response.text());
    }

    /** Loads the fictional corpus from the classpath. */
    static List<KnowledgeDocument> loadKnowledge() {
        List<KnowledgeDocument> documents = new ArrayList<>();
        for (String source : KNOWLEDGE_SOURCES) {
            String path = "/knowledge/" + source;
            try (InputStream stream = RagExample.class.getResourceAsStream(path)) {
                if (stream == null) {
                    throw new IllegalStateException("Missing knowledge file on classpath: " + path);
                }
                documents.add(new KnowledgeDocument(
                        source, new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new IllegalStateException("Could not read knowledge file: " + path, e);
            }
        }
        return List.copyOf(documents);
    }

    /**
     * The augmentation step: instructions, question, and retrieved context,
     * clearly separated. The document tags are structure for readability, not
     * a security boundary — retrieved text stays untrusted input either way.
     */
    static String buildPrompt(String question, List<RetrievedDocument> retrieved) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are answering a question about the Helio platform using the ")
                .append("supplied project documentation.\n\n")
                .append("Use only the provided context when answering, and mention which ")
                .append("source file the information comes from. If the context does not ")
                .append("contain enough information, say that the available documentation ")
                .append("is insufficient.\n\n")
                .append("Question:\n")
                .append(question)
                .append("\n\nRetrieved context:\n");
        for (RetrievedDocument doc : retrieved) {
            prompt.append("\n<document source=\"").append(doc.source()).append("\">\n")
                    .append(doc.document().content().strip())
                    .append("\n</document>\n");
        }
        return prompt.toString();
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
