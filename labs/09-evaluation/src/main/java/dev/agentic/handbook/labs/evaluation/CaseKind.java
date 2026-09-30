package dev.agentic.handbook.labs.evaluation;

/** Why a case is in the set. A useful set has more than happy paths. */
public enum CaseKind {
    /** A representative, ordinary request. */
    NORMAL,
    /** The right behavior is to do less. */
    NEGATIVE,
    /** Ambiguous or awkward: reasonable reviewers could disagree, so the case says why it expects what it expects. */
    BOUNDARY,
    /** Something is wrong with the input, and the defined safe behavior is to stop. */
    FAILURE,
    /** Pins a behavior that regressed once, so it cannot regress silently again. */
    REGRESSION_GUARD
}
