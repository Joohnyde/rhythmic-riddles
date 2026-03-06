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
