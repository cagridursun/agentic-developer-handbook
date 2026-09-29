"""Static checks for the Agentic System Readiness Assessment.

Standard-library unittest only — no browser automation, no Node, no network.
These checks keep the canonical Markdown, the interactive site, and the
project's boundaries consistent; they do not replace reading the assessment.

Run from the repository root:

    python -m unittest discover -s scripts/tests
"""

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
ASSESSMENT_DIR = REPO_ROOT / "assessments" / "agentic-system-readiness"
SITE = REPO_ROOT / "site"

CAPABILITY_NAMES = [
    "Model", "Structured Output", "Tools", "Knowledge / RAG",
    "Memory", "Skills", "Agent Runtime",
]

AUTHORITY_QUESTIONS = [
    "What decisions may the model influence?",
    "What decisions become binding on the system?",
    "What remains deterministic or application-controlled?",
    "What data is authoritative?",
    "What actions create side effects?",
    "What requires authorization?",
    "What requires human approval?",
    "What stops the run?",
    "What time, token, or cost budget exists?",
]

EXAMPLES = [
    "deterministic-lookup.md", "model-only-task.md",
    "rag-assistant.md", "incident-investigator.md",
]

# Scoring vocabulary the assessment must not use — except to say it is not one.
SCORING_TERMS = re.compile(
    r"\b(score|scores|scoring|percentage|maturity|grade|graded|grading|pass/fail|agentic score)\b",
    re.IGNORECASE)
NEGATION = re.compile(r"\b(not|never|no|isn't|without)\b", re.IGNORECASE)


def read(path):
    return path.read_text(encoding="utf-8")


def normalized(text):
    return re.sub(r"\s+", " ", text)


class AssessmentFilesTest(unittest.TestCase):

    def test_files_exist(self):
        for path in [
            REPO_ROOT / "assessments" / "README.md",
            ASSESSMENT_DIR / "README.md",
            ASSESSMENT_DIR / "ASSESSMENT.md",
            *[ASSESSMENT_DIR / "examples" / name for name in EXAMPLES],
        ]:
            self.assertTrue(path.is_file(), path)

    def test_assessments_readme_distinguishes_artifacts(self):
        text = normalized(read(REPO_ROOT / "assessments" / "README.md"))
        self.assertIn("not an automatic architecture generator", text)
        self.assertIn("structured engineering conversation", text)
        for word in ["Labs", "Capstones", "Assessments"]:
            self.assertIn(word, text)

    def test_all_seven_capabilities_appear(self):
        for path in [ASSESSMENT_DIR / "README.md", ASSESSMENT_DIR / "ASSESSMENT.md",
                     SITE / "js" / "readiness.js"]:
            text = read(path)
            for name in CAPABILITY_NAMES:
                self.assertIn(name, text, f"{name} missing from {path.name}")

    def test_template_uses_neutral_statuses(self):
        template = read(ASSESSMENT_DIR / "ASSESSMENT.md")
        for status in ["CONSIDER", "NOT JUSTIFIED", "UNDECIDED"]:
            self.assertIn(status, template)
        self.assertIn("| Capability | Consider? | Why / why not? | Simpler alternative |", template)
        for forbidden in ["PASS", "FAIL", "GOOD", "BAD"]:
            self.assertIsNone(re.search(rf"\b{forbidden}\b", template), forbidden)


class DecisionOrderTest(unittest.TestCase):

    def setUp(self):
        self.readme = normalized(read(ASSESSMENT_DIR / "README.md"))
        self.data = read(SITE / "js" / "readiness.js")

    def test_deterministic_software_is_question_zero(self):
        self.assertIn("| 0 | Can deterministic software solve this problem adequately?",
                      self.readme)
        self.assertIn("Start with deterministic software.", self.readme)
        first = re.search(r'id: "(\w+)", number: 0', self.data)
        self.assertEqual("deterministic", first.group(1))

    def test_agent_runtime_requires_unknown_next_step_from_observations(self):
        question = ("Is the next useful action genuinely unknown in advance, and "
                    "dependent on model interpretation of observations?")
        for text in [self.readme, normalized(read(ASSESSMENT_DIR / "ASSESSMENT.md")),
                     self.data]:
            self.assertIn(question, text)
        self.assertIn("prefer a fixed workflow", self.readme.lower())

    def test_site_questions_mirror_the_canonical_readme(self):
        texts = re.findall(r'text: "([^"]+)",\n\s+help:', self.data)
        self.assertEqual(9, len(texts))
        for text in texts:
            self.assertIn(text, self.readme, text)

    def test_decision_authority_questions_exist(self):
        template = read(ASSESSMENT_DIR / "ASSESSMENT.md")
        for question in AUTHORITY_QUESTIONS:
            self.assertIn(question, template, question)
            self.assertIn(question, self.readme, question)
            self.assertIn(question, self.data, question)
        self.assertIn("docs/model-vs-decision-authority.md", template)


