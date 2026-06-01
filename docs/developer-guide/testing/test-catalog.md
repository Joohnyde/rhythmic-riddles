
# Test Catalog

## Purpose

This document gives a high-level overview of the current automated test suite.

It is not intended to list every assertion in prose. Instead, it explains where major coverage exists, which runner owns each layer, and where contributors should look first before adding new tests.

A more detailed inventory of individual test cases is maintained in `test-catalog.csv`.

## CSV catalog format

`test-catalog.csv` is the detailed machine-readable inventory of tests. Each row represents one test case or one parameterized test family.

Current columns:

| Field | Meaning |
|---|---|
| `framework` | Test runner/framework. Use `junit` for Java/Spring tests and `playwright` for Angular/browser E2E tests. |
| `file` | Source file that contains the test, relative to the owning project test root. |
| `suite` | Logical suite/group/describe block. For JUnit this can be the nested class or service area; for Playwright this is usually the `test.describe(...)` group. |
| `test_name` | Short stable name of the test or parameterized test family. |
| `description` | One-sentence explanation of the behavior protected by the test. |
| `importance` | Relative importance from `1` to `10`; `10` protects the most business-critical or regression-prone behavior. |

The catalog is not supposed to replace the source code. It is a map for reviewers and contributors.

Use it to answer:

- where is this behavior already tested?
- which layer owns this behavior?
- is this new test duplicating an existing one?
- which high-risk behavior has no coverage yet?

## Test runners and ownership

The repository has two main automated test worlds.

### Java / Spring tests

Java tests are owned by JUnit and run inside the backend project.

Typical location:

```text
apps/backend/src/test/java/...
```

Typical runner:

```bash
cd apps/backend
mvn test -Dtest=TestCatalogConsistencyTest
```

These tests should own:

- pure domain and service rules;
- controller request/response behavior;
- exception mapping;
- repository/query behavior when backed by integration tests;
- Spring wiring and transaction behavior where needed.



### Angular / Playwright tests

Browser tests are owned by Playwright and run inside the frontend project.

Typical location:

```text
apps/frontend/e2e/...
```

Typical runner:

```bash
cd apps/frontend
npm run test:catalog:playwright
```

Expected script:

```json
{
  "test:catalog:playwright": "playwright test --config=playwright.catalog.config.ts"
}
```

Playwright tests should own behavior that requires real browser clients:

- browser WebSocket connections;
- Admin/TV role-specific WebSocket URLs;
- duplicate socket behavior;
- replacement/reconnect behavior;
- browser-observed WebSocket frame routing, ordering, and schemas;
- high-value end-to-end user flows once full E2E regression is added.


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

Service tests are the best place for fast, focused assertions about business behavior. They should not require a browser and should not depend on WebSocket delivery unless the test is explicitly validating a broadcast side effect through a mock/stub.

### Controller tests

Controller tests exist for the REST API layer and are organized one file per controller.

These tests typically verify:

- HTTP status codes;
- response body content;
- response media types;
- malformed payload handling;
- request rejection before service invocation;
- controller-managed exception responses.

Controller endpoints are expected to have tests covering:

- happy path behavior;
- `DerivedException` behavior;
- unexpected exception behavior.

For JSON endpoints, tests should verify `application/json` media type for both success and controller-handled error responses.

Controller tests should not retest every service rule. They should prove that HTTP wiring, validation, serialization, and exception handling are correct.

### Lower-level/supporting tests

Additional tests may exist for:

- WebSocket handshake helpers;
- cached DTO behavior;
- application bootstrap sanity;
- request/response DTO behavior;
- utility classes.

These tests should stay small. If a supporting test starts describing full gameplay, it probably belongs in a service or integration layer instead.

## Current Playwright WebSocket integration tests

The seeded WebSocket Playwright suite lives under:

```text
apps/frontend/e2e
```

Its purpose is browser-level WebSocket integration using backend E2E fixtures. The suite creates deterministic rooms, opens real Admin and TV browser clients, captures browser-observed WebSocket frames, and asserts runtime behavior.

### Runtime contracts and schemas

Primary files:

- `e2e/specs/contracts/websocket-runtime-contracts.seeded.spec.ts`
- `e2e/specs/contracts/websocket-schema-governance.spec.ts`

These tests protect:

- frontend registry completeness;
- loadability of shared WebSocket schema files;
- type discriminator consistency;
- browser-observed runtime frames matching published schemas.

This layer should validate only frames produced from reachable game states. If a test creates an impossible state and then contract validation fails, fix the test or fixture rather than weakening the schema.

### Lobby and album WebSocket behavior

Primary files:

- `e2e/specs/lobby/websocket-lobby-side-effects-and-isolation.seeded.spec.ts`
- `e2e/specs/lobby/websocket-album-stage.seeded.spec.ts`

These tests protect:

- team creation and kick WebSocket side effects;
- TV-only lobby frame routing;
- room isolation for lobby activity;
- album-stage recovery data;
- `album_picked` and album-to-songs transitions.

### Recovery snapshots

Primary files:

