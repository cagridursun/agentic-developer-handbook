#!/usr/bin/env python3
"""Synchronize GitHub milestones from ROADMAP.md.

ROADMAP.md is canonical. GitHub Milestones are the operational tracking view.
The direction is strictly one way:

    ROADMAP.md -> GitHub Milestones

The script is idempotent and additive: it creates missing milestones, updates
managed milestones that drifted, and never deletes anything. Milestones whose
titles do not match a canonical or project milestone section in ROADMAP.md are
left untouched. Track headings are structure, never milestones.

Standard library only. Verified against the GitHub REST API reference for
milestones (API version 2026-03-10).
"""

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path

API_ROOT = "https://api.github.com"
API_VERSION = "2026-03-10"
USER_AGENT = "agentic-developer-handbook-milestone-sync"

MANAGED_MARKER = "<!-- managed-by: scripts/sync-github-milestones.py -->"

# The REST reference documents no explicit description limit, so this is a
# deliberately conservative bound. Longer sections are truncated with a pointer
# back to ROADMAP.md instead of risking a 422.
MAX_DESCRIPTION_LENGTH = 5000
TRUNCATION_NOTICE = "\n\n(Truncated. The full text lives in ROADMAP.md.)"

# ROADMAP.md structure: "## Track <letter> — <name>" sections containing
# "### Milestone <number> — <title>" (canonical learning path) and
# "### Project Milestone — <title>" (project-level) headings, em dash exactly
# as ROADMAP.md writes it. Anything that starts like a milestone heading but
# does not match the canonical form is an error rather than silently skipped.
TRACK_HEADING = re.compile(r"^## Track [A-Z] — (\S.*)$")
MILESTONE_HEADING = re.compile(r"^### Milestone (\d+) — (\S.*)$")
PROJECT_HEADING = re.compile(r"^### Project Milestone — (\S.*)$")
MILESTONE_HEADING_LOOSE = re.compile(
    r"^#{2,4} (Project )?Milestone\b", re.IGNORECASE)

CANONICAL_TRACK = "Canonical Learning Path"

STATE_BY_STATUS = {
    "Done.": "closed",
    "Not started.": "open",
    "In progress.": "open",
}


class RoadmapError(Exception):
    """ROADMAP.md violates the structure this script relies on."""


class GitHubError(Exception):
    """A GitHub API call failed. Never carries headers or tokens."""


@dataclass(frozen=True)
class RoadmapMilestone:
    number: int  # canonical milestone number, or None for project milestones
    title: str  # full GitHub title, e.g. "Milestone 4 — Knowledge / RAG"
    state: str  # "open" or "closed"
    description: str
    track: str  # the Track name the milestone belongs to


# ---------------------------------------------------------------------------
# ROADMAP parsing
# ---------------------------------------------------------------------------

