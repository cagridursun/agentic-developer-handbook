package dev.agentic.handbook.labs.rag;

/**
 * One retrieval unit: a whole documentation file. In this introductory lab a
 * document is not chunked; the file is small enough to be selected as a unit.
 */
public record KnowledgeDocument(
        String source,
        String content) {
}
