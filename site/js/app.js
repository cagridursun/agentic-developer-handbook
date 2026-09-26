// Capstone 01 interactive experience: stage navigation, decision cards,
// progress, reference reveal, export. No framework, no backend, no network.

import {
  STAGES, CAPABILITIES, REFERENCE_DECISIONS, REFERENCE_AUTHORITY,
  REFLECTION_QUESTIONS,
} from "./capstone.js";
import { loadState, saveState, resetState } from "./storage.js";
import { buildDecisionsMarkdown, downloadMarkdown, copyMarkdown } from "./markdown-export.js";

let state = loadState();
let currentStage = firstOpenStage();

function firstOpenStage() {
  const open = STAGES.find((stage) => !state.completed[stage.id]);
  return (open || STAGES[0]).id;
}

function persist() {
  saveState(state);
  renderNav();
  renderProgress();
}

// ---------- Derived completion ----------

function decisionComplete(capabilityId) {
  const decision = state.decisions[capabilityId];
  return Boolean(decision && decision.use && (decision.why || "").trim() !== "");
}

function decidedCount() {
  return CAPABILITIES.filter((capability) => decisionComplete(capability.id)).length;
}

function allDecisionsComplete() {
  return decidedCount() === CAPABILITIES.length;
}

function syncDerivedCompletion() {
  state.completed.decisions = allDecisionsComplete();
  state.completed.authority =
    state.authority.influence.trim() !== "" && state.authority.deterministic.trim() !== "";
  state.completed.compare = Boolean(state.referenceRevealed);
}

// ---------- Navigation and progress ----------

function renderNav() {
  syncDerivedCompletion();
  const list = document.getElementById("stage-list");
  list.replaceChildren();
  for (const stage of STAGES) {
    const item = document.createElement("li");
    const button = document.createElement("button");
    button.type = "button";
    const done = Boolean(state.completed[stage.id]);
    const status = document.createElement("span");
    status.className = "stage-status";
    status.textContent = done ? "✓" : stage.id === currentStage ? "→" : "○";
    status.setAttribute("aria-hidden", "true");
    button.append(status, document.createTextNode(stage.label));
    if (stage.id === currentStage) {
      button.setAttribute("aria-current", "step");
    }
    if (done) {
      button.setAttribute("aria-label", stage.label + " (completed)");
    }
    button.addEventListener("click", () => showStage(stage.id));
    item.append(button);
    list.append(item);
  }
}

function renderProgress() {
  syncDerivedCompletion();
  const stagesDone = STAGES.filter((stage) => state.completed[stage.id]).length;
  document.getElementById("stages-figure").textContent = `${stagesDone} / ${STAGES.length}`;
  document.getElementById("stages-fill").style.width =
    `${(stagesDone / STAGES.length) * 100}%`;

  const decided = decidedCount();
  document.getElementById("decisions-figure").textContent =
    `${decided} / ${CAPABILITIES.length}`;
  document.getElementById("decisions-fill").style.width =
    `${(decided / CAPABILITIES.length) * 100}%`;
}

function showStage(stageId) {
  currentStage = stageId;
  for (const stage of STAGES) {
    const section = document.getElementById("stage-" + stage.id);
    section.hidden = stage.id !== stageId;
  }
  if (stageId === "decisions") {
    renderSummary();
  }
  if (stageId === "compare") {
    renderCompare();
  }
  renderNav();
  document.getElementById("stage-" + stageId).focus({ preventScroll: false });
}

function completeAndAdvance(stageId) {
  state.completed[stageId] = true;
  persist();
  const index = STAGES.findIndex((stage) => stage.id === stageId);
  showStage(STAGES[Math.min(index + 1, STAGES.length - 1)].id);
}

// ---------- Decisions stage ----------

