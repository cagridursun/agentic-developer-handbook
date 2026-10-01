package dev.agentic.handbook.labs.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The whole harness in one method: for every case, run the system under
 * evaluation with the real bounded runtime, capture the trace, and apply the
 * deterministic checks. No framework, no plugin points, no persistence.
 */
public final class EvaluationRunner {

    private EvaluationRunner() {
    }

    public static EvaluationReport run(String setName, List<EvalCase> cases, SystemVersion version) {
        List<CaseReport> reports = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            reports.add(runCase(evalCase, version));
        }
        return new EvaluationReport(setName, version.name(), List.copyOf(reports));
    }

    private static CaseReport runCase(EvalCase evalCase, SystemVersion version) {
        AgentRunResult result;
        try {
            // A fresh model per case: models are stateful for one run.
            result = new AgentRuntime(version.models().get(), evalCase.expectations().maxModelSteps())
                    .run(evalCase.goal());
        } catch (RuntimeException failure) {
            // With a live model, a run can fail outside the runtime's own stop rules
            // (a provider error, an unusable response). That is a result, not a crash:
            // record it as a failed check and keep evaluating the other cases.
            return new CaseReport(evalCase, Optional.empty(), List.of(new CheckResult(
                    Dimension.BOUNDED_EXECUTION, "the run completed", false,
                    failure.getClass().getSimpleName() + ": " + failure.getMessage())));
        }
        return new CaseReport(evalCase, Optional.of(result),
                BehaviorChecks.evaluate(new EvaluationRun(evalCase, result)));
    }
}
