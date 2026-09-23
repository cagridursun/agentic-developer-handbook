package dev.agentic.handbook.capstones.agenticsystem.reference;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Knowledge / RAG decision of this reference: Lab 04 style, application-
 * controlled, executed once BEFORE the agent loop. Retrieval is deliberately
 * not exposed as a tool, so the capstone demonstrates composition without
 * introducing another variation of agentic retrieval.
 */
public final class Knowledge {

    /** One runbook file acting as one retrieval unit. */
    public record Document(String source, String content) {
    }

    /** A document selected for the current goal, with its lexical score. */
    public record Retrieved(Document document, int score) {
        public String source() {
            return document.source();
        }
    }

    static final List<String> SOURCES =
            List.of("notifications.md", "deployment.md", "incident-response.md");

    static final int TOP_K = 2;

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "and", "are", "at", "by", "do", "does", "for", "from",
            "how", "in", "is", "it", "of", "on", "our", "the", "to", "was",
            "what", "when", "where", "which", "who", "why", "with", "after",
            "not", "without");

    private Knowledge() {
    }

    /** Loads the fictional runbook corpus from the classpath. */
    public static List<Document> load() {
        List<Document> documents = new ArrayList<>();
        for (String source : SOURCES) {
            String path = "/knowledge/" + source;
            try (InputStream stream = Knowledge.class.getResourceAsStream(path)) {
                if (stream == null) {
                    throw new IllegalStateException("Missing knowledge file: " + path);
                }
                documents.add(new Document(source,
                        new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new IllegalStateException("Could not read knowledge file: " + path, e);
            }
        }
        return List.copyOf(documents);
    }

    /**
     * The same small deterministic lexical scoring as Lab 04: distinct query
     * terms found in content (+1) and in the filename (+2), zero scores
     * dropped, ties broken alphabetically, top-K kept.
     */
    public static List<Retrieved> retrieve(List<Document> documents, String question, int topK) {
        Set<String> questionTerms = tokenize(question);
        List<Retrieved> scored = new ArrayList<>();
        for (Document document : documents) {
            Set<String> contentTerms = tokenize(document.content());
            Set<String> nameTerms = tokenize(document.source().replace(".md", ""));
            int score = 0;
            for (String term : questionTerms) {
                if (contentTerms.contains(term)) {
                    score += 1;
                }
                if (nameTerms.contains(term)) {
                    score += 2;
                }
            }
            if (score > 0) {
                scored.add(new Retrieved(document, score));
            }
        }
        scored.sort(Comparator.comparingInt(Retrieved::score).reversed()
                .thenComparing(Retrieved::source));
        return List.copyOf(scored.subList(0, Math.min(topK, scored.size())));
    }

    static Set<String> tokenize(String text) {
        Set<String> terms = new HashSet<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.isEmpty() || STOPWORDS.contains(raw)) {
                continue;
            }
            terms.add(raw.length() > 3 && raw.endsWith("s")
                    ? raw.substring(0, raw.length() - 1)
                    : raw);
        }
        return terms;
    }
}