function renderDecisionCards() {
  const host = document.getElementById("decision-cards");
  host.replaceChildren();
  for (const capability of CAPABILITIES) {
    const decision = state.decisions[capability.id] ||
        (state.decisions[capability.id] = { use: null, why: "", alternative: "" });

    const card = document.createElement("section");
    card.className = "card decision-card";

    const heading = document.createElement("h3");
    heading.append(document.createTextNode(capability.name));
    const hint = document.createElement("span");
    hint.className = "hint";
    hint.textContent = capability.hint;
    heading.append(hint);
    card.append(heading);

    const choice = document.createElement("div");
    choice.className = "usechoice";
    choice.setAttribute("role", "group");
    choice.setAttribute("aria-label", "Use " + capability.name + "?");
    for (const value of ["yes", "no"]) {
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = value === "yes" ? "Yes" : "No";
      button.setAttribute("aria-pressed", String(decision.use === value));
      button.addEventListener("click", () => {
        decision.use = value;
        for (const sibling of choice.querySelectorAll("button")) {
          sibling.setAttribute("aria-pressed",
              String(sibling.textContent.toLowerCase() === value));
        }
        persist();
        renderSummary();
      });
      choice.append(button);
    }
    card.append(choice);

    card.append(textareaField(capability.id + "-why", "Why / why not?", decision.why,
        (value) => { decision.why = value; persist(); }));
    card.append(textareaField(capability.id + "-alt", "Simpler alternative considered",
        decision.alternative, (value) => { decision.alternative = value; persist(); }));

    host.append(card);
  }
}

function textareaField(id, labelText, value, onChange) {
  const wrapper = document.createElement("div");
  const label = document.createElement("label");
  label.htmlFor = id;
  label.textContent = labelText;
  const area = document.createElement("textarea");
  area.id = id;
  area.value = value || "";
  area.addEventListener("input", () => onChange(area.value));
  wrapper.append(label, area);
  return wrapper;
}

function renderSummary() {
  const selected = CAPABILITIES
      .filter((capability) => state.decisions[capability.id]?.use === "yes")
      .map((capability) => capability.name);
  const excluded = CAPABILITIES
      .filter((capability) => state.decisions[capability.id]?.use === "no")
      .map((capability) => capability.name);
  fillList("summary-selected", selected, "nothing selected yet");
  fillList("summary-excluded", excluded, "nothing excluded yet");
}

function fillList(id, items, emptyText) {
  const list = document.getElementById(id);
  list.replaceChildren();
  if (items.length === 0) {
    const item = document.createElement("li");
    item.className = "muted";
    item.textContent = emptyText;
    list.append(item);
    return;
  }
  for (const text of items) {
    const item = document.createElement("li");
    item.textContent = text;
    list.append(item);
  }
}

// ---------- Compare stage (soft gate + comparison) ----------

function renderCompare() {
  const gate = document.getElementById("compare-gate");
  const body = document.getElementById("compare-body");
  if (state.referenceRevealed) {
    gate.hidden = true;
    body.hidden = false;
    renderComparison();
    return;
  }
  body.hidden = true;
  gate.hidden = false;
  document.getElementById("gate-incomplete").hidden = allDecisionsComplete();
  document.getElementById("gate-complete").hidden = !allDecisionsComplete();
}

function reveal() {
  state.referenceRevealed = true;
  state.completed.compare = true;
  persist();
  renderCompare();
}

function renderComparison() {
  const tbody = document.getElementById("compare-rows");
  tbody.replaceChildren();
  for (const capability of CAPABILITIES) {
    const mine = state.decisions[capability.id]?.use;
    const mineText = mine === "yes" ? "YES" : mine === "no" ? "NO" : "—";
    const reference = REFERENCE_DECISIONS[capability.id];
    const referenceText = reference.use ? "YES" : "NO";

    const row = document.createElement("tr");
    const name = document.createElement("th");
    name.scope = "row";
    name.textContent = capability.name;

    const mineCell = document.createElement("td");
    mineCell.textContent = mineText;
    const referenceCell = document.createElement("td");
    referenceCell.textContent = referenceText;

    // Differences are discussion points, not mistakes: mark with text, never
    // with red or a score.
    if (mine && mineText !== referenceText) {
      mineCell.classList.add("differs");
      mineCell.textContent += " (differs — worth discussing)";
    }

    row.append(name, mineCell, referenceCell);
    tbody.append(row);

    const whyRow = document.createElement("tr");
    const whyCell = document.createElement("td");
    whyCell.colSpan = 3;
    const details = document.createElement("details");
    details.className = "why";
    const summary = document.createElement("summary");
    summary.textContent = "Why did the reference choose this?";
    const text = document.createElement("p");
    text.textContent = reference.why;
    details.append(summary, text);
    whyCell.append(details);
    whyRow.append(whyCell);
    tbody.append(whyRow);
  }

  fillList("ref-influence", REFERENCE_AUTHORITY.influence, "");
  fillList("ref-application", REFERENCE_AUTHORITY.application, "");
}

