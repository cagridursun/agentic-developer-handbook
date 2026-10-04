// Generates an ASSESSMENT.md from the learner's local answers, shaped like the
// canonical template in assessments/agentic-system-readiness/ASSESSMENT.md.
// Browser-side only: the site never edits repository files, and the learner
// decides where the exported document goes.

import {
  QUESTIONS, CAPABILITIES, AUTHORITY_QUESTIONS, RUNTIME_QUESTIONS,
  EVALUATION_QUESTIONS, OBSERVABILITY_QUESTIONS, SECURITY_QUESTIONS, STATUS,
} from "./readiness.js";
import { summarize } from "./readiness-logic.js";

const EMPTY = "_(not answered yet)_";

function text(value) {
  const trimmed = (value || "").trim();
  return trimmed === "" ? EMPTY : trimmed;
}

function cell(value) {
  return (value || "").replaceAll("|", "\\|").replaceAll(/\r?\n/g, " ").trim();
}

function answerLabel(answer) {
  return answer === "yes" ? "Yes" : answer === "no" ? "No"
    : answer === "unsure" ? "Unsure" : "—";
}

function question(id) {
  return QUESTIONS.find((candidate) => candidate.id === id);
}

export function buildAssessmentMarkdown(state) {
  const summary = summarize(state);
  const answers = state.answers || {};
  const lines = [];
  const push = (...items) => lines.push(...items);

  push("# Agentic System Readiness Assessment", "");
  push("Exported from the local interactive assessment. The capability statuses",
    "were derived from the answers by the rules in",
    "assessments/agentic-system-readiness/README.md — candidates to consider,",
    "not a score and not \"the correct architecture\".", "");
  push(`**${summary.headline.title}** ${summary.headline.detail}`, "");

  push("## Use case", "");
  push("### Problem statement", "", text(state.useCase.problem), "");
  push("### User / consumer", "", text(state.useCase.consumer), "");
  push("### Desired outcome", "", text(state.useCase.outcome), "");

  push("## Simpler alternative", "");
  push("### Can deterministic code solve this? (question 0)", "");
  push(`**${answerLabel(answers.deterministic)}.** ${text(state.simplest.deterministicWhy)}`, "");
  push("### Existing API, database, or workflow alternative", "",
    text(state.simplest.existingAlternative), "");
  push("### Why an LLM is or is not justified (question 1)", "");
  push(`**${answerLabel(answers.language)}.** ${text(state.simplest.languageWhy)}`, "");

  push("## Capability decisions", "");
  push("| # | Question | Answer |", "| --- | --- | --- |");
  for (const item of QUESTIONS.filter((candidate) => candidate.number >= 2)) {
    push(`| ${item.number} | ${cell(item.text)} | ${answerLabel(answers[item.id])} |`);
  }
  push("");
  push("| Capability | Consider? | Why / why not? | Simpler alternative |",
    "| --- | --- | --- | --- |");
  for (const capability of CAPABILITIES) {
    const derived = summary.statuses[capability.id];
    const notes = (state.capabilityNotes || {})[capability.id] || {};
    const why = (notes.why || "").trim() !== "" ? notes.why : derived.reason;
    push(`| ${capability.name} | ${derived.status} | ${cell(why)} | ${cell(notes.alternative)} |`);
  }
  push("");

  push("## Decision authority", "");
  for (const item of AUTHORITY_QUESTIONS) {
    push(`### ${item.text}`, "", text(state.authority[item.id]), "");
  }

  push("## Runtime boundary", "");
  for (const item of RUNTIME_QUESTIONS.always) {
    push(`### ${item.text}`, "", text(state.runtime[item.id]), "");
  }
  if (summary.statuses.agentRuntime.status === STATUS.consider) {
    push("### If using an Agent Runtime", "");
    for (const item of RUNTIME_QUESTIONS.agentRuntime) {
      push(`- **${item.text}:** ${cell(state.runtime[item.id]) || EMPTY}`);
    }
    push("");
  } else {
    push("### If not using one", "", text(state.runtime.workflowWhy), "");
  }

  push("## Evaluation plan", "");
  push("Not a capability: how behavior will be checked before it is relied on. Lab 09 teaches the concepts.", "");
  for (const item of EVALUATION_QUESTIONS) {
    push(`### ${item.text}`, "", text(state.production[item.id]), "");
  }

  push("## Observability plan", "");
  push("Not a capability: how a failed run will be reconstructed. Lab 10 teaches the concepts.", "");
  for (const item of OBSERVABILITY_QUESTIONS) {
    push(`### ${item.text}`, "", text(state.production[item.id]), "");
  }

  push("## Security plan", "");
  push("Not a capability: what controls what a model's proposal may cause. Lab 11 teaches the concepts.", "");
  for (const item of SECURITY_QUESTIONS) {
    push(`### ${item.text}`, "", text(state.production[item.id]), "");
  }

  push("## Final architecture shape", "");
  push("Likely shape derived from the candidates:", "", "```", ...summary.shape, "```", "");
  if ((state.result.shape || "").trim() !== "") {
    push("In my words:", "", state.result.shape.trim(), "");
  }

  push("## Rejected complexity", "");
  if (summary.notJustified.length === 0) {
    push("_(nothing marked NOT JUSTIFIED yet)_");
  }
  for (const name of summary.notJustified) {
    push(`- ${name}`);
  }
  push("");
  if ((state.result.rejected || "").trim() !== "") {
    push(state.result.rejected.trim(), "");
  }

  push("## Unresolved questions", "");
  const openAuthority = summary.statuses.model.status === STATUS.notJustified ? []
    : AUTHORITY_QUESTIONS.filter((item) => (state.authority[item.id] || "").trim() === "")
        .map((item) => `Decision authority: ${item.text}`);
  const unresolved = [...summary.unresolved, ...openAuthority];
  if (unresolved.length === 0) {
    push("None recorded.");
  }
  for (const item of unresolved) {
    push(`- ${item}`);
  }
  push("");

  push("## Protocol boundary (MCP)", "");
  push(`Question 8 (${cell(question("protocolBoundary").text)}): ${answerLabel(answers.protocolBoundary)}.`, "");
  push("A local capability never needs MCP, and a remote capability does not",
    "automatically require it: one explicit API integration is often simpler. MCP",
    "may be justified when compatible clients need standardized discovery and",
    "invocation. Discovery is not permission — the allowlist stays in the",
    "application. See Lab 08.", "");

  return lines.join("\n");
}

export function downloadMarkdown(markdown) {
  const blob = new Blob([markdown], { type: "text/markdown" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "ASSESSMENT.md";
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export async function copyMarkdown(markdown) {
  await navigator.clipboard.writeText(markdown);
}
