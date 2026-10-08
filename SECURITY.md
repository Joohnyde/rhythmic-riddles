# Security Policy

## Supported versions

| Version / line | Security support |
|---|---|
| Foundation `0.2.0` | In development; security fixes are applied during development and after release while this is the current supported release line |
| `master` (source version `0.3.0`) | Active development branch; not a separate supported release |
| `0.1.x` and older prototypes | Not maintained |

Release identity is defined in [release configuration](scripts/release/release-config.json); manifests distinguish release and source versions. No long-term support commitment is implied.

## Reporting a vulnerability

Please do **not** open a public issue for security bugs.

Prefer GitHub's private [Report a vulnerability](https://github.com/Joohnyde/rhythmic-riddles/security/advisories/new) flow. Maintainers must keep private reporting enabled in repository settings. Alternatively, email **security@cevapinxile.com**.

For general support requests, email **support@cevapinxile.com**. Send vulnerability reports to the private GitHub reporting flow or the security address.

Include the affected release/tag, reproduction steps, impact, and relevant redacted logs. Coordinate disclosure privately with the maintainers.

Do not include credentials, private keys or other live secrets in public issues, pull requests or discussions.

## CI/CD trust boundary

PR code runs on disposable GitHub-hosted runners without reusable publishing, signing, or deployment credentials. Tag-triggered releases requalify source and packages; a separate publication job validates release assets and receives narrowly scoped write/attestation permissions. See [CI security](docs/developer-guide/ci-security.md) and the [release runbook](docs/developer-guide/release-process.md).

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

See [Security model](docs/developer-guide/security.md) for the application security model and [CI security](docs/developer-guide/ci-security.md) for CI/supply-chain security rules.
