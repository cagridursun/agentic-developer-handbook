package dev.agentic.handbook.capstones.agenticsystem.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Deterministic tests for the composed reference: retrieval, skill loading,
 * context construction, and the bounded runtime. These prove application
 * behavior — they do NOT prove the agent is good. Systematic behavioral
 * evaluation is Milestone 9.
 */
class CapstoneReferenceTest {

    private static final Agent.ToolRequest STATUS =
            new Agent.ToolRequest("getServiceStatus", Map.of("serviceName", "notifications"));
    private static final Agent.ToolRequest DEPLOYMENT =
            new Agent.ToolRequest("getRecentDeployment", Map.of("serviceName", "notifications"));

    // --- Knowledge: application-controlled retrieval before the loop ---

    @Test
    void goalRetrievesNotificationsAndIncidentGuidance() {
        List<Knowledge.Retrieved> retrieved =
                Knowledge.retrieve(Knowledge.load(), CapstoneExample.GOAL, Knowledge.TOP_K);
        List<String> sources = retrieved.stream().map(Knowledge.Retrieved::source).toList();
        assertEquals("notifications.md", sources.get(0));
        assertTrue(sources.size() <= Knowledge.TOP_K);
    }

    @Test
    void unrelatedKnowledgeIsNotBlindlyInjected() {
        String context = CapstoneExample.buildInitialContext(
                CapstoneExample.GOAL,
                Knowledge.retrieve(Knowledge.load(), CapstoneExample.GOAL, Knowledge.TOP_K),
                SkillLoader.loadIncidentHandoff());
        // Top-K is 2, so at least one of the three runbooks stays out.
        long included = Knowledge.SOURCES.stream()
                .filter(source -> context.contains("source=\"" + source + "\"")).count();
        assertEquals(Knowledge.TOP_K, included);
    }

    // --- Skill ---

    @Test
    void incidentHandoffSkillLoads() {
        SkillLoader.Skill skill = SkillLoader.loadIncidentHandoff();
        assertEquals("incident-handoff", skill.name());
        assertTrue(skill.instructions().contains("Never state an unproven root cause as fact"));
    }

    @Test
    void initialContextComposesGoalRunbooksAndSkill() {
        String context = CapstoneExample.buildInitialContext(
                CapstoneExample.GOAL,
                Knowledge.retrieve(Knowledge.load(), CapstoneExample.GOAL, Knowledge.TOP_K),
                SkillLoader.loadIncidentHandoff());
        assertTrue(context.contains(CapstoneExample.GOAL));
        assertTrue(context.contains("<skill name=\"incident-handoff\">"));
        assertTrue(context.contains("source=\"notifications.md\""));
    }

    // --- The bounded runtime, composed ---

    @Test
    void scriptedInvestigationExecutesBothToolsInOrderThenStops() {
        ScriptedModel model = ScriptedModel.of(List.of(
                STATUS, DEPLOYMENT, new Agent.FinalAnswer("handoff")));

        Agent.RunResult result = new Agent(model, CapstoneExample.MAX_STEPS).run("context");

        assertEquals(Agent.StopReason.FINAL_ANSWER, result.stopReason());
        assertEquals(3, result.modelSteps());
        assertEquals(List.of("getServiceStatus", "getRecentDeployment"),
                result.exchanges().stream().map(e -> e.request().name()).toList());
        // The status observation exists before the deployment call happens.
        assertEquals("DEGRADED", result.exchanges().get(0).result().get("status"));
        assertEquals("notifications-2.4.1", result.exchanges().get(1).result().get("version"));
        // The final answer stopped the run: exactly three decisions served.
        assertEquals(3, model.decisionsServed());
    }

    @Test
    void toolResultsBecomeObservationsForTheNextDecision() {
        List<Agent.ToolExchange> observed = new java.util.ArrayList<>();
        Agent.Model recording = new Agent.Model() {
            private int calls;

            @Override
            public Agent.Decision start(String initialContext) {
                calls++;
                return STATUS;
            }

            @Override
            public Agent.Decision observe(Agent.ToolExchange exchange) {
                calls++;
                observed.add(exchange);
                return calls == 2 ? DEPLOYMENT : new Agent.FinalAnswer("done");
            }
        };
        new Agent(recording, 4).run("context");
        assertEquals("getServiceStatus", observed.get(0).request().name());
        assertEquals("getRecentDeployment", observed.get(1).request().name());
    }

