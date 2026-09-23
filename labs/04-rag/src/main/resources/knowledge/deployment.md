# Deploying Helio

> Helio is a fictional developer platform invented for this lab. Nothing in
> this document describes a real company or product.

Every merge to main produces one release candidate artifact. The same artifact
is promoted through three stages: dev, staging, and production. Nothing is
rebuilt between stages.

Promotion to staging is automatic when the dev smoke suite passes. Promotion
to production requires approvals from two release captains, and at least one
of them must be from the team that owns the largest change in the release.

Production rollout is canary-based: 5 percent of traffic for 30 minutes, then
100 percent. If the health checks fail during the canary window, the rollback
is automatic and completes within 10 minutes. A rollback pins the previous
artifact and freezes further promotions until a release captain unfreezes the
pipeline.

Database migrations ship separately from code and must be backward compatible
for one release, following the expand-and-contract pattern.