// ---------- Reflection ----------

function renderReflection() {
  const host = document.getElementById("reflection-questions");
  host.replaceChildren();
  for (const question of REFLECTION_QUESTIONS) {
    host.append(textareaField("reflect-" + question.id, question.text,
        state.reflection[question.id],
        (value) => { state.reflection[question.id] = value; persist(); }));
  }
}

// ---------- Wiring ----------

function bindStaticControls() {
  document.getElementById("overview-continue").addEventListener("click",
      () => completeAndAdvance("overview"));
  document.getElementById("challenge-continue").addEventListener("click",
      () => completeAndAdvance("challenge"));
  document.getElementById("decisions-continue").addEventListener("click", () => {
    persist();
    showStage("authority");
  });
  document.getElementById("authority-continue").addEventListener("click", () => {
    persist();
    showStage("build");
  });

  const influence = document.getElementById("authority-influence");
  influence.value = state.authority.influence;
  influence.addEventListener("input", () => {
    state.authority.influence = influence.value;
    persist();
  });
  const deterministic = document.getElementById("authority-deterministic");
  deterministic.value = state.authority.deterministic;
  deterministic.addEventListener("input", () => {
    state.authority.deterministic = deterministic.value;
    persist();
  });

  bindCheckbox("build-done", "build");
  bindCheckbox("verify-done", "verify");

  document.getElementById("reflection-complete").addEventListener("click", () => {
    state.completed.reflection = true;
    persist();
    showStage("compare");
  });

  document.getElementById("gate-return").addEventListener("click",
      () => showStage("decisions"));
  document.getElementById("gate-reveal-anyway").addEventListener("click", reveal);
  document.getElementById("gate-reveal").addEventListener("click", reveal);

  document.getElementById("export-download").addEventListener("click",
      () => downloadMarkdown(buildDecisionsMarkdown(state)));
  document.getElementById("export-copy").addEventListener("click", async (event) => {
    await copyMarkdown(buildDecisionsMarkdown(state));
    event.target.textContent = "Copied";
    setTimeout(() => { event.target.textContent = "Copy Markdown"; }, 1500);
  });

  document.getElementById("reset-progress").addEventListener("click", () => {
    const confirmed = window.confirm(
        "Reset local progress for Capstone 01? This clears your decisions, "
        + "answers, and completion state in this browser.");
    if (confirmed) {
      resetState();
      state = loadState();
      currentStage = "overview";
      initialize();
    }
  });
}

function bindCheckbox(elementId, stageId) {
  const checkbox = document.getElementById(elementId);
  checkbox.checked = Boolean(state.completed[stageId]);
  checkbox.addEventListener("change", () => {
    state.completed[stageId] = checkbox.checked;
    persist();
  });
}

function initialize() {
  renderDecisionCards();
  renderReflection();
  renderSummary();
  document.getElementById("authority-influence").value = state.authority.influence;
  document.getElementById("authority-deterministic").value = state.authority.deterministic;
  document.getElementById("build-done").checked = Boolean(state.completed.build);
  document.getElementById("verify-done").checked = Boolean(state.completed.verify);
  renderNav();
  renderProgress();
  showStage(currentStage);
}

bindStaticControls();
initialize();