    @Test
    void maxStepGuardStopsAnEndlessModel() {
        ScriptedModel endless = ScriptedModel.repeating(STATUS);
        Agent.RunResult result = new Agent(endless, 3).run("context");
        assertEquals(Agent.StopReason.MAX_STEPS, result.stopReason());
        assertEquals(3, result.modelSteps());
        assertEquals(3, endless.decisionsServed());
    }

    @Test
    void unknownToolIsRejected() {
        ScriptedModel model = ScriptedModel.of(List.of(
                new Agent.ToolRequest("restartService", Map.of("serviceName", "notifications"))));
        Agent.RunResult result = new Agent(model, 4).run("context");
        assertEquals(Agent.StopReason.REJECTED_TOOL_CALL, result.stopReason());
        assertTrue(result.exchanges().isEmpty());
    }

    @Test
    void malformedArgumentsAreRejected() {
        for (Map<String, Object> bad : List.of(
                Map.<String, Object>of(),
                Map.<String, Object>of("serviceName", "  "),
                Map.<String, Object>of("serviceName", 42),
                Map.<String, Object>of("serviceName", "notifications", "force", true))) {
            ScriptedModel model = ScriptedModel.of(List.of(
                    new Agent.ToolRequest("getServiceStatus", bad)));
            assertEquals(Agent.StopReason.REJECTED_TOOL_CALL,
                    new Agent(model, 4).run("context").stopReason(), bad.toString());
        }
    }

    @Test
    void unknownServiceIsRejected() {
        ScriptedModel model = ScriptedModel.of(List.of(
                new Agent.ToolRequest("getServiceStatus", Map.of("serviceName", "payments"))));
        assertEquals(Agent.StopReason.REJECTED_TOOL_CALL,
                new Agent(model, 4).run("context").stopReason());
    }

    // --- The final artifact keeps facts and hypotheses apart ---

    @Test
    void scriptedHandoffSeparatesFactsFromHypotheses() {
        // The demo's scripted final answer is application-owned text; it must
        // model the behavior the skill demands.
        ScriptedModel model = ScriptedModel.of(List.of(STATUS, DEPLOYMENT,
                new Agent.FinalAnswer("**Observed facts**\n- x\n\n**Hypotheses** (unproven)\n- y")));
        Agent.RunResult result = new Agent(model, 4).run("context");
        String answer = result.answer().orElseThrow();
        assertTrue(answer.contains("Observed facts"));
        assertTrue(answer.contains("Hypotheses"));
    }

    // --- Deliberate absences ---

    @Test
    void memoryAndMcpAreDeliberatelyAbsent() {
        // The reference composition contains no memory type and no MCP anything:
        // the decisions in DECISIONS.md are structural, not just prose.
        String pkg = "dev.agentic.handbook.capstones.agenticsystem.reference.";
        assertThrows(ClassNotFoundException.class, () -> Class.forName(pkg + "SessionMemory"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName(pkg + "McpClient"));
        String context = CapstoneExample.buildInitialContext(
                CapstoneExample.GOAL,
                Knowledge.retrieve(Knowledge.load(), CapstoneExample.GOAL, Knowledge.TOP_K),
                SkillLoader.loadIncidentHandoff());
        assertFalse(context.contains("Memory:"));
    }

    // --- Configuration, same semantics as the canonical labs ---

    @Test
    void apiKeyAndModelResolution() {
        assertEquals("primary", CapstoneExample.apiKey(Map.of(
                "GOOGLE_API_KEY", "primary",
                "GEMINI_API_KEY", "legacy")).orElseThrow());
        assertTrue(CapstoneExample.apiKey(Map.of()).isEmpty());
        assertEquals(CapstoneExample.DEFAULT_MODEL, CapstoneExample.model(Map.of()));
    }
}
