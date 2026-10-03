# Architecture Decision Records

An ADR records a decision that later work should follow. It has four parts: context, decision, consequences, and a status.

| ADR | Title | Status |
| --- | --- | --- |
| [0001](0001-java-first.md) | Java-first | Accepted; Java version superseded by 0005 |
| [0002](0002-not-an-agent-framework.md) | Not an agent framework | Accepted |
| [0003](0003-progressive-learning-model.md) | Progressive learning model | Accepted |
| [0004](0004-skills-are-a-building-block.md) | Skills are a building block | Accepted |
| [0005](0005-upgrade-canonical-java-to-27.md) | Upgrade the canonical Java version to 27 | Accepted |
| [0006](0006-application-owned-authorization-for-state-changing-tools.md) | State-changing tool invocations pass through application-owned authorization | Accepted |

Add a new ADR when a choice changes the shape of the project. Do not add one for a local implementation detail.

The principles these decisions sit on are summarized in [../architecture.md](../architecture.md).
