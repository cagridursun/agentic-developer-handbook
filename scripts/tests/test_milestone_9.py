"""Cross-project facts that Milestone 9 (Evaluation) established.

Standard-library unittest only — no Maven, no network. The Java tests in
labs/09-evaluation prove the harness works; these checks keep the rest of the
repository telling the same story: evaluation is taught and available, it is
not another runtime capability, the capstone does not depend on it, no
evaluation or observability platform crept in, and nothing after Milestone 9
is presented as done.

Run from the repository root:

    python -m unittest discover -s scripts/tests
"""

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
LAB = REPO_ROOT / "labs" / "09-evaluation"
CAPSTONE = REPO_ROOT / "capstones" / "01-agentic-system"
ASSESSMENT_DIR = REPO_ROOT / "assessments" / "agentic-system-readiness"
SITE = REPO_ROOT / "site"

EVALUATION_QUESTION_IDS = ["evalCases", "evalSuccess", "evalDeterministic", "evalHuman", "evalTriggers"]

# Platforms and frameworks this milestone must not introduce.
FORBIDDEN_DEPENDENCIES = [
    "langsmith", "langfuse", "arize", "phoenix", "mlflow", "wandb", "weights",
    "opentelemetry", "micrometer", "spring", "langchain4j", "testcontainers",
]


def read(path):
    return path.read_text(encoding="utf-8")


def normalized(text):
    return re.sub(r"\s+", " ", text)


def milestone(number):
    """The body of a canonical milestone section, without its heading line."""
    roadmap = read(REPO_ROOT / "ROADMAP.md")
    section = roadmap.split(f"### Milestone {number} — ")[1].split("###")[0]
    return section.split("\n", 1)[1]


class LabNineExistsTest(unittest.TestCase):

    def test_lab_is_a_module_of_the_build(self):
        self.assertTrue((LAB / "README.md").is_file())
        self.assertTrue((LAB / "pom.xml").is_file())
        self.assertIn("<module>labs/09-evaluation</module>", read(REPO_ROOT / "pom.xml"))

    def test_no_evaluation_or_observability_platform_is_a_dependency(self):
        pom = read(LAB / "pom.xml")
        dependencies = pom.split("<dependencies>")[1].split("</dependencies>")[0]
        artifacts = re.findall(r"<artifactId>([^<]+)</artifactId>", dependencies)
        # The harness is plain Java: only the optional live model's SDK and the test framework.
        self.assertEqual(["google-genai", "junit-jupiter"], artifacts)
        for forbidden in FORBIDDEN_DEPENDENCIES:
            self.assertNotIn(forbidden, dependencies.lower(), forbidden)

    def test_the_lab_stays_on_the_shared_sdk_version_and_java_27_rules(self):
        genai = re.search(r"<artifactId>google-genai</artifactId>\s*<version>([^<]+)</version>", read(LAB / "pom.xml"))
        for other in ["07-agent-runtime", "08-mcp"]:
            other_pom = read(REPO_ROOT / "labs" / other / "pom.xml")
            self.assertIn(f"<artifactId>google-genai</artifactId>\n      <version>{genai.group(1)}</version>", other_pom)
        self.assertNotIn("--enable-preview", read(LAB / "pom.xml"))

    def test_lab_readme_teaches_the_distinctions_and_the_decision(self):
        readme = normalized(read(LAB / "README.md"))
        for fact in [
            "## Sources consulted",
            "## Do I actually need an evaluation set?",
            "## Tests vs evaluations",
            "## Deterministic checks vs model judges",
            "## Human review",
            "## Decision authority",
            "## What we STILL do not have",
            "Tests can all pass while behavior gets worse.",
            "One successful demo is not evidence of reliable behavior.",
            "An evaluation trace is not an observability trace.",
            "There is deliberately no \"Agent Score\".",
            "Evaluation does not transfer authority to the model.",
            "A judge is not ground truth.",
            "**No ADR.**",
            "Milestone 10 — Observability",
        ]:
            self.assertIn(fact, readme, fact)

    def test_lab_readme_follows_the_documented_run_command(self):
        readme = read(LAB / "README.md")
        self.assertIn("./mvnw -pl labs/09-evaluation compile exec:java", readme)
        self.assertIn('./mvnw -pl labs/09-evaluation compile exec:java -Dexec.args="--live"', readme)

    def test_live_mode_is_opt_in_and_never_part_of_ci(self):
        for workflow in (REPO_ROOT / ".github" / "workflows").glob("*.yml"):
            text = read(workflow)
            self.assertNotIn("GOOGLE_API_KEY", text, workflow.name)
            self.assertNotIn("GEMINI", text, workflow.name)
            self.assertNotIn("--live", text, workflow.name)
        self.assertIn("GOOGLE_API_KEY=", read(LAB / ".env.example"))

    def test_the_evaluation_set_is_plain_java_and_readable(self):
        # No dataset file format: the cases are records in the source, reviewable in a diff.
        self.assertEqual([], [p for p in LAB.rglob("*") if p.suffix in {".json", ".jsonl", ".yaml", ".yml", ".csv"}])
        self.assertTrue((LAB / "src/main/java/dev/agentic/handbook/labs/evaluation/HelioIncidentsV1.java").is_file())


