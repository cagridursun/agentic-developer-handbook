// Agentic System Readiness Assessment: stage navigation, questions, derived
// candidates, result, export. No framework, no backend, no network, and no
// model call — the summary comes from readiness-logic.js.

import {
  STAGES, ANSWERS, QUESTIONS, CAPABILITIES, STATUS, AUTHORITY_QUESTIONS,
  RUNTIME_QUESTIONS, EVALUATION_QUESTIONS, OBSERVABILITY_QUESTIONS, SECURITY_QUESTIONS, EXAMPLES, REFLECTION_QUESTIONS,
} from "./readiness.js";
import { summarize, deterministicFirst } from "./readiness-logic.js";
import { loadState, saveState, resetState } from "./readiness-storage.js";
import { buildAssessmentMarkdown, downloadMarkdown, copyMarkdown } from "./readiness-export.js";

const USE_CASE_FIELDS = [
  { id: "problem", text: "Problem statement — what problem exists today?" },
  { id: "consumer", text: "User / consumer — who or what consumes the result?" },
  { id: "outcome", text: "Desired outcome — what does “solved” look like?" },
];

const CORE_QUESTIONS = QUESTIONS.filter((question) => question.stage === "capabilities");

let state = loadState();
let currentStage = firstOpenStage();

function firstOpenStage() {
  syncDerivedCompletion();
  const open = STAGES.find((stage) => !state.completed[stage.id]);
  return (open || STAGES[0]).id;
}

function filled(value) {
  return (value || "").trim() !== "";
}

// ---------- Derived completion ----------

function syncDerivedCompletion() {
  const answers = state.answers;
  const summary = summarize(state);
  const noModel = summary.statuses.model.status === STATUS.notJustified;
  const runtimeCandidate = summary.statuses.agentRuntime.status === STATUS.consider;

  state.completed.usecase = filled(state.useCase.problem);
  state.completed.simplest = Boolean(answers.deterministic) &&
    (answers.deterministic === "yes" || Boolean(answers.language));
  state.completed.capabilities = deterministicFirst(answers) ||
    CORE_QUESTIONS.every((question) => Boolean(answers[question.id]));
  state.completed.authority = noModel ||
    AUTHORITY_QUESTIONS.every((question) => filled(state.authority[question.id]));
  const specific = runtimeCandidate ? RUNTIME_QUESTIONS.agentRuntime : RUNTIME_QUESTIONS.workflow;
  state.completed.runtime = noModel ||
    [...RUNTIME_QUESTIONS.always, ...specific].every((question) => filled(state.runtime[question.id]));
}

function persist() {
  syncDerivedCompletion();
  saveState(state);
  renderNav();
}

// ---------- Navigation ----------

