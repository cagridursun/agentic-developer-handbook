"""Cross-project facts that Milestone 10 (Observability) established.

Standard-library unittest only — no Maven, no network. The Java tests in
labs/10-observability prove the trace model works; these checks keep the rest of
the repository telling the same story: observability is taught and available, it
is not another runtime capability, the capstone does not depend on it, no
telemetry platform crept in, and nothing after Milestone 10 is presented as done.

Run from the repository root:

    python -m unittest discover -s scripts/tests
"""

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
LAB = REPO_ROOT / "labs" / "10-observability"
CAPSTONE = REPO_ROOT / "capstones" / "01-agentic-system"
ASSESSMENT_DIR = REPO_ROOT / "assessments" / "agentic-system-readiness"
SITE = REPO_ROOT / "site"

OBSERVABILITY_QUESTION_IDS = [
    "obsReconstruct", "obsLogged", "obsNeverLogged", "obsCorrelation", "obsMetrics", "obsTrace",
]

# Platforms and frameworks this milestone must not introduce.
FORBIDDEN_DEPENDENCIES = [
    "opentelemetry", "micrometer", "prometheus", "jaeger", "zipkin", "grafana",
    "datadog", "newrelic", "langfuse", "langsmith", "slf4j", "logback", "log4j",
    "spring", "langchain4j", "testcontainers",
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


class LabTenExistsTest(unittest.TestCase):

    def test_lab_is_a_module_of_the_build(self):
        self.assertTrue((LAB / "README.md").is_file())
        self.assertTrue((LAB / "pom.xml").is_file())
        self.assertIn("<module>labs/10-observability</module>", read(REPO_ROOT / "pom.xml"))

    def test_no_telemetry_platform_is_a_dependency(self):
        pom = read(LAB / "pom.xml")
        dependencies = pom.split("<dependencies>")[1].split("</dependencies>")[0]
        artifacts = re.findall(r"<artifactId>([^<]+)</artifactId>", dependencies)
        # The trace model is plain Java: only the optional live model's SDK and the test framework.
        self.assertEqual(["google-genai", "junit-jupiter"], artifacts)
        for forbidden in FORBIDDEN_DEPENDENCIES:
            self.assertNotIn(forbidden, dependencies.lower(), forbidden)

    def test_the_lab_stays_on_the_shared_sdk_version_and_java_27_rules(self):
        genai = re.search(r"<artifactId>google-genai</artifactId>\s*<version>([^<]+)</version>", read(LAB / "pom.xml"))
        for other in ["07-agent-runtime", "08-mcp", "09-evaluation"]:
            other_pom = read(REPO_ROOT / "labs" / other / "pom.xml")
            self.assertIn(f"<artifactId>google-genai</artifactId>\n      <version>{genai.group(1)}</version>", other_pom)
        self.assertNotIn("--enable-preview", read(LAB / "pom.xml"))

    def test_no_telemetry_library_is_imported_by_the_lab(self):
        for source in LAB.rglob("*.java"):
            text = read(source).lower()
            for forbidden in ["io.opentelemetry", "io.micrometer", "org.slf4j", "java.util.logging",
                              "org.apache.logging"]:
                self.assertNotIn(forbidden, text, f"{source.name}: {forbidden}")

    def test_lab_readme_teaches_the_distinctions_and_the_decision(self):
        readme = normalized(read(LAB / "README.md"))
        for fact in [
            "## Sources consulted",
            "## Logs vs metrics vs traces",
            "## Evaluation vs observability",
            "## What should NOT be logged?",
            "## Decision authority and observability",
            "## Optional production tooling: OpenTelemetry",
            "## Do I actually need this?",
            "## What we STILL do not have",
            "Logs tell you individual events. Metrics tell you aggregate behavior. Traces tell you how events belong to one run.",
            "Evaluation asks whether the system behaves as intended across cases. Observability asks what actually happened during this run.",
            "\"Observable\" does not mean \"record every byte.\"",
            "the observability plane **observes** the system; it must not become a decision authority",
            "Not every application needs a tracing backend.",
            "**No ADR.**",
            "Milestone 11 — Security",
        ]:
            self.assertIn(fact, readme, fact)

    def test_lab_readme_follows_the_documented_run_command(self):
        readme = read(LAB / "README.md")
        self.assertIn("./mvnw -pl labs/10-observability compile exec:java", readme)
        self.assertIn('./mvnw -pl labs/10-observability compile exec:java -Dexec.args="--live"', readme)

    def test_live_mode_is_opt_in_and_never_part_of_ci(self):
        for workflow in (REPO_ROOT / ".github" / "workflows").glob("*.yml"):
            text = read(workflow)
            self.assertNotIn("GOOGLE_API_KEY", text, workflow.name)
            self.assertNotIn("GEMINI", text, workflow.name)
            self.assertNotIn("--live", text, workflow.name)
        self.assertIn("GOOGLE_API_KEY=", read(LAB / ".env.example"))


class StatusTest(unittest.TestCase):

    def test_roadmap_marks_ten_done_and_twelve_not_started(self):
        ten = milestone(10)
        self.assertTrue(ten.strip().startswith(
            "Done. The lab is [labs/10-observability](labs/10-observability/README.md)."), ten[:120])
        self.assertNotIn("is the intended direction", ten)
        self.assertTrue(milestone(11).strip().startswith(
            "Done. The lab is [labs/11-security](labs/11-security/README.md)."))
        self.assertTrue(milestone(12).strip().startswith("Not started."))
        self.assertIn("Milestones 0 through 11 are done", read(REPO_ROOT / "ROADMAP.md"))

    def test_readme_marks_observability_implemented_and_deployment_next(self):
        readme = read(REPO_ROOT / "README.md")
        rows = {row.split("|")[1].strip(): row for row in readme.splitlines()
                if re.match(r"^\| \d+ \|", row)}
        self.assertIn("[Lab 10](labs/10-observability/README.md)", rows["10"])
        self.assertIn("[Lab 11](labs/11-security/README.md)", rows["11"])
        self.assertTrue(rows["12"].rstrip().endswith("| Planned |"), rows["12"])
        not_yet = next(line for line in readme.splitlines() if "**Not implemented yet:**" in line)
        self.assertNotIn("observability", not_yet)
        self.assertNotIn("security", not_yet)
        self.assertIn("deployment", not_yet)
        self.assertIn("│   ├── 09-evaluation/", readme)
        self.assertIn("│   ├── 10-observability/", readme)
        self.assertIn('evaluation["Evaluation"] --> observability["Observability"]', readme)
        self.assertIn("eleven runnable labs", normalized(readme))
        self.assertIn("deployment (Milestone 12, next)", normalized(readme))

    def test_labs_index_links_lab_ten(self):
        index = read(REPO_ROOT / "labs" / "README.md")
        self.assertIn("| [`10-observability`](10-observability/README.md) | 10 — Observability |", index)
        self.assertIn("| [`11-security`](11-security/README.md) | 11 — Security |", index)
        self.assertIn("Eleven labs are available", index)

    def test_landing_page_shows_observability_implemented_and_the_rest_planned(self):
        landing = normalized(read(SITE / "index.html"))
        path = landing.split('class="path"')[1].split("</ol>")[0]
        self.assertIn("/blob/main/labs/10-observability/README.md\">Observability</a>", path)
        self.assertEqual(11, path.count("<li>"))
        planned = landing.split('class="roadmap-note"')[1].split("</p>")[0]
        self.assertNotIn("Observability", planned)
        self.assertNotIn("Security", planned)
        self.assertIn("Deployment", planned)

    def test_the_landing_page_counts_the_same_number_of_labs_everywhere(self):
        landing = normalized(read(SITE / "index.html"))
        path = landing.split('class="path"')[1].split("</ol>")[0]
        labs = path.count("<li>")
        self.assertEqual(11, labs)
        self.assertIn("Eleven runnable Java labs", landing)
        self.assertIn(f"{labs} runnable Java labs</h3>", landing)
        self.assertIn("Labs 01 to 11", landing)

    def test_no_stale_lab_counts_remain(self):
        for relative in ["README.md", "labs/README.md", "site/index.html"]:
            text = read(REPO_ROOT / relative)
            for stale in ["nine runnable", "Nine runnable", "Nine labs", "9 runnable", "Labs 01–09",
                          "Labs 01 to 09", "observability (Milestone 10, next)", "Ten runnable", "Ten labs",
                          "ten runnable", "10 runnable", "Labs 01–10", "Labs 01 to 10",
                          "security (Milestone 11, next)"]:
                self.assertNotIn(stale, text, f"{relative}: {stale}")

    def test_no_future_milestone_lab_exists(self):
        for name in ["12-production"]:
            self.assertFalse((REPO_ROOT / "labs" / name).exists(), name)

    def test_earlier_labs_link_forward_to_lab_ten(self):
        for lab in ["07-agent-runtime", "08-mcp", "09-evaluation", "01-model-call"]:
            self.assertIn("../10-observability/README.md", read(REPO_ROOT / "labs" / lab / "README.md"), lab)


class ObservabilityIsNotACapabilityTest(unittest.TestCase):
    """Observability is how a failed run is reconstructed, not another row in the
    capability table beside Model, Tools, Memory, and MCP."""

    def setUp(self):
        self.data = read(SITE / "js" / "readiness.js")
        self.template = read(ASSESSMENT_DIR / "ASSESSMENT.md")
        self.readme = normalized(read(ASSESSMENT_DIR / "README.md"))

    def test_observability_is_not_a_selectable_capability(self):
        self.assertNotRegex(self.data, r'capability: "observability"')
        self.assertNotRegex(self.data, r'id: "observability"')
        table = self.template.split("| Capability | Consider? |")[1].split("\n\n")[0]
        self.assertNotIn("Observability", table)

    def test_the_six_planning_questions_are_asked_in_the_template_and_the_site(self):
        questions = re.findall(r'\{ id: "(obs\w+)", text: "([^"]+)" \}', self.data)
        self.assertEqual(OBSERVABILITY_QUESTION_IDS, [question_id for question_id, _ in questions])
        for _, text in questions:
            self.assertIn(f"### {text}", self.template, text)
        self.assertIn("## Observability plan", self.template)

    def test_the_old_single_question_is_gone_so_old_answers_are_not_reinterpreted(self):
        self.assertNotIn("What must be observable?", self.template)
        self.assertNotIn("What must be observable?", self.data)
        storage = read(SITE / "js" / "readiness-storage.js")
        self.assertIn('STORAGE_KEY = "adh.learning.v1.readinessAssessment"', storage)

    def test_the_plan_is_conditional_on_a_model_and_recommends_no_framework(self):
        self.assertIn("No model-influenced decisions", self.template.split("## Observability plan")[1])
        self.assertIn("not a capability to select and not a tool to choose", self.template)
        self.assertIn("recommends no observability framework or product", self.readme)
        self.assertIn("labs/10-observability/README.md", self.readme)

    def test_the_later_question_became_the_security_plan(self):
        self.assertIn("## Security plan", self.template)
        self.assertNotIn("## Production questions for later", self.template)
        self.assertNotIn("What must be observable", self.template.split("## Security plan")[1])
        for path in [SITE / "readiness-assessment" / "index.html", SITE / "js" / "readiness-export.js",
                     ASSESSMENT_DIR / "README.md", ASSESSMENT_DIR / "ASSESSMENT.md"]:
            self.assertNotIn("Milestones 10 and 11", read(path), path.name)

    def test_export_and_page_include_the_observability_plan(self):
        self.assertIn("## Observability plan", read(SITE / "js" / "readiness-export.js"))
        page = read(SITE / "readiness-assessment" / "index.html")
        self.assertIn('id="observability-fields"', page)
        self.assertIn("labs/10-observability/README.md", page)

    def test_every_template_heading_appears_in_the_export(self):
        export = read(SITE / "js" / "readiness-export.js")
        for heading in re.findall(r"^## (.+)$", self.template, flags=re.MULTILINE):
            if heading in {"Protocol boundary (MCP)"}:
                continue  # reference text, not an answer section
            self.assertIn(f"## {heading}", export, heading)

    def test_every_worked_example_has_an_observability_plan(self):
        for path in (ASSESSMENT_DIR / "examples").glob("*.md"):
            text = read(path)
            self.assertIn("## Observability plan", text, path.name)
            self.assertNotIn("**Observable:**", text, path.name)

    def test_the_assessment_does_not_score(self):
        plan = self.template.split("## Observability plan")[1].split("## Security plan")[0].lower()
        for word in ["score", "grade", "rating"]:
            self.assertNotIn(word, plan, word)


class CapstoneConsistencyTest(unittest.TestCase):

    def test_capstone_distinguishes_verification_evaluation_and_observability(self):
        for path in [CAPSTONE / "README.md", SITE / "capstone-01" / "index.html"]:
            text = normalized(read(path))
            self.assertIn("labs/10-observability/README.md", text, path.name)
            self.assertIn("Lab 09", text, path.name)
            self.assertIn("does not depend on Lab 10", text, path.name)

    def test_capstone_does_not_depend_on_lab_ten(self):
        for pom in [CAPSTONE / "starter" / "pom.xml", CAPSTONE / "reference" / "pom.xml"]:
            self.assertNotIn("lab-10", read(pom), pom)
            self.assertNotIn("10-observability", read(pom), pom)
        self.assertNotIn('id: "observability"', read(SITE / "js" / "capstone.js"))


class HandbookDocumentsTest(unittest.TestCase):

    def test_glossary_defines_the_terms_and_the_pairs(self):
        glossary = read(REPO_ROOT / "docs" / "glossary.md")
        for entry in ["**Observability.**", "**Log.**", "**Metric.**", "**Trace.**", "**Span.**",
                      "**Correlation ID.**", "**Telemetry.**", "**Instrumentation.**",
                      "## Evaluation vs observability", "## Observability vs logging"]:
            self.assertIn(entry, glossary, entry)

    def test_mental_model_draws_observability_around_the_run_and_keeps_the_pair(self):
        text = normalized(read(REPO_ROOT / "docs" / "mental-model.md"))
        self.assertIn("labs/10-observability/README.md", text)
        self.assertIn("OBSERVABILITY: logs / metrics / traces", text)
        self.assertIn("observability observes, it does not decide", text)
        self.assertIn("Evaluation gives controlled evidence about quality; observability gives runtime evidence", text)

    def test_architecture_has_three_planes_and_an_observing_plane(self):
        text = normalized(read(REPO_ROOT / "docs" / "architecture.md"))
        for term in ["**Control plane:**", "**Execution plane:**", "**Observability plane:**"]:
            self.assertIn(term, text, term)
        self.assertIn("The observability plane **observes**; it never decides.", text)
        self.assertIn("in-memory, plain-Java trace model — no OpenTelemetry", text)
        self.assertNotIn("telemetry from a running system is a later milestone", text)

    def test_decision_authority_says_the_trace_records_without_granting(self):
        text = normalized(read(REPO_ROOT / "docs" / "model-vs-decision-authority.md"))
        self.assertIn("labs/10-observability/README.md", text)
        self.assertIn("Recording is not authorizing.", text)
        for span in ["AGENT_DECISION", "TOOL_VALIDATION", "TOOL_EXECUTION"]:
            self.assertIn(span, text, span)

    def test_lab_nine_still_separates_evaluation_from_observability(self):
        text = normalized(read(REPO_ROOT / "labs" / "09-evaluation" / "README.md"))
        self.assertIn("An evaluation trace is not an observability trace.", text)
        self.assertIn("Observability ≠ evaluation.", text)


class ScopeTest(unittest.TestCase):

    def test_no_adr_was_created_for_a_lab_local_trace_model(self):
        adrs = sorted(p.name for p in (REPO_ROOT / "docs" / "adr").glob("0*.md"))
        self.assertEqual(6, len(adrs), adrs)  # 0006 belongs to Milestone 11

    def test_jev_is_still_only_a_track_c_note(self):
        roadmap = read(REPO_ROOT / "ROADMAP.md")
        track_c = roadmap.split("## Track C")[1].split("## Track D")[0]
        self.assertIn("Jev", track_c)
        self.assertEqual(1, roadmap.count("Jev"))
        self.assertIn("not part of the canonical learning path", track_c)
        self.assertEqual([], [p for p in REPO_ROOT.rglob("*")
                              if "jev" in p.name.lower() and ".git" not in p.parts])
        for source in LAB.rglob("*"):
            if source.is_file() and "target" not in source.parts:
                self.assertNotIn("Jev", read(source), source.name)


if __name__ == "__main__":
    unittest.main()
