"""Unit tests for the ROADMAP parser and the synchronization plan.

Standard-library unittest only. Nothing here talks to GitHub: the parser and
compute_plan are pure functions, and the repository's own ROADMAP.md is
validated as a local file.

Run from the repository root:

    python -m unittest discover -s scripts/tests
"""

import importlib.util
import sys
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SCRIPT = REPO_ROOT / "scripts" / "sync-github-milestones.py"

spec = importlib.util.spec_from_file_location("sync_github_milestones", SCRIPT)
sync = importlib.util.module_from_spec(spec)
sys.modules["sync_github_milestones"] = sync
spec.loader.exec_module(sync)


ROADMAP_SAMPLE = """# Roadmap

Introduction text.

## Track A — Canonical Learning Path

Track prose that is not a milestone.

### Milestone 0 — Foundation

Done.

- Repository skeleton
- Continuous integration

### Milestone 1 — The Model

Done. The lab is [labs/01-model-call](labs/01-model-call/README.md).

The first Java lab.

### Milestone 2 — Structured Output

In progress.

Constrain a model response to a schema.

### Milestone 3 — Tools

Not started.

Let the application perform an action the model can only request.

## Track B — Open Source & Community

Track prose.

### Project Milestone — Community Foundations

In progress.

- Licensing is done
- Vulnerability reporting channel is pending

## Track C — Ecosystem

### Project Milestone — Ecosystem v1

Not started.

- Alternative inference providers
- Tool integrations

## How to read this list

Notes that are not milestones.

## GitHub milestone tracking

Explanatory prose mentioning milestones without being one.
"""


class ParseTracksTest(unittest.TestCase):

    def parsed(self):
        return sync.parse_roadmap(ROADMAP_SAMPLE)

    def test_track_headings_do_not_become_milestones(self):
        titles = [m.title for m in self.parsed()]
        for title in titles:
            self.assertFalse(title.startswith("Track"), title)
        self.assertEqual(6, len(titles))

    def test_canonical_milestones_under_track_a_parse(self):
        milestones = self.parsed()
        self.assertEqual([0, 1, 2, 3],
                         [m.number for m in milestones if m.number is not None])
        self.assertEqual("Milestone 0 — Foundation", milestones[0].title)

    def test_project_milestones_parse(self):
        titles = [m.title for m in self.parsed()]
        self.assertIn("Project Milestone — Community Foundations", titles)
        self.assertIn("Project Milestone — Ecosystem v1", titles)

    def test_project_milestones_have_no_number(self):
        projects = [m for m in self.parsed() if m.title.startswith("Project Milestone")]
        self.assertTrue(all(m.number is None for m in projects))

    def test_track_is_associated_with_each_milestone(self):
        by_title = {m.title: m for m in self.parsed()}
        self.assertEqual("Canonical Learning Path",
                         by_title["Milestone 0 — Foundation"].track)
        self.assertEqual("Open Source & Community",
                         by_title["Project Milestone — Community Foundations"].track)
        self.assertEqual("Ecosystem",
                         by_title["Project Milestone — Ecosystem v1"].track)

    def test_description_names_the_track(self):
        by_title = {m.title: m for m in self.parsed()}
        self.assertTrue(by_title["Milestone 0 — Foundation"]
                        .description.startswith("Track: Canonical Learning Path"))
        self.assertTrue(by_title["Project Milestone — Ecosystem v1"]
                        .description.startswith("Track: Ecosystem"))

    def test_description_does_not_consume_the_next_track(self):
        by_title = {m.title: m for m in self.parsed()}
        tools = by_title["Milestone 3 — Tools"].description
        self.assertIn("perform an action", tools)
        self.assertNotIn("Track B", tools)
        self.assertNotIn("Community Foundations", tools)

    def test_bullets_do_not_become_milestones(self):
        # "- Alternative inference providers" etc. stay inside descriptions.
        titles = [m.title for m in self.parsed()]
        self.assertEqual(6, len(titles))
        self.assertNotIn("Alternative inference providers", " ".join(titles))


