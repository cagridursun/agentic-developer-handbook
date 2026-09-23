# Authentication on Helio

> Helio is a fictional developer platform invented for this lab. Nothing in
> this document describes a real company or product.

Users sign in against the identity module, which issues a signed access token
with a lifetime of 45 minutes. Clients receive a refresh token valid for 30
days; refreshing rotates the refresh token, and reuse of a rotated token
revokes the whole session family.

Access tokens are JWTs signed with keys that rotate every 24 hours. The edge
gateway validates the signature and expiry on every request before anything
reaches `helio-core`. Modules never validate tokens themselves; they trust the
subject header injected by the gateway.

Service-to-service calls do not use user tokens. Internal callers authenticate
with mutual TLS, and each workload has its own certificate identity issued by
the platform CA. Certificates expire after 7 days and renew automatically.

Password login requires a second factor for accounts with the `operator` role.
API keys exist only for the public read-only API and cannot be used to call
any endpoint that changes state.