class ExamplesTest(unittest.TestCase):

    def example(self, name):
        return read(ASSESSMENT_DIR / "examples" / name)

    def test_deterministic_lookup_rejects_unnecessary_llm_use(self):
        text = self.example("deterministic-lookup.md")
        self.assertIn("| Model | NOT JUSTIFIED |", text)
        self.assertIn("| Agent Runtime | NOT JUSTIFIED |", text)
        self.assertIn("ordinary deterministic application logic", text)
        self.assertIn("not every product feature needs ai", text.lower())

    def test_model_only_task_is_not_an_agent(self):
        text = self.example("model-only-task.md")
        self.assertIn("| Model | CONSIDER |", text)
        self.assertIn("| Agent Runtime | NOT JUSTIFIED |", text)
        self.assertIn("| Knowledge / RAG | NOT JUSTIFIED |", text)
        self.assertIn("| Memory | NOT JUSTIFIED |", text)
        self.assertIn("LLM use does not imply agent use.", text)

    def test_rag_assistant_is_not_an_agent_by_default(self):
        text = self.example("rag-assistant.md")
        self.assertIn("| Knowledge / RAG | CONSIDER |", text)
        self.assertIn("| Agent Runtime | NOT JUSTIFIED |", text)

    def test_incident_investigator_has_authority_boundary_and_workflow_alternative(self):
        text = self.example("incident-investigator.md")
        self.assertIn("| Agent Runtime | CONSIDER |", text)
        self.assertIn("## Decision authority", text)
        self.assertIn("fixed workflow", text.lower())
        self.assertIn("capstones/01-agentic-system/README.md", text)


class McpBoundaryTest(unittest.TestCase):
    """MCP is taught (Lab 08), so it is an actual candidate capability — earned
    only by question 8, never by the mere presence of an LLM."""

    def setUp(self):
        self.logic = read(SITE / "js" / "readiness-logic.js")
        self.data = read(SITE / "js" / "readiness.js")

    def test_mcp_is_a_real_candidate_earned_by_question_8(self):
        # MCP follows its own question like the other capabilities...
        self.assertIn('mcp: "protocolBoundary"', self.logic)
        self.assertRegex(self.data, r'id: "protocolBoundary", number: 8, stage: "capabilities", capability: "mcp"')
        # ...and rule 4 can still hold it back when there is nothing to carry.
        rule = self.logic.split("// Rule 4")[1].split("return result;")[0]
        self.assertIn("STATUS.consider", rule)
        self.assertIn("STATUS.undecided", rule)
        self.assertIn("result.tools.status !== STATUS.consider", rule)

    def test_future_consideration_status_is_gone(self):
        for path in [ASSESSMENT_DIR / "README.md", ASSESSMENT_DIR / "ASSESSMENT.md",
                     *(ASSESSMENT_DIR / "examples").glob("*.md"),
                     SITE / "readiness-assessment" / "index.html",
                     *SITE.glob("js/readiness*.js")]:
            text = read(path)
            self.assertNotIn("FUTURE CONSIDERATION", text, path.name)
            self.assertNotIn("STATUS.future", text, path.name)
            self.assertNotIn("Milestone 8", text, path.name)

    def test_mcp_needs_a_reason_beyond_being_remote_or_using_an_llm(self):
        readme = normalized(read(ASSESSMENT_DIR / "README.md"))
        self.assertIn("never a candidate just because the application contains an LLM", readme)
        self.assertIn("does not automatically require MCP", readme)
        self.assertIn("Is one explicit API integration sufficient?", readme)
        self.assertIn("labs/08-mcp/README.md", readme)
        for path in [ASSESSMENT_DIR / "README.md", ASSESSMENT_DIR / "ASSESSMENT.md",
                     SITE / "js" / "readiness-export.js"]:
            self.assertIn("iscovery is not permission", normalized(read(path)), path.name)

    def test_old_boundary_answers_are_not_reinterpreted(self):
        # The question was reworded, so it has a new id; the storage key and
        # the rest of the state shape are unchanged.
        self.assertNotIn('id: "boundary"', self.data)
        self.assertNotIn("answers.boundary", self.logic)
        storage = read(SITE / "js" / "readiness-storage.js")
        self.assertIn('STORAGE_KEY = "adh.learning.v1.readinessAssessment"', storage)

    def test_worked_examples_resolve_mcp(self):
        for name in EXAMPLES:
            text = self.example(name)
            self.assertIn("| MCP (protocol boundary) | NOT JUSTIFIED |", text, name)
        # The remote-capability example says why remote is still not MCP.
        investigator = normalized(self.example("incident-investigator.md"))
        self.assertIn("remote does not mean MCP", investigator)

    def example(self, name):
        return read(ASSESSMENT_DIR / "examples" / name)


