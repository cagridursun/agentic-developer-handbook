"""Cross-project facts that Milestone 12 (Deployment) established.

Standard-library unittest only: no Maven, no Docker, no network. The Java tests in
labs/12-deployment prove the deployed application behaves; these checks keep the rest
of the repository telling the same story: deployment is taught and available, it is a
teaching deployment and not a production platform, it added no orchestrator, cloud,
secret platform, or telemetry backend, the pipeline was extended and not replaced and
pushes nothing, and no secret is committed.

Run from the repository root:

    python -m unittest discover -s scripts/tests
"""

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
LAB = REPO_ROOT / "labs" / "12-deployment"
WORKFLOWS = REPO_ROOT / ".github" / "workflows"
CAPSTONE = REPO_ROOT / "capstones" / "01-agentic-system"
SITE = REPO_ROOT / "site"

# Platforms and libraries this milestone must not introduce.
FORBIDDEN_DEPENDENCIES = [
    "spring", "jetty", "netty", "undertow", "tomcat", "javalin", "micronaut", "quarkus", "kubernetes", "fabric8",
    "vault", "opentelemetry", "micrometer", "prometheus", "slf4j", "logback", "log4j", "testcontainers",
    "jackson", "google-genai", "mcp",
]

REQUIRED_SECTIONS = [
    "## Why Deployment Matters", "## Learning Objectives", "## Prerequisites", "## Architecture",
    "## Deployment Mental Model", "## Build", "## Configuration", "## Containerization",
    "## Health and Readiness", "## Graceful Shutdown", "## Security Boundaries", "## Observability",
    "## CI/CD", "## Deployment Failure Modes", "## Supply-Chain Considerations", "## Hands-On Exercise",
    "## Verification", "## What This Project Does Not Solve", "## Production Gap Analysis",
    "## Reflection Questions", "## Transition Beyond the Handbook",
]

NOT_SOLVED = [
    "Kubernetes production orchestration", "Autoscaling", "Multi-region", "Cloud IAM",
    "Enterprise secret management", "Production identity federation", "Zero-trust networking",
    "Full supply-chain security", "Production incident response", "High availability and disaster recovery",
    "Distributed transactions", "Production model serving", "Enterprise policy management",
    "Full OpenTelemetry", "SIEM",
]

GAP_CONCERNS = ["Packaging", "Configuration", "Secrets", "Health", "Readiness", "Security", "Observability",
                "CI/CD", "Supply chain", "Scaling", "Recovery"]


def read(path):
    return path.read_text(encoding="utf-8")


def normalized(text):
    return re.sub(r"\s+", " ", text)


def milestone(number):
    roadmap = read(REPO_ROOT / "ROADMAP.md")
    section = roadmap.split(f"### Milestone {number} — ")[1].split("###")[0]
    return section.split("\n", 1)[1]


def lab_files():
    return [path for path in LAB.rglob("*") if path.is_file() and "target" not in path.parts]


