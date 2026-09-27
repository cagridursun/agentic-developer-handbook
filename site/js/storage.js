// Local-only progress. One namespaced, versioned key; nothing leaves the
// browser, and nothing sensitive is ever stored (no credentials, no keys,
// no source code).

export const STORAGE_KEY = "adh.learning.v1.capstone01";

export function defaultState() {
  return {
    version: 1,
    completed: {}, // stageId -> true
    decisions: {}, // capabilityId -> { use: "yes"|"no"|null, why: "", alternative: "" }
    authority: { influence: "", deterministic: "" },
    reflection: {}, // questionId -> answer
    referenceRevealed: false,
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
    // Merge nested objects too, so a partially stored state (for example an
    // authority object missing one answer) cannot break rendering.
    return {
      ...defaults,
      ...parsed,
      completed: { ...defaults.completed, ...(parsed.completed || {}) },
      decisions: { ...defaults.decisions, ...(parsed.decisions || {}) },
      authority: { ...defaults.authority, ...(parsed.authority || {}) },
      reflection: { ...defaults.reflection, ...(parsed.reflection || {}) },
    };
  } catch {
    return defaultState();
  }
}

// Storage can be unavailable (private browsing, disabled storage, quota).
// The page keeps working for the current visit; progress just is not saved.
export function saveState(state) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // Intentionally ignored: local progress is a convenience, not a requirement.
  }
}

export function resetState() {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Nothing stored, nothing to clear.
  }
}
