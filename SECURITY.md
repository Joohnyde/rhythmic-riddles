# Security Policy

## Reporting a vulnerability
Please do **not** open a public issue for security bugs.

Preferred reporting paths:
1. GitHub Security Advisories (recommended once enabled in repo settings)
2. Private email to the maintainers: **TODO: add a security contact email**

## Secrets handling
- Never commit passwords, API keys, or tokens.
- Use `.env.example` to document required variables.
- Real secrets go into:
  - local `.env` files (gitignored)
  - GitHub Actions Secrets (CI)
  - (future) secret manager in SaaS deployments

## Sensitive game data
- **TV app must not receive answers** (song title/artist/track answer).
- Admin app is allowed to access answers.
- Logs must not contain secrets or answers that could leak.

See `docs/developer-guide/security.md` for detailed rules.

