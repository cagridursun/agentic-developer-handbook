// Agentic System Readiness Assessment — data for the interactive layer.
//
// The canonical content lives in assessments/agentic-system-readiness/.
// This file holds only the small structured representation the UI needs:
// question wording mirrors README.md there, and a Python stdlib test
// (scripts/tests/test_assessment.py) keeps the two from drifting.

export const STAGES = [
  { id: "overview", label: "Overview" },
  { id: "usecase", label: "Use case" },
  { id: "simplest", label: "Simplest solution" },
  { id: "capabilities", label: "Capability decisions" },
  { id: "authority", label: "Decision Authority" },
  { id: "runtime", label: "Runtime boundary" },
  { id: "result", label: "Result" },
  { id: "examples", label: "Examples & reflection" },
];

// Answers are "yes", "no", or "unsure". Unanswered is null.
export const ANSWERS = [
  { value: "yes", label: "Yes" },
  { value: "no", label: "No" },
  { value: "unsure", label: "Unsure" },
];

// The decision order, from the simplest answer toward more complexity.
// source: assessments/agentic-system-readiness/README.md, "The decision order"
export const QUESTIONS = [
  {
    id: "deterministic", number: 0, stage: "simplest",
    text: "Can deterministic software solve this problem adequately?",
    help: "An ordinary query, API call, rule, template, or workflow. If yes, prefer ordinary software — do not introduce an LLM merely because this is an AI handbook.",
  },
  {
    id: "language", number: 1, stage: "simplest", capability: "model",
    text: "Does the task require probabilistic language understanding, reasoning, or generation?",
    help: "If no, prefer deterministic software: no model, and nothing below is justified.",
  },
  {
    id: "consumer", number: 2, stage: "capabilities", capability: "structuredOutput",
    text: "Does downstream application code need to consume the model output?",
    help: "A human reading prose is not a downstream consumer. A program parsing fields is.",
  },
  {
    id: "authoritative", number: 3, stage: "capabilities", capability: "tools",
    text: "Does the system need current or authoritative application/system data, or actions?",
    help: "Status, orders, permissions, deployments: facts owned by a system, never model recall.",
  },
  {
    id: "unstructured", number: 4, stage: "capabilities", capability: "knowledge",
    text: "Does the model need unstructured information that exists outside the current interaction?",
    help: "Manuals, runbooks, policies. A lookup by id is a database query, not RAG.",
  },
  {
    id: "retained", number: 5, stage: "capabilities", capability: "memory",
    text: "Must information created by earlier interactions survive into later interactions?",
    help: "Retained, scoped, forgettable state — not the knowledge corpus, and not just the current conversation.",
  },
  {
    id: "procedure", number: 6, stage: "capabilities", capability: "skills",
    text: "Is there a reusable, model-performed procedure that should be reviewed and reused independently of individual prompts?",
    help: "How a task should be done, shared across prompts or tasks. A skill changes procedure; it adds no capability.",
  },
  {
    id: "nextStep", number: 7, stage: "capabilities", capability: "agentRuntime",
    text: "Is the next useful action genuinely unknown in advance, and dependent on model interpretation of observations?",
    help: "If you can write the sequence of steps down in advance, the answer is no — prefer a fixed workflow.",
  },
  // Question 8 was reworded when MCP was taught (Lab 08). It has a new id so an
  // answer stored for the old, broader question — "is anything outside the
  // process?" — is never silently read as "a standardized boundary is needed".
  {
    id: "protocolBoundary", number: 8, stage: "capabilities", capability: "mcp",
    text: "Does the system need a standardized boundary for externally owned capabilities, one that compatible clients can discover and invoke, where an explicit API integration is not enough?",
    help: "Local code never needs MCP, and one stable service used by one application is usually a normal API client. Yes only when standardized discovery and invocation solve a real integration problem. Discovery is not permission: the allowlist stays in the application.",
  },
];

