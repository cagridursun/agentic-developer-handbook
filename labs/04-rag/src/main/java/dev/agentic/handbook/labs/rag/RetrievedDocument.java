package dev.agentic.handbook.labs.rag;

/**
 * A document the retriever selected for the current question, with the score
 * that ranked it. The score is application data: provenance never comes from
 * a model claim.
 */
public record RetrievedDocument(
        KnowledgeDocument document,
        int score) {

    public String source() {
        return document.source();
    }
}
