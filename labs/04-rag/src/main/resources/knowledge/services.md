# Helio Modules and Ownership

> Helio is a fictional developer platform invented for this lab. Nothing in
> this document describes a real company or product.

`helio-core` currently contains five modules:

- **gateway-config** — owned by the Edge team. Holds routing rules and rate
  limit policies consumed by the edge gateway.
- **identity** — owned by the Identity team. Sign-in, tokens, roles, and the
  platform CA for workload certificates.
- **notifications** — owned by the Comms team. Email and webhook delivery,
  executed by the worker pool through Beacon events.
- **billing** — owned by the Revenue team. Subscriptions, invoices, and the
  `invoice.settled` event family.
- **search** — owned by the Discovery team. Full-text indexes over projects
  and documents, rebuilt nightly.

Escalations page the owning team directly. There is no shared on-call rotation;
the Edge team is the fallback only for incidents at the gateway itself.
