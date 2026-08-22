
# Writing Tests

## Purpose

This document explains how developers should add and maintain tests in this repository.

The goal is to keep the test suite readable, useful, and focused on regression prevention.

## General rules

When writing tests:

1. test behavior, not implementation details when possible
2. prefer clear business-oriented test names
3. add a regression test for every bug fix
4. keep tests deterministic
5. avoid unnecessary duplication in setup data and helpers

## What makes a good test

A good test should:

- describe one meaningful behavior or rule
- clearly state the expected outcome
- fail for the right reason
- be easy to read without understanding the entire implementation

A test is especially valuable when it protects:

- stage transitions
- interrupt handling
- side-effect ordering
- websocket broadcasts
- recovery logic
- invalid or inconsistent state handling

## Minimum expectation for new logic

For every meaningful rule or public service behavior, try to cover:

- one success path
- one failure path
- one edge case

If the code writes state or broadcasts messages, add side-effect assertions as well.

## Where to put tests

Backend service tests belong under the Spring test tree, grouped by service.

Recommended structure:

- one `*ServiceImplTest` file per service
- suites grouped by functionality
- shared test data helpers extracted into reusable factory/helper classes when duplication becomes noticeable

Examples of suite grouping:

- Context Fetch
- Stage Transitions
- Interrupt Validation
- Broadcast Behavior
- Recovery Scenarios

Avoid grouping by development iteration names such as `Iteration5` or `Extra` in final long-term test files.

## Naming tests

Prefer names that describe business behavior, for example:

- `contextFetchReturnsSafeContextWhenScheduleMissing`
- `interruptRejectsDuplicateAttempt`
- `progressStopsAtEndOfGame`

Avoid vague names such as:

- `testContext`
- `worksCorrectly`
- `shouldPass`

## When to add tests

Add or update tests when:

- adding a feature
- changing a rule
- fixing a bug
- changing a payload visible to clients
- changing a recovery or reconnection path

If production behavior changes, tests should usually change too.

## Regression-first mindset

For bugs:

1. reproduce the bug in a test
2. make the test fail
3. fix the implementation
4. keep the test to prevent future regressions

## Side-effect assertions

For methods that persist state or broadcast events, tests should verify not only returned values but also:

- repository writes
- websocket notifications
- ordering of side effects when important
- absence of partial writes on rejected operations

Use ordering assertions only where order truly matters.

## Shared helpers

If multiple test files reuse helper builders such as `game()`, `projection()`, `schedule()`, or common entities, prefer moving those helpers into a shared test utility/factory class instead of duplicating them.

Shared helpers should:

- reduce repetition
- keep test setup readable
- not hide important test meaning

## What not to over-test

Avoid spending too much time on:

- trivial getters/setters
- simple delegation with no business value
- implementation details that are likely to change without affecting behavior

Focus effort on logic that can break user-visible behavior.

## Running tests locally

Developers should be able to run backend tests locally before opening a pull request.

Typical expectations:

- run the full backend unit suite regularly
- run relevant service tests when modifying core logic
- run targeted tests while developing, then full suite before merge

## Relationship with other docs

- `testing-overview.md` explains the overall strategy and purpose
- `test-catalog.md` describes what is already covered
- `test-catalog.csv` provides a more detailed inventory of individual tests


## Writing controller tests

Controller tests protect the HTTP contract of REST endpoints.

For every endpoint, tests should normally cover:

- happy path behavior
- `DerivedException` behavior
- unexpected exception behavior

Each of these paths should verify:

- HTTP status code
- response content when applicable
- response `Content-Type` when applicable

When an endpoint returns JSON, tests should assert the `application/json`
media type for success and controller-handled error responses.

For endpoints returning binary media (for example audio snippets),
tests should verify the success media type and ensure controller-handled
error responses return JSON.

Controller-level exception handling is centralized in

`com.cevapinxile.cestereg.api.support.ApiErrorResponses.handleApiException`

Tests should treat this response format as part of the API contract.

Controller tests should also validate malformed request handling and confirm
that service-layer methods are not called when the request is rejected at
the controller boundary.

## Writing Spring full-stack integration tests

Use a random-port `@SpringBootTest` only for a composition risk that narrower tests cannot already demonstrate. Enter through real HTTP and keep Spring services, repositories, PostgreSQL, application transactions, and serialization real; substitute only genuine external boundaries such as the physical serial adapter.

Do not use a test-managed `@Transactional` around HTTP scenarios. Arrange prerequisite state directly when appropriate, then verify the externally visible contract and meaningful persisted state after the request. Prefer one representative test per distinct composition mechanism over endpoint-by-endpoint duplication, and extract shared support only after multiple test files actually need it.


## How to add a new WebSocket integration test

1. Decide whether the behavior is truly WebSocket integration.
2. Choose the smallest fixture that creates the required backend state.
3. Use `withGameFixture` for normal stage fixtures.
4. Use `withDeterministicFixture` for precise Stage-2 interrupt/seek state.
5. Connect only the required browser clients.
6. Trigger one action through REST or browser UI.
7. Assert browser-observed frames:
   - type
   - routing
   - ordering
   - exact count when important
   - semantic payload fields
   - contract validity when the state is reachable
8. Clean up through the fixture helper.

Example shape:

```ts
await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
  const clients = await connectAdminAndTv(browser, seed.roomCode);

  try {
    const before = countBackendWsFramesOfType(clients.tv.frames, 'song_reveal');

    expect(await revealSchedule(request, seed.roomCode, seed.currentScheduleId!)).toBeLessThan(400);

    await expectBackendWsFrameTypeAfter(clients.tv.frames, 'song_reveal', before);
  } finally {
    await clients.close();
  }
});
```

## Writing deterministic fixtures

Use deterministic fixtures for states that are hard to create through normal REST calls without waiting. For more detailed rule-set see `docs/developer-guide/testing/e2e.md`

Good deterministic fixtures:

- expired schedule with no pauses
- team paused
- system paused with a valid scenario marker
- layered team/system pause
- resolved long system pause
- resolved layered team/system group
- overlapping resolved pauses to test most-encompassing duration

Bad deterministic fixtures:

- team interrupt after an unresolved system interrupt
- two unresolved team interrupts
- system pause with no scenario marker
- scenario `3`
- resolved and unresolved interrupts mixed in one active group
- team interrupt with `scenario`
- system interrupt with `correct`

## Maintenance

When adding, renaming, or deleting tests:

- update `test-catalog.csv` for every test addition, rename, or deletion; update `test-catalog.md` when the overall coverage model changes meaningfully
- keep helper names behavior-focused
- delete redundant tests instead of keeping two near-identical variants
- prefer one strong test over several trivial variants