// The eight taught capabilities. Each one has to be earned by an answer above.
export const CAPABILITIES = [
  { id: "model", name: "Model", hint: "Lab 01" },
  { id: "structuredOutput", name: "Structured Output", hint: "Lab 02" },
  { id: "tools", name: "Tools", hint: "Lab 03" },
  { id: "knowledge", name: "Knowledge / RAG", hint: "Lab 04" },
  { id: "memory", name: "Memory", hint: "Lab 05" },
  { id: "skills", name: "Skills", hint: "Lab 06" },
  { id: "agentRuntime", name: "Agent Runtime", hint: "Lab 07" },
  { id: "mcp", name: "MCP (protocol boundary)", hint: "Lab 08" },
];

export const STATUS = {
  consider: "CONSIDER",
  notJustified: "NOT JUSTIFIED",
  undecided: "UNDECIDED",
};

// source: docs/model-vs-decision-authority.md, applied as assessment questions.
export const AUTHORITY_QUESTIONS = [
  { id: "influence", text: "What decisions may the model influence?" },
  { id: "binding", text: "What decisions become binding on the system?" },
  { id: "deterministic", text: "What remains deterministic or application-controlled?" },
  { id: "authoritative", text: "What data is authoritative?" },
  { id: "sideEffects", text: "What actions create side effects?" },
  { id: "authorization", text: "What requires authorization?" },
  { id: "approval", text: "What requires human approval?" },
];

export const RUNTIME_QUESTIONS = {
  always: [
    { id: "stops", text: "What stops the run?" },
    { id: "budget", text: "What time, token, or cost budget exists?" },
  ],
  agentRuntime: [
    { id: "allowedActions", text: "Allowed actions" },
    { id: "maxSteps", text: "Max steps" },
    { id: "failure", text: "Failure behavior — tool failure, rejected proposal, exhausted budget" },
  ],
  workflow: [
    { id: "workflowWhy", text: "Why is a fixed workflow, a single model call, or ordinary software sufficient? Write down the known sequence of steps." },
  ],
};

// Evaluation is a plan, not a capability: it is asked after the design, never
// selected like Model or Tools. The old single "evaluation" question was
// replaced by these five with new ids, so answers stored for it are not
// reinterpreted; the storage key and state shape are unchanged.
export const EVALUATION_QUESTIONS = [
  { id: "evalCases", text: "What representative cases will you use?" },
  { id: "evalSuccess", text: "What behavior counts as success?" },
  { id: "evalDeterministic", text: "Which failures can be checked deterministically?" },
  { id: "evalHuman", text: "Which behaviors require human judgment?" },
  { id: "evalTriggers", text: "What change would trigger re-evaluation?" },
];

export const PRODUCTION_QUESTIONS = [
  { id: "observability", text: "What must be observable?" },
  { id: "trust", text: "What trust and security boundaries exist?" },
];

// Summaries only — the canonical worked examples are Markdown in the
// repository. Link to GitHub: the published site contains only site/.
const EXAMPLES_BASE =
  "https://github.com/cagridursun/agentic-developer-handbook/blob/main/assessments/agentic-system-readiness/examples/";
export const EXAMPLES = [
  {
    file: "deterministic-lookup.md",
    title: "Order status lookup",
    lands: "Deterministic software",
    lesson: "Not every product feature needs AI.",
  },
  {
    file: "model-only-task.md",
    title: "Customer-facing release notes",
    lands: "One model call",
    lesson: "LLM use does not imply agent use.",
  },
  {
    file: "rag-assistant.md",
    title: "Technical manuals assistant",
    lands: "Model + Knowledge / RAG",
    lesson: "A RAG assistant is not an agent by default.",
  },
  {
    file: "incident-investigator.md",
    title: "Checkout latency investigation",
    lands: "Bounded agent runtime",
    lesson: "An agent earns its place only when the next step depends on observations.",
  },
].map((example) => ({ ...example, href: EXAMPLES_BASE + example.file }));

export const REFLECTION_QUESTIONS = [
  { id: "tempting", text: "Which capability was most tempting to add, and what stopped you?" },
  { id: "workflow", text: "If you kept an agent runtime: what would have to be true for a fixed workflow to be the better choice?" },
  { id: "authority", text: "Which decision in your design must never move to the model?" },
];