class LabTwelveExistsTest(unittest.TestCase):

    def test_lab_is_a_module_of_the_build_with_the_deployment_files(self):
        self.assertIn("<module>labs/12-deployment</module>", read(REPO_ROOT / "pom.xml"))
        for name in ["README.md", "pom.xml", "Dockerfile", "scripts/smoke-test.sh",
                     "scripts/container-smoke-test.sh", "config/local.env", "config/test.env", "config/demo.env"]:
            self.assertTrue((LAB / name).is_file(), name)
        self.assertTrue((REPO_ROOT / ".dockerignore").is_file())

    def test_the_only_runtime_dependency_is_the_lab_eleven_module(self):
        pom = read(LAB / "pom.xml")
        dependencies = pom.split("<dependencies>")[1].split("</dependencies>")[0]
        self.assertEqual(["lab-11-security", "junit-jupiter"], re.findall(r"<artifactId>([^<]+)</artifactId>", dependencies))
        for forbidden in FORBIDDEN_DEPENDENCIES:
            self.assertNotIn(forbidden, dependencies.lower(), forbidden)
        self.assertNotIn("--enable-preview", pom)
        self.assertNotIn("<release>", pom.replace("${maven.compiler.release}", ""))

    def test_sources_use_no_framework_and_no_other_lab_than_lab_eleven(self):
        for source in LAB.rglob("*.java"):
            text = read(source)
            for forbidden in ["org.springframework", "io.opentelemetry", "org.slf4j", "java.util.logging",
                              "dev.agentic.handbook.labs.observability", "dev.agentic.handbook.labs.mcp",
                              "dev.agentic.handbook.labs.agentruntime", "com.google.genai", "jdk.incubator"]:
                self.assertNotIn(forbidden, text, f"{source.name}: {forbidden}")

    def test_readme_has_every_required_section_and_the_load_bearing_statements(self):
        readme = normalized(read(LAB / "README.md"))
        for section in REQUIRED_SECTIONS:
            self.assertIn(section, readme, section)
        for fact in [
            "**Deployment packages and runs the application. It does not redefine who decides what the application may do.**",
            "**A container is not an authorization boundary.",
            "**Secrets are supplied at runtime, not packaged into the artifact.**",
            "This is a teaching deployment, not a production platform.",
            "Build once, run the same artifact.",
            "Whoever does reach `/assist` still gets only what the policy gives their principal.",
            "the **denial**, and that the **privileged operation never executed**",
            "Why killing an agentic process is worse than killing a stateless service",
            "production uses a dedicated secret manager",
            "**No new ADR.**",
            "**What this lab does not do: authenticate.**",
        ]:
            self.assertIn(fact, readme, fact)
        not_solved = readme.split("## What This Project Does Not Solve")[1].split("## Production Gap Analysis")[0]
        for item in NOT_SOLVED:
            self.assertIn(item, not_solved, item)
        gap = readme.split("## Production Gap Analysis")[1].split("## Reflection Questions")[0]
        self.assertIn("| Concern | Handbook (this lab) | Production |", gap)
        for concern in GAP_CONCERNS:
            self.assertIn(f"| {concern} |", gap, concern)

    def test_readme_documents_the_dockerfile_decisions(self):
        readme = normalized(read(LAB / "README.md"))
        for fact in ["| Build stage |", "| Runtime stage |", "| Artifact copied |", "| Runtime user |", "| Port |",
                     "| Start command |", "| Configuration source |", "Exec form"]:
            self.assertIn(fact, readme, fact)

    def test_readme_does_not_claim_the_image_was_built_where_it_was_not(self):
        readme = normalized(read(LAB / "README.md"))
        self.assertIn("The image build and the container smoke test are performed by CI", readme)
        self.assertNotIn("production-ready", readme.lower().replace("not a production-ready", ""))

    def test_the_documented_environment_variables_are_the_ones_the_code_reads(self):
        code = read(LAB / "src" / "main" / "java" / "dev" / "agentic" / "handbook" / "labs" / "deployment" / "DeploymentConfig.java")
        readme = read(LAB / "README.md")
        in_code = set(re.findall(r'"(APP_[A-Z_]+)"', code))
        in_table = set(re.findall(r"^\| `(APP_[A-Z_]+)` \|", readme, re.MULTILINE))
        self.assertEqual(in_code, in_table)

    def test_scripts_are_executable_and_the_samples_carry_no_secret(self):
        for script in (LAB / "scripts").glob("*.sh"):
            self.assertTrue(script.stat().st_mode & 0o111, f"{script.name} must be executable")
            self.assertTrue(read(script).startswith("#!/usr/bin/env bash"))
        for sample in (LAB / "config").glob("*.env"):
            self.assertNotIn("APP_MONITORING_TOKEN=", read(sample), sample.name)

    def test_no_real_credential_shape_exists_anywhere_in_the_lab_or_the_workflow(self):
        for path in [*lab_files(), WORKFLOWS / "build.yml", REPO_ROOT / ".dockerignore"]:
            text = read(path)
            self.assertNotRegex(text, r"AIza[0-9A-Za-z_-]{30,}", path.name)
            self.assertNotRegex(text, r"sk-[A-Za-z0-9]{20,}", path.name)
            self.assertNotRegex(text, r"-----BEGIN [A-Z ]*PRIVATE KEY", path.name)
            self.assertNotRegex(text, r"ghp_[A-Za-z0-9]{20,}", path.name)

    def test_no_build_output_or_stray_environment_file_is_part_of_the_lab(self):
        for path in lab_files():
            self.assertNotIn(path.suffix, {".jar", ".class", ".pem", ".key"}, str(path))
            if path.suffix == ".env":
                self.assertEqual("config", path.parent.name, str(path))
        self.assertIn("target/", read(REPO_ROOT / ".gitignore"))

    def test_nothing_beyond_scope_was_added(self):
        for pattern in ["Jenkinsfile", "docker-compose*.y*ml", "compose.y*ml", "*.tf", "Chart.yaml",
                        "skaffold.yaml", "kustomization.yaml"]:
            self.assertEqual([], [p for p in REPO_ROOT.rglob(pattern) if ".git" not in p.parts and "target" not in p.parts],
                             pattern)
        for path in REPO_ROOT.rglob("*.y*ml"):
            if ".git" in path.parts or "target" in path.parts or path.parent != WORKFLOWS and ".github" not in path.parts:
                continue
            self.assertNotIn("kind: Deployment", read(path), str(path))
        self.assertFalse((REPO_ROOT / "labs" / "12-production").exists())

    def test_jev_is_still_only_a_track_c_note_and_no_ai_authorship_appears(self):
        self.assertEqual(1, read(REPO_ROOT / "ROADMAP.md").count("Jev"))
        for path in lab_files():
            text = read(path)
            self.assertNotIn("Jev", text, path.name)
            self.assertNotIn("co-authored-by", text.lower(), path.name)
            self.assertNotIn("generated with", text.lower(), path.name)


