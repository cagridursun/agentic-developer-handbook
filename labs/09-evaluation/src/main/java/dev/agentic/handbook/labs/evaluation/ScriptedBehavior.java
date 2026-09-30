package dev.agentic.handbook.labs.evaluation;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deterministic test double, not an AI: a tiny rule-based "model" whose
 * decisions depend on what it observes, in two variants.
 *
 * <p>It exists so the evaluation <em>mechanics</em> are deterministic and
 * inspectable — the same cases, the same checks, the same numbers on every run.
 * It is not a real model evaluation. Live-model evaluation is the probabilistic
 * use case of the same harness (see {@code --live}).
 *
 * <ul>
 *   <li>{@link #baseline()}: checks the status; looks up the deployment only for
 *       a degraded service; reports what it observed and says correlation is
 *       not cause.</li>
 *   <li>{@link #candidate()}: a deliberately regressed version — "we changed the
 *       prompt". It always looks up the deployment, and when a deployment
 *       exists it states that the deployment caused the incident. It still
 *       stops on a final answer, well inside the budget: every runtime test
 *       would still pass.</li>
 * </ul>
 */
public final class ScriptedBehavior implements AgentModel {

    private static final Pattern SERVICE = Pattern.compile("Investigate the (\\w+) service");

    private final boolean regressed;
    private Map<String, Object> status;

    private ScriptedBehavior(boolean regressed) {
        this.regressed = regressed;
    }

    public static SystemVersion baseline() {
        return new SystemVersion("baseline",
                "checks status; looks up a deployment only when degraded; correlation, not cause",
                () -> new ScriptedBehavior(false));
    }

    public static SystemVersion candidate() {
        return new SystemVersion("candidate",
                "regressed: always looks up a deployment, and blames it when one exists",
                () -> new ScriptedBehavior(true));
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
            boolean degraded = "DEGRADED".equals(result.get("status"));
            if (degraded || regressed) {
                return new ModelDecision.ToolRequest("getRecentDeployment",
                        Map.of("serviceName", result.get("serviceName")));
            }
            return new ModelDecision.FinalAnswer(statusSummary() + noFurtherInvestigation());
        }
        return new ModelDecision.FinalAnswer(statusSummary() + deploymentSummary(result));
    }

    private String statusSummary() {
        return status.get("serviceName") + " is " + status.get("status") + ". " + status.get("message");
    }

    private String noFurtherInvestigation() {
        return "HEALTHY".equals(status.get("status"))
                ? " No degradation was observed, so no further investigation was needed."
                : " This is planned work, not an incident; no further investigation was needed.";
    }

    private String deploymentSummary(Map<String, Object> deployment) {
        Object version = deployment.get("version");
        if (version == null) {
            return "DEGRADED".equals(status.get("status"))
                    ? " No deployment was found within the last 7 days, so a deployment is not a "
                            + "candidate explanation from these observations. The root cause is not "
                            + "established; further investigation is needed."
                    : " A deployment lookup found nothing within the last 7 days.";
        }
        String facts = " The most recent deployment is " + version + " (" + deployment.get("summary")
                + "), deployed at " + deployment.get("deployedAt") + ".";
        if (regressed) {
            return facts + " The deployment " + version + " caused the incident.";
        }
        return facts + " The timing may be relevant, but the observations show correlation only: "
                + "whether the deployment caused the degradation cannot be concluded from this "
                + "evidence, and the root cause is not established. Comparing behavior against the "
                + "previous version is a reasonable next step.";
    }
}
