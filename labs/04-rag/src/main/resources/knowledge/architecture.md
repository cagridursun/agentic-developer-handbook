# Helio Platform Architecture

> Helio is a fictional developer platform invented for this lab. Nothing in
> this document describes a real company or product.

Helio is a modular monolith. All modules run in one deployable unit called
`helio-core`, and modules communicate in-process through an internal event bus
named Beacon. There are no synchronous calls between modules; a module either
handles a request itself or publishes an event.

Two components run outside `helio-core`:

- The edge gateway terminates TLS, applies rate limits, and forwards requests.
- The worker pool consumes Beacon events that are marked as deferrable, such
  as email delivery and report generation.

State lives in one PostgreSQL cluster. Each module owns its schema and no
module reads another module's tables. Cross-module reads go through Beacon
events or the module's public Java API.

The platform targets Java 21 and is built with Maven. A single artifact is
produced per release and promoted unchanged through every environment.