function renderNav() {
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

function showStage(stageId) {
  currentStage = stageId;
  for (const stage of STAGES) {
    document.getElementById("stage-" + stage.id).hidden = stage.id !== stageId;
  }
  refreshDerived();
  if (stageId === "result") {
    renderResult();
  }
  renderNav();
  document.getElementById("stage-" + stageId).focus({ preventScroll: false });
}

// ---------- Field builders ----------

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

function statusBadge(status) {
  const badge = document.createElement("span");
  badge.className = "status-badge " + statusClass(status);
  badge.textContent = status;
  return badge;
}

function statusClass(status) {
  return {
    [STATUS.consider]: "is-consider",
    [STATUS.notJustified]: "is-not-justified",
    [STATUS.undecided]: "is-undecided",
  }[status] || "is-undecided";
}

// One question: a native radio group (keyboard-accessible by default), help
// text, an optional derived-status line, and optional reasoning fields.
function questionCard(question) {
  const card = document.createElement("section");
  card.className = "card question-card";
  card.id = "question-" + question.id;

  const fieldset = document.createElement("fieldset");
  const legend = document.createElement("legend");
  legend.textContent = `${question.number}. ${question.text}`;
  fieldset.append(legend);

  const help = document.createElement("p");
  help.className = "muted help";
  help.textContent = question.help;
  fieldset.append(help);

  const choices = document.createElement("div");
  choices.className = "answer-choices";
  for (const answer of ANSWERS) {
    const id = `${question.id}-${answer.value}`;
    const input = document.createElement("input");
    input.type = "radio";
    input.name = "answer-" + question.id;
    input.id = id;
    input.value = answer.value;
    input.checked = state.answers[question.id] === answer.value;
    input.addEventListener("change", () => {
      state.answers[question.id] = answer.value;
      persist();
      refreshDerived();
    });
    const label = document.createElement("label");
    label.htmlFor = id;
    label.textContent = answer.label;
    const option = document.createElement("span");
    option.className = "answer-option";
    option.append(input, label);
    choices.append(option);
  }
  fieldset.append(choices);
  card.append(fieldset);

  if (question.capability) {
    const capability = CAPABILITIES.find((candidate) => candidate.id === question.capability);
    const derived = document.createElement("p");
    derived.className = "derived";
    derived.id = "derived-" + capability.id;
    card.append(derived);

    const notes = state.capabilityNotes[capability.id] ||
        (state.capabilityNotes[capability.id] = { why: "", alternative: "" });
    card.append(textareaField(`${capability.id}-why`, `${capability.name}: why / why not?`,
        notes.why, (value) => { notes.why = value; persist(); }));
    card.append(textareaField(`${capability.id}-alt`, "Simpler alternative considered",
        notes.alternative, (value) => { notes.alternative = value; persist(); }));
  }
  return card;
}

// ---------- Stage rendering ----------

function renderUseCase() {
  const host = document.getElementById("usecase-fields");
  host.replaceChildren();
  for (const field of USE_CASE_FIELDS) {
    host.append(textareaField("usecase-" + field.id, field.text, state.useCase[field.id],
        (value) => { state.useCase[field.id] = value; persist(); }));
  }
}

function renderSimplest() {
  const host = document.getElementById("simplest-questions");
  host.replaceChildren();
  const [deterministic, language] = QUESTIONS.filter((question) => question.stage === "simplest");

  const first = questionCard(deterministic);
  first.append(
    textareaField("simplest-deterministicWhy", "Why?", state.simplest.deterministicWhy,
        (value) => { state.simplest.deterministicWhy = value; persist(); }),
    textareaField("simplest-existingAlternative",
        "Existing API, database, or workflow alternative — and what it would fail to do",
        state.simplest.existingAlternative,
        (value) => { state.simplest.existingAlternative = value; persist(); }));
  host.append(first);

  const second = questionCard(language);
  second.append(textareaField("simplest-languageWhy", "Why an LLM is or is not justified",
      state.simplest.languageWhy, (value) => { state.simplest.languageWhy = value; persist(); }));
  host.append(second);
}

function renderCapabilities() {
  const host = document.getElementById("capability-questions");
  host.replaceChildren();

  const model = document.createElement("section");
  model.className = "card";
  const heading = document.createElement("h2");
  heading.textContent = "Model";
  const derived = document.createElement("p");
  derived.className = "derived";
  derived.id = "derived-model";
  model.append(heading, derived);
  const notes = state.capabilityNotes.model ||
      (state.capabilityNotes.model = { why: "", alternative: "" });
  model.append(textareaField("model-alt", "Simpler alternative considered for the model",
      notes.alternative, (value) => { notes.alternative = value; persist(); }));
  host.append(model);

  for (const question of CORE_QUESTIONS) {
    host.append(questionCard(question));
  }
}

function renderAuthority() {
  const host = document.getElementById("authority-fields");
  host.replaceChildren();
  for (const question of AUTHORITY_QUESTIONS) {
    host.append(textareaField("authority-" + question.id, question.text,
        state.authority[question.id],
        (value) => { state.authority[question.id] = value; persist(); }));
  }
}

function renderRuntime() {
  const host = document.getElementById("runtime-fields");
  host.replaceChildren();
  const add = (questions, groupId) => {
    const group = document.createElement("div");
    if (groupId) {
      group.id = groupId;
    }
    for (const question of questions) {
      group.append(textareaField("runtime-" + question.id, question.text,
          state.runtime[question.id],
          (value) => { state.runtime[question.id] = value; persist(); }));
    }
    host.append(group);
  };
  add(RUNTIME_QUESTIONS.always);
  add(RUNTIME_QUESTIONS.agentRuntime, "runtime-agent");
  add(RUNTIME_QUESTIONS.workflow, "runtime-workflow");

  const evaluation = document.getElementById("evaluation-fields");
  evaluation.replaceChildren();
  for (const question of EVALUATION_QUESTIONS) {
    evaluation.append(textareaField("production-" + question.id, question.text,
        state.production[question.id],
        (value) => { state.production[question.id] = value; persist(); }));
  }

  const observability = document.getElementById("observability-fields");
  observability.replaceChildren();
  for (const question of OBSERVABILITY_QUESTIONS) {
    observability.append(textareaField("production-" + question.id, question.text,
        state.production[question.id],
        (value) => { state.production[question.id] = value; persist(); }));
  }

  const security = document.getElementById("security-fields");
  security.replaceChildren();
  for (const question of SECURITY_QUESTIONS) {
    security.append(textareaField("production-" + question.id, question.text,
        state.production[question.id],
        (value) => { state.production[question.id] = value; persist(); }));
  }
}

function renderExamples() {
  const list = document.getElementById("example-list");
  list.replaceChildren();
  for (const example of EXAMPLES) {
    const item = document.createElement("li");
    const link = document.createElement("a");
    link.href = example.href;
    link.textContent = example.title;
    const lands = document.createElement("span");
    lands.className = "muted";
    lands.textContent = ` — lands on: ${example.lands}. `;
    const lesson = document.createElement("em");
    lesson.textContent = example.lesson;
    item.append(link, lands, lesson);
    list.append(item);
  }

  const host = document.getElementById("reflection-questions");
  host.replaceChildren();
  for (const question of REFLECTION_QUESTIONS) {
    host.append(textareaField("reflect-" + question.id, question.text,
        state.reflection[question.id],
        (value) => { state.reflection[question.id] = value; persist(); }));
  }
}

// ---------- Derived views (updated on every answer) ----------

function refreshDerived() {
  const summary = summarize(state);
  const answers = state.answers;

  for (const capability of CAPABILITIES) {
    const line = document.getElementById("derived-" + capability.id);
    if (line) {
      const derived = summary.statuses[capability.id];
      line.replaceChildren(document.createTextNode(capability.name + ": "),
          statusBadge(derived.status), document.createTextNode(" " + derived.reason));
    }
  }

  // Simplest stage: question 1 only matters when question 0 is not "yes".
  const language = document.getElementById("question-language");
  if (language) {
    language.hidden = answers.deterministic === "yes";
  }
  const outcome = document.getElementById("simplest-outcome");
  const closed = deterministicFirst(answers);
  outcome.hidden = !closed;
  outcome.replaceChildren();
  if (closed) {
    const title = document.createElement("strong");
    title.textContent = summary.headline.title + " ";
    outcome.append(title, document.createTextNode(summary.headline.detail));
  }

  // Capability stage: closed while ordinary software is the answer.
  document.getElementById("capabilities-closed").hidden = !closed;
  document.getElementById("capabilities-open").hidden = closed;
  document.getElementById("capabilities-closed-title").textContent = summary.headline.title + " ";
  document.getElementById("capabilities-closed-detail").textContent = summary.headline.detail;

  document.getElementById("authority-no-model").hidden =
      summary.statuses.model.status !== STATUS.notJustified;

  // Runtime stage: agent-runtime fields only when a runtime is a candidate.
  const runtimeCandidate = summary.statuses.agentRuntime.status === STATUS.consider;
  document.getElementById("runtime-agent").hidden = !runtimeCandidate;
  document.getElementById("runtime-workflow").hidden = runtimeCandidate;
  document.getElementById("runtime-intro").textContent = runtimeCandidate
    ? "An agent runtime is a candidate. Write its envelope down before building the loop: allowed actions, budget, stop conditions, failure behavior."
    : "No agent runtime is a candidate. Say why the known sequence — a workflow, a single call, or ordinary software — is sufficient, and what still bounds it.";

  renderRail(summary);
}

function renderRail(summary) {
  const rail = document.getElementById("status-rail");
  rail.replaceChildren();
  for (const capability of CAPABILITIES) {
    const item = document.createElement("li");
    const name = document.createElement("span");
    name.textContent = capability.name;
    item.append(name, statusBadge(summary.statuses[capability.id].status));
    rail.append(item);
  }
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

function renderResult() {
  const summary = summarize(state);

  const headline = document.getElementById("result-headline");
  headline.replaceChildren();
  const title = document.createElement("strong");
  title.textContent = summary.headline.title;
  const detail = document.createElement("p");
  detail.textContent = summary.headline.detail;
  headline.append(title, detail);

  document.getElementById("result-usecase").textContent =
      filled(state.useCase.problem) ? state.useCase.problem.trim() : "(no problem statement yet)";
  document.getElementById("result-alternative").textContent =
      filled(state.simplest.existingAlternative)
        ? state.simplest.existingAlternative.trim() : "(none recorded yet)";

  fillList("result-consider", summary.consider, "nothing — which may be exactly right");
  fillList("result-not-justified", summary.notJustified, "nothing ruled out yet");

  const rows = document.getElementById("result-rows");
  rows.replaceChildren();
  for (const capability of CAPABILITIES) {
    const derived = summary.statuses[capability.id];
    const row = document.createElement("tr");
    const name = document.createElement("th");
    name.scope = "row";
    name.textContent = capability.name;
    const status = document.createElement("td");
    status.append(statusBadge(derived.status));
    const why = document.createElement("td");
    why.textContent = derived.reason;
    row.append(name, status, why);
    rows.append(row);
  }

  document.getElementById("result-shape").textContent = summary.shape.join("\n");

  const authority = document.getElementById("result-authority");
  authority.replaceChildren();
  for (const question of AUTHORITY_QUESTIONS) {
    const term = document.createElement("dt");
    term.textContent = question.text;
    const description = document.createElement("dd");
    description.textContent = filled(state.authority[question.id])
      ? state.authority[question.id].trim() : "(not answered yet)";
    authority.append(term, description);
  }

  fillList("result-rejected", summary.notJustified, "nothing marked NOT JUSTIFIED yet");

  const openAuthority = summary.statuses.model.status === STATUS.notJustified ? []
    : AUTHORITY_QUESTIONS.filter((question) => !filled(state.authority[question.id]))
        .map((question) => "Decision authority: " + question.text);
  fillList("result-unresolved", [...summary.unresolved, ...openAuthority], "none recorded");
}

function renderResultFields() {
  document.getElementById("result-shape-field").replaceChildren(
      textareaField("result-shape-text", "The shape in your own words (optional)",
          state.result.shape, (value) => { state.result.shape = value; persist(); }));
  document.getElementById("result-rejected-field").replaceChildren(
      textareaField("result-rejected-text", "Why each rejected capability stays out (optional)",
          state.result.rejected, (value) => { state.result.rejected = value; persist(); }));
}

// ---------- Wiring ----------

function exportDownload() {
  downloadMarkdown(buildAssessmentMarkdown(state));
}

function bindStaticControls() {
  document.getElementById("overview-continue").addEventListener("click", () => {
    state.completed.overview = true;
    persist();
    showStage("usecase");
  });
  for (const button of document.querySelectorAll("[data-next]")) {
    button.addEventListener("click", () => {
      persist();
      showStage(button.dataset.next);
    });
  }
  for (const button of document.querySelectorAll("[data-go]")) {
    button.addEventListener("click", () => showStage(button.dataset.go));
  }
  document.getElementById("result-complete").addEventListener("click", () => {
    state.completed.result = true;
    persist();
    showStage("examples");
  });
  document.getElementById("examples-complete").addEventListener("click", () => {
    state.completed.examples = true;
    persist();
  });

  document.getElementById("result-export").addEventListener("click", exportDownload);
  document.getElementById("export-download").addEventListener("click", exportDownload);
  document.getElementById("export-copy").addEventListener("click", async (event) => {
    const button = event.currentTarget;
    try {
      await copyMarkdown(buildAssessmentMarkdown(state));
      button.textContent = "Copied";
    } catch {
      button.textContent = "Copy failed — use Export";
    }
    setTimeout(() => { button.textContent = "Copy Markdown"; }, 1500);
  });

  document.getElementById("reset-progress").addEventListener("click", () => {
    const confirmed = window.confirm(
        "Reset this assessment? This clears its answers in this browser. "
        + "Capstone 01 progress is not affected.");
    if (confirmed) {
      resetState();
      state = loadState();
      currentStage = "overview";
      initialize();
    }
  });
}

function initialize() {
  syncDerivedCompletion();
  renderUseCase();
  renderSimplest();
  renderCapabilities();
  renderAuthority();
  renderRuntime();
  renderResultFields();
  renderExamples();
  refreshDerived();
  renderNav();
  showStage(currentStage);
}

bindStaticControls();
initialize();
