
# Seeded WebSocket Playwright E2E

This document explains the seeded browser-level WebSocket E2E system used by the frontend tests.

The short version: the frontend `e2e` suite creates deterministic backend game rooms, opens real Admin and TV browser clients, observes the actual WebSocket frames delivered by Spring Boot, and validates that routing, ordering, recovery, and schema contracts remain correct.

## Scope

These tests are **WebSocket integration tests**.

They are not meant to replace:

- backend unit tests for service-level business rules
- backend controller tests for REST request/response details
- frontend unit tests for components/services
- full E2E gameplay regression tests that exercise the UI like a human host
- visual regression tests

They sit between backend integration tests and full product E2E. They prove that the real browser clients and backend WebSocket runtime communicate correctly.

## Why this suite exists

The game depends on two synchronized browser clients:

- Admin controls the flow.
- TV shows the public game state.

The backend is authoritative, but the experience breaks if WebSocket delivery, routing, recovery, or frame shape drifts. These tests protect that real-time contract.

The most important risks are:

- Admin connects as the wrong socket role.
- TV receives Admin-only or wrong-room frames.
- A duplicate TV/Admin becomes active and processes operational frames.
- Reconnect recovery sends an incomplete or stale `welcome` snapshot.
- Stage-2 events arrive out of order or more than once.
- System/team interrupts produce impossible state.
- Browser-observed frames stop matching the shared schema contract.

## Where the files live

Frontend tests live under:

```text
apps/frontend/e2e
```

Expected structure:

```text
e2e/
  README.md
  contracts/backend/websocket-contracts/v1/schema/
    _published-frame-registry.schema.json
    welcome.schema.json
    pause.schema.json
    answer.schema.json
    ...
  pages/
    login-page.ts
    ...
  specs/
    contracts/
    lobby/
    recovery/
    smoke/
    sockets/
    stage2/
  utils/
    api-client.ts
    backend-schema-governance.ts
    deterministic-fixture-api.ts
    e2e-session.ts
    env.ts
    fixture-api.ts
    selectors.ts
    ws-capture.ts
    ws-contracts.ts
    ws-test-assertions.ts
```

The exact spec grouping may evolve, but the intent should stay stable:

| Folder | Purpose |
|---|---|
| `contracts` | Runtime frame validation and schema governance. |
| `lobby` | Stage-0 lobby side effects such as `new_team` and `kick_team`. |
| `recovery` | Seeded `welcome` snapshots for stages and substates. |
| `smoke` | Minimal connection/login/routing sanity checks. |
| `sockets` | Duplicate-role, replacement, disconnect, and socket-slot behavior. |
| `stage2` | Song-state WebSocket behavior: repeat, reveal, pause, answer, error recovery, next. |

## Backend support: the `e2e` Spring profile

The seeded tests require the backend to run with the `e2e` profile.

```bash
cd apps/backend
./mvnw spring-boot:run -Pe2e -De2e.clean=true
```

The profile exists so browser tests do not mutate normal development data. It enables test-only endpoints and configures the application to work against an isolated `e2e` schema.

Important expectations:

- E2E endpoints are active only under `@Profile("e2e")`.
- The application uses the `e2e` database schema while this profile is active.
- `-De2e.clean=true` should recreate a clean E2E schema at startup.
- Normal production/dev endpoints should not expose the fixture API.

## E2E database schema

The test profile should use a separate PostgreSQL schema named `e2e`.

Typical startup behavior:

1. Drop the existing `e2e` schema when `-De2e.clean=true` is provided.
2. Recreate the `e2e` schema.
3. Copy the current `public` schema structure into `e2e`.
4. Run Spring Boot with Hibernate/default SQL search path pointing at `e2e`.
5. Let tests create and delete deterministic fixture rooms without touching normal dev data.

This is important because the Playwright tests are destructive: they create rooms, advance schedules, create interrupts, disconnect sockets, and delete rooms afterward.

Setting up the e2e schema is done using `scripts/dev/setup-e2e-schema.sh` script. It is automatically executed by maven if the passed parameter is true.

### Why copy from `public`?

The E2E schema should match the real application schema. Copying from `public` means tests exercise the same tables, constraints, indexes, and foreign-key behavior as the application.

If `public` migrations change, the E2E schema should change with them on the next clean run.

### What to verify when E2E startup fails

Check the active schema:

```sql
SELECT current_schema();
```

Check E2E tables exist:

```sql
SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_schema = 'e2e'
ORDER BY table_name;
```

Check foreign keys and cascades:

```sql
SELECT
  conname,
  conrelid::regclass AS child_table,
  confrelid::regclass AS parent_table,
  pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE contype = 'f'
  AND connamespace = 'e2e'::regnamespace
ORDER BY child_table::text, conname;
```

The fixture cleanup path relies on deleting a game by room code and letting dependent runtime data disappear safely.

## Backend fixture API

The test-only fixture API is exposed under:

```text
/api/e2e/v1/game-fixtures
```

