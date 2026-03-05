
# Contributing to RhytmicRiddles

This project is intentionally lightweight on ceremony but strict on **quality** and **traceability**.

## Issue-first workflow

Every change must have a GitHub Issue:
- **Feature**
- **Bug**
- **DevOps issue**
- **Chore / refactor**
- **Docs**
- **Security**
- **Performance**

Each issue should include:
- **Context / motivation**
- **Acceptance criteria**
- **API contract changes** (request/response + errors)
- **DB changes** (tables/columns/migrations)
- **Testing plan**
- **Open questions** + decisions made

### Definition of Done (DoD)
A ticket is “Done” only when:
- code is merged to `main`
- CI is green
- tests added/updated
- docs updated (user/dev docs when relevant)
- error catalog updated when new errors added

##  Branch naming

Create a branch from `main` per ticket:

`<name>/<ticketNr>_<short_description>`

Example:
- `filip/6728_palette_dark_mode`

##  Commit message convention

Each commit message begins with:

`[<ticketNr> <Name>] <message>`

Example:
- `[6728 Filip] Add dark-mode toggle`

##  PR title and description

### PR Title
Use:
- `[feature_6728] Change palette to dark mode`
- `[bugfix_1022] Fix interrupt seek calculation`
- `[chore_9001] Update dependencies`
- `[docs_7711] Add host guide`
- `[perf_3100] Optimize album selection query`
- `[security_5002] Sanitize error messages`

### PR Description (required)
Include:
- What changed (short)
- Why it changed (short)
- DB migrations (if any)
- API changes (if any)
- Tests added (list)
- Screenshots/gifs (UI changes)
- Known limitations / follow-ups

## Review policy

- All PRs require review by the other two people (unless explicitly agreed otherwise).
- Use **Draft PRs** for work-in-progress.
- No direct pushes to `main`.

## Formatting & linting

CI enforces formatting:
- Java: Spotless (or configured formatter)
- Angular: Prettier + ESLint

Run locally:
- `./scripts/dev/format-all.sh`
- `./scripts/dev/test-all.sh`

See `docs/developer-guide/ide-setup.md`.

##  Secrets

Never commit secrets. Use:
- `.example` files
- local `.env` ignored by git
- GitHub Actions secrets for CI/CD

See `SECURITY.md`.

## Code Quality & CI Gate

This repository enforces automated code quality checks through a CI
gate. All pull requests must pass these checks before they can be
merged.

### What the CI gate verifies

The CI pipeline runs automated verification for both backend and
frontend code:

Backend: - Java formatting via **Spotless** - Java style and conventions
via **Checkstyle**

Frontend: - Code formatting via **Prettier** - Static analysis via
**ESLint**

If any of these checks fail, the CI pipeline will fail and the pull
request cannot be merged.

### Running checks locally

Developers are expected to verify code quality locally before opening a
pull request.

To automatically fix formatting issues:

    ./scripts/dev/format-all.sh

To verify that the repository passes all CI checks locally:

    ./scripts/ci/verify-code-quality.sh

The verification script runs the same checks that the CI pipeline runs.
This helps ensure that pull requests pass the CI gate on the first
attempt.

### Script structure

The repository separates developer utilities from CI automation scripts.

    scripts/
      dev/
        format-all.sh            # fixes formatting issues locally
      ci/
        verify-code-quality.sh   # verifies formatting and lint rules (used by CI)

Developer scripts may modify files, while CI scripts only **verify**
code quality and never modify repository contents.

### CI workflow

The CI pipeline:

1.  Checks out the repository
2.  Installs required tooling (Java, Node)
3.  Runs the repository verification script

The pipeline executes:

    scripts/ci/verify-code-quality.sh

This ensures that local developer checks and CI behavior remain
consistent.

### Dependency and security automation

This repository also uses automated security and dependency management
tools:

-   **Dependabot** -- keeps dependencies up to date and opens pull
    requests for updates
-   **CodeQL** -- performs automated static security analysis
-   **Secret scanning & push protection** -- prevents credentials from
    entering the repository

These tools run automatically and require no manual interaction beyond
reviewing Dependabot pull requests.