class StateMappingTest(unittest.TestCase):

    def parsed(self):
        return {m.title: m for m in sync.parse_roadmap(ROADMAP_SAMPLE)}

    def test_done_maps_to_closed(self):
        self.assertEqual("closed", self.parsed()["Milestone 0 — Foundation"].state)

    def test_done_with_trailing_text_maps_to_closed(self):
        self.assertEqual("closed", self.parsed()["Milestone 1 — The Model"].state)

    def test_in_progress_maps_to_open(self):
        self.assertEqual("open", self.parsed()["Milestone 2 — Structured Output"].state)

    def test_not_started_maps_to_open(self):
        self.assertEqual("open", self.parsed()["Milestone 3 — Tools"].state)

    def test_project_milestone_states_use_the_same_rules(self):
        parsed = self.parsed()
        self.assertEqual("open", parsed["Project Milestone — Community Foundations"].state)
        self.assertEqual("open", parsed["Project Milestone — Ecosystem v1"].state)


class ValidationTest(unittest.TestCase):

    def test_missing_status_is_rejected(self):
        text = ("## Track A — Canonical Learning Path\n\n"
                "### Milestone 0 — Foundation\n\n"
                "### Milestone 1 — The Model\n\nDone.\n")
        with self.assertRaises(sync.RoadmapError):
            sync.parse_roadmap(text)

    def test_unknown_status_is_rejected(self):
        text = ("## Track A — Canonical Learning Path\n\n"
                "### Milestone 0 — Foundation\n\nAlmost finished.\n")
        with self.assertRaises(sync.RoadmapError):
            sync.parse_roadmap(text)

    def test_malformed_milestone_heading_is_rejected(self):
        # Hyphen instead of the canonical em dash must fail, not be skipped.
        text = ("## Track A — Canonical Learning Path\n\n"
                "### Milestone 0 - Foundation\n\nDone.\n")
        with self.assertRaises(sync.RoadmapError):
            sync.parse_roadmap(text)

    def test_duplicate_numbers_are_rejected(self):
        text = ("## Track A — Canonical Learning Path\n\n"
                "### Milestone 1 — A\n\nDone.\n\n"
                "### Milestone 1 — B\n\nDone.\n")
        with self.assertRaises(sync.RoadmapError):
            sync.parse_roadmap(text)

    def test_duplicate_project_titles_are_rejected(self):
        text = ("## Track B — Open Source & Community\n\n"
                "### Project Milestone — Community Foundations\n\nDone.\n\n"
                "### Project Milestone — Community Foundations\n\nNot started.\n")
        with self.assertRaises(sync.RoadmapError):
            sync.parse_roadmap(text)

    def test_canonical_milestone_outside_track_a_is_rejected(self):
        text = ("## Track B — Open Source & Community\n\n"
                "### Milestone 0 — Foundation\n\nDone.\n")
        with self.assertRaises(sync.RoadmapError):
            sync.parse_roadmap(text)

    def test_canonical_milestone_outside_any_track_is_rejected(self):
        text = "### Milestone 0 — Foundation\n\nDone.\n"
        with self.assertRaises(sync.RoadmapError):
            sync.parse_roadmap(text)

    def test_project_milestone_outside_a_track_is_rejected(self):
        text = "### Project Milestone — Community Foundations\n\nDone.\n"
        with self.assertRaises(sync.RoadmapError):
            sync.parse_roadmap(text)

    def test_project_milestone_after_non_track_h2_is_rejected(self):
        text = ("## Track B — Open Source & Community\n\n"
                "## How to read this list\n\n"
                "### Project Milestone — Community Foundations\n\nDone.\n")
        with self.assertRaises(sync.RoadmapError):
            sync.parse_roadmap(text)

    def test_empty_roadmap_is_rejected(self):
        with self.assertRaises(sync.RoadmapError):
            sync.parse_roadmap("# Roadmap\n\nNo milestones here.\n")

    def test_long_sections_truncate_with_pointer(self):
        body = "x" * (sync.MAX_DESCRIPTION_LENGTH * 2)
        description = sync.build_description(body, "Ecosystem")
        self.assertLessEqual(len(description), sync.MAX_DESCRIPTION_LENGTH)
        self.assertIn("ROADMAP.md", description)
        self.assertIn(sync.MANAGED_MARKER, description)
        self.assertTrue(description.startswith("Track: Ecosystem"))


