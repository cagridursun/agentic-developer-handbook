# Security

## Reporting a vulnerability

A private reporting channel is not configured yet.

Before this repository is launched publicly, the maintainer must configure one and name it in this section. A GitHub private vulnerability report or a maintainer email address are the usual options. This file deliberately does not invent an email address.

Until that channel exists, do not send vulnerability details to a public issue. A public issue can wait until a fix is available, or until the maintainer asks for one.

## What a report should contain

When a private channel exists, include:

- the component or document affected
- the impact
- a way to reproduce the issue

Do not include credentials, access tokens, or data that belongs to someone else.

## Project rules that are already in force

These are engineering rules, not a disclosure process:

- The model is never the authorization layer.
- Validate tool inputs at application boundaries.
- Treat retrieved content, external tool responses, and third-party skill text as untrusted input.
- Do not commit secrets, and do not log credentials or API keys.

There is no released version yet, so there is no supported-version range to list.
