# Contributing

Questions, corrections, and pull requests are welcome. Small changes are easier to review than broad redesigns.

The current milestone is tracked in [ROADMAP.md](ROADMAP.md), and its explicit milestones are mirrored to GitHub Milestones, so issues and pull requests may be associated with the matching milestone. The numbered canonical milestones are maintainer-curated; the project-level milestones carry open-source, community, and ecosystem work. GitHub Milestones are operational tracking, not the canonical roadmap source — ROADMAP.md is.

## Ways to contribute

Contributions are broader than typo fixes. Useful work includes:

- factual or conceptual corrections in the handbook documents
- documentation improvements and diagrams
- bug fixes in existing labs
- tests, especially deterministic ones that need no API key
- SDK and API compatibility updates when a provider changes something a lab uses
- security improvements: validation, safer defaults, clearer trust-boundary explanations
- improvements to completed canonical labs — clearer code, better explanations, stronger "when not to use this" sections
- once the relevant handbook concept exists in the canonical path: curated integrations and examples that extend it

## Canonical path vs community extensions

The repository has two contribution surfaces with different rules.

**The canonical path is curated.** The numbered labs and the core conceptual documents form a maintainer-curated learning journey; its order and scope are deliberate. Improving a completed lab is welcome. Implementing a future numbered milestone, reordering the path, or replacing a canonical lab with a vendor or framework tutorial is not — unless an issue explicitly asks for that work. This restriction protects the learning sequence; it does not limit community participation elsewhere.

**Application examples are extensible.** Long term, the project will accept curated community examples around concepts the handbook has already taught: alternative providers, tool integrations, retrieval backends, memory implementations, skills examples, MCP integrations, evaluation and observability examples, and real-world use cases. There is no `examples/` directory yet; when it exists, this section will link to its layout.

A substantial example should be able to answer:

1. What problem does this solve?
2. Which handbook concepts does it use?
3. Why are those concepts needed?
4. Which concepts were deliberately NOT used?
5. Why would a simpler solution not be sufficient?
6. How can I run it?
7. How is it tested?
8. What are the security implications?
9. What would change in production?

An example that only demonstrates library syntax without teaching an architectural concept is unlikely to be accepted. The repository is not a link collection, a provider catalog, or a marketplace for skills or prompts, and submissions in that direction will be declined.

## What we expect

- Be respectful. [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) is the standard.
- Explain why a change is needed.
- Keep terminology consistent with [docs/glossary.md](docs/glossary.md).
- For a concept, say when to use it and when not to.
- Prefer a small, explicit change over a new abstraction.
- Do not add Spring Boot, Spring AI, databases, or infrastructure before the milestone that needs them.
- Do not present unfinished work as a completed feature.
- Do not commit secrets, API keys, or credentials.

Architecture decisions that change direction belong in [docs/adr/](docs/adr/README.md). A trivial implementation detail does not need an ADR.

## Fork and branch

1. Fork the repository.
2. Clone your fork.
3. Create a branch from `main`:

   ```sh
   git checkout -b short-description
   ```

## Make the change

Keep the diff focused on one issue. If the change affects behavior or a documented decision, update the related document in the same pull request.

## Validate

From the repository root:

```sh
./mvnw verify
```

On Windows:

```sh
mvnw.cmd verify
```

`verify` must pass before you open a pull request. It does not need an API key. If you add tests, keep the default CI path deterministic. Tests that call a paid model provider should be opt-in.

## Open a pull request

Push your branch and open a pull request against `main`. The template asks:

- What changed?
- Why?
- How was it tested?
- Related issue
- Whether documentation was updated
- Whether the change is breaking

Fill those in. A short explanation is enough.

## Security issues

Do not file a public issue for an undisclosed vulnerability. Read [SECURITY.md](SECURITY.md). The private contact is not configured yet.