def parse_roadmap(text):
    """Parses canonical and project milestone sections out of ROADMAP.md.

    Only two heading forms become GitHub milestones:

        ### Milestone <number> — <title>       (must be inside Track A)
        ### Project Milestone — <title>        (must be inside some Track)

    Track headings themselves are structure, never milestones. The whole
    document is validated before anything talks to GitHub: malformed headings,
    missing or unknown status lines, milestones outside their required track,
    and duplicate numbers or titles all raise RoadmapError.
    """
    lines = text.splitlines()

    # Collect (line_index, number_or_None, github_title, track) headings while
    # tracking which Track section we are inside.
    headings = []
    current_track = None
    for index, line in enumerate(lines):
        track_match = TRACK_HEADING.match(line)
        if track_match:
            current_track = track_match.group(1).strip()
            continue
        if line.startswith("## "):
            # Any other H2 (e.g. "How to read this list") ends the track.
            current_track = None
            continue

        if not MILESTONE_HEADING_LOOSE.match(line):
            continue

        canonical = MILESTONE_HEADING.match(line)
        project = PROJECT_HEADING.match(line)
        if canonical:
            number = int(canonical.group(1))
            title = f"Milestone {number} — {canonical.group(2).strip()}"
            if current_track != CANONICAL_TRACK:
                raise RoadmapError(
                    f"{title}: canonical milestones must be inside "
                    f"'## Track A — {CANONICAL_TRACK}', found in {current_track!r}.")
            headings.append((index, number, title, current_track))
        elif project:
            title = f"Project Milestone — {project.group(1).strip()}"
            if current_track is None:
                raise RoadmapError(f"{title}: project milestones must be inside a Track.")
            headings.append((index, None, title, current_track))
        else:
            raise RoadmapError(
                "Malformed milestone heading (expected '### Milestone <number> — <title>' "
                "or '### Project Milestone — <title>'): " + line.strip())

    milestones = []
    for start, number, title, track in headings:
        # The section runs until the next H2 or H3 heading, so a milestone
        # description can never swallow the next Track or milestone.
        end = len(lines)
        for index in range(start + 1, len(lines)):
            if lines[index].startswith("## ") or lines[index].startswith("### "):
                end = index
                break

        section = lines[start + 1:end]

        # The first meaningful line is the status line.
        status_line = None
        status_index = None
        for index, line in enumerate(section):
            if line.strip():
                status_line = line.strip()
                status_index = index
                break
        if status_line is None:
            raise RoadmapError(f"{title}: no status line found below the heading.")

        state = None
        for prefix, mapped in STATE_BY_STATUS.items():
            if status_line.startswith(prefix):
                state = mapped
                break
        if state is None:
            raise RoadmapError(
                f"{title}: unknown status line {status_line!r}. "
                f"Expected one of: {', '.join(STATE_BY_STATUS)}")

        body = "\n".join(section[status_index + 1:]).strip()
        description = build_description(body, track)

        milestones.append(RoadmapMilestone(number, title, state, description, track))

    if not milestones:
        raise RoadmapError("No milestone sections found in ROADMAP.md.")

    numbers = [m.number for m in milestones if m.number is not None]
    if len(set(numbers)) != len(numbers):
        raise RoadmapError(f"Duplicate milestone numbers: {sorted(numbers)}")
    titles = [m.title for m in milestones]
    if len(set(titles)) != len(titles):
        duplicates = sorted({t for t in titles if titles.count(t) > 1})
        raise RoadmapError(f"Duplicate milestone titles in ROADMAP.md: {duplicates}")

    return milestones


def build_description(body, track):
    """Prepends the track, appends the managed marker, truncates safely."""
    prefix = f"Track: {track}"
    parts = [prefix, body, MANAGED_MARKER] if body else [prefix, MANAGED_MARKER]
    description = "\n\n".join(parts)
    if len(description) <= MAX_DESCRIPTION_LENGTH:
        return description

    overhead = len(prefix) + len(TRUNCATION_NOTICE) + len(MANAGED_MARKER) + 4
    truncated = body[:MAX_DESCRIPTION_LENGTH - overhead].rstrip()
    return "\n\n".join([prefix, truncated + TRUNCATION_NOTICE, MANAGED_MARKER])


# ---------------------------------------------------------------------------
# Synchronization plan (pure logic, unit-testable without a network)
# ---------------------------------------------------------------------------

def compute_plan(roadmap_milestones, existing_milestones):
    """Compares ROADMAP milestones with GitHub state.

    Returns a list of actions: ("unchanged"|"create"|"update", milestone, detail).
    Existing GitHub milestones whose titles match no ROADMAP milestone are
    ignored entirely; this function never proposes deleting or touching them.
    """
    by_title = {m["title"]: m for m in existing_milestones}
    plan = []
    for wanted in roadmap_milestones:
        current = by_title.get(wanted.title)
        if current is None:
            plan.append(("create", wanted, f"[{wanted.state}]"))
            continue

        changes = []
        if current["state"] != wanted.state:
            changes.append(f"{current['state']} -> {wanted.state}")
        if _normalize(current.get("description")) != _normalize(wanted.description):
            changes.append("description")

        if changes:
            plan.append(("update", wanted, "[" + ", ".join(changes) + "]"))
        else:
            plan.append(("unchanged", wanted, ""))
    return plan


def _normalize(text):
    """GitHub may return CRLF; compare with normalized line endings."""
    if text is None:
        return ""
    return text.replace("\r\n", "\n").strip()


# ---------------------------------------------------------------------------
# GitHub REST API (standard library only)
# ---------------------------------------------------------------------------

def github_request(method, url, token, body=None):
    data = None
    headers = {
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "X-GitHub-Api-Version": API_VERSION,
        "User-Agent": USER_AGENT,
    }
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"

    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = ""
        try:
            payload = json.loads(error.read().decode("utf-8"))
            detail = payload.get("message", "")
        except Exception:
            pass
        # Deliberately excludes headers and the token.
        raise GitHubError(
            f"{method} {url} failed with HTTP {error.code}. {detail}".strip()) from None
    except urllib.error.URLError as error:
        raise GitHubError(f"{method} {url} failed: {error.reason}") from None


