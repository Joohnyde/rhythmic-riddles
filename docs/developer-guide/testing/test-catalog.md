# Test Catalog

## Purpose

This document gives a high-level overview of the current automated test suite.

It is not intended to list every assertion in prose. Instead, it explains where major coverage exists, which runner owns each layer, and where contributors should look first before adding new tests.

A more detailed inventory of individual test cases is maintained in `test-catalog.csv`.

## CSV catalog format

`test-catalog.csv` is the detailed machine-readable inventory of tests. Each row represents one test case or one parameterized test family.

Current columns:

| Field         | Meaning                                                                                                                                                                  |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `framework`   | Test runner/framework. Use `junit` for Java/Spring tests, `vitest` for Angular unit/component tests, and `playwright` for browser E2E tests.                             |
| `file`        | Source file that contains the test, relative to the owning project test root.                                                                                            |
| `suite`       | Logical suite/group/describe block. For JUnit this can be the nested class or service area; for Vitest/Playwright this is usually the surrounding `describe(...)` group. |
| `test_name`   | Short stable name of the test or parameterized test family.                                                                                                              |
| `description` | One-sentence explanation of the behavior protected by the test.                                                                                                          |
| `importance`  | Relative importance from `1` to `10`; `10` protects the most business-critical or regression-prone behavior.                                                             |

The catalog is not supposed to replace the source code. It is a map for reviewers and contributors.

Use `importance` consistently:

- `10`: business-critical invariants, data/state integrity, protocol compatibility, or historically fragile behavior;
- `8-9`: major feature behavior and high-value regression protection;
- `6-7`: meaningful supporting behavior and boundary coverage;
- `1-5`: low-risk or primarily presentational checks whose failure does not directly threaten feature behavior.

Use it to answer:

- where is this behavior already tested?
- which layer owns this behavior?
- is this new test duplicating an existing one?
- which high-risk behavior has no coverage yet?

## Test runners and ownership

The repository has three main automated test worlds.

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

### Angular / Vitest tests

Frontend unit and component tests are owned by Vitest through Angular's `ng test` integration.

Typical location:

```text
apps/frontend/src/**/*.spec.ts
```

Typical runner:

```bash
cd apps/frontend
npm test -- --watch=false
```

Vitest tests should own fast frontend behavior such as:

- Signal Store state transitions and derived state, including Stage 1 picker/selected recovery hydration;
- Angular component rendering and interaction;
- login handshake behavior that can be isolated from a real browser backend;
- pagination and deterministic layout helpers;
- icon allocation, buzzer-linking, and animation trigger semantics.

Stage 1 album-selection coverage should keep the live-pick and welcome-recovery paths aligned.
Fast unit tests own source-order preservation, image-gated focus measurement, rendered-grid neighbor
geometry, carousel layout planning, card state, and picker-header presentation. Playwright keeps the
browser WebSocket recovery path honest, including selected-album recovery through the TV carousel.

### Angular / Playwright tests

Browser tests are owned by Playwright and run inside the frontend project.

Typical location:

```text
apps/frontend/e2e/...
```

Frontend catalog governance checks both Vitest and Playwright rows:

```bash
cd apps/frontend
npm run test:catalog:frontend
```

Expected script:

