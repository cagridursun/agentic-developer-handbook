# Capstones

The canonical labs isolate concepts: one lab, one idea, one runnable example. Capstones do the opposite job — they combine concepts the labs have already taught.

A capstone never introduces a new canonical concept. It asks the learner to:

1. Understand a realistic problem
2. Choose capabilities — starting from the problem, not from an AI feature list
3. Justify exclusions as deliberately as inclusions
4. Implement the design
5. Test the application-controlled behavior deterministically
6. Compare the result with one reference design

Each capstone ships a `starter/` (fictional assets, fixtures, and a `DECISIONS.md` to fill out *before* coding) and a `reference/` (one runnable solution with its own `DECISIONS.md`).

The reference implementation is not "the correct architecture." It is one documented set of trade-offs. A different, well-justified capability selection can be an equally good — or better — answer.

| Capstone | Combines |
| --- | --- |
| [01-agentic-system](01-agentic-system/README.md) | Model, Tools, Knowledge, Skills, Agent Runtime — and deliberate exclusions |

Capstones belong to Track F — Learning Experience in [ROADMAP.md](../ROADMAP.md). They are not part of the numbered canonical path, and this is not the future Track D reference application: a capstone is a deliberately small teaching exercise, while the reference application will later add production concerns like evaluation, observability, security, and deployment.
