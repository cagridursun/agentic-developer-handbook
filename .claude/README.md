# Claude Code setup for this repository

These files configure an optional **implement, code review, QA review** loop for Claude Code. They are tooling for contributors who use Claude Code; nothing in the handbook, the labs, or CI depends on them.

| File | Role |
| --- | --- |
| `agents/implementer.md` | The only role that edits files. Implements the task with tests and docs. |
| `agents/code-reviewer.md` | Read-only. Reviews the diff for bugs, project-principle violations, and dishonest documentation. |
| `agents/qa-reviewer.md` | Read-only. Runs the build and tests and exercises the delivered behavior against the acceptance criteria. |
| `skills/implement-loop/SKILL.md` | The `/implement-loop <task>` command that orchestrates the three, for at most three rounds. |

## Use it

1. Work on a branch, never on `main`. Use Java 27.
2. Start Claude Code in the repository root. Agents are loaded at startup, so restart Claude Code after these files are added or changed. `/agents` lists the loaded agents.
3. Run:

   ```
   /implement-loop <what you want implemented>
   ```

The loop shows its acceptance criteria, runs the roles, and stops when both reviewers approve or after three rounds. It never commits, pushes, or opens a pull request; you decide that after reading its report.

## Design notes

- Reviewers see the task and the result, not the implementer's reasoning, so they do not share its blind spots.
- Changes are measured from the commit the loop started at, and the implementer does not commit. The reviewers therefore read the working tree, and the loop also works on a branch that already carries earlier commits.
- Reviewers cannot edit. They report, and the implementer fixes.
- The orchestration runs in the main session, because it is the one place that can start subagents.
- The agents read the project's rules at run time instead of copying them: `CLAUDE.md`, `.cursor/rules/project-principles.mdc`, the ADRs under `docs/adr/`, and the handbook documents under `docs/`. Editing those files changes the agents' behavior without touching `.claude/`. Reviewers also check that documents stay consistent with each other, because a milestone changes many of them at once.
- The agents follow `CLAUDE.md` and `.cursor/rules/project-principles.mdc`, including the rule that no AI tool is ever a commit author or co-author.
- Local, personal settings belong in `.claude/settings.local.json`, which should not be committed.