class ComputePlanTest(unittest.TestCase):

    def wanted(self, title="Milestone 0 — Foundation", state="closed", description="d"):
        return sync.RoadmapMilestone(0, title, state, description, "Canonical Learning Path")

    def test_missing_milestone_is_created(self):
        plan = sync.compute_plan([self.wanted()], [])
        self.assertEqual(["create"], [action for action, _, _ in plan])

    def test_identical_milestone_is_unchanged(self):
        existing = [{"title": "Milestone 0 — Foundation", "state": "closed",
                     "description": "d", "number": 7}]
        plan = sync.compute_plan([self.wanted()], existing)
        self.assertEqual("unchanged", plan[0][0])

    def test_state_drift_is_an_update_including_reopening(self):
        existing = [{"title": "Milestone 0 — Foundation", "state": "closed",
                     "description": "d", "number": 7}]
        plan = sync.compute_plan([self.wanted(state="open")], existing)
        self.assertEqual("update", plan[0][0])
        self.assertIn("closed -> open", plan[0][2])

    def test_description_drift_is_an_update(self):
        # Existing Milestones 0-12 with the old description format are adopted
        # and updated in place, never duplicated: matching is by exact title.
        existing = [{"title": "Milestone 0 — Foundation", "state": "closed",
                     "description": "old-format description", "number": 7}]
        plan = sync.compute_plan([self.wanted()], existing)
        self.assertEqual("update", plan[0][0])
        self.assertEqual(1, len(plan))

    def test_crlf_descriptions_do_not_cause_false_drift(self):
        existing = [{"title": "Milestone 0 — Foundation", "state": "closed",
                     "description": "line1\r\nline2", "number": 7}]
        plan = sync.compute_plan([self.wanted(description="line1\nline2")], existing)
        self.assertEqual("unchanged", plan[0][0])

    def test_unmanaged_milestones_are_ignored(self):
        existing = [{"title": "v1.0 release", "state": "open",
                     "description": "manual", "number": 3}]
        plan = sync.compute_plan([self.wanted()], existing)
        titles_in_plan = [m.title for _, m, _ in plan]
        self.assertNotIn("v1.0 release", titles_in_plan)
        self.assertEqual(1, len(plan))


class RepositoryRoadmapTest(unittest.TestCase):
    """Validates the real ROADMAP.md so pull requests catch structural drift."""

    @classmethod
    def setUpClass(cls):
        text = (REPO_ROOT / "ROADMAP.md").read_text(encoding="utf-8")
        cls.milestones = sync.parse_roadmap(text)

    def test_seventeen_milestones_are_synchronized(self):
        canonical = [m for m in self.milestones if m.number is not None]
        projects = [m for m in self.milestones if m.number is None]
        self.assertEqual(13, len(canonical))
        self.assertEqual(4, len(projects))

    def test_canonical_titles_are_unchanged(self):
        titles = [m.title for m in self.milestones if m.number is not None]
        self.assertEqual("Milestone 0 — Foundation", titles[0])
        self.assertEqual("Milestone 12 — Deployment", titles[-1])
        numbers = [m.number for m in self.milestones if m.number is not None]
        self.assertEqual(list(range(13)), numbers)

    def test_project_milestone_titles(self):
        titles = {m.title for m in self.milestones if m.number is None}
        self.assertEqual({
            "Project Milestone — Community Foundations",
            "Project Milestone — Ecosystem v1",
            "Project Milestone — Reference Application v1",
            "Project Milestone — Advanced Topics v1",
        }, titles)

    def test_states_match_current_project_reality(self):
        by_title = {m.title: m for m in self.milestones}
        for number in range(4):
            title = next(t for t in by_title if t.startswith(f"Milestone {number} "))
            self.assertEqual("closed", by_title[title].state, title)
        self.assertEqual("open", by_title["Project Milestone — Community Foundations"].state)

    def test_every_milestone_has_a_track(self):
        for milestone in self.milestones:
            self.assertTrue(milestone.track, milestone.title)
            self.assertIn(f"Track: {milestone.track}", milestone.description)


if __name__ == "__main__":
    unittest.main()
