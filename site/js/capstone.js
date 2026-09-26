// Capstone 01 data for the interactive layer.
//
// The canonical content lives in the repository; this file holds only the
// small structured representation the UI needs. Where data mirrors a
// repository document, the source is noted and a Python stdlib test
// (scripts/tests/test_site.py) keeps them from drifting.

export const STAGES = [
  { id: "overview", label: "Overview" },
  { id: "challenge", label: "Challenge" },
  { id: "decisions", label: "Decisions" },
  { id: "authority", label: "Decision Authority" },
  { id: "build", label: "Build" },
  { id: "verify", label: "Verify" },
  { id: "reflection", label: "Reflection" },
  { id: "compare", label: "Reference" },
];

// The seven decidable capabilities, mirroring starter/DECISIONS.md.
// MCP is deliberately NOT a decision here: it has not been taught yet, and
// every capability in this capstone is local Java.
export const CAPABILITIES = [
  { id: "model", name: "Model", hint: "Lab 01" },
  { id: "structuredOutput", name: "Structured Output", hint: "Lab 02" },
  { id: "tools", name: "Tools", hint: "Lab 03" },
  { id: "knowledge", name: "Knowledge / RAG", hint: "Lab 04" },
  { id: "memory", name: "Memory", hint: "Lab 05" },
  { id: "skills", name: "Skills", hint: "Lab 06" },
  { id: "agentRuntime", name: "Agent Runtime", hint: "Lab 07" },
];

// source: capstones/01-agentic-system/reference/DECISIONS.md
// One defensible architecture, not the answer key.
export const REFERENCE_DECISIONS = {
  model: {
    use: true,
    why: "Interpret observations and synthesize the human-readable handoff. A template over tool output was rejected because the handoff must weave runbook guidance, observations, and hypothesis discipline into prose.",
  },
  structuredOutput: {
    use: false,
    why: "The deliverable is read by an engineer; no downstream code parses fields, so a typed contract adds cost without a consumer. The skill's output structure covers the sections as prose.",
  },
  tools: {
    use: true,
    why: "Current status and deployment facts are authoritative application data; the model must never recall or invent them. Pasting fixtures into the prompt was rejected as hardcoded and instantly stale.",
  },
  knowledge: {
    use: true,
    why: "The runbooks are unstructured operational documentation relevant to the investigation; retrieval selects the two that matter. Always sending all three would teach the wrong habit.",
  },
  memory: {
    use: false,
    why: "One bounded investigation run; nothing from an earlier interaction affects this request, and nothing must survive it.",
  },
  skills: {
    use: true,
    why: "The incident-handoff procedure is reusable across incidents and must be reviewable; one SKILL.md beats copies drifting through prompts.",
  },
  agentRuntime: {
    use: true,
    why: "The next diagnostic action depends on the previous observation: a healthy status would end the investigation without a deployment lookup. A fixed two-lookup workflow was seriously considered and would be a valid production choice if the sequence were always known.",
  },
};

// source: capstones/01-agentic-system/reference/DECISIONS.md,
// section "Decision authority in this reference".
export const REFERENCE_AUTHORITY = {
  influence: [
    "Which read-only diagnostic tool to request next, from the two exposed to it",
    "The synthesis and wording of the final handoff",
    "The interpretation of observations — which hypotheses are worth stating",
  ],
  application: [
    "The tool allowlist (two read-only tools, a visible switch, no reflection)",
    "Argument and service validation before any execution",
    "The step budget (4 model decisions) and every stop condition",
    "Execution itself — the model never runs anything",
    "What counts as authoritative data (tool results, never model recall)",
    "Authorization boundaries — nothing in model output can widen them",
  ],
};

// Reflection prompts, drawn from capstones/01-agentic-system/README.md.
export const REFLECTION_QUESTIONS = [
  { id: "hardestReject", text: "Which capability was hardest to reject?" },
  { id: "fixedWorkflow", text: "Could this have been a fixed workflow? Under what conditions would that be better?" },
  { id: "memoryJustify", text: "What information, if any, would justify adding Memory?" },
  { id: "knowledgeVsSkills", text: "Why are runbooks Knowledge rather than Skills?" },
  { id: "removeFirst", text: "Which capability would you remove first if simplicity became the primary goal?" },
];

export const CHALLENGE_TEXT =
  "The notifications service is slow after a release. Investigate what is " +
  "currently known and prepare a concise engineering handoff. Do not claim " +
  "a root cause without evidence.";
