package dev.agentic.handbook.labs.observability;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deterministic test double, not an AI: a tiny rule-based "model" whose
 * decisions depend on what it observes. It exists so the lab runs with no key
 * and no network, and so the same run can be compared with the README.
 *
 * <p>A scripted model reports no token usage, because it has none. The trace
 * shows no token fields for it; nothing is invented.
 *
 * <ul>
 *   <li>{@link #baseline()}: the Lab 09 baseline. Checks the status; looks up
 *       the deployment only for a degraded service; reports what it observed
 *       and says correlation is not cause.</li>
 *   <li>{@link #repeatingDeploymentLookup()}: a failure mode, not a claim about
 *       any real model. After the status it asks for the recent deployment, and
 *       keeps asking after every observation, as if it never accepted the
 *       answer as sufficient. It never answers, so only the step budget stops it.
 *       Models do get stuck repeating a tool call; this is a stand-in for that.</li>
 * </ul>
 */
public final class ScriptedModel implements AgentModel {

    private static final Pattern SERVICE = Pattern.compile("Investigate the (\\w+) service");

    private final boolean repeating;
    private Map<String, Object> status;

    private ScriptedModel(boolean repeating) {
        this.repeating = repeating;
    }

    public static ScriptedModel baseline() {
        return new ScriptedModel(false);
    }

    public static ScriptedModel repeatingDeploymentLookup() {
        return new ScriptedModel(true);
    }

    @Override
    public ModelInfo info() {
        return new ModelInfo("scripted", repeating ? "repeating-lookup" : "baseline");
    }

    @Override
    public ModelDecision start(String goal) {
        Matcher matcher = SERVICE.matcher(goal);
        if (!matcher.find()) {
            return new ModelDecision.FinalAnswer("I cannot tell which service to investigate.");
        }
        return new ModelDecision.ToolRequest("getServiceStatus", Map.of("serviceName", matcher.group(1)));
    }

    @Override
    public ModelDecision observe(ToolExchange exchange) {
        Map<String, Object> result = exchange.result();
        if (exchange.request().name().equals("getServiceStatus")) {
            status = result;
            if ("DEGRADED".equals(result.get("status"))) {
                return new ModelDecision.ToolRequest("getRecentDeployment",
                        Map.of("serviceName", result.get("serviceName")));
            }
            return new ModelDecision.FinalAnswer(statusSummary() + " No further investigation was needed.");
        }
        if (repeating) {
            return new ModelDecision.ToolRequest("getRecentDeployment",
                    Map.of("serviceName", result.get("serviceName")));
        }
        return new ModelDecision.FinalAnswer(statusSummary() + deploymentSummary(result));
    }

    private String statusSummary() {
        return status.get("serviceName") + " is " + status.get("status") + ". " + status.get("message");
    }

    private String deploymentSummary(Map<String, Object> deployment) {
        Object version = deployment.get("version");
        if (version == null) {
            return " No deployment was found within the last 7 days, so a deployment is not a candidate "
                    + "explanation from these observations. The root cause is not established; further "
                    + "investigation is needed.";
        }
        return " The most recent deployment is " + version + " (" + deployment.get("summary")
                + "), deployed at " + deployment.get("deployedAt") + ". The timing may be relevant, but the "
                + "observations show correlation only: whether the deployment caused the degradation cannot "
                + "be concluded from this evidence, and the root cause is not established.";
    }
}
