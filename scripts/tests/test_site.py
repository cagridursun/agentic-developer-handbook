"""Static checks for the interactive learning site.

Standard-library unittest only — no browser automation, no Node, no network.
These checks verify structure and consistency; they do not replace manual
browser inspection, and they do not pretend to.

Run from the repository root:

    python -m unittest discover -s scripts/tests
"""

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SITE = REPO_ROOT / "site"

CAPABILITY_IDS = [
    "model", "structuredOutput", "tools", "knowledge",
    "memory", "skills", "agentRuntime",
]

# Mirrors capstones/01-agentic-system/reference/DECISIONS.md.
EXPECTED_REFERENCE_USE = {
    "model": True,
    "structuredOutput": False,
    "tools": True,
    "knowledge": True,
    "memory": False,
    "skills": True,
    "agentRuntime": True,
}


def read(path):
    return path.read_text(encoding="utf-8")


class SiteStructureTest(unittest.TestCase):

    def test_entry_files_exist(self):
        for relative in [
            "README.md", "index.html", "styles/main.css",
            "js/app.js", "js/storage.js", "js/capstone.js", "js/markdown-export.js",
            "capstone-01/index.html",
        ]:
            self.assertTrue((SITE / relative).is_file(), relative)

    def test_capstone_page_links_canonical_documents(self):
        page = read(SITE / "capstone-01" / "index.html")
        for target in [
            "capstones/01-agentic-system/README.md",
            "capstones/01-agentic-system/starter/DECISIONS.md",
            "docs/model-vs-decision-authority.md",
        ]:
            self.assertIn(target, page, target)
            # The linked canonical file must actually exist.
            self.assertTrue((REPO_ROOT / target).exists(), target)

    def test_no_network_dependencies(self):
        # The site is local: no CDN scripts, no external stylesheets, no fonts.
        for html in [read(SITE / "index.html"), read(SITE / "capstone-01" / "index.html")]:
            self.assertNotIn("https://", html.split("<body")[0].replace(
                "content=", ""), "no external resources in <head>")
            self.assertNotIn("cdn.", html)


class CapstoneDataTest(unittest.TestCase):

    def setUp(self):
        self.data = read(SITE / "js" / "capstone.js")

    def test_all_seven_capabilities_are_represented(self):
        for capability_id in CAPABILITY_IDS:
            self.assertIn(f'"{capability_id}"', self.data.replace("'", '"')
                          if False else self.data, capability_id)
            self.assertIn(f'id: "{capability_id}"', self.data, capability_id)

    def test_mcp_is_not_a_decision_capability(self):
        self.assertNotIn('id: "mcp"', self.data)
        # The page explains the absence instead. Normalize whitespace because
        # the prose wraps across source lines.
        page = re.sub(r"\s+", " ", read(SITE / "capstone-01" / "index.html"))
        self.assertIn("MCP", page)
        self.assertIn("has not been taught yet", page)

    def test_reference_choices_match_reference_decisions_md(self):
        # Parse the REFERENCE_DECISIONS block of capstone.js.
        block = self.data.split("REFERENCE_DECISIONS")[1].split("};")[0]
        for capability_id, expected_use in EXPECTED_REFERENCE_USE.items():
            match = re.search(capability_id + r":\s*\{\s*use:\s*(true|false)", block)
            self.assertIsNotNone(match, capability_id)
            self.assertEqual(str(expected_use).lower(), match.group(1), capability_id)

        # And the canonical document agrees with the expectation table above.
        decisions = read(REPO_ROOT / "capstones" / "01-agentic-system"
                         / "reference" / "DECISIONS.md")
        self.assertIn("| Model | YES |", decisions)
        self.assertIn("| Structured Output | NO |", decisions)
        self.assertIn("| Memory | NO |", decisions)
        self.assertIn("| Agent Runtime | YES |", decisions)

    def test_capstone_js_names_its_source(self):
        self.assertIn("capstones/01-agentic-system/reference/DECISIONS.md", self.data)


class StorageAndExportTest(unittest.TestCase):

    def test_storage_key_is_namespaced_and_versioned(self):
        storage = read(SITE / "js" / "storage.js")
        self.assertIn('"adh.learning.v1.capstone01"', storage)

    def test_export_contains_required_headings(self):
        export = read(SITE / "js" / "markdown-export.js")
        for required in [
            "# Architecture decisions",
            "| Capability | Use? | Why / why not? | Simpler alternative considered |",
            "## Decision authority",
            "What decisions may the model influence?",
            "What decisions must remain deterministic or application-controlled?",
        ]:
            self.assertIn(required, export, required)


class DecisionAuthorityDocTest(unittest.TestCase):

    def test_doc_exists_with_core_statements(self):
        doc = read(REPO_ROOT / "docs" / "model-vs-decision-authority.md")
        self.assertIn("# LLM vs Decision Authority", doc)
        self.assertIn("Model influence ≠ decision authority", doc)
        self.assertIn("Decision Envelope".lower(), doc.lower())

    def test_starter_decisions_include_authority_questions(self):
        starter = read(REPO_ROOT / "capstones" / "01-agentic-system"
                       / "starter" / "DECISIONS.md")
        self.assertIn("What decisions may the model influence?", starter)
        self.assertIn("What decisions must remain deterministic or application-controlled?",
                      starter)


if __name__ == "__main__":
    unittest.main()
