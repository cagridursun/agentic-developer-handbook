// Local-only state for the readiness assessment. A separate, namespaced,
// versioned key: assessment state never mixes with Capstone 01 progress
// (adh.learning.v1.capstone01). Nothing leaves the browser, and nothing
// sensitive is stored — no credentials, no keys, no source code.

export const STORAGE_KEY = "adh.learning.v1.readinessAssessment";

export function defaultState() {
  return {
    version: 1,
    completed: {},       // stageId -> true
    useCase: { problem: "", consumer: "", outcome: "" },
    simplest: { deterministicWhy: "", existingAlternative: "", languageWhy: "" },
    answers: {},         // questionId -> "yes" | "no" | "unsure"
    capabilityNotes: {}, // capabilityId -> { why, alternative }
    authority: {},       // authority question id -> answer
    runtime: {},         // runtime question id -> answer
    production: {},      // production question id -> answer
    result: { shape: "", rejected: "" },
    reflection: {},      // reflection question id -> answer
  };
}

export function loadState() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return defaultState();
    }
    const parsed = JSON.parse(raw);
    const defaults = defaultState();
    const merged = { ...defaults, ...parsed };
    // Merge every nested object so a partial or older state cannot break rendering.
    for (const key of Object.keys(defaults)) {
      if (defaults[key] && typeof defaults[key] === "object") {
        merged[key] = { ...defaults[key], ...(parsed[key] || {}) };
      }
    }
    return merged;
  } catch {
    return defaultState();
  }
}

export function saveState(state) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // Storage unavailable: the page still works for this visit.
  }
}

export function resetState() {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Nothing stored, nothing to clear.
  }
}