class DockerfileTest(unittest.TestCase):

    def test_the_dockerfile_follows_the_documented_decisions(self):
        text = read(LAB / "Dockerfile")
        instructions = [line.strip() for line in text.replace("\\\n", " ").splitlines()
                        if line.strip() and not line.strip().startswith("#")]
        froms = [line for line in instructions if line.startswith("FROM ")]
        self.assertEqual(2, len(froms))
        self.assertIn("-jdk", froms[0])
        self.assertIn("-jre", froms[1])
        self.assertEqual(["EXPOSE 8080"], [line for line in instructions if line.startswith("EXPOSE ")])
        users = [line for line in instructions if line.startswith("USER ")]
        self.assertEqual(1, len(users))
        self.assertNotIn(users[0].split()[1].split(":")[0], {"root", "0"})
        entry = next(line for line in instructions if line.startswith("ENTRYPOINT "))
        self.assertTrue(entry.startswith('ENTRYPOINT ["java"'))
        self.assertNotRegex(text, r"(?im)^\s*(ENV|ARG)\s+\w*(SECRET|TOKEN|PASSWORD|KEY)")


class CiTest(unittest.TestCase):

    def test_the_existing_build_workflow_was_extended_not_replaced(self):
        names = sorted(path.name for path in WORKFLOWS.glob("*.yml"))
        self.assertEqual(["build.yml", "deploy-pages.yml", "sync-milestones.yml"], names)
        build = read(WORKFLOWS / "build.yml")
        self.assertIn("name: Build", build)
        self.assertIn("java-version: \"27\"", build)
        self.assertIn("./mvnw -B verify", build)
        for step in ["java -version", "smoke-test.sh", "docker build -f labs/12-deployment/Dockerfile",
                     "container-smoke-test.sh", "upload-artifact"]:
            self.assertIn(step, build, step)

    def test_the_pipeline_pushes_nothing_and_uses_no_secret(self):
        build = read(WORKFLOWS / "build.yml")
        for forbidden in ["docker push", "docker login", "docker/login-action", "GOOGLE_API_KEY",
                          "GEMINI_API_KEY", "id-token", "aws-", "azure/", "google-github-actions"]:
            self.assertNotIn(forbidden, build, forbidden)
        self.assertNotRegex(build, r"\$\{\{\s*secrets\.")
        self.assertIn("permissions:\n  contents: read", build)
        self.assertNotIn("--live", build)


