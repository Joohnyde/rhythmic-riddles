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

The current safety net now has two complementary integration boundaries in addition to the existing service/controller tests:

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

### 2. Seeded browser WebSocket integration

The Playwright suite under `apps/frontend/e2e` verifies the real browser/WebSocket boundary:

- Admin and TV socket connection;
- role-specific routing;
- room isolation;
- duplicate socket handling;
- disconnect/reconnect behavior;
- browser-observed frame ordering;
- recovery `welcome` snapshots;
- schema compliance for reachable WebSocket frames.

Together, the DB-backed suite proves that persisted state is reconstructed correctly and the browser suite proves that the resulting runtime protocol reaches real clients correctly.

## Running backend tests

On Linux, run the full backend suite with:

```bash
cd apps/backend
mvn -Dplatform=linux test
```

The `platform` Maven property selects the native binary used by embedded PostgreSQL. Use `-Dplatform=windows` on Windows or `-Dplatform=macos` on macOS. A bare `mvn test` does not select one of these platform-specific dependencies. See `db-integration-tests.md` for suite structure and troubleshooting.

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

#### 2. Controller/API tests

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

#### 3. Repository/query integration

The persistence integration suite runs against real PostgreSQL and protects semantics that mocked repositories cannot prove:

- ordering and `LIMIT` behavior;
- schedule/category scoping;
- interrupt-frame nesting and filtering;
- update-query targeting;
- PostgreSQL-specific projections such as `DISTINCT ON`;
- score and album-picker query edge cases.

Do not add integration tests for trivial inherited CRUD behavior. A repository integration test should protect a meaningful query contract.

#### 4. DB-backed recovery integration

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

#### 5. Seeded browser WebSocket integration tests

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

This is not full product E2E. REST calls and fixture endpoints may be used as triggers. The important assertions are browser-observed WebSocket effects.

### Planned layers

#### 1. Frontend unit and integration tests

Frontend unit tests should cover component and domain-store behavior that does not require a real backend WebSocket connection.

Good candidates include recovery-state mapping, song-round transitions, duplicate-action guards, route guards, form validation, and error rendering. These tests should stay smaller than Playwright tests and should not duplicate browser-level WebSocket coverage.

#### 2. Small number of full-stack integration tests

A few high-value ones:

- controller → service → DB happy path;
- exception advice wired;
- transactions wired;
- serialization wired.

This layer should be small. The point is not to duplicate every service test, but to prove that Spring wiring and infrastructure behave correctly together.

#### 3. E2E regression

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
- `test-catalog.csv` for detailed inventory.

This allows contributors to understand what is already covered and where gaps remain.

## Document ownership

This document describes the project’s testing approach and current structure. More practical guidance for contributors belongs in `writing-tests.md`.

Detailed seeded WebSocket E2E guidance belongs in `e2e.md`.