def list_milestones(repo, token):
    """Lists every milestone, open and closed, across all pages."""
    milestones = []
    page = 1
    while True:
        query = urllib.parse.urlencode({"state": "all", "per_page": 100, "page": page})
        url = f"{API_ROOT}/repos/{repo}/milestones?{query}"
        batch = github_request("GET", url, token)
        milestones.extend(batch)
        if len(batch) < 100:
            return milestones
        page += 1


def create_milestone(repo, token, milestone):
    url = f"{API_ROOT}/repos/{repo}/milestones"
    github_request("POST", url, token, {
        "title": milestone.title,
        "state": milestone.state,
        "description": milestone.description,
    })


def update_milestone(repo, token, github_number, milestone):
    # due_on is intentionally never sent: the roadmap is undated, and PATCH
    # only changes the fields it receives, so existing due dates are untouched.
    url = f"{API_ROOT}/repos/{repo}/milestones/{github_number}"
    github_request("PATCH", url, token, {
        "title": milestone.title,
        "state": milestone.state,
        "description": milestone.description,
    })


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def synchronize(roadmap_path, repo, token, dry_run, check):
    milestones = parse_roadmap(roadmap_path.read_text(encoding="utf-8"))
    existing = list_milestones(repo, token)
    plan = compute_plan(milestones, existing)

    print("Agentic Developer Handbook milestone sync")
    print()

    counts = {"created": 0, "updated": 0, "unchanged": 0}
    by_title = {m["title"]: m for m in existing}

    for action, milestone, detail in plan:
        if action == "unchanged":
            counts["unchanged"] += 1
            print(f"UNCHANGED {milestone.title}")
        elif action == "create":
            counts["created"] += 1
            print(f"CREATE    {milestone.title} {detail}")
            if not dry_run and not check:
                create_milestone(repo, token, milestone)
        elif action == "update":
            counts["updated"] += 1
            print(f"UPDATE    {milestone.title} {detail}")
            if not dry_run and not check:
                update_milestone(repo, token, by_title[milestone.title]["number"], milestone)

    print()
    print("Summary:")
    print(f"created: {counts['created']}")
    print(f"updated: {counts['updated']}")
    print(f"unchanged: {counts['unchanged']}")

    drift = counts["created"] + counts["updated"]
    if check and drift:
        print()
        print(f"--check: {drift} milestone(s) out of sync with ROADMAP.md.")
        return 1
    return 0


def main():
    parser = argparse.ArgumentParser(
        description="Synchronize GitHub milestones from ROADMAP.md (one direction only).")
    parser.add_argument("--repo",
                        default=os.environ.get("GITHUB_REPOSITORY"),
                        help="owner/name, defaults to $GITHUB_REPOSITORY")
    parser.add_argument("--roadmap",
                        default=str(Path(__file__).resolve().parent.parent / "ROADMAP.md"),
                        help="path to ROADMAP.md, defaults to the repository root copy")
    parser.add_argument("--dry-run", action="store_true",
                        help="print the plan, make no changes, exit 0")
    parser.add_argument("--check", action="store_true",
                        help="make no changes, exit non-zero when drift exists")
    args = parser.parse_args()

    if args.dry_run and args.check:
        print("Use either --dry-run or --check, not both: --dry-run always exits 0, "
              "--check fails on drift.", file=sys.stderr)
        return 2

    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if not token:
        print("No token found. Set GITHUB_TOKEN (or GH_TOKEN).", file=sys.stderr)
        return 2
    if not args.repo:
        print("No repository configured. Set GITHUB_REPOSITORY or pass --repo owner/name.",
              file=sys.stderr)
        return 2

    roadmap_path = Path(args.roadmap)
    if not roadmap_path.is_file():
        print(f"ROADMAP file not found: {roadmap_path}", file=sys.stderr)
        return 2

    try:
        return synchronize(roadmap_path, args.repo, token, args.dry_run, args.check)
    except RoadmapError as error:
        print(f"ROADMAP.md is invalid, nothing was changed: {error}", file=sys.stderr)
        return 2
    except GitHubError as error:
        print(f"GitHub API error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
