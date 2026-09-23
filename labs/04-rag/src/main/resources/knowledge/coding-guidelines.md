# Helio Coding Guidelines

> Helio is a fictional developer platform invented for this lab. Nothing in
> this document describes a real company or product.

Java 21 is the only language in `helio-core`. Records are preferred for
immutable data, constructor injection is required, and reflection is banned
outside framework boundaries.

Every module exposes exactly one public package; everything else is
package-private. A pull request that adds a class to a public package needs a
maintainer review from the owning team.

Unit tests must be deterministic: no wall-clock reads, no network, no sleeps.
Integration suites run nightly, not per commit. Test names describe behavior,
not method signatures.

Beacon events are versioned with a numeric suffix, such as
`invoice.settled.v2`. Publishing a new version requires keeping the previous
version emitted for at least two release cycles.