class StatusTest(unittest.TestCase):

    def test_roadmap_marks_twelve_done_and_invents_nothing_after_it(self):
        twelve = milestone(12)
        self.assertTrue(twelve.strip().startswith("Done. The lab is [labs/12-deployment](labs/12-deployment/README.md)."))
        roadmap = read(REPO_ROOT / "ROADMAP.md")
        self.assertIn("Milestones 0 through 12 are done", roadmap)
        self.assertNotIn("### Milestone 13", roadmap)
        self.assertEqual(13, len(re.findall(r"^### Milestone \d+ — ", roadmap, re.MULTILINE)))
        for track_c in ["Project Milestone — Reference Application v1", "Project Milestone — Advanced Topics v1"]:
            self.assertTrue(roadmap.split(f"### {track_c}")[1].strip().startswith("Not started."), track_c)

    def test_readme_marks_deployment_implemented_without_calling_it_production(self):
        readme = read(REPO_ROOT / "README.md")
        rows = {row.split("|")[1].strip(): row for row in readme.splitlines() if re.match(r"^\| \d+ \|", row)}
        self.assertIn("[Lab 12](labs/12-deployment/README.md)", rows["12"])
        self.assertIn('security["Security"] --> deployment["Deployment"]', readme)
        self.assertIn("│   └── 12-deployment/", readme)
        self.assertIn("twelve runnable labs", normalized(readme))
        self.assertIn("not a production platform", normalized(readme))

    def test_labs_index_links_lab_twelve(self):
        index = read(REPO_ROOT / "labs" / "README.md")
        self.assertIn("| [`12-deployment`](12-deployment/README.md) | 12 — Deployment |", index)
        self.assertIn("Twelve labs are available", index)
        self.assertIn("## After Lab 12", index)

    def test_landing_page_has_twelve_labs_with_deployment_last(self):
        landing = normalized(read(SITE / "index.html"))
        path = landing.split('class="path"')[1].split("</ol>")[0]
        self.assertEqual(12, path.count("<li>"))
        self.assertTrue(path.rstrip().endswith('/blob/main/labs/12-deployment/README.md">Deployment</a></li>'), path[-160:])
        self.assertIn("Twelve runnable Java labs", landing)
        self.assertIn("12 runnable Java labs</h3>", landing)
        self.assertIn("Labs 01 to 12", landing)
        note = landing.split('class="roadmap-note"')[1].split("</p>")[0]
        self.assertIn("not a production platform", note)

    def test_no_stale_counts_or_next_milestone_text_remain(self):
        for relative in ["README.md", "labs/README.md", "site/index.html", "ROADMAP.md"]:
            text = read(REPO_ROOT / relative)
            for stale in ["Eleven runnable", "eleven runnable", "Eleven labs", "11 runnable", "Labs 01–11",
                          "Labs 01 to 11", "deployment (Milestone 12, next)", "Milestones 0 through 11 are done",
                          "Docker is the intended packaging direction", "Deployment — on the"]:
                self.assertNotIn(stale, text, f"{relative}: {stale}")

    def test_lab_eleven_links_forward_to_lab_twelve(self):
        readme = read(REPO_ROOT / "labs" / "11-security" / "README.md")
        self.assertIn("../12-deployment/README.md", readme)
        self.assertNotIn("Not implemented here: no Docker, no environment configuration, and no deployment infrastructure exist in the repository",
                         readme)


class AdrTest(unittest.TestCase):

    def test_no_new_adr_was_added_and_the_lab_says_why(self):
        adrs = sorted(path.name for path in (REPO_ROOT / "docs" / "adr").glob("0*.md"))
        self.assertEqual(6, len(adrs), adrs)
        self.assertIn("**No new ADR.**", read(LAB / "README.md"))
        self.assertIn("No new ADR was needed", read(REPO_ROOT / "ROADMAP.md"))


class HandbookDocumentsTest(unittest.TestCase):

    def test_glossary_defines_the_deployment_terms_and_pairs(self):
        glossary = read(REPO_ROOT / "docs" / "glossary.md")
        for entry in ["**Deployment.**", "**Deployment artifact.**", "**Immutable artifact.**", "**Containerization.**",
                      "**Configuration.**", "**Secret injection.**", "**Liveness.**", "**Readiness.**",
                      "**Graceful shutdown.**", "**Deployment boundary.**", "## Liveness vs readiness",
                      "## Container vs authorization"]:
            self.assertIn(entry, glossary, entry)

    def test_mental_model_architecture_and_decision_authority_say_the_same_thing(self):
        mental = normalized(read(REPO_ROOT / "docs" / "mental-model.md"))
        self.assertIn("labs/12-deployment/README.md", mental)
        self.assertIn("### Deployment: the same boundary, packaged and run", mental)
        self.assertIn("neither is the container", mental)
        architecture = normalized(read(REPO_ROOT / "docs" / "architecture.md"))
        self.assertIn("## Deployment packages the boundary and does not redefine it", architecture)
        self.assertIn("A container, a network, or a loopback address is not an authorization boundary", architecture)
        authority = normalized(read(REPO_ROOT / "docs" / "model-vs-decision-authority.md"))
        self.assertIn("## Deploying a delegation", authority)
        self.assertIn("is not decision authority", authority)

    def test_capstone_and_assessment_point_to_lab_twelve_without_depending_on_it(self):
        for path in [CAPSTONE / "README.md", SITE / "capstone-01" / "index.html"]:
            text = normalized(read(path))
            self.assertIn("labs/12-deployment/README.md", text, path.name)
            self.assertIn("does not depend on Lab 12", text.replace("Finishing this capstone does not depend on Lab 12",
                                                                      "does not depend on Lab 12"), path.name)
        for pom in [CAPSTONE / "starter" / "pom.xml", CAPSTONE / "reference" / "pom.xml"]:
            self.assertNotIn("lab-12", read(pom), pom)
        assessment = normalized(read(REPO_ROOT / "assessments" / "agentic-system-readiness" / "README.md"))
        self.assertIn("labs/12-deployment/README.md", assessment)
        self.assertIn("The assessment deploys nothing", assessment)
        self.assertIn("labs/12-deployment/README.md", read(SITE / "readiness-assessment" / "index.html"))


if __name__ == "__main__":
    unittest.main()
