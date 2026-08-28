# Testing Overview

## Purpose

This project uses automated tests to protect core gameplay logic, reduce regressions, and make refactoring safer.

Testing is especially important because the application contains:

- a stage-driven game flow;
- multiple client roles (Admin / TV / team-facing behavior);
- interrupt and recovery logic;
- context reconstruction via backend state;
- WebSocket broadcasts with ordering-sensitive side effects.

The goal of the test suite is not just to prove that code works on the happy path, but to detect invalid state, illegal transitions, and subtle regressions before they reach production.

## Current Test Focus

The current safety net now has three complementary integration boundaries in addition to the existing focused service/controller/adapter tests:

### 1. Real-PostgreSQL repository and recovery integration

The backend DB integration suite verifies query behavior that mocks cannot prove and reconstructs recovery directly from persisted rows. It covers:

- outermost interrupt-frame ordering and nested-pause suppression;
- independent latest team/system interrupt retrieval;
- schedule and category ordering/scoping;
- PostgreSQL-native score projections and picker queries;
- normal playback recovery;
- active team and technical-pause recovery;
- ended-but-unrevealed and revealed recovery;
- persisted invalid-state rejection;
- supported-path protection of the disjoint-or-nested interrupt invariant.

These tests use the project’s embedded real PostgreSQL runtime rather than an H2 compatibility substitute, and initialize it from the root production `db/db_01_create_schema.sql` and `db/db_04_add_runtime_invariants.sql`. Fixture rows are inserted directly so repository tests exercise the query itself rather than `JpaRepository.save()`. See `db-integration-tests.md` for practical guidance.

### 2. Spring full-stack application integration

A deliberately small random-port Spring Boot layer verifies that real HTTP, Spring wiring, application transactions, repositories, PostgreSQL, and JSON contracts compose correctly. It uses representative success and failure outcomes rather than repeating endpoint behavior already owned by narrower suites. Exact scenarios are listed in `test-catalog.md`.

### 3. Seeded browser WebSocket integration

The Playwright suite under `apps/frontend/e2e` verifies the real browser/WebSocket boundary:

- Admin and TV socket connection;
- role-specific routing;
- room isolation;
- duplicate socket handling;
- disconnect/reconnect behavior;
- browser-observed frame ordering;
- recovery `welcome` snapshots, including Stage 1 snapshots that carry `albums` together with `team` or `selected`;
- schema compliance for reachable WebSocket frames.

Together, the DB-backed suite protects persistence semantics, the Spring full-stack layer proves HTTP/application composition, and the browser suite proves the runtime protocol reaches real clients correctly.

## Running backend tests

On Linux, run the full backend suite with:

```bash
cd apps/backend
mvn -Dplatform=linux test
```

The `platform` Maven property selects the native binary used by embedded PostgreSQL. Use `-Dplatform=windows` on Windows or `-Dplatform=macos` on macOS. A bare `mvn test` does not select one of these platform-specific dependencies. See `db-integration-tests.md` for suite structure and troubleshooting.

## Running frontend tests

Run the complete frontend test suite with:

```bash
cd apps/frontend
npm run test:all
```

This runs the frontend checks in sequence:

1. Angular unit and component tests through **Vitest**;
2. frontend `test-catalog.csv` consistency checks for both **Vitest** and **Playwright** tests;
3. the complete **Playwright** E2E suite.

Individual layers can also be run separately:

```bash
npm test -- --watch=false
npm run test:catalog:frontend
npm run e2e
```

The Playwright suite uses a bounded worker count because the E2E tests share the same backend, PostgreSQL instance, and frontend server. Set `E2E_WORKERS` to override the default when needed:

```bash
E2E_WORKERS=2 npm run e2e
```

Any frontend test that is added, renamed, or removed must be reflected in `docs/developer-guide/testing/test-catalog.csv` in the same change. The frontend catalog consistency check enforces this independently for Vitest and Playwright.

## Why `contextFetch` matters

`GameServiceImpl.contextFetch(...)` is one of the most important parts of the backend because it is responsible for reconstructing the current state of the game for clients.

Tests for this method are expected to validate:

- every game stage;
- absent/present team combinations;
- absent/present category;
- absent/present current song / schedule;
- interrupt state combinations;
- corrupted or inconsistent persisted state;
- recovery behavior after invalid or partial state.

If this method becomes incorrect, the application can appear broken to clients even if lower-level data is still present.

## Test Layers

### Current layers

#### 1. Backend service-layer tests

The project has strong backend service-layer tests for the core gameplay services.

This layer protects:

- business rules;
- invalid transitions;
- interrupt rules;
- schedule progression;
- score/result behavior;
- domain-level side effects.

Service tests should stay fast and focused. They are the right place to prove that a method rejects invalid state, updates the correct entities, and calls collaborators correctly.

#### 2. Backend infrastructure adapter tests

Focused adapter tests protect behavior at non-database infrastructure boundaries without starting the full application. For example, `LocalAssetGatewayTest` uses a temporary filesystem to prove image directory resolution, supported extension/MIME mapping, deterministic extension precedence, and canonical missing-asset errors. These tests should exercise the adapter contract directly and should not duplicate controller or service assertions.

#### 3. Controller/API tests

Controller tests validate the HTTP contract of REST endpoints.

This layer protects:

- happy path responses;
- `DerivedException` responses;
- unexpected error responses;
- response status codes;
- response body content when applicable;
- response media types;
- rejection of malformed requests before reaching service logic.

Controller-managed error responses are centralized through:

```text
com.cevapinxile.cestereg.api.support.ApiErrorResponses.handleApiException
```

That behavior is considered part of the API contract.

#### 4. Repository/query integration

The persistence integration suite runs against real PostgreSQL and protects semantics that mocked repositories cannot prove:

- ordering and `LIMIT` behavior;
- schedule/category scoping;
- interrupt-frame nesting and filtering;
- update-query targeting;
- PostgreSQL-specific projections such as `DISTINCT ON`;
- score and album-picker query edge cases.

Do not add integration tests for trivial inherited CRUD behavior. A repository integration test should protect a meaningful query contract.

#### 5. DB-backed recovery integration

`GameRecoveryIntegrationTest` builds reachable persisted states directly in PostgreSQL and calls `GameServiceImpl.contextFetch(...)` through real repositories and services.

This layer proves recovery for:

- normal playback;
- active team interrupt;
- technical pause;
- ended-not-revealed;
- revealed;
- nested/disjoint pause timing;
- representative corrupt persisted state.

This is deliberately separate from the E2E fixture API so fixture infrastructure is not part of the assertion chain.

#### 6. Spring full-stack application integration

`RhytmicRiddlesApplicationTests` is the narrow real-HTTP composition layer. Add tests here only when crossing the running Spring application exposes a risk not already demonstrated by controller, service, repository, transaction, concurrency, recovery, or WebSocket suites. The application owns the request transaction, and committed state is verified directly in PostgreSQL afterward.

#### 7. Seeded browser WebSocket integration tests

The frontend Playwright suite under `apps/frontend/e2e` validates real browser WebSocket behavior using the backend `e2e` profile and deterministic fixtures.

This layer protects:

- connection lifecycle and session-registry behavior;
- Admin / TV audience routing and room isolation;
- duplicate Admin/TV socket behavior;
- replacement socket recovery;
- broadcast delivery and suppression rules;
- Stage-2 song-state frames;
- recovery `welcome` snapshots;
- JSON/schema contract stability for browser-observed frames.

Most seeded scenarios are WebSocket integration tests and may use REST/fixture triggers. Stage 1 also
contains one intentionally higher-value real Admin UI journey (card → confirmation → focus → Play)
because that user-action chain is a regression boundary in its own right. Browser-observed WebSocket
frames remain strictly schema-validated.

#### 8. Frontend unit and component tests

Angular unit and component tests run through Vitest and cover frontend behavior that does not require a real backend WebSocket connection. Current coverage includes Signal Store Stage 0 behavior, Stage 1 recovery hydration for both picker and selected-album snapshots, login handshakes, team-icon allocation, Admin lobby components, buzzer animation triggers, and TV lobby pagination/layout helpers.
Stage 1 now also has direct Store, page, focus-component, marquee-component, dialog, and image-readiness
coverage. Those tests protect canonical album ordering, reconnect-safe async guards, request
supersession, page/component teardown, cancellable RAF/image preparation, reduced-motion final state,
recovered focus animation, deferred-resize geometry recomputation, real responsive TV focus-origin
capture across looping/static layouts, and bounded image-loading behavior without depending on browser
E2E timing.

These tests should stay faster and narrower than Playwright tests and should not duplicate browser-level WebSocket coverage.

### Planned layers

#### 1. E2E regression

Then cover the user-visible product flow:

- room creation;
- both apps connected;
- teams;
- transitions;
- category;
- song;
- interrupt;
- answer;
- scoring;
- finish/results.

This layer should not duplicate every low-level WebSocket assertion already covered by the seeded WebSocket integration suite.

## What should be tested when code changes

When a feature is added or behavior is changed, tests should be added or updated in the same pull request.

At minimum:

- new rules require tests;
- bug fixes require regression tests;
- changes to stage handling require `GameServiceImpl` / `contextFetch` coverage;
- changes to interrupt logic require `InterruptServiceImpl` coverage;
- changes to WebSocket-visible state require payload and side-effect assertions;
- changes to persisted recovery state require DB-backed recovery tests;
- changes to query behavior require repository/query integration tests.

## CI expectations

The long-term expectation is that pull requests should pass automated quality gates before merge.

Desired minimum CI gates:

- backend build;
- backend automated tests;
- frontend build;
- frontend automated tests;
- formatting / lint checks.

Future additions may include:

- coverage reporting;
- the real-PostgreSQL DB integration suite as a merge/release gate;
- Playwright end-to-end smoke tests;
- seeded WebSocket E2E in CI;
- full product E2E regression for release branches.

## Test catalog

A catalog of the current test suite should be maintained separately in:

- `test-catalog.md` for human-readable overview;
- `test-catalog.csv` for the detailed JUnit, Vitest, and Playwright inventory.

Every test addition, rename, or deletion must update the CSV in the same change. Backend and frontend consistency checks enforce that source and catalog remain synchronized. This allows contributors to understand what is already covered and where gaps remain.

## Document ownership

This document describes the project’s testing approach and current structure. More practical guidance for contributors belongs in `writing-tests.md`.

Detailed seeded WebSocket E2E guidance belongs in `e2e.md`.