class StatusTest(unittest.TestCase):

    def test_roadmap_marks_nine_done_and_ten_onward_not_started(self):
        nine = milestone(9)
        self.assertTrue(nine.strip().startswith(
            "Done. The lab is [labs/09-evaluation](labs/09-evaluation/README.md)."), nine[:120])
        for number in range(10, 13):
            self.assertTrue(milestone(number).strip().startswith("Not started."), number)

    def test_readme_marks_evaluation_implemented_and_observability_next(self):
        readme = read(REPO_ROOT / "README.md")
        rows = {row.split("|")[1].strip(): row for row in readme.splitlines()
                if re.match(r"^\| \d+ \|", row)}
        self.assertIn("[Lab 09](labs/09-evaluation/README.md)", rows["9"])
        for number in ["10", "11", "12"]:
            self.assertTrue(rows[number].rstrip().endswith("| Planned |"), rows[number])
        not_yet = next(line for line in readme.splitlines() if "**Not implemented yet:**" in line)
        self.assertNotIn("evaluation", not_yet)
        self.assertIn("observability", not_yet)
        self.assertIn("│   └── 09-evaluation/", readme)
        self.assertIn("observability (Milestone 10, next)", normalized(readme))

    def test_labs_index_links_lab_nine_only(self):
        index = read(REPO_ROOT / "labs" / "README.md")
        self.assertIn("| [`09-evaluation`](09-evaluation/README.md) | 9 — Evaluation |", index)
        self.assertIn("| `10-observability` | 10 — Observability |", index)

    def test_landing_page_shows_evaluation_implemented_and_the_rest_planned(self):
        landing = normalized(read(SITE / "index.html"))
        path = landing.split('class="path"')[1].split("</ol>")[0]
        self.assertIn("/blob/main/labs/09-evaluation/README.md\">Evaluation</a>", path)
        planned = landing.split('class="roadmap-note"')[1].split("</p>")[0]
        self.assertNotIn("Evaluation", planned)
        for name in ["Observability", "Security", "Deployment"]:
            self.assertIn(name, planned)

    def test_the_landing_page_counts_the_same_number_of_labs_everywhere(self):
        landing = normalized(read(SITE / "index.html"))
        path = landing.split('class="path"')[1].split("</ol>")[0]
        labs = path.count("<li>")
        self.assertEqual(9, labs)
        self.assertIn("Nine runnable Java labs", landing)
        self.assertIn(f"{labs} runnable Java labs</h3>", landing)
        self.assertIn("Labs 01 to 09", landing)

    def test_no_future_milestone_lab_exists(self):
        for name in ["10-observability", "11-security", "12-production"]:
            self.assertFalse((REPO_ROOT / "labs" / name).exists(), name)

    def test_evaluation_is_not_the_headline(self):
        readme = read(REPO_ROOT / "README.md")
        self.assertIn("**You probably don't need all of these.**", readme)
        headline = readme.split("## ")[0].split("**You probably")[0]
        self.assertNotIn("Evaluation", headline.replace('evaluation["Evaluation"]', ""))


