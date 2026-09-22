# Contributing

Questions, corrections, and pull requests are welcome. Small changes are easier to review than broad redesigns.

The current milestone is tracked in [ROADMAP.md](ROADMAP.md). Please do not start later milestones unless an issue asks for that work.

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
