# Test Catalog

## Purpose

This document gives a high-level overview of the current automated test suite.

It is not intended to list every assertion in prose. Instead, it explains where major coverage exists and where contributors should look first.

A more detailed inventory of test cases should be maintained in `test-catalog.csv`.

## Current backend test groups

### Core service tests

The most extensive backend coverage is concentrated in service-level tests for:

- `GameServiceImpl`
- `InterruptServiceImpl`
- `ScheduleServiceImpl`
- `CategoryServiceImpl`
- `TeamServiceImpl`
- `SongServiceImpl`

These tests are expected to cover the main gameplay rules and state transitions.

### Lower-level/supporting tests

Additional tests may exist for:

- websocket handshake helpers
- cached DTO behavior
- application bootstrap sanity

## Most critical coverage areas

The following areas are considered especially important:

### 1. Context generation

`GameServiceImpl.contextFetch(...)` should be treated as a critical regression surface.

It should cover:

- all game stages
- valid and invalid state combinations
- missing related data
- recovery from inconsistent state
- payload coherence for clients

### 2. Interrupt flow

`InterruptServiceImpl` should protect:

- valid/invalid interrupt attempts
- duplicate attempts
- answer handling
- score and error persistence
- cleanup and recovery behavior

### 3. Schedule progression

`ScheduleServiceImpl` should protect:

- song progression
- missing current or next song
- reveal/replay edge cases
- end-of-game behavior
- ordering-sensitive side effects

## Using the catalog

Before writing new tests:

1. inspect the relevant service test file
2. check `test-catalog.csv`
3. avoid duplicating an existing test unless the new test covers a genuinely distinct rule or state combination

## Catalog maintenance

When adding, merging, or deleting tests:

- update `test-catalog.csv`
- keep descriptions short and behavior-focused
- note the test suite/group where the test belongs

## Controller tests

Controller tests exist for the REST API layer and are organized one file per controller.

These tests typically verify:

- HTTP status codes
- response body content
- response media types
- malformed payload handling
- request rejection before service invocation
- controller-managed exception responses

Controller endpoints are expected to have tests covering:

- happy path behavior
- `DerivedException` behavior
- unexpected exception behavior

For JSON endpoints, tests should verify `application/json` media type for both success and controller-handled error responses.

## Future expansion

This catalog should later expand to include:

- repository integration tests
- frontend unit and integration tests
- end-to-end regression tests
