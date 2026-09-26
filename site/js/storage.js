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
    return { ...defaultState(), ...parsed };
  } catch {
    return defaultState();
  }
}

export function saveState(state) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

export function resetState() {
  localStorage.removeItem(STORAGE_KEY);
}
