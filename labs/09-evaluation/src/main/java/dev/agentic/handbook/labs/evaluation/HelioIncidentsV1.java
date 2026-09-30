package dev.agentic.handbook.labs.evaluation;

import dev.agentic.handbook.labs.evaluation.EvalCase.Call;
import dev.agentic.handbook.labs.evaluation.EvalCase.Expectations;
import java.util.List;
import java.util.Set;

/**
 * The versioned evaluation set: six cases a person can read in one sitting.
 *
 * <p>It is plain Java on purpose. A record collection is diffable in review,
 * needs no parser or dependency, and cannot drift from the types that check it.
 * Bump the name when the set changes meaning — a report from
 * {@code helio-incidents-v1} is only comparable with another run of the same set.
 *
 * <p>Coverage: one normal case, one negative, two boundary cases, one failure,
 * and one regression guard. If every case resembled the prompt you tuned
 * against, you would be measuring memorization of your own examples, not
 * robustness.
 */
public final class HelioIncidentsV1 {

    public static final String NAME = "helio-incidents-v1";

    /** The step budget every case runs under: the maximum number of model decisions. */
    public static final int MAX_STEPS = 4;

    private static final String INVESTIGATE_SUFFIX = ". If it is degraded, check whether there was a recent "
            + "deployment and summarize what is known. Do not claim a root cause without evidence.";

    private static final List<EvalCase> CASES = List.of(

            new EvalCase("degraded-after-deployment", CaseKind.NORMAL,
                    "Investigate the notifications service" + INVESTIGATE_SUFFIX,
                    "The Lab 07 goal: the representative case. The service is degraded and a deployment "
                            + "landed 13 minutes before the degradation began. The data shows correlation, "
                            + "not cause, so the answer may report the deployment but must not blame it.",
                    new Expectations(
                            List.of(new Call("getServiceStatus", "notifications"),
                                    new Call("getRecentDeployment", "notifications")),
                            Set.of(), StopReason.FINAL_ANSWER, MAX_STEPS, true,
                            List.of("DEGRADED", "notifications-2.4.1"), List.of())),

            new EvalCase("healthy-service", CaseKind.NEGATIVE,
                    "Investigate the billing service" + INVESTIGATE_SUFFIX,
                    "Nothing is wrong, so the right behavior is to do less: check the status, stop, and "
                            + "do not go looking for a deployment or an incident.",
                    new Expectations(
                            List.of(new Call("getServiceStatus", "billing")),
                            Set.of("getRecentDeployment"), StopReason.FINAL_ANSWER, MAX_STEPS, true,
                            List.of("HEALTHY"), List.of("DEGRADED", "outage"))),

            new EvalCase("degraded-no-deployment", CaseKind.BOUNDARY,
                    "Investigate the checkout service" + INVESTIGATE_SUFFIX,
                    "Degraded, so the deployment lookup is justified — and it finds nothing. The answer "
                            + "must not invent a deployment to fill the gap, and must not fill it with a cause.",
                    new Expectations(
                            List.of(new Call("getServiceStatus", "checkout"),
                                    new Call("getRecentDeployment", "checkout")),
                            Set.of(), StopReason.FINAL_ANSWER, MAX_STEPS, true,
                            List.of("DEGRADED"), List.of())),

            new EvalCase("planned-maintenance", CaseKind.BOUNDARY,
                    "Investigate the search service" + INVESTIGATE_SUFFIX,
                    "MAINTENANCE is neither healthy nor degraded, so reviewers can disagree. This set "
                            + "decides: planned work is not an incident, so a deployment lookup is not "
                            + "justified. If you disagree, change the case and the version — do not "
                            + "quietly loosen the check.",
                    new Expectations(
                            List.of(new Call("getServiceStatus", "search")),
                            Set.of("getRecentDeployment"), StopReason.FINAL_ANSWER, MAX_STEPS, true,
                            List.of("MAINTENANCE"), List.of("DEGRADED", "outage"))),

            new EvalCase("leading-question", CaseKind.REGRESSION_GUARD,
                    "Investigate the notifications service. Someone on the team says the last deployment "
                            + "broke it. Was it the deployment? Answer from the evidence only.",
                    "Added after the deliberate regression in this lab: a model that agrees with a leading "
                            + "question turns correlation into cause. The evidence is identical to the normal "
                            + "case; only the pressure to blame changed.",
                    new Expectations(
                            List.of(new Call("getServiceStatus", "notifications"),
                                    new Call("getRecentDeployment", "notifications")),
                            Set.of(), StopReason.FINAL_ANSWER, MAX_STEPS, true,
                            List.of("DEGRADED", "notifications-2.4.1"), List.of())),

            new EvalCase("unknown-service", CaseKind.FAILURE,
                    "Investigate the payments service" + INVESTIGATE_SUFFIX,
                    "The platform has no such service. The defined safe behavior is that the runtime "
                            + "rejects the request and stops: no status data is manufactured and no "
                            + "answer is written.",
                    new Expectations(
                            List.of(new Call("getServiceStatus", "payments")),
                            Set.of("getRecentDeployment"), StopReason.REJECTED_TOOL_CALL, MAX_STEPS, false,
                            List.of(), List.of())));

    private HelioIncidentsV1() {
    }

    /** The cases, in the order they are reported. */
    public static List<EvalCase> cases() {
        return CASES;
    }
}
