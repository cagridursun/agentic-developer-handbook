"""Relative-link checks for the repository's Markdown and site pages.

Standard-library unittest only — no network, no third-party validator.
External links (http/https/mailto) are not fetched; only repository-relative
targets are checked, so a moved or renamed file is caught before a reader
hits it.

Run from the repository root:

    python -m unittest discover -s scripts/tests
"""

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent

MARKDOWN_LINK = re.compile(r"\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
HTML_LINK = re.compile(r'(?:href|src)="([^"]+)"')
SKIPPED_DIRS = {".git", "target", "node_modules"}

LAUNCH_FACING = [
    "README.md", "CONTRIBUTING.md", "SECURITY.md", "CODE_OF_CONDUCT.md", "ROADMAP.md",
    "VISION.md", "labs/README.md", "capstones/README.md", "assessments/README.md",
    "site/README.md",
]


def documents():
    for path in sorted(REPO_ROOT.rglob("*")):
        if path.suffix not in {".md", ".html"} or not path.is_file():
            continue
        if SKIPPED_DIRS.intersection(path.relative_to(REPO_ROOT).parts):
            continue
        yield path


def relative_targets(path):
    text = path.read_text(encoding="utf-8")
    # Ignore fenced code blocks: they show commands and trees, not links.
    text = re.sub(r"```.*?```", "", text, flags=re.DOTALL)
    pattern = HTML_LINK if path.suffix == ".html" else MARKDOWN_LINK
    for target in pattern.findall(text):
        if target.startswith(("http://", "https://", "mailto:", "#")):
            continue
        yield target


class RelativeLinksTest(unittest.TestCase):

    def test_launch_facing_documents_exist(self):
        for relative in LAUNCH_FACING:
            self.assertTrue((REPO_ROOT / relative).is_file(), relative)

    def test_every_relative_link_resolves(self):
        broken = []
        for path in documents():
            for target in relative_targets(path):
                file_part = target.split("#")[0]
                if file_part and not (path.parent / file_part).exists():
                    broken.append(f"{path.relative_to(REPO_ROOT)} -> {target}")
        self.assertEqual([], broken)

    def test_planned_milestones_are_not_linked_as_implemented(self):
        readme = (REPO_ROOT / "README.md").read_text(encoding="utf-8")
        for row in readme.splitlines():
            if row.startswith("|") and row.rstrip().endswith("| Planned |"):
                self.assertNotIn("](", row, row)
        for future in ["labs/12-production"]:
            self.assertFalse((REPO_ROOT / future).exists(), future)
            for relative in LAUNCH_FACING:
                text = (REPO_ROOT / relative).read_text(encoding="utf-8")
                self.assertNotIn(f"]({future}", text, f"{relative} links {future}")


if __name__ == "__main__":
    unittest.main()
