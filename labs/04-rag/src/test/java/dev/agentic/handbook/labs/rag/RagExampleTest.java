package dev.agentic.handbook.labs.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Deterministic tests for the whole local half of the RAG pipeline: loading,
 * retrieval, ranking, and prompt augmentation. The Gemini call is a manual
 * smoke test; CI needs no API key and no network.
 */
class RagExampleTest {

    // --- Knowledge loading ---

    @Test
    void knowledgeCorpusLoads() {
        List<KnowledgeDocument> docs = RagExample.loadKnowledge();
        assertEquals(5, docs.size());
        List<String> sources = docs.stream().map(KnowledgeDocument::source).toList();
        assertTrue(sources.contains("authentication.md"));
        assertTrue(sources.contains("deployment.md"));
        for (KnowledgeDocument doc : docs) {
            assertFalse(doc.content().isBlank(), doc.source());
        }
    }

    // --- Retrieval ---

    @Test
    void authenticationQuestionRanksAuthenticationDocFirst() {
        List<RetrievedDocument> retrieved = retrieve("How does authentication work?");
        assertFalse(retrieved.isEmpty());
        assertEquals("authentication.md", retrieved.get(0).source());
    }

    @Test
    void deploymentQuestionRetrievesDeploymentDoc() {
        List<RetrievedDocument> retrieved = retrieve("What happens during a production deployment?");
        assertFalse(retrieved.isEmpty());
        assertEquals("deployment.md", retrieved.get(0).source());
    }

    @Test
    void notificationsQuestionRetrievesServicesDoc() {
        List<RetrievedDocument> retrieved = retrieve("Which service owns notifications?");
        assertFalse(retrieved.isEmpty());
        assertEquals("services.md", retrieved.get(0).source());
    }

    @Test
    void irrelevantDocumentsAreNotReturned() {
        // An authentication question must not drag in the coding guidelines.
        List<RetrievedDocument> retrieved = retrieve("How does authentication work?");
        List<String> sources = retrieved.stream().map(RetrievedDocument::source).toList();
        assertFalse(sources.contains("coding-guidelines.md"), sources.toString());
    }

    @Test
    void zeroMatchQuestionReturnsNothing() {
        assertTrue(retrieve("Best banana smoothie recipe?").isEmpty());
    }

    @Test
    void rankingIsDeterministic() {
        String question = "How are tokens and certificates handled?";
        assertEquals(retrieve(question), retrieve(question));
    }

    @Test
    void topKIsRespected() {
        // A question touching many documents still returns at most TOP_K.
        List<RetrievedDocument> retrieved = retrieve(
                "How do modules, tokens, deployments and events relate on the platform?");
        assertTrue(retrieved.size() <= RagExample.TOP_K,
                "expected at most " + RagExample.TOP_K + " but got " + retrieved.size());
    }

    @Test
    void scoresAreOrderedDescending() {
        List<RetrievedDocument> retrieved = retrieve(
                "How does authentication and the identity module work in production?");
        for (int i = 1; i < retrieved.size(); i++) {
            assertTrue(retrieved.get(i - 1).score() >= retrieved.get(i).score());
        }
    }

    // --- Augmentation ---

    @Test
    void promptContainsSelectedSourcesAndContent() {
        List<RetrievedDocument> retrieved = retrieve("How does authentication work?");
        String prompt = RagExample.buildPrompt("How does authentication work?", retrieved);

        assertTrue(prompt.contains("How does authentication work?"));
        for (RetrievedDocument doc : retrieved) {
            assertTrue(prompt.contains("<document source=\"" + doc.source() + "\">"));
            assertTrue(prompt.contains(doc.document().content().strip()));
        }
    }

    @Test
    void promptDoesNotContainUnselectedDocuments() {
        List<RetrievedDocument> retrieved = retrieve("How does authentication work?");
        String prompt = RagExample.buildPrompt("How does authentication work?", retrieved);
        List<String> selected = retrieved.stream().map(RetrievedDocument::source).toList();

        for (KnowledgeDocument doc : RagExample.loadKnowledge()) {
            if (!selected.contains(doc.source())) {
                assertFalse(prompt.contains("source=\"" + doc.source() + "\""),
                        doc.source() + " leaked into the prompt");
            }
        }
    }

    @Test
    void retrievedSourcesAreApplicationData() {
        // Provenance comes from the retriever, not from a model claim: every
        // reported source is one of the files this application loaded.
        List<String> known = RagExample.loadKnowledge().stream()
                .map(KnowledgeDocument::source).toList();
        for (RetrievedDocument doc : retrieve("How are tokens and deployments handled?")) {
            assertTrue(known.contains(doc.source()), doc.source());
        }
    }

    // --- Configuration, same semantics as the earlier labs ---

    @Test
    void apiKeyAndModelResolution() {
        assertEquals("primary", RagExample.apiKey(java.util.Map.of(
                "GOOGLE_API_KEY", "primary",
                "GEMINI_API_KEY", "legacy")).orElseThrow());
        assertTrue(RagExample.apiKey(java.util.Map.of()).isEmpty());
        assertEquals(RagExample.DEFAULT_MODEL, RagExample.model(java.util.Map.of()));
    }

    private static List<RetrievedDocument> retrieve(String question) {
        return LocalRetriever.retrieve(RagExample.loadKnowledge(), question, RagExample.TOP_K);
    }
}
