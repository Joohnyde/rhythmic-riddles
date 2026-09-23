# Continuous Integration and Merge Qualification

This document describes the repository's GitHub Actions CI pipeline, what each qualification stage proves, and the contract that must pass before changes are merged to `master`.

The workflow is defined in:

```text
.github/workflows/ci.yml
```

## Goals

The CI pipeline is designed to provide one reproducible merge qualification path across the parts of the product that can fail independently:

- code quality and formatting;
- backend behavior on supported operating systems;
- frontend unit/component and Playwright E2E behavior;
- firmware host tests and Arduino compilation;
- native desktop packaging on Linux, Windows, and macOS;
- packaged runtime behavior, including embedded PostgreSQL, persistence, assets, and shutdown/cleanup.

The pipeline is intentionally split into focused jobs rather than one large build so failures identify the boundary that broke.

## Triggers and concurrency

CI runs for:

- pull requests targeting `master`;
- pushes to `master`.

Only the newest run for the same pull request/ref is kept active. Older in-progress runs are cancelled through the workflow concurrency group.

Workflow permissions are read-only (`contents: read`). Build/test credentials used by CI are disposable test values and must not be treated as production secrets.

## Qualification flow

The workflow has three stages.

### 1. Logical and source qualification

These jobs run independently first:

#### `Code Quality`

Runs the repository's canonical quality script:

```bash
scripts/ci/verify-code-quality.sh
```

This is the fast formatting/lint/static contract used before the heavier test and packaging jobs.

#### `Backend Linux`

Runs the complete backend Maven verification on Linux:

```bash
cd apps/backend
mvn -B -ntp -Dplatform=linux verify
```

The explicit platform property selects the correct native embedded-PostgreSQL dependency.

#### `Frontend + Playwright Linux`

Runs the frontend unit/catalog/contract checks together with the complete Playwright E2E suite against a real PostgreSQL container and a real Spring Boot backend.

The local `application-e2e.yml` remains ignored. CI creates a disposable profile file on the runner with test-only database settings and an isolated asset directory, starts PostgreSQL from `docker-compose.yml.example`, waits for readiness, runs the backend/frontend processes, and executes:

```bash
npm run test:all
```

The job also performs a production Angular build after the test suite.

#### `Firmware`

Uses the pinned PlatformIO version to:

```bash
pio test -d hardware/firmware/receiver -e native
pio run -d hardware/firmware/receiver -e nanoatmega328
```

The first command protects deterministic receiver logic; the second proves the real Arduino target still compiles.

#### `Platform Tests`

Runs backend tests natively on the additional supported operating systems:

- Windows (`windows-2025`, `-Dplatform=windows`)
- Intel macOS (`macos-15-intel`, `-Dplatform=macos`)

The matrix uses `fail-fast: false` so one platform failure does not hide the result of the other.

### 2. Native package qualification

Linux, Windows, and macOS package jobs start only after all logical/source qualification jobs succeed.

Each package job:

1. checks out a clean repository;
2. prepares the baseline binary asset payload;
3. creates the minimal CI production-management configuration used by the package smoke lifecycle;
4. installs the platform-specific build prerequisites;
5. builds an embedded-DB production package;
6. requires the expected native installer to exist;
7. runs the packaged product smoke test on that same operating system.

The current merge gate qualifies the embedded-PostgreSQL package (`embeddb=true`) on all three supported platforms. External-DB packaging remains supported by the build scripts but is not currently a required CI package job.

See `release-builds.md` for the native packaging contract and smoke-test behavior.

## Baseline asset payload

Large runtime media is intentionally not committed to Git. Package qualification retrieves the shared payload from the repository's GitHub Release:

```text
tag:   baseline-data
asset: baseline-data.zip
```

The archive is extracted at repository root and must contain:

```text
data/
  audio/
    snippets/
    answers/
  images/
    albums/
```

Frontend-owned static team icons remain part of the frontend source tree and are not supplied by this archive.

The baseline release is treated as a build input, not mutable working storage. Do not silently replace an existing asset with different contents. If the baseline dataset must change, create a new release/tag and update the workflow deliberately in the same change.

The IDs required by package smoke must remain consistent with the rows inserted by `db_03_fill_tables_with_initial_data.sql` and with the files in the baseline archive.

## CI production configuration

The real local `application-production.yml` remains ignored because it may contain machine/customer-specific settings. Package jobs therefore create only the non-secret management configuration required by the package smoke lifecycle:

```yaml
management:
  server:
    port: 8081
    address: 127.0.0.1
  endpoints:
    web:
      exposure:
        include: shutdown,health,info
  endpoint:
    shutdown:
      enabled: true
```

This allows the smoke runner to wait on health and request a clean application shutdown before verifying process cleanup and restart persistence.

This generated CI file is not a replacement for the complete production configuration used by a real installation. Product/customer-specific production settings remain a separate packaging/deployment concern.

## Package smoke contract

`scripts/prod/package-smoke.mjs` runs against the native app-image produced on the same operating system. For embedded packages it verifies, at minimum:

- application and Actuator readiness;
- embedded PostgreSQL startup;
- first-run SQL bootstrap and `.cestereg_sql_done` marker creation;
- a real application write;
- Angular TV/Admin boot in Chromium;
- representative packaged audio/image access;
- Actuator shutdown;
- Java/PostgreSQL process cleanup;
- restart against the same application-data directory;
- persistence of the previously created application state.

The smoke harness uses temporary application/log directories and an isolated embedded-PostgreSQL port so it does not modify normal user data.

## Merge Gate

The final job is:

```text
Merge Gate
```

It depends on every required qualification job and succeeds only when all of them report `success`:

- Code Quality
- Backend Linux
- Frontend + Playwright Linux
- Firmware
- Platform Tests
- Linux Package Qualification
- Windows Package Qualification
- macOS Package Qualification

This provides one stable branch-protection contract even if the internal CI graph changes later.

The `master` repository ruleset should require `Merge Gate` before merge rather than independently requiring every implementation job. If a new mandatory qualification job is added, it must also be added to `merge-gate.needs` and to the final result check.

## Ownership and change rules

`.github/CODEOWNERS` assigns CI/CD workflows, execution scripts, infrastructure files, dependencies, and database scripts to the repository owner responsible for those areas. CODEOWNERS declares review ownership; enforcement still depends on the repository's branch/ruleset configuration.

When changing CI:

- keep runner/tool versions explicit where platform behavior matters;
- prefer repository scripts over duplicating build logic in YAML;
- keep secrets out of generated test configuration;
- do not weaken package smoke assertions to make a package pass;
- keep native package execution on the OS that produced the package;
- update `release-builds.md`, testing documentation, or asset/database documentation when their runtime contract changes.
