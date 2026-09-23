package dev.agentic.handbook.capstones.agenticsystem.starter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the fictional assets available to the learner: three runbook
 * documents and one Agent Skill. Whether and how a solution uses them is the
 * learner's decision.
 */
public final class StarterAssets {

    /** The runbook corpus available for a Knowledge / RAG decision. */
    public static final List<String> KNOWLEDGE_FILES =
            List.of("notifications.md", "deployment.md", "incident-response.md");

    /** The reusable procedure available for a Skills decision. */
    public static final String SKILL_PATH = "/skills/incident-handoff/SKILL.md";

    private StarterAssets() {
    }

    /** All runbook documents, keyed by filename, in a deterministic order. */
    public static Map<String, String> knowledge() {
        Map<String, String> documents = new LinkedHashMap<>();
        for (String name : KNOWLEDGE_FILES) {
            documents.put(name, resource("/knowledge/" + name));
        }
        return documents;
    }

    /** The raw SKILL.md text. Parsing it is part of the learner's design. */
    public static String skillMarkdown() {
        return resource(SKILL_PATH);
    }

    private static String resource(String path) {
        try (InputStream stream = StarterAssets.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing classpath resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read resource: " + path, e);
        }
    }
}
