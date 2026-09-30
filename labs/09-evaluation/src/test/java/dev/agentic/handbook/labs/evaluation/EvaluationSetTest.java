package dev.agentic.handbook.labs.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The evaluation set is data a person reviews; these tests keep it reviewable and well-formed. */
class EvaluationSetTest {

    private final List<EvalCase> cases = HelioIncidentsV1.cases();

    @Test
    void caseIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (EvalCase evalCase : cases) {
            assertTrue(ids.add(evalCase.id()), "duplicate id: " + evalCase.id());
        }
    }

    @Test
    void everyCaseStatesItsInputItsReasonAndAnExplicitBehavioralContract() {
        for (EvalCase evalCase : cases) {
            assertFalse(evalCase.goal().isBlank(), evalCase.id());
            assertFalse(evalCase.why().isBlank(), evalCase.id() + " must say why it exists");
            EvalCase.Expectations expectations = evalCase.expectations();
            assertFalse(expectations.requiredCalls().isEmpty(),
                    evalCase.id() + " must require at least one tool request");
            assertTrue(expectations.maxModelSteps() >= 1, evalCase.id());
            assertEquals(expectations.stopReason() == StopReason.FINAL_ANSWER, expectations.expectFinalAnswer(),
                    evalCase.id() + ": a final answer is expected exactly when the run should end on one");
        }
    }

    @Test
    void theSetIsSmallEnoughToReadAndCoversMoreThanHappyPaths() {
        assertTrue(cases.size() <= 10, "a learner must be able to read every case");
        Set<CaseKind> kinds = EnumSet.noneOf(CaseKind.class);
        cases.forEach(evalCase -> kinds.add(evalCase.kind()));
        assertEquals(EnumSet.allOf(CaseKind.class), kinds,
                "normal, negative, boundary, failure, and regression-guard cases are all present");
    }

    @Test
    void requiredCallsNameKnownServicesExceptInTheFailureCase() {
        for (EvalCase evalCase : cases) {
            for (EvalCase.Call call : evalCase.expectations().requiredCalls()) {
                boolean known = ServiceTools.knownServices().contains(call.serviceName());
                assertEquals(evalCase.kind() != CaseKind.FAILURE, known, evalCase.id() + " " + call);
            }
        }
    }

    @Test
    void goalsNameTheServiceTheExpectationsAreAbout() {
        for (EvalCase evalCase : cases) {
            String service = evalCase.expectations().requiredCalls().get(0).serviceName();
            assertTrue(evalCase.goal().contains(service), evalCase.id());
        }
    }
}