```json
{
    "test:catalog:frontend": "playwright test --config=playwright.catalog.config.ts"
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
- `ImageServiceImpl`

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

Controller tests should not retest every service rule. They should prove that HTTP wiring, validation, serialization, and exception handling are correct. Binary asset controllers additionally lock the returned bytes/MIME contract and asset-specific HTTP errors without duplicating filesystem resolution tests.

### Spring full-stack application integration

`RhytmicRiddlesApplicationTests` starts the real application on a random port against the shared ephemeral PostgreSQL database. It keeps the Spring graph real except for the physical `BuzzerSerialAdapter` boundary and verifies three representative composition outcomes:

- team creation commits and returns the serialized result;
- a stale lobby mutation returns `409 / E003` without persistence;
- real room-row lock contention returns `423 / E010` without persistence.

The suite is intentionally small; deeper business, rollback, concurrency, recovery, and WebSocket variants remain in their focused suites. The random-port tests also supersede the old bootstrap-only `contextLoads()` test.

### Real-PostgreSQL repository integration tests

Persistence integration tests live under:

```text
apps/backend/src/test/java/com/cevapinxile/cestereg/persistence/repository/integration
```

They use the shared real-PostgreSQL support in `persistence/integration/support` and intentionally seed rows through SQL rather than repository `save()` calls. This keeps the query under test as the thing being proved.

Current suites:

- `GameRepositoryIntegrationTest`
- `InterruptRepositoryIntegrationTest`
- `ScheduleRepositoryIntegrationTest`
- `CategoryRepositoryIntegrationTest`
- `TeamRepositoryIntegrationTest`

This layer protects stage-aware lookup, ordering, filtering, game/schedule scoping, nested interrupt semantics, update-query targeting, client-facing projections, PostgreSQL-native scoring retrieval, and the automatic-picker/admin-fallback boundary.

Do not add tests here for trivial inherited CRUD behavior.

### DB-backed recovery and persistence invariants

Service-level DB integration tests live under:

```text
apps/backend/src/test/java/com/cevapinxile/cestereg/core/service/integration
```

Current service suites:

- `GameLifecycleIntegrationTest`
- `GameRecoveryIntegrationTest`
- `GameStateAtomicityIntegrationTest`
- `InterruptInvariantIntegrationTest`
- `RoomOwnershipIntegrationTest`
- `ScheduleAtomicityIntegrationTest`
- `ScoreCacheConsistencyIntegrationTest`
- `TransactionAtomicityIntegrationTest`
- `GameConcurrencyIntegrationTest`
- `InterruptConcurrencyIntegrationTest`

Schema-level integration lives in `persistence/integration/DatabaseSchemaIntegrationTest`.

Together these tests seed persisted state directly into an ephemeral PostgreSQL database initialized from `db_01_create_schema.sql` followed by `db_04_add_runtime_invariants.sql` and invoke real repositories/services where appropriate. They protect recovery across lobby, album-selection, song, and winner stages; persisted lifecycle transitions; incomplete/corrupt state; active team answering; layered team/system interruption; technical pause; ended-not-revealed and revealed song states; nested/disjoint pause timing; cross-room UUID ownership; transaction rollback; DB/in-memory score-cache coherence; initializer/schema invariants; and persistence invariants under controlled concurrent requests. A fixed `Clock` is used when wall-clock time affects assertions.

See `db-integration-tests.md` for the local runner and fixture conventions.

### Lower-level/supporting tests

Additional tests may exist for:

- filesystem/storage adapters such as `LocalAssetGateway`;
- WebSocket handshake helpers;
- cached DTO behavior;
- application/full-stack composition sanity;
- request/response DTO behavior;
- utility classes.

These tests should stay small. Storage-adapter tests should use temporary directories and prove path/format/error contracts at the gateway boundary rather than retesting controller behavior. If a supporting test starts describing full gameplay, it probably belongs in a service or integration layer instead.

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
- bundled frontend schema copies staying synchronized with the backend source;
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
- album-stage recovery data, including reconnect after an album is picked but before it starts;
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

### 4. Real-PostgreSQL recovery/query composition

Database integration should protect:

- recovery-critical repository ordering and filtering;
- stage-aware game lookups and game-owned cascade cleanup;
- disjoint-or-nested interrupt semantics;
- persisted score/result projections;
- `contextFetch(...)` reconstruction from actual rows across all game stages;
- representative invalid persisted state;
- query behavior that depends on PostgreSQL rather than mocked repositories;
- persisted lifecycle transitions and failure atomicity;
- supported write-path ownership so externally supplied identifiers cannot cross room boundaries;
- database/in-memory cache coherence and controlled transaction races;
- the current `db_01` plus `db_04` initializer contract and database-enforced runtime invariants;
- team/system interrupt contention where team commands fail fast but must-persist system events are never lost.

### 5. Browser-observed WebSocket runtime

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

- does this test belong in JUnit, Vitest, or Playwright?
- does it assert the most meaningful observable effect?
- does it cover a new rule or just repeat an existing case?
- does it use a valid fixture or reachable runtime state?
- is its `test-catalog.csv` row present and correctly classified?

## Catalog maintenance

When adding, renaming, merging, or deleting tests:

- update `test-catalog.csv` in the same change; this is required for every JUnit, Vitest, and Playwright test, not optional documentation cleanup;
- keep descriptions short and behavior-focused;
- note the test suite/group where the test belongs;
- keep `importance` realistic: reserve `10` for business-critical or highly regression-prone behavior and do not inflate cosmetic assertions;
- update this file when a new major test family is added.

The backend consistency test enforces JUnit rows. The frontend catalog-governance check independently enforces both `vitest` and `playwright` rows, so a new test without a catalog entry must fail validation.

## Future expansion

This catalog should later expand further as new test families are introduced, including full product E2E regression coverage beyond the current seeded WebSocket suite.
