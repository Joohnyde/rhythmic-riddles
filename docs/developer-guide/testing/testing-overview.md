# Testing Overview

## Purpose

This project uses automated tests to protect core gameplay logic, reduce regressions, and make refactoring safer.

Testing is especially important because the application contains:

- a stage-driven game flow
- multiple client roles (Admin / TV / team-facing behavior)
- interrupt and recovery logic
- context reconstruction via backend state
- websocket broadcasts with ordering-sensitive side effects

The goal of the test suite is not just to prove that code works on the happy path, but to detect invalid state, illegal transitions, and subtle regressions before they reach production.

## Current Test Focus

The backend test suite also includes targeted verification of
WebSocket-related behavior where it intersects with core game logic.

Rather than testing WebSocket infrastructure directly, tests focus on
validating that services correctly trigger broadcast side effects
through the messaging gateway when state changes occur.

Particular emphasis has been placed on:

-   verifying that broadcasts occur when stage transitions happen
-   ensuring interrupt and answer resolution triggers appropriate state
    updates
-   confirming that broadcast payloads contain correct context
    information
-   validating that broadcasts are suppressed when operations are
    rejected or invalid
-   ensuring correct ordering of persistence and broadcast side effects
    when required

These tests ensure that the real-time synchronization layer between
Admin and TV clients remains consistent with backend state transitions
without requiring a full WebSocket environment during unit testing.

## Why `contextFetch` matters

`GameServiceImpl.contextFetch(...)` is one of the most important parts of the backend because it is responsible for reconstructing the current state of the game for clients.

Tests for this method are expected to validate:

- every game stage
- absent/present team combinations
- absent/present category
- absent/present current song / schedule
- interrupt state combinations
- corrupted or inconsistent persisted state
- recovery behavior after invalid or partial state

If this method becomes incorrect, the application can appear broken to clients even if lower-level data is still present.

## Test Layers

### Current layers

The project currently has strong backend service-layer unit tests and controller tests that validate the HTTP contract of REST endpoints.

Controller tests verify:
- happy path responses
- `DerivedException` responses
- unexpected error responses
- response status codes
- response body content when applicable
- response media types
- rejection of malformed requests before reaching service logic

Controller-managed error responses are centralized through
`com.cevapinxile.cestereg.api.support.ApiErrorResponses.handleApiException`, and this behavior is considered part of the API contract.

### Planned layers

The following layers should be expanded after the service layer is considered stable:

- repository integration tests
- frontend unit tests
- frontend integration tests
- end-to-end regression tests

## What should be tested when code changes

When a feature is added or behavior is changed, tests should be added or updated in the same pull request.

At minimum:

- new rules require tests
- bug fixes require regression tests
- changes to stage handling require `GameServiceImpl` / `contextFetch` coverage
- changes to interrupt logic require `InterruptServiceImpl` coverage
- changes to websocket-visible state require payload and side-effect assertions

## CI expectations

The long-term expectation is that pull requests should pass automated quality gates before merge.

Desired minimum CI gates:

- backend build
- backend automated tests
- frontend build
- frontend automated tests
- formatting / lint checks

Future additions may include:

- coverage reporting
- repository integration tests with Testcontainers
- Playwright end-to-end smoke tests

## Test catalog

A catalog of the current backend test suite should be maintained separately in:

- `test-catalog.md` for human-readable overview
- `test-catalog.csv` for detailed inventory

This allows contributors to understand what is already covered and where gaps remain.

## Document ownership

This document describes the project’s testing approach and current structure. More practical guidance for contributors belongs in `writing-tests.md`.
