// Generates a DECISIONS.md-shaped Markdown document from the learner's local
// answers. Browser-side only; the learner decides whether to save it into
// capstones/01-agentic-system/starter/DECISIONS.md. The site never edits
// repository files.

import { CAPABILITIES, REFLECTION_QUESTIONS } from "./capstone.js";

function cell(text) {
  return (text || "").replaceAll("|", "\\|").replaceAll(/\r?\n/g, " ").trim();
}

export function buildDecisionsMarkdown(state) {
  const lines = [];
  lines.push("# Architecture decisions — Capstone 01");
  lines.push("");
  lines.push("Exported from the local interactive learning UI. Review before");
  lines.push("saving into capstones/01-agentic-system/starter/DECISIONS.md.");
  lines.push("");
  lines.push("| Capability | Use? | Why / why not? | Simpler alternative considered |");
  lines.push("| --- | --- | --- | --- |");
  for (const capability of CAPABILITIES) {
    const decision = state.decisions[capability.id] || {};
    const use = decision.use === "yes" ? "YES" : decision.use === "no" ? "NO" : "";
    lines.push(`| ${capability.name} | ${use} | ${cell(decision.why)} | ${cell(decision.alternative)} |`);
  }
  lines.push("");
  lines.push("MCP is not in the table: it has not been taught yet (Milestone 8), and");
  lines.push("every capability in this capstone is local Java.");
  lines.push("");
  lines.push("## Decision authority");
  lines.push("");
  lines.push("### What decisions may the model influence?");
  lines.push("");
  lines.push((state.authority.influence || "(not answered yet)").trim());
  lines.push("");
  lines.push("### What decisions must remain deterministic or application-controlled?");
  lines.push("");
  lines.push((state.authority.deterministic || "(not answered yet)").trim());
  lines.push("");

  const answered = REFLECTION_QUESTIONS.filter(
    (question) => (state.reflection[question.id] || "").trim() !== "");
  if (answered.length > 0) {
    lines.push("## Reflection");
    lines.push("");
    for (const question of answered) {
      lines.push(`### ${question.text}`);
      lines.push("");
      lines.push(state.reflection[question.id].trim());
      lines.push("");
    }
  }
  return lines.join("\n");
}

export function downloadMarkdown(markdown) {
  const blob = new Blob([markdown], { type: "text/markdown" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "DECISIONS.md";
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export async function copyMarkdown(markdown) {
  await navigator.clipboard.writeText(markdown);
}
