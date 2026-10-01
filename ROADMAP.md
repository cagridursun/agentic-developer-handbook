# Roadmap

This file is the canonical source of truth for the whole project's roadmap. It is organized into tracks: the numbered milestones belong specifically to the canonical learning path, and the other tracks carry project-level work around community, ecosystem, the reference application, and advanced topics.

GitHub Milestones are the operational tracking view generated from this file. Milestones are ordered, not dated.

The learning path is a teaching sequence, not an architecture every application must implement. Not every application needs every canonical capability — skip a concept when the problem does not need it.

## Track A — Canonical Learning Path

The maintainer-curated sequence of concepts and runnable labs. Milestones 0 through 10 are done. Later milestones are planned work and are not implemented.

### Milestone 0 — Foundation

Done.

- Repository skeleton
- Handbook documents: vision, mental model, glossary, architecture
- Architecture decision records
- Contribution model, code of conduct, and security reporting
- Continuous integration
- A Maven build that succeeds without application code and without API keys

### Milestone 1 — The Model

Done. The lab is [labs/01-model-call](labs/01-model-call/README.md).

- A real LLM call
- The difference between a model and an agent
- What an inference provider is
- The first Java lab

### Milestone 2 — Structured Output

Done. The lab is [labs/02-structured-output](labs/02-structured-output/README.md).

Constrain a model response to a schema the application can validate.

### Milestone 3 — Tools

Done. The lab is [labs/03-tool-calling](labs/03-tool-calling/README.md).

Let the application perform an action the model can only request.

### Milestone 4 — Knowledge / RAG

Done. The lab is [labs/04-rag](labs/04-rag/README.md).

Bring outside information into the prompt. Include the cases where an ordinary query is the right tool and retrieval is not.

### Milestone 5 — Memory

Done. The lab is [labs/05-memory](labs/05-memory/README.md).

Decide what the application keeps across turns, and what it should forget.

### Milestone 6 — Skills

Done. The lab is [labs/06-skills](labs/06-skills/README.md).

Show Agent Skills / `SKILL.md` as instructions for how a task should be performed. A few example skills, not a catalog and not a skill runtime. See [ADR 0004](docs/adr/0004-skills-are-a-building-block.md).

### Milestone 7 — Agent Runtime

Done. The lab is [labs/07-agent-runtime](labs/07-agent-runtime/README.md).

An execution loop that can call a model, use tools, and stop. This is the first point at which the handbook will call the example an agent.

### Milestone 8 — MCP

Done. The lab is [labs/08-mcp](labs/08-mcp/README.md).

Connect the runtime to external capabilities through the Model Context Protocol. Show why a local Java call is not a reason to introduce MCP.

### Milestone 9 — Evaluation

Done. The lab is [labs/09-evaluation](labs/09-evaluation/README.md).

Check behavior, not only components: a small versioned evaluation set, checks on both the final answer and the trajectory, and a baseline-versus-candidate comparison that makes a regression visible. Paid provider calls stay opt-in so normal CI does not need API keys.

### Milestone 10 — Observability

Done. The lab is [labs/10-observability](labs/10-observability/README.md).

Reconstruct what one real run did: a trace id shared by every span, nested model-call, decision, validation, and tool spans, a few structured log lines, a handful of in-memory metrics, and a diagnosis of a realistic failure from the trace. The lab teaches logs, metrics, and traces with a small plain-Java model and explains OpenTelemetry as the production ecosystem; it adds no OpenTelemetry dependency, no backend, and no collector. What may be recorded, and who may read it, is Milestone 11.

### Milestone 11 — Security

Not started.

Boundaries: user input, model output, tool arguments, retrieved content, skill text, and protocol messages. The model is not the authorization layer.

### Milestone 12 — Deployment

Not started.

What has to be true to run the reference system as a deployed application. Docker is the intended packaging direction when this milestone starts. It is not part of the repository yet.

## Track B — Open Source & Community

Making the project a healthy, contributable open-source project rather than only published source code.

### Project Milestone — Community Foundations

In progress.

Established:

- Apache 2.0 licensing with NOTICE
- Contribution guidelines, including the canonical-vs-community contribution model
- Code of Conduct
- A pull request template
- Continuous integration without API keys
- Architecture decision records
- Private vulnerability reporting through GitHub (see [SECURITY.md](SECURITY.md))
- A Code of Conduct enforcement contact
- Issue templates for corrections, learning feedback, and proposals, and a README "Start here" path
- Repository description, homepage, and GitHub topics
- Social preview metadata and image for the public site

Remaining and evolving:

