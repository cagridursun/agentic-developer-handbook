"""Cross-project facts that Milestone 11 (Security) established.

Standard-library unittest only — no Maven, no network. The Java tests in
labs/11-security prove the controls work; these checks keep the rest of the
repository telling the same story: security is taught and available, the
vulnerable example stays isolated and opt-in, no identity, policy, secrets, or
DLP platform crept in, the capstone does not depend on it, and Milestone 12 is recorded as its own lab.

Run from the repository root:

    python -m unittest discover -s scripts/tests
"""

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
LAB = REPO_ROOT / "labs" / "11-security"
CAPSTONE = REPO_ROOT / "capstones" / "01-agentic-system"
ASSESSMENT_DIR = REPO_ROOT / "assessments" / "agentic-system-readiness"
SITE = REPO_ROOT / "site"
ADR = REPO_ROOT / "docs" / "adr" / "0006-application-owned-authorization-for-state-changing-tools.md"

SECURITY_QUESTION_IDS = [
    "trust", "secToolPermissions", "secLeastPrivilege", "secValidation", "secUntrusted",
    "secUnauthorizedProposal", "secApproval", "secSensitive", "secCredentials", "secDenials",
    "secTests", "secRemote",
]

# Platforms and libraries this milestone must not introduce.
FORBIDDEN_DEPENDENCIES = [
    "spring", "keycloak", "oauth", "jwt", "nimbus", "opa", "casbin", "vault", "bouncycastle",
    "google-genai", "opentelemetry", "slf4j", "logback", "log4j", "testcontainers", "docker",
]


def read(path):
    return path.read_text(encoding="utf-8")


def normalized(text):
    return re.sub(r"\s+", " ", text)


def milestone(number):
    roadmap = read(REPO_ROOT / "ROADMAP.md")
    section = roadmap.split(f"### Milestone {number} — ")[1].split("###")[0]
    return section.split("\n", 1)[1]


