# Security

## Reporting a vulnerability

Report vulnerabilities privately through GitHub: open the repository's **Security** tab and choose **Report a vulnerability**, or go directly to <https://github.com/cagridursun/agentic-developer-handbook/security/advisories/new>. Only the maintainer can see the report.

Do not put vulnerability details in a public issue, pull request, or discussion. A public issue can wait until a fix is available, or until the maintainer asks for one.

## What a report should contain

A report should include:

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