- `e2e/specs/recovery/websocket-fixture-recovery.seeded.spec.ts`
- `e2e/specs/recovery/websocket-recovery-substates.seeded.spec.ts`
- `e2e/specs/recovery/websocket-ongoing-interrupt-recovery.seeded.spec.ts`
- `e2e/specs/recovery/websocket-resolved-interrupt-recovery.seeded.spec.ts`
- `e2e/specs/recovery/websocket-song-disconnect-recovery.seeded.spec.ts`

These tests protect reconnect/replacement recovery from persisted state:

- lobby;
- albums;
- songs/listening;
- songs/revealed;
- winner;
- active team interrupt;
- technical/system pause;
- layered team/system pause;
- resolved interrupts that must not recover as active.

### Socket lifecycle and uniqueness

Primary files:

- `e2e/specs/sockets/websocket-socket-uniqueness.seeded.spec.ts`
- `e2e/specs/sockets/websocket-socket-close-semantics.seeded.spec.ts`
- `e2e/specs/smoke/websocket-login-and-boundaries.seeded.spec.ts`

These tests protect:

- Admin/TV role-specific socket URLs;
- Admin and TV coexisting in one room;
- duplicate Admin/TV not becoming active clients;
- closing duplicates not affecting active clients;
- closing original clients allowing replacement clients;
- replacement recovery receiving exactly one meaningful `welcome`.

### Stage-2 song-state WebSocket behavior

Primary files:

- `e2e/specs/stage2/websocket-stage2-events.seeded.spec.ts`
- `e2e/specs/stage2/websocket-song-core.seeded.spec.ts`
- `e2e/specs/stage2/websocket-interrupt-stack.seeded.spec.ts`
- `e2e/specs/stage2/websocket-time-window.seeded.spec.ts`
- `e2e/specs/stage2/websocket-seek-and-boundaries.seeded.spec.ts`
- `e2e/specs/stage2/websocket-semantic-payloads.seeded.spec.ts`
- `e2e/specs/stage2/websocket-both-apps-safety.seeded.spec.ts`
- `e2e/specs/stage2/websocket-deterministic-paused-safety.seeded.spec.ts`
- `e2e/specs/stage2/websocket-room-isolation.seeded.spec.ts`

These tests protect:

- `song_repeat`;
- `song_reveal`;
- team `pause`;
- system/technical `pause`;
- `answer`;
- `error_solved`;
- `song_next` or recovery `welcome`;
- event ordering;
- semantic payload fields;
- room isolation;
- both-apps-present safety;
- buzz window behavior;
- deterministic interrupt and seek states.

## Future E2E regression tests

The current Playwright suite is primarily WebSocket integration. Full product E2E regression should be tracked separately when added.

Full E2E should cover complete user-visible flows:

- room creation;
- both apps connected;
- team creation;
- stage transitions;
- category/album selection;
- song play/repeat/reveal/next;
- interrupt;
- answer;
- scoring;
- finish/results.

These tests should assert the user-facing journey through UI. They should not duplicate every low-level WebSocket assertion already covered by the WS integration suite.

## Most critical coverage areas

The following areas are considered especially important.

### 1. Context generation

`GameServiceImpl.contextFetch(...)` should be treated as a critical regression surface.

It should cover:

- all game stages;
- valid and invalid state combinations;
- missing related data;
- recovery from inconsistent state;
- payload coherence for clients.

### 2. Interrupt flow

`InterruptServiceImpl` should protect:

- valid/invalid interrupt attempts;
- duplicate attempts;
- answer handling;
- score and error persistence;
- cleanup and recovery behavior.

### 3. Schedule progression

`ScheduleServiceImpl` should protect:

- song progression;
- missing current or next song;
- reveal/replay edge cases;
- end-of-game behavior;
- ordering-sensitive side effects.

### 4. Browser-observed WebSocket runtime

Playwright WebSocket integration should protect:

- role-specific socket connection;
- Admin/TV routing;
- room isolation;
- duplicate socket behavior;
- reconnect/replacement recovery;
- browser-observed runtime frames;
- schema compliance for reachable runtime states.

## Using the catalog

Before writing new tests:

1. inspect the relevant source test file;
2. check `test-catalog.csv`;
3. identify the correct layer;
4. avoid duplicating an existing test unless the new test covers a genuinely distinct rule or state combination.

When reviewing a new test, ask:

- does this test belong in JUnit or Playwright?
- does it assert the most meaningful observable effect?
- does it cover a new rule or just repeat an existing case?
- does it use a valid fixture or reachable runtime state?
- does it need to be in the catalog?

## Catalog maintenance

When adding, merging, or deleting tests:

- update `test-catalog.csv`;
- keep descriptions short and behavior-focused;
- note the test suite/group where the test belongs;
- keep `importance` realistic;
- update this file when a new major test family is added.

## Future expansion

This catalog should later expand to include:

- repository integration tests;
- DB-backed recovery integration tests;
- full-stack Spring integration tests;
- full product E2E regression tests;
- frontend unit and integration tests.