class LabElevenExistsTest(unittest.TestCase):

    def test_lab_is_a_module_of_the_build(self):
        self.assertTrue((LAB / "README.md").is_file())
        self.assertTrue((LAB / "pom.xml").is_file())
        self.assertIn("<module>labs/11-security</module>", read(REPO_ROOT / "pom.xml"))

    def test_no_new_dependency_beyond_the_test_framework(self):
        pom = read(LAB / "pom.xml")
        dependencies = pom.split("<dependencies>")[1].split("</dependencies>")[0]
        artifacts = re.findall(r"<artifactId>([^<]+)</artifactId>", dependencies)
        self.assertEqual(["junit-jupiter"], artifacts)
        for forbidden in FORBIDDEN_DEPENDENCIES:
            self.assertNotIn(forbidden, dependencies.lower(), forbidden)
        self.assertNotIn("--enable-preview", pom)

    def test_the_lab_imports_no_identity_policy_or_telemetry_library_and_no_other_lab(self):
        for source in LAB.rglob("*.java"):
            text = read(source)
            for forbidden in ["io.opentelemetry", "org.slf4j", "java.util.logging", "org.springframework",
                              "dev.agentic.handbook.labs.observability", "dev.agentic.handbook.labs.mcp",
                              "dev.agentic.handbook.labs.agentruntime", "com.google.genai"]:
                self.assertNotIn(forbidden, text, f"{source.name}: {forbidden}")

    def test_lab_readme_has_the_progression_and_the_load_bearing_statements(self):
        readme = normalized(read(LAB / "README.md"))
        for fact in [
            "## The problem", "## Learning goals", "## Existing architecture", "## Threat model",
            "## Reproduce the vulnerability safely", "## Why a prompt is not a security boundary",
            "## Proposal versus authorization", "## Least privilege", "## Argument validation",
            "## Authorization", "## Direct and indirect prompt injection", "## Sensitive information",
            "## Approval", "## The execution boundary", "## MCP and remote-provider boundary",
            "## Observability and security events", "## Run it", "## Do I actually need this?", "## Tests",
            "## What it protects", "## What it does NOT protect", "## Reflection",
            "## Implementation decisions", "## What we STILL do not have", "## What limitation remains?",
            "## Sources consulted",
            "**Observability records. Authorization controls. Logging a denial does not prevent it.**",
            "**Observability records; authorization controls.**",
            "Logging a denial does not prevent the action it describes",
            "**Keyword redaction is incomplete.**",
            "Delimiters, system prompts, and a \"do not follow instructions in documents\" line are mitigations",
            "this lab does not claim they eliminate injection",
            "Milestone 12 — Deployment",
            "Not implemented here: no Docker",
            "ADR 0006",
        ]:
            self.assertIn(fact, readme, fact)

    def test_lab_readme_follows_the_documented_commands(self):
        readme = read(LAB / "README.md")
        self.assertIn("./mvnw -pl labs/11-security compile exec:java\n", readme)
        self.assertIn("-Dexec.mainClass=dev.agentic.handbook.labs.security.vulnerable.VulnerableDemo", readme)
        pom = read(LAB / "pom.xml")
        self.assertIn("<exec.mainClass>dev.agentic.handbook.labs.security.SecurityExample</exec.mainClass>", pom)
        self.assertNotIn("<mainClass>", pom, "the main class must stay a property so -Dexec.mainClass can override it")

    def test_the_vulnerable_example_is_isolated_labelled_and_off_the_default_path(self):
        vulnerable = LAB / "src" / "main" / "java" / "dev" / "agentic" / "handbook" / "labs" / "security" / "vulnerable"
        files = sorted(vulnerable.glob("*.java"))
        self.assertTrue(files)
        for source in files:
            self.assertIn("INTENTIONALLY VULNERABLE", read(source), source.name)
        for source in vulnerable.parent.glob("*.java"):
            self.assertNotIn("security.vulnerable", read(source), source.name)
        self.assertIn("<exec.mainClass>dev.agentic.handbook.labs.security.SecurityExample", read(LAB / "pom.xml"))

    def test_only_a_fake_credential_exists_anywhere_in_the_lab(self):
        for source in LAB.rglob("*"):
            if not source.is_file() or "target" in source.parts:
                continue
            text = read(source)
            self.assertNotRegex(text, r"AIza[0-9A-Za-z_-]{30,}", source.name)
            self.assertNotRegex(text, r"sk-[A-Za-z0-9]{20,}", source.name)
            self.assertNotRegex(text, r"-----BEGIN [A-Z ]*PRIVATE KEY", source.name)
        self.assertIn("FAKE_API_TOKEN_FOR_TESTING_ONLY", read(
            LAB / "src" / "main" / "java" / "dev" / "agentic" / "handbook" / "labs" / "security" / "HelioPlatform.java"))

    def test_no_deployment_infrastructure_beyond_lab_twelve_was_added(self):
        # Milestone 12 added one Dockerfile, in its own lab. Nothing else of this kind exists.
        for name in ["docker-compose.yml", "docker-compose.yaml", "compose.yaml", "Jenkinsfile"]:
            self.assertEqual([], [p for p in REPO_ROOT.rglob(name) if ".git" not in p.parts and "target" not in p.parts], name)
        dockerfiles = [p.relative_to(REPO_ROOT).as_posix() for p in REPO_ROOT.rglob("Dockerfile")
                       if ".git" not in p.parts and "target" not in p.parts]
        self.assertEqual(["labs/12-deployment/Dockerfile"], dockerfiles)
        self.assertFalse((REPO_ROOT / "labs" / "12-production").exists())

    def test_sources_are_dated_versioned_and_never_use_bare_owasp_ids(self):
        readme = read(LAB / "README.md")
        sources = readme.split("## Sources consulted")[1]
        self.assertIn("Verified on 2026-10-03", sources)
        self.assertIn("OWASP GenAI LLM Top 10 2026", sources)
        self.assertIn("revision 2026-07-28", sources)
        self.assertIn("was **not consulted**", sources)
        self.assertIn("**the 2026 list reorders and renames entries**", sources)
        self.assertIn("always carry the year", sources)
        # A 2025 identifier appears only to contrast with the 2026 one it became.
        for sentence in re.findall(r"[^.;]*LLM\d\d:2025[^.;]*", readme):
            self.assertRegex(sentence, r"LLM\d\d:2026")