Endpoints:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/e2e/v1/game-fixtures` | Create a complete deterministic game fixture. |
| `DELETE` | `/api/e2e/v1/game-fixtures/{roomCode}` | Delete a fixture room and dependent runtime state. |

The backend branch exposes these through an `e2e` package containing the fixture controller, request DTO, service, service implementation, and validator.
The controller is profile-gated with `@Profile("e2e")`, and the service persists game, teams, categories, albums, tracks, schedules, and interrupts in dependency order.

## Fixture payload model

A fixture describes a complete game state.

Conceptual shape:

```text
Game
  teams[]
  categories[]
    album
      tracks[]
        schedule?
          interrupts[]
```

Important fields:

| Object | Important fields |
|---|---|
| Game | `id`, `roomCode`, `maxSongs`, `maxAlbums`, `stage` |
| Team | `id`, `buttonCode`, `name`, `image` |
| Category | `id`, `pickedByTeamId`, `ordinalNumber`, `done`, `album` |
| Album | `id`, `name`, `customQuestion`, `tracks` |
| Track | `customAnswer`, `schedule` |
| Schedule | `id`, `trackId`, `startedAt`, `revealedAt`, `ordinalNumber`, `interrupts` |
| Interrupt | `id`, `teamId`, `arrivedAt`, `resolvedAt`, `correct`, `score`, `scenario` |

The current `Track` fixture payload should stay minimal. Do not send removed track metadata fields just because older examples did.

## Stage meanings in fixtures

| Stage number | Runtime stage | Meaning |
|---:|---|---|
| `0` | `lobby` | Teams can be created/kicked. |
| `1` | `albums` | Album/category selection or selected album preview. |
| `2` | `songs` | Song listening/reveal/interrupt state. |
| `3` | `winner` | Game is finished and winner state is shown. |

## Category rules

Category state determines album-stage recovery and progression.

Important rules:

- `ordinalNumber = null` means the category has not been chosen yet.
- A category can exist in the prepared game without being selected.
- `done = true` means all scheduled tracks for that category have been listened to.
- When a category is selected, only `maxSongs` tracks should be scheduled.
- Schedule ordinal numbers should run from `1` to `maxSongs` for the selected category.
- In Stage 2, at least one schedule must have `startedAt != null`.
- At most one schedule may have `startedAt != null && revealedAt == null`.

## Interrupt rules

The fixture validator should reject impossible interrupt states. Tests should not depend on impossible states.

Rules for all interrupts:

- Every interrupt must have `arrivedAt`.
- Team interrupts have `teamId != null`.
- System interrupts have `teamId == null`.
- Team interrupts must not carry `scenario`.
- System interrupts must not carry team-answer result fields.
- `score` and `correct` belong together for resolved team answers.

Rules for ongoing interruptions:

- There can be at most one ongoing team interrupt.
- There can be multiple ongoing system interrupts.
- If an ongoing team interrupt and ongoing system interrupts coexist, the team interrupt must arrive before every ongoing system interrupt.
- Exactly one ongoing system interrupt must carry a scenario marker.
- System scenario must be one of `0`, `1`, `2`, or `4`. Scenario `3` is not a recoverable song substate.

Rules for resolved groups:

- If an interruption group is resolved, all interrupts in that group share the exact same `resolvedAt`.
- A fixture should not mix resolved and unresolved interrupts in the same active interruption group.
- When a team answer is resolved, any system interruptions that happened while the team was answering are resolved together with the team interruption.

## `previousScenario` behavior

`previousScenario` is easy to misunderstand.

It is not guaranteed simply because a test created a system interrupt through a backend endpoint.

The runtime flow is:

1. A client receives a `pause` frame with `answeringTeamId == null` or the literal string `"null"`.
2. The client saves the current scenario to the backend.
3. Resolving the system pause later emits `error_solved` with that saved `previousScenario`.

Therefore:

- Tests that assert `error_solved.previousScenario` should use a deterministic fixture with a scenario marker or drive the client-side save path.
- Do not loosen the schema to accept `null` just because a test created an invalid or incomplete state.
- Contract tests should validate frames produced from reachable states.

## Running the suite

Start backend:

```bash
cd apps/backend
./mvnw spring-boot:run -Pe2e -De2e.clean=true
```

Start frontend:

```bash
cd apps/frontend
npm start
```

Run WebSocket tests:

```bash
cd apps/frontend
npm run e2e:ws
```

Run the complete frontend unit + E2E validation:

```bash
cd apps/frontend
npm run test:all
```

### Playwright worker limit

These E2E tests isolate application data by room, but every worker still shares the same Angular server, Spring Boot process, PostgreSQL instance, and connection pool. Playwright otherwise defaults to half of the machine's logical CPUs, which can overload this integration stack and make parallel tests substantially slower than a bounded run.

`playwright.config.ts` therefore uses four workers locally and two on CI by default. Override the limit only when intentionally tuning a capable environment:

```bash
E2E_WORKERS=2 npm run e2e
```

`E2E_WORKERS` must be a positive integer. Keep `fullyParallel` disabled so tests inside one spec file continue to execute in declaration order.

Recommended scripts in `apps/frontend/package.json`:

```json
{
  "test:all": "npm test -- --watch=false && npm run test:catalog:frontend && npm run e2e",
  "e2e": "playwright test",
  "e2e:ws": "playwright test \"e2e/specs/(.*/)?websocket-.*\\.spec\\.ts\"",
  "e2e:headed": "playwright test --headed",
  "e2e:debug": "playwright test --debug",
  "e2e:report": "playwright show-report"
}
```

If specs are not nested, `e2e:ws` can use `e2e/specs/ws-*.spec.ts`. If specs are grouped into folders, use the recursive pattern.

## Environment variables

Common variables:

| Variable | Default | Purpose |
|---|---|---|
| `E2E_FRONTEND_URL` | `http://localhost:4200` | Angular frontend URL. |
| `E2E_BACKEND_URL` | `http://localhost:8080` | Spring Boot backend URL. |
| `E2E_WORKERS` | `4` locally / `2` on CI | Positive integer overriding the Playwright worker cap. |