- Improve contributor onboarding
- Define a community example proposal workflow
- Labels and an issue taxonomy when volume needs them
- Maintainer guidance as the community grows

## Track C — Ecosystem

The canonical labs teach concepts. The ecosystem demonstrates alternative applications of concepts that have already been taught.

### Project Milestone — Ecosystem v1

Not started.

Curated contribution areas, each opened only after the canonical path has taught the underlying concept:

- Alternative inference providers
- Tool integrations
- RAG and retrieval implementations
- Memory implementations
- Skills examples
- MCP integrations
- Evaluation examples
- Observability integrations
- Framework-based alternatives, such as Spring AI or LangChain4j, where they genuinely clarify a concept
- Real-world use cases

Standing constraints: this must not become an uncurated example dump, a provider catalog, or a skill marketplace. An example must teach an architectural decision, not only library syntax. The acceptance questions are in [CONTRIBUTING.md](CONTRIBUTING.md).

Future candidate, recorded rather than scheduled — Milestone 9 (Evaluation) is now done, so the prerequisite is met; it is still not scheduled, not started, and not part of the canonical learning path: a comparative decision-model example that puts three approaches side by side on the same decision problem — deterministic rules, a general LLM with structured output, and Jev as a specialized probabilistic decision model. Its purpose would be to teach that not every probabilistic decision needs a general generative model, that a typed probabilistic decision is not an agent, and that model influence is not decision authority, with evaluation comparing behavior, uncertainty, and — where reproducible — latency and cost. It would make no vendor or performance claims.

## Track D — Reference Application

The labs intentionally isolate concepts. Once enough concepts are mature, a composed reference application should show how justified capabilities fit together in a real system.

### Project Milestone — Reference Application v1

Not started.

- Select a realistic software/developer-oriented use case
- Identify which handbook concepts the use case actually needs
- Deliberately exclude unnecessary concepts, and document why
- Compose model, tools, retrieval, memory, skills, and runtime only where justified
- Add production concerns: evaluation, observability, security
- Provide runnable deployment guidance

The reference application must not contain every concept merely because the handbook teaches it. You probably don't need all of these — that principle applies to our own reference application first.

## Track E — Advanced Topics

Topics that come after the single-runtime path is concrete enough that adding them teaches something genuinely new.

### Project Milestone — Advanced Topics v1

Not started.

- Multi-agent systems
- A2A
- Fine-tuning

These are deliberately not part of the numbered canonical learning path. Most applications are one model, one runtime, and the capabilities that runtime actually needs.

## Track F — Learning Experience

How the handbook teaches, as deliberate design rather than incidental style.

### Project Milestone — Guided Learning Experience v1

In progress.

Established:

- The experience-first format validated in Labs 05, 06, 07, 08, 09, and 10: limitation → naive approach → failure → design → reflection → decision
- Reflection sections in each experience-first lab
- "Do I actually need this?" decision checkpoints reinforcing that not every capability is needed
- [Capstone 01 — Build a Small Agentic System](capstones/01-agentic-system/README.md): composing already-taught concepts with a decisions-first exercise and a reference that deliberately excludes capabilities
- [Capstone 01 interactive learning UI](site/README.md): a static, backend-free experience with local-only progress, architecture decisions, a decision-authority checkpoint, reflection, and an ungraded reference comparison
- The interactive site published through GitHub Pages at <https://cagridursun.github.io/agentic-developer-handbook/>, deployed from `site/`; answers still stay in the browser
- [LLM vs Decision Authority](docs/model-vs-decision-authority.md) as an explicit handbook concept, wired into the capstone decisions
- [Agentic System Readiness Assessment](assessments/agentic-system-readiness/README.md): a reusable architecture decision framework that starts with deterministic software, derives candidate capabilities without scoring, and makes decision authority explicit — with four worked examples and an interactive version on the local site with its own local progress

Planned:

- Selectively retrofit Labs 01–04 where it materially improves learning
- An optional workshop path
- Broader learning UI/site expansion beyond the capstone and the assessment remains to be evaluated

## How to read this list

[labs/README.md](labs/README.md) describes the lab format. The labs themselves will be added with the milestone that needs them. Empty lab directories are not created in advance.

## GitHub milestone tracking

The numbered milestones and the explicit project milestones above are synchronized to GitHub Issues → Milestones automatically by [scripts/sync-github-milestones.py](scripts/sync-github-milestones.py) whenever this file changes on `main`. Track headings are structure, not milestones; each GitHub milestone description names its track.

ROADMAP.md is canonical. GitHub Milestones are the operational tracking view. Changing a milestone on GitHub does not change the roadmap; the next sync restores the state written here.