class StatusTest(unittest.TestCase):

    def test_roadmap_marks_eleven_done_and_twelve_done_after_it(self):
        eleven = milestone(11)
        self.assertTrue(eleven.strip().startswith(
            "Done. The lab is [labs/11-security](labs/11-security/README.md)."), eleven[:120])
        self.assertTrue(milestone(12).strip().startswith(
            "Done. The lab is [labs/12-deployment](labs/12-deployment/README.md)."))
        roadmap = read(REPO_ROOT / "ROADMAP.md")
        self.assertIn("Milestones 0 through 12 are done", roadmap)
        self.assertNotIn("Milestones 0 through 11 are done", roadmap)

    def test_readme_marks_security_implemented_and_deployment_next(self):
        readme = read(REPO_ROOT / "README.md")
        rows = {row.split("|")[1].strip(): row for row in readme.splitlines()
                if re.match(r"^\| \d+ \|", row)}
        self.assertIn("[Lab 11](labs/11-security/README.md)", rows["11"])
        self.assertIn("[Lab 12](labs/12-deployment/README.md)", rows["12"])
        not_yet = next(line for line in readme.splitlines() if "**Not implemented:**" in line)
        self.assertNotIn("security", not_yet)
        self.assertIn("production platform", not_yet)
        self.assertIn("│   ├── 11-security/", readme)
        self.assertIn('observability["Observability"] --> security["Security"]', readme)
        self.assertIn("twelve runnable labs", normalized(readme))
        self.assertNotIn("deployment (Milestone 12, next)", normalized(readme))

    def test_labs_index_links_lab_eleven(self):
        index = read(REPO_ROOT / "labs" / "README.md")
        self.assertIn("| [`11-security`](11-security/README.md) | 11 — Security |", index)
        self.assertIn("| [`12-deployment`](12-deployment/README.md) | 12 — Deployment |", index)
        self.assertIn("Twelve labs are available", index)
        self.assertIn("## After Lab 11", index)

    def test_landing_page_shows_security_implemented_and_deployment_planned(self):
        landing = normalized(read(SITE / "index.html"))
        path = landing.split('class="path"')[1].split("</ol>")[0]
        self.assertIn("/blob/main/labs/11-security/README.md\">Security</a>", path)
        self.assertEqual(12, path.count("<li>"))
        self.assertIn("Twelve runnable Java labs", landing)
        self.assertIn("12 runnable Java labs</h3>", landing)
        self.assertIn("Labs 01 to 12", landing)
        planned = landing.split('class="roadmap-note"')[1].split("</p>")[0]
        self.assertNotIn("Security", planned)
        self.assertIn("not a production platform", planned)

    def test_no_stale_lab_counts_remain(self):
        for relative in ["README.md", "labs/README.md", "site/index.html", "ROADMAP.md"]:
            text = read(REPO_ROOT / relative)
            for stale in ["Ten runnable", "ten runnable", "Ten labs", "10 runnable", "Labs 01–10", "Labs 01 to 10",
                          "security (Milestone 11, next)", "Milestones 0 through 10 are done"]:
                self.assertNotIn(stale, text, f"{relative}: {stale}")

    def test_earlier_labs_link_forward_to_lab_eleven(self):
        for lab in ["07-agent-runtime", "08-mcp", "10-observability"]:
            self.assertIn("../11-security/README.md", read(REPO_ROOT / "labs" / lab / "README.md"), lab)


class AdrTest(unittest.TestCase):

    def test_adr_0006_exists_is_indexed_and_states_the_rule(self):
        text = normalized(read(ADR))
        for part in ["## Status", "Accepted", "## Context", "## Decision", "## Consequences",
                     "independent of the model's output", "fails closed", "Observability is not an enforcement mechanism"]:
            self.assertIn(part, text, part)
        index = read(REPO_ROOT / "docs" / "adr" / "README.md")
        self.assertIn("[0006](0006-application-owned-authorization-for-state-changing-tools.md)", index)
        self.assertEqual(6, len(list((REPO_ROOT / "docs" / "adr").glob("0*.md"))))


class SecurityIsNotACapabilityTest(unittest.TestCase):
    """Security is a plan, not a row in the capability table beside Model, Tools, and MCP."""

    def setUp(self):
        self.data = read(SITE / "js" / "readiness.js")
        self.template = read(ASSESSMENT_DIR / "ASSESSMENT.md")
        self.readme = normalized(read(ASSESSMENT_DIR / "README.md"))

    def test_security_is_not_a_selectable_capability(self):
        self.assertNotRegex(self.data, r'capability: "security"')
        self.assertNotRegex(self.data, r'id: "security"')
        table = self.template.split("| Capability | Consider? |")[1].split("\n\n")[0]
        self.assertNotIn("Security", table)

    def test_the_questions_are_asked_in_the_template_and_the_site(self):
        questions = re.findall(r'\{ id: "((?:sec|trust)\w*)", text: "([^"]+)" \}', self.data)
        self.assertEqual(SECURITY_QUESTION_IDS, [question_id for question_id, _ in questions])
        for _, text in questions:
            self.assertIn(f"### {text}", self.template, text)
        self.assertIn("## Security plan", self.template)

    def test_the_old_trust_answer_is_not_reinterpreted(self):
        # The first question keeps the id the old production question had, so stored answers stay with it.
        self.assertIn('{ id: "trust", text: "What trust and security boundaries exist?" }', self.data)
        storage = read(SITE / "js" / "readiness-storage.js")
        self.assertIn('STORAGE_KEY = "adh.learning.v1.readinessAssessment"', storage)

    def test_the_plan_is_conditional_and_recommends_no_product_and_does_not_score(self):
        plan = self.template.split("## Security plan")[1]
        self.assertIn("No model-influenced actions", plan)
        self.assertIn("not a capability to select and not a product to choose", plan)
        self.assertIn("recommends no security product or framework", self.readme)
        self.assertIn("labs/11-security/README.md", self.readme)
        for word in ["score", "grade", "rating"]:
            self.assertNotIn(word, plan.lower().split("## final")[0], word)

    def test_export_and_page_include_the_security_plan(self):
        self.assertIn("## Security plan", read(SITE / "js" / "readiness-export.js"))
        page = read(SITE / "readiness-assessment" / "index.html")
        self.assertIn('id="security-fields"', page)
        self.assertIn("labs/11-security/README.md", page)
        self.assertNotIn("production-fields", page)

    def test_every_worked_example_has_a_security_plan(self):
        for path in (ASSESSMENT_DIR / "examples").glob("*.md"):
            text = read(path)
            self.assertIn("## Security plan", text, path.name)
            self.assertNotIn("## Production questions for later", text, path.name)

    def test_nothing_claims_the_assessment_secures_anything(self):
        self.assertIn("The assessment secures nothing", self.readme)


