"""Cross-project facts that Milestone 8 (MCP) established.

Standard-library unittest only — no Maven, no network. The Java tests in
labs/08-mcp prove the lab works; these checks keep the rest of the repository
telling the same story: MCP is taught and available, it is not the headline,
Capstone 01 still deliberately leaves it out, and nothing after Milestone 10 is
presented as done (later milestones' facts live in test_milestone_9.py and test_milestone_10.py).

Run from the repository root:

    python -m unittest discover -s scripts/tests
"""

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
LAB = REPO_ROOT / "labs" / "08-mcp"
CAPSTONE = REPO_ROOT / "capstones" / "01-agentic-system"


def read(path):
    return path.read_text(encoding="utf-8")


def normalized(text):
    return re.sub(r"\s+", " ", text)


def milestone(number):
    """The body of a canonical milestone section, without its heading line."""
    roadmap = read(REPO_ROOT / "ROADMAP.md")
    section = roadmap.split(f"### Milestone {number} — ")[1].split("###")[0]
    return section.split("\n", 1)[1]


class LabEightExistsTest(unittest.TestCase):

    def test_lab_is_a_module_of_the_build(self):
        self.assertTrue((LAB / "README.md").is_file())
        self.assertTrue((LAB / "pom.xml").is_file())
        self.assertIn("<module>labs/08-mcp</module>", read(REPO_ROOT / "pom.xml"))

    def test_official_sdk_pinned_to_a_stable_release(self):
        pom = read(LAB / "pom.xml")
        self.assertRegex(pom, r"<groupId>io\.modelcontextprotocol\.sdk</groupId>\s*"
                              r"<artifactId>mcp</artifactId>\s*<version>2\.0\.1</version>")
        for forbidden in ["SNAPSHOT</version>", "-M", "-RC", "spring", "langchain4j"]:
            dependencies = pom.split("<dependencies>")[1].split("</dependencies>")[0]
            self.assertNotIn(forbidden, dependencies, forbidden)

    def test_readme_records_versions_sources_and_the_decision(self):
        readme = normalized(read(LAB / "README.md"))
        for fact in [
            "io.modelcontextprotocol.sdk:mcp:2.0.1",
            "2025-11-25",
            "2026-07-28",
            "## Sources consulted",
            "## Do I actually need MCP?",
            "Discovery is not permission.",
            "MCP does not create the agent.",
            "The model does not speak MCP.",
            "MCP does not replace REST.",
            "**Transport: STDIO.**",
            "**No ADR.**",
            "Milestone 9 — Evaluation",
        ]:
            self.assertIn(fact, readme, fact)


class StatusTest(unittest.TestCase):

    def test_roadmap_marks_eight_done_and_twelve_done_after_it(self):
        eight = milestone(8)
        self.assertTrue(eight.strip().startswith("Done. The lab is [labs/08-mcp](labs/08-mcp/README.md)."),
                        eight[:120])
        self.assertTrue(milestone(11).strip().startswith(
            "Done. The lab is [labs/11-security](labs/11-security/README.md)."))
        self.assertTrue(milestone(12).strip().startswith(
            "Done. The lab is [labs/12-deployment](labs/12-deployment/README.md)."))

    def test_readme_marks_mcp_implemented_and_deployment_next(self):
        readme = read(REPO_ROOT / "README.md")
        rows = {row.split("|")[1].strip(): row for row in readme.splitlines()
                if re.match(r"^\| \d+ \|", row)}
        self.assertIn("[Lab 08](labs/08-mcp/README.md)", rows["8"])
        self.assertIn("[Lab 11](labs/11-security/README.md)", rows["11"])
        self.assertIn("[Lab 12](labs/12-deployment/README.md)", rows["12"])
        not_yet = next(line for line in readme.splitlines() if "**Not implemented:**" in line)
        self.assertNotIn("MCP", not_yet)
        self.assertNotIn("security", not_yet)
        self.assertIn("production platform", not_yet)
        self.assertIn("│   ├── 08-mcp/", readme)
        self.assertNotIn("deployment (Milestone 12, next)", normalized(readme))

    def test_labs_index_links_lab_eight_and_no_later_placeholder(self):
        index = read(REPO_ROOT / "labs" / "README.md")
        self.assertIn("| [`08-mcp`](08-mcp/README.md) | 8 — MCP |", index)
        self.assertIn("| [`10-observability`](10-observability/README.md) | 10 — Observability |", index)

    def test_mcp_is_not_the_headline(self):
        readme = read(REPO_ROOT / "README.md")
        self.assertIn("**You probably don't need all of these.**", readme)
        headline = readme.split("## ")[0]
        self.assertNotIn("MCP", headline.split("**You probably")[0])


class CapstoneStillExcludesMcpTest(unittest.TestCase):

    CAPSTONE_TEXTS = [
        CAPSTONE / "README.md",
        CAPSTONE / "starter" / "DECISIONS.md",
        CAPSTONE / "reference" / "DECISIONS.md",
        REPO_ROOT / "site" / "capstone-01" / "index.html",
        REPO_ROOT / "site" / "js" / "capstone.js",
        REPO_ROOT / "site" / "js" / "markdown-export.js",
    ]

    def test_no_longer_says_mcp_is_not_taught(self):
        for path in self.CAPSTONE_TEXTS:
            text = normalized(read(path))
            for stale in ["not been taught", "not taught until", "NOT YET APPLICABLE"]:
                self.assertNotIn(stale, text, f"{path.name}: {stale}")
            self.assertIn("Lab 08", text, path.name)

    def test_still_does_not_select_mcp(self):
        self.assertIn("| MCP | NO |", read(CAPSTONE / "reference" / "DECISIONS.md"))
        self.assertNotIn('id: "mcp"', read(REPO_ROOT / "site" / "js" / "capstone.js"))
        for pom in [CAPSTONE / "starter" / "pom.xml", CAPSTONE / "reference" / "pom.xml"]:
            self.assertNotIn("modelcontextprotocol", read(pom), pom)
        self.assertIn('"McpClient"', read(
            CAPSTONE / "reference" / "src" / "test" / "java" / "dev" / "agentic" / "handbook"
            / "capstones" / "agenticsystem" / "reference" / "CapstoneReferenceTest.java"))


class ScopeTest(unittest.TestCase):

    def test_jev_is_a_track_c_note_only(self):
        roadmap = read(REPO_ROOT / "ROADMAP.md")
        track_c = roadmap.split("## Track C")[1].split("## Track D")[0]
        self.assertIn("Jev", track_c)
        self.assertEqual(1, roadmap.count("Jev"))
        self.assertEqual([], [p for p in REPO_ROOT.rglob("*")
                              if "jev" in p.name.lower() and ".git" not in p.parts])

    def test_no_future_milestone_lab_exists(self):
        for name in ["12-production"]:
            self.assertFalse((REPO_ROOT / "labs" / name).exists(), name)


if __name__ == "__main__":
    unittest.main()
