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

The current focus is now:

### 1. Seeded browser WebSocket integration

The Playwright suite under `apps/frontend/e2e` verifies the real browser/WebSocket boundary:

- Admin and TV socket connection;
- role-specific routing;
- room isolation;
- duplicate socket handling;
- disconnect/reconnect behavior;
- browser-observed frame ordering;
- recovery `welcome` snapshots;
- schema compliance for reachable WebSocket frames.

This layer is already a major part of the current safety net. It proves that real browser clients and the backend WebSocket runtime agree on routing, frame shape, lifecycle, and recovery behavior.

### 2. DB-backed recovery integration

After that, prove reconnect recovery from persisted state:

- normal playback;
- active team interrupt;
- technical pause;
- ended-not-revealed;
- revealed.

That is where recovery becomes truly trustworthy.

The WebSocket tests prove that browsers receive recovery frames. DB-backed recovery integration should prove that persisted state is queried and reconstructed correctly before a WebSocket frame is ever sent.

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

#### 3. Seeded browser WebSocket integration tests

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

Frontend unit tests should cover component and service behavior that does not require a real backend WebSocket connection.

Good candidates:

- pure component state;
- route guards;
- frontend services;
- selector stability;
- form validation;
- error rendering.

These tests should stay smaller than Playwright tests and should not duplicate browser-level WebSocket coverage.

#### 2. DB-backed recovery integration

After that, prove reconnect recovery from persisted state:

- normal playback;
- active team interrupt;
- technical pause;
- ended-not-revealed;
- revealed.

That is where recovery becomes truly trustworthy.

This layer should focus on persisted data and recovery queries. It should answer: “Given this database state, does the backend reconstruct the correct game context?”

#### 3. Repository/query integration

Especially for the recovery-critical queries:

- ordering;
- stage-aware lookups;
- interrupt retrieval;
- schedule/category edge cases;
- score/result queries.

This layer should use a real database or a database-compatible integration setup. It should protect query semantics that mocks cannot prove.

#### 4. Small number of full-stack integration tests

A few high-value ones:

- controller → service → DB happy path;
- exception advice wired;
- transactions wired;
- serialization wired.

This layer should be small. The point is not to duplicate every service test, but to prove that Spring wiring and infrastructure behave correctly together.

#### 5. E2E regression

Then cover the whole flow:

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

This layer should test the user-visible product journey. It should not duplicate every low-level WebSocket assertion already covered by the seeded WebSocket integration suite.

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
- repository integration tests with Testcontainers;
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
