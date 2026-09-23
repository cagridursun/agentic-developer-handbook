package dev.agentic.handbook.capstones.agenticsystem.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests only the provided scaffolding — never an architecture the learner has
 * not designed yet.
 */
class StarterAssetsTest {

    @Test
    void allRunbooksLoad() {
        Map<String, String> knowledge = StarterAssets.knowledge();
        assertEquals(3, knowledge.size());
        for (Map.Entry<String, String> doc : knowledge.entrySet()) {
            assertFalse(doc.getValue().isBlank(), doc.getKey());
            assertTrue(doc.getValue().contains("fictional"), doc.getKey()
                    + " must declare itself fictional");
        }
    }

    @Test
    void skillAssetLoadsAndFollowsTheAgentSkillsShape() {
        String skill = StarterAssets.skillMarkdown();
        assertTrue(skill.startsWith("---"));
        assertTrue(skill.contains("name: incident-handoff"));
        assertTrue(skill.contains("description:"));
        assertTrue(skill.contains("Never state an unproven root cause as fact"));
    }

    @Test
    void serviceFixturesAreDeterministic() {
        assertEquals("DEGRADED",
                ServiceFixtures.serviceStatus("notifications").orElseThrow().get("status"));
        assertEquals("notifications-2.4.1",
                ServiceFixtures.recentDeployment("notifications").orElseThrow().get("version"));
        assertTrue(ServiceFixtures.recentDeployment("billing").isEmpty());
        assertTrue(ServiceFixtures.serviceStatus("payments").isEmpty());
    }

    @Test
    void fixturesContainNoCredentials() {
        for (String content : StarterAssets.knowledge().values()) {
            assertFalse(content.contains("AIza"), "no API keys in fixtures");
        }
        assertFalse(StarterAssets.skillMarkdown().contains("AIza"));
    }
}