Keep these in one helper such as `e2e/utils/env.ts`. Do not scatter hardcoded ports across specs.

## Coverage map

### Connection and role routing

- Admin connects to position `0`.
- TV connects to position `1`.
- Invalid room stays on login and receives no backend application frame.
- Admin and TV can connect to the same room.

### Socket uniqueness and replacement

- Duplicate Admin/TV does not become active.
- Duplicate clients do not receive operational frames.
- Closing a duplicate does not affect the original active socket.
- Closing the original allows a replacement client to connect and receive exactly one recovery `welcome`.

### Lobby

- Team creation emits `new_team` to TV.
- Team removal emits `kick_team` to TV.
- Lobby frames do not leak across rooms.

### Albums

- Album-stage recovery sends a usable `welcome` snapshot.
- `album_picked` is routed correctly.
- Starting a selected category transitions into song state.

### Stage 2 songs

- `song_repeat` emits once to active Admin and TV.
- `song_reveal` emits once when reveal is legal.
- `song_next` or a recovery `welcome` is emitted when continuing from a revealed schedule.
- Team pause emits `pause` with the answering team id and interrupt id.
- Answer emits `answer` with team id, schedule id, and correctness.
- System pause emits `pause` with null-like answering team id.
- Resolving a scenario-backed system pause emits `error_solved` with `previousScenario`.

### Stage 2 safety

- Team pause blocks a second team buzz.
- System pause blocks team buzz.
- Missing TV blocks answer/continue paths that require both apps.
- Replacement TV restores the ability to continue after the safety condition is satisfied.

### Recovery snapshots

- Lobby recovery.
- Albums recovery.
- Songs/listening recovery.
- Songs/revealed recovery.
- Team-pause recovery.
- System-pause recovery.
- Layered team/system recovery.
- Winner recovery.

### Contract validation

- Every observed frame has a registered type.
- Every runtime frame type has a schema file.
- Every schema declares the matching `type.const`.
- Browser-observed frames validate against the bundled shared schema files.

## What does not belong here

Do not turn this suite into a full UI regression pack.

Avoid:

- checking exact CSS classes
- checking layouts or pixel positions
- checking animations
- testing every button label variation
- full scoring regression with all edge cases
- exhaustive backend business-rule combinations already covered by service tests

Those belong in frontend component tests, full E2E regression, visual regression, or backend unit/integration tests.


## Schema workflow

When a WebSocket frame changes, update the contract and tests together.

Checklist:

1. Update runtime backend frame payload.
2. Update the schema under `e2e/contracts/backend/websocket-contracts/v1/schema`.
3. Update `_published-frame-registry.schema.json` if adding/removing a type.
4. Update frontend frame handling if needed.
5. Add or adjust a Playwright path that observes the changed frame.
6. Run `npm run e2e:ws`.

Never loosen a schema just to make an impossible test state pass. First ask whether the frame was produced through a reachable game flow.

## Troubleshooting

### Fixture creation returns 500

Usually means the fixture violates semantic rules. Check:

- interrupt group rules
- `startedAt` / `revealedAt` consistency
- category ordinal rules
- schedule ordinal rules
- team ids referenced by interrupts
- track/schedule payload shape

### Team buzz returns 409

Common causes:

- effective seek is outside the 9.6 second snippet window
- system pause is active
- another team is answering
- TV/Admin presence requirement is not satisfied

### Reveal/next returns 503

Usually one of the required clients is missing. Some actions require both Admin and TV to be connected.

### Replacement client stays on login

The original same-role socket may still own the slot. Close the original Admin/TV before testing replacement recovery.

### Schema validation fails

Ask two questions:

1. Was the frame produced from a reachable game state?
2. If yes, is the schema outdated or is the runtime payload wrong?

Do not automatically widen the schema. If the test created an impossible state, fix the test.