class NoScoringTest(unittest.TestCase):

    def test_no_scoring_language(self):
        paths = [
            REPO_ROOT / "assessments" / "README.md",
            *ASSESSMENT_DIR.rglob("*.md"),
            SITE / "readiness-assessment" / "index.html",
            *SITE.glob("js/readiness*.js"),
        ]
        for path in paths:
            for number, line in enumerate(read(path).splitlines(), start=1):
                if SCORING_TERMS.search(line):
                    self.assertRegex(line, NEGATION,
                                     f"{path.name}:{number} uses scoring language: {line.strip()}")

    def test_no_progress_meter_or_percentages_in_assessment_ui(self):
        page = read(SITE / "readiness-assessment" / "index.html")
        self.assertNotIn("progress-fill", page)
        for script in SITE.glob("js/readiness*.js"):
            self.assertNotIn("%", read(script), script.name)


class SiteIntegrationTest(unittest.TestCase):

    def test_landing_links_both_experiences(self):
        landing = read(SITE / "index.html")
        self.assertIn('href="capstone-01/index.html"', landing)
        self.assertIn('href="readiness-assessment/index.html"', landing)
        # Three ways to use the project: Learn (labs), Compose (capstone),
        # Decide (assessment).
        for kicker in ["</span> Learn</p>", "</span> Compose</p>", "</span> Decide</p>"]:
            self.assertIn(kicker, landing)

    def test_readiness_ui_exists_and_links_canonical_documents(self):
        page = read(SITE / "readiness-assessment" / "index.html")
        self.assertIn('src="../js/readiness-app.js"', page)
        for target in [
            "assessments/agentic-system-readiness/README.md",
            "assessments/agentic-system-readiness/ASSESSMENT.md",
            "docs/model-vs-decision-authority.md",
        ]:
            self.assertIn(target, page)
            self.assertTrue((REPO_ROOT / target).is_file(), target)
        data = read(SITE / "js" / "readiness.js")
        for name in EXAMPLES:
            self.assertIn(name, data)

    def test_storage_key_is_separate_and_versioned(self):
        storage = read(SITE / "js" / "readiness-storage.js")
        match = re.search(r'STORAGE_KEY = "([^"]+)"', storage)
        self.assertIsNotNone(match)
        key = match.group(1)
        self.assertEqual("adh.learning.v1.readinessAssessment", key)
        self.assertRegex(key, r"^adh\.learning\.v\d+\.")
        self.assertNotIn(key, read(SITE / "js" / "storage.js"))
        self.assertNotIn("capstone01", storage.split("STORAGE_KEY =")[1].split(";")[0])

    def test_export_includes_every_template_section(self):
        template = read(ASSESSMENT_DIR / "ASSESSMENT.md")
        export = read(SITE / "js" / "readiness-export.js")
        headings = re.findall(r"^## (.+)$", template, re.MULTILINE)
        self.assertGreaterEqual(len(headings), 9)
        for heading in headings:
            self.assertIn(f"## {heading}", export, heading)
        self.assertIn('link.download = "ASSESSMENT.md"', export)

    def test_no_network_dependency(self):
        for html in [SITE / "index.html", SITE / "readiness-assessment" / "index.html"]:
            for tag in re.findall(r"<(?:script|link)\b[^>]*>", read(html)):
                if 'rel="canonical"' in tag:
                    continue
                self.assertNotIn("://", tag, "no external resources: " + tag)
            self.assertNotIn("cdn.", read(html))
        for script in SITE.glob("js/*.js"):
            text = read(script)
            for forbidden in ["fetch(", "XMLHttpRequest", "WebSocket", "import(\"http", "from \"http"]:
                self.assertNotIn(forbidden, text, f"{script.name}: {forbidden}")
        self.assertEqual([], list(REPO_ROOT.glob("**/package.json")))

    def test_no_java_assessment_engine(self):
        pom = read(REPO_ROOT / "pom.xml")
        self.assertNotIn("assessment", pom.lower())
        self.assertEqual([], list((REPO_ROOT / "assessments").rglob("*.java")))


if __name__ == "__main__":
    unittest.main()