class EvaluationIsNotACapabilityTest(unittest.TestCase):
    """Evaluation is how the chosen architecture is checked, not another row in
    the capability table beside Model, Tools, Memory, and MCP."""

    def setUp(self):
        self.data = read(SITE / "js" / "readiness.js")
        self.template = read(ASSESSMENT_DIR / "ASSESSMENT.md")
        self.readme = normalized(read(ASSESSMENT_DIR / "README.md"))

    def test_evaluation_is_not_a_selectable_capability(self):
        self.assertNotRegex(self.data, r'capability: "evaluation"')
        self.assertNotRegex(self.data, r'id: "evaluation"')
        table = self.template.split("| Capability | Consider? |")[1].split("\n\n")[0]
        self.assertNotIn("Evaluation", table)

    def test_the_five_planning_questions_are_asked_in_the_template_and_the_site(self):
        questions = re.findall(r'\{ id: "(eval\w+)", text: "([^"]+)" \}', self.data)
        self.assertEqual(EVALUATION_QUESTION_IDS, [question_id for question_id, _ in questions])
        for _, text in questions:
            self.assertIn(f"### {text}", self.template, text)
        self.assertIn("## Evaluation plan", self.template)

    def test_the_old_single_question_is_gone_so_old_answers_are_not_reinterpreted(self):
        self.assertNotIn("How will behavior eventually be evaluated?", self.template)
        self.assertNotIn("How will behavior eventually be evaluated?", self.data)
        storage = read(SITE / "js" / "readiness-storage.js")
        self.assertIn('STORAGE_KEY = "adh.learning.v1.readinessAssessment"', storage)

    def test_the_plan_is_conditional_on_a_model_and_recommends_no_framework(self):
        self.assertIn("No model-influenced decisions", self.template)
        self.assertIn("not a capability to select and not a framework to choose", self.template)
        self.assertIn("does not recommend an evaluation framework", self.readme)
        self.assertIn("labs/09-evaluation/README.md", self.readme)

    def test_later_questions_are_only_observability_and_security(self):
        template_tail = self.template.split("## Production questions for later")[1].split("## Final")[0]
        self.assertIn("Milestones 10 and 11", template_tail)
        self.assertNotIn("Milestones 9", read(SITE / "readiness-assessment" / "index.html"))
        self.assertNotIn("Milestones 9", read(SITE / "js" / "readiness-export.js"))
        for path in [ASSESSMENT_DIR / "README.md", ASSESSMENT_DIR / "ASSESSMENT.md"]:
            self.assertNotIn("Milestones 9–11", read(path), path.name)

    def test_export_and_page_include_the_evaluation_plan(self):
        self.assertIn("## Evaluation plan", read(SITE / "js" / "readiness-export.js"))
        page = read(SITE / "readiness-assessment" / "index.html")
        self.assertIn('id="evaluation-fields"', page)
        self.assertIn("labs/09-evaluation/README.md", page)

    def test_every_worked_example_has_an_evaluation_plan(self):
        for path in (ASSESSMENT_DIR / "examples").glob("*.md"):
            self.assertIn("## Evaluation plan", read(path), path.name)


