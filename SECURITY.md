# Security Policy

## Supported versions

Security fixes are applied to the current development/release line. Older prototype releases are not maintained unless explicitly stated otherwise.

## Reporting a vulnerability

Please do **not** open a public issue for security bugs.

Use GitHub's private **Report a vulnerability** / Security Advisory flow when it is available for the repository. If that option is unavailable, contact a maintainer through the project's established private communication channel.

Do not include credentials, private keys or other live secrets in public issues, pull requests or discussions.

## Secrets handling

- Never commit passwords, API keys, or tokens.
- Use `.env.example` to document required variables.
- Real secrets go into:
    - local `.env` files (gitignored)
    - (future) secret manager in SaaS deployments
- CI build/test/package jobs must not receive reusable release, cloud, signing or deployment credentials.

## Sensitive game data

- **TV app must not receive answers** (song title/artist/track answer).
- Admin app is allowed to access answers.
- Logs must not contain secrets or answers that could leak.

See `docs/developer-guide/security.md` for the application security model and `docs/developer-guide/ci-security.md` for CI/supply-chain security rules.
