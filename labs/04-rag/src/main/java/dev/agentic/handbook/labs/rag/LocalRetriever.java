package dev.agentic.handbook.labs.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A deliberately small lexical retriever.
 *
 * <p>This is not production-grade information retrieval, and it does not try
 * to be. For a five-file corpus, counting shared terms is enough to make the
 * RAG architecture visible: retrieve, then augment, then generate. Embeddings
 * and vector stores are one possible retrieval implementation for harder
 * problems (vocabulary mismatch, paraphrase, large corpora) — they are not
 * part of the definition of RAG.
 */
public final class LocalRetriever {

    /** Common words that carry no signal for this corpus. */
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "and", "are", "at", "by", "do", "does", "for", "from",
            "how", "in", "is", "it", "of", "on", "our", "the", "to", "was",
            "what", "when", "where", "which", "who", "why", "with");

    private LocalRetriever() {
    }

    /**
     * Scores every document against the question and returns the best matches.
     *
     * <p>Score = number of distinct question terms found in the document
     * content, plus 2 for each question term that matches the filename.
     * Documents with zero relevance are discarded; ties break alphabetically
     * by source so ranking is fully deterministic.
     */
    public static List<RetrievedDocument> retrieve(
            List<KnowledgeDocument> documents, String question, int topK) {
        Set<String> questionTerms = tokenize(question);

        List<RetrievedDocument> scored = new ArrayList<>();
        for (KnowledgeDocument document : documents) {
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
                scored.add(new RetrievedDocument(document, score));
            }
        }

        scored.sort(Comparator.comparingInt(RetrievedDocument::score).reversed()
                .thenComparing(RetrievedDocument::source));
        return List.copyOf(scored.subList(0, Math.min(topK, scored.size())));
    }

    /**
     * Lowercases, splits on anything that is not a letter or digit, drops
     * stopwords, and crudely singularizes plural terms so "services" matches
     * "service". One line of stemming, not a linguistics library.
     */
    static Set<String> tokenize(String text) {
        Set<String> terms = new HashSet<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.isEmpty() || STOPWORDS.contains(raw)) {
                continue;
            }
            String term = raw.length() > 3 && raw.endsWith("s")
                    ? raw.substring(0, raw.length() - 1)
                    : raw;
            terms.add(term);
        }
        return terms;
    }
}
