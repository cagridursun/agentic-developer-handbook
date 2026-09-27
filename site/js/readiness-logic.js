// Deterministic, transparent rules that turn assessment answers into a
// capability summary. No model is called: the assessment is an engineering
// framework, and these rules are printed in
// assessments/agentic-system-readiness/README.md ("How the summary is derived")
// so a reader can check every result by hand.
//
// The output is candidates to consider, never "the correct architecture".

import { CAPABILITIES, STATUS } from "./readiness.js";

// Which question gives each capability a reason to exist.
const QUESTION_FOR = {
  structuredOutput: "consumer",
  tools: "authoritative",
  knowledge: "unstructured",
  memory: "retained",
  skills: "procedure",
  agentRuntime: "nextStep",
};

function fromAnswer(answer) {
  if (answer === "yes") return STATUS.consider;
  if (answer === "no") return STATUS.notJustified;
  return STATUS.undecided;
}

// True when question 0 or question 1 already rules out a model.
export function deterministicFirst(answers) {
  return answers.deterministic === "yes" || answers.language === "no";
}

export function deriveStatuses(answers) {
  const result = {};

  // Rule 1: Model.
  if (deterministicFirst(answers)) {
    result.model = {
      status: STATUS.notJustified,
      reason: answers.deterministic === "yes"
        ? "Deterministic software solves the problem (question 0)."
        : "The task needs no probabilistic language, reasoning, or generation (question 1).",
    };
  } else if (answers.deterministic === "no" && answers.language === "yes") {
    result.model = {
      status: STATUS.consider,
      reason: "Deterministic software is not enough, and the task needs language, reasoning, or generation.",
    };
  } else {
    result.model = {
      status: STATUS.undecided,
      reason: "Questions 0 and 1 are not both answered yet.",
    };
  }
  const model = result.model.status;

  // Rule 2: each capability follows its own question, gated by the model.
  for (const [capability, question] of Object.entries(QUESTION_FOR)) {
    const own = fromAnswer(answers[question]);
    if (model === STATUS.notJustified) {
      result[capability] = {
        status: STATUS.notJustified,
        reason: capability === "tools"
          ? "No model, so no model tools. Reading authoritative data is ordinary deterministic application logic."
          : "No model is justified, so this capability has nothing to serve.",
      };
    } else if (model === STATUS.undecided && own === STATUS.consider) {
      result[capability] = {
        status: STATUS.undecided,
        reason: "Your answer points here, but whether a model is justified is still open.",
      };
    } else {
      result[capability] = {
        status: own,
        reason: own === STATUS.consider ? "Your answer gives this capability a reason to exist."
          : own === STATUS.notJustified ? "Nothing in your answers requires it."
          : "The question is unanswered or answered unsure.",
      };
    }
  }

  // Rule 3: a runtime chooses among allowed actions; without tools there are none.
  if (result.agentRuntime.status === STATUS.consider && result.tools.status !== STATUS.consider) {
    result.agentRuntime = {
      status: STATUS.undecided,
      reason: "The next step depends on observations, but no tools are a candidate: a runtime needs allowed actions to choose among. Revisit question 3.",
    };
  }
  if (answers.nextStep === "no" && model !== STATUS.notJustified) {
    result.agentRuntime = {
      status: STATUS.notJustified,
      reason: "The sequence of steps is known in advance: prefer a fixed workflow.",
    };
  }

  // Rule 4: protocol boundary — never an automatic recommendation.
  if (model === STATUS.notJustified) {
    result.mcp = {
      status: STATUS.notJustified,
      reason: "Behind ordinary software, a remote capability is just an API call.",
    };
  } else if (answers.boundary === "yes") {
    result.mcp = {
      status: STATUS.future,
      reason: "Capabilities cross a process or system boundary. Evaluate a protocol later (Milestone 8); a normal API and client may still be simpler.",
    };
  } else if (answers.boundary === "no") {
    result.mcp = {
      status: STATUS.notJustified,
      reason: "Everything is reachable in-process or through existing APIs.",
    };
  } else {
    result.mcp = {
      status: STATUS.undecided,
      reason: "Question 8 is unanswered or answered unsure.",
    };
  }
  return result;
}

export function headline(answers, statuses) {
  if (answers.deterministic === "yes") {
    return {
      title: "Start with deterministic software.",
      detail: "You answered that ordinary software solves this adequately. Build that first; no AI capability is justified. Reassess only if a new requirement genuinely needs language understanding or generation.",
    };
  }
  if (answers.language === "no") {
    return {
      title: "You probably do not need an LLM.",
      detail: "The task needs no probabilistic language, reasoning, or generation. Prefer deterministic software.",
    };
  }
  if (statuses.model.status === STATUS.undecided) {
    return {
      title: "Decide the simplest question first.",
      detail: "Whether deterministic software is enough — and whether the task needs a model at all — is still open. Everything below depends on it.",
    };
  }
  const runtime = statuses.agentRuntime.status;
  if (runtime === STATUS.notJustified) {
    return {
      title: "You need an LLM, but not an agent.",
      detail: "A fixed workflow is simpler than an Agent Runtime when the sequence of steps is known.",
    };
  }
  if (runtime === STATUS.consider) {
    return {
      title: "A bounded agent runtime is a candidate.",
      detail: "The next step depends on observations. Keep the envelope explicit — allowed actions, budget, stop conditions — and seriously consider a fixed workflow before building the loop.",
    };
  }
  return {
    title: "A model is a candidate; the runtime question is still open.",
    detail: "Decide whether the next step is genuinely unknown in advance before choosing between a workflow and an agent runtime.",
  };
}

// A likely shape, assembled from the candidates. Not universally correct.
export function likelyShape(answers, statuses) {
  const is = (id) => statuses[id].status === STATUS.consider;
  if (statuses.model.status === STATUS.notJustified) {
    return ["Deterministic software", "→ ordinary queries, APIs, and rules", "→ no model call"];
  }
  if (statuses.model.status === STATUS.undecided) {
    return ["Undecided — resolve questions 0 and 1 first"];
  }
  const lines = [];
  lines.push(is("agentRuntime")
    ? "Bounded agent runtime (model proposes the next step; the runtime validates and executes)"
    : "Fixed workflow");
  if (is("tools")) lines.push("→ authoritative tools (application-executed)");
  if (is("knowledge")) lines.push("→ retrieved knowledge");
  if (is("memory")) lines.push("→ scoped memory from earlier interactions");
  if (is("skills")) lines.push("→ reusable skill");
  lines.push(is("agentRuntime") ? "→ model decisions within the step budget" : "→ one model call");
  if (is("structuredOutput")) lines.push("→ validated structured output for downstream code");
  return lines;
}

export function summarize(state) {
  const answers = state.answers || {};
  const statuses = deriveStatuses(answers);
  const byStatus = (status) => CAPABILITIES
    .filter((capability) => statuses[capability.id].status === status)
    .map((capability) => capability.name);

  const unresolved = [];
  for (const capability of CAPABILITIES) {
    if (statuses[capability.id].status === STATUS.undecided) {
      unresolved.push(`${capability.name}: ${statuses[capability.id].reason}`);
    }
  }

  return {
    statuses,
    headline: headline(answers, statuses),
    consider: byStatus(STATUS.consider),
    notJustified: byStatus(STATUS.notJustified),
    undecided: byStatus(STATUS.undecided),
    future: byStatus(STATUS.future),
    shape: likelyShape(answers, statuses),
    unresolved,
  };
}