class CapstoneConsistencyTest(unittest.TestCase):

    def test_capstone_distinguishes_evaluation_observability_and_security(self):
        for path in [CAPSTONE / "README.md", SITE / "capstone-01" / "index.html"]:
            text = normalized(read(path))
            self.assertIn("labs/11-security/README.md", text, path.name)
            self.assertIn("evaluation tests behavior", text.lower(), path.name)
            self.assertIn("does not depend on Lab 11", text, path.name)

    def test_capstone_does_not_depend_on_lab_eleven(self):
        for pom in [CAPSTONE / "starter" / "pom.xml", CAPSTONE / "reference" / "pom.xml"]:
            self.assertNotIn("lab-11", read(pom), pom)
            self.assertNotIn("11-security", read(pom), pom)
        self.assertNotIn('id: "security"', read(SITE / "js" / "capstone.js"))


class HandbookDocumentsTest(unittest.TestCase):

    def test_glossary_defines_the_terms_and_the_pairs(self):
        glossary = read(REPO_ROOT / "docs" / "glossary.md")
        for entry in ["**Threat model.**", "**Trust boundary.**", "**Authentication.**", "**Authorization.**",
                      "**Least privilege.**", "**Prompt injection.**", "**Direct prompt injection.**",
                      "**Indirect prompt injection.**", "**Input validation.**", "**Sensitive information.**",
                      "**Secret redaction.**", "**Approval boundary.**", "**Security event.**",
                      "## Validation vs authorization", "## Observability vs enforcement"]:
            self.assertIn(entry, glossary, entry)

    def test_mental_model_places_security_at_the_execution_boundary(self):
        text = normalized(read(REPO_ROOT / "docs" / "mental-model.md"))
        self.assertIn("labs/11-security/README.md", text)
        self.assertIn("Security: decisions at the execution boundary", text)
        self.assertIn("Observability sits beside this and does not belong in it", text)
        self.assertIn("Security enforces what is permitted", text)

    def test_architecture_ties_security_to_the_control_plane_and_the_adr(self):
        text = normalized(read(REPO_ROOT / "docs" / "architecture.md"))
        self.assertIn("## Security is the control plane at the execution boundary", text)
        self.assertIn("adr/0006-application-owned-authorization-for-state-changing-tools.md", text)
        self.assertIn("is deployment, which [Lab 12](../labs/12-deployment/README.md) takes up", text)

    def test_decision_authority_says_authorization_holds_the_boundary(self):
        text = normalized(read(REPO_ROOT / "docs" / "model-vs-decision-authority.md"))
        self.assertIn("labs/11-security/README.md", text)
        self.assertIn("## Authorizing a delegation", text)
        self.assertIn("no control reads it", text)


class ScopeTest(unittest.TestCase):

    def test_jev_is_still_only_a_track_c_note(self):
        roadmap = read(REPO_ROOT / "ROADMAP.md")
        self.assertEqual(1, roadmap.count("Jev"))
        for source in LAB.rglob("*"):
            if source.is_file() and "target" not in source.parts:
                self.assertNotIn("Jev", read(source), source.name)

    def test_no_ai_authorship_in_the_lab_or_adr(self):
        for path in [*LAB.rglob("*"), ADR]:
            if path.is_file() and "target" not in path.parts:
                text = read(path).lower()
                self.assertNotIn("co-authored-by", text, path.name)
                self.assertNotIn("generated with", text, path.name)


if __name__ == "__main__":
    unittest.main()