class CapstoneConsistencyTest(unittest.TestCase):

    def test_capstone_no_longer_says_evaluation_is_a_future_milestone(self):
        for path in [CAPSTONE / "README.md", SITE / "capstone-01" / "index.html",
                     CAPSTONE / "reference" / "src" / "test" / "java" / "dev" / "agentic" / "handbook"
                     / "capstones" / "agenticsystem" / "reference" / "CapstoneReferenceTest.java"]:
            text = normalized(read(path))
            self.assertNotIn("Milestone 9", text, path.name)
            self.assertNotIn("covers later", text, path.name)
            self.assertIn("Lab 09", text, path.name)

    def test_deterministic_verify_stage_is_preserved(self):
        page = read(SITE / "capstone-01" / "index.html")
        self.assertIn("Verify deterministically", page)
        self.assertIn("application-controlled", page)
        self.assertIn("does <strong>not</strong> prove the agent's answers are", normalized(page))

    def test_capstone_does_not_depend_on_lab_nine(self):
        for pom in [CAPSTONE / "starter" / "pom.xml", CAPSTONE / "reference" / "pom.xml"]:
            self.assertNotIn("lab-09", read(pom), pom)
            self.assertNotIn("09-evaluation", read(pom), pom)
        self.assertIn("does not depend on Lab 09", normalized(read(CAPSTONE / "README.md")))
        self.assertNotIn('id: "evaluation"', read(SITE / "js" / "capstone.js"))


class HandbookDocumentsTest(unittest.TestCase):

    def test_glossary_defines_the_terms_and_the_pairs(self):
        glossary = read(REPO_ROOT / "docs" / "glossary.md")
        for entry in ["**Evaluation set.**", "**Trajectory.**", "**Model judge (LLM-as-a-judge).**",
                      "## Test vs evaluation", "## Evaluation vs observability"]:
            self.assertIn(entry, glossary, entry)
        self.assertIn("is not ground truth", normalized(glossary))

    def test_mental_model_separates_evaluation_from_observability(self):
        text = normalized(read(REPO_ROOT / "docs" / "mental-model.md"))
        self.assertIn("labs/09-evaluation/README.md", text)
        self.assertIn("Evaluation gives controlled evidence about quality; observability gives runtime evidence", text)

    def test_decision_authority_says_evaluation_transfers_none(self):
        text = normalized(read(REPO_ROOT / "docs" / "model-vs-decision-authority.md"))
        self.assertIn("evaluation transfers none", text)
        self.assertIn("labs/09-evaluation/README.md", text)

    def test_architecture_keeps_the_harness_lab_local_and_the_trace_out_of_observability(self):
        text = normalized(read(REPO_ROOT / "docs" / "architecture.md"))
        self.assertIn("plain-Java harness", text)
        self.assertIn("It is not an observability trace", text)


class ScopeTest(unittest.TestCase):

    def test_no_adr_was_created_for_a_lab_local_harness(self):
        adrs = sorted(p.name for p in (REPO_ROOT / "docs" / "adr").glob("0*.md"))
        self.assertEqual(5, len(adrs), adrs)

    def test_jev_is_still_only_a_track_c_note(self):
        roadmap = read(REPO_ROOT / "ROADMAP.md")
        track_c = roadmap.split("## Track C")[1].split("## Track D")[0]
        self.assertIn("Jev", track_c)
        self.assertEqual(1, roadmap.count("Jev"))
        self.assertIn("Milestone 9 (Evaluation) is now done", track_c)
        self.assertNotIn("to consider only after Milestone 9", track_c)
        self.assertIn("not part of the canonical learning path", track_c)
        self.assertEqual([], [p for p in REPO_ROOT.rglob("*")
                              if "jev" in p.name.lower() and ".git" not in p.parts])
        self.assertNotIn("Jev", read(LAB / "README.md"))

    def test_no_observability_or_tracing_code_in_the_lab(self):
        for source in LAB.rglob("*.java"):
            text = read(source).lower()
            for forbidden in ["opentelemetry", "io.micrometer", "slf4j", "java.util.logging", "span"]:
                self.assertNotIn(forbidden, text, f"{source.name}: {forbidden}")

    def test_lab_eight_is_unchanged_in_behavior_and_still_links_forward(self):
        readme = read(REPO_ROOT / "labs" / "08-mcp" / "README.md")
        self.assertIn("../09-evaluation/README.md", readme)
        self.assertIn("modelcontextprotocol", read(REPO_ROOT / "labs" / "08-mcp" / "pom.xml"))


if __name__ == "__main__":
    unittest.main()
