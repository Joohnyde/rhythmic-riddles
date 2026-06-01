# Seeded WebSocket Playwright E2E

This folder contains browser-level WebSocket integration tests for the Angular frontend.

The suite opens real Playwright browser clients, connects them to the Spring Boot backend, captures the actual `/ws/{pos}{roomCode}` WebSocket traffic, and asserts routing, ordering, recovery snapshots, socket lifecycle behavior, and schema compliance.

This README is intentionally operational. For the full explanation of the E2E profile, database schema copy, fixture API, interrupt model, and coverage strategy, read:

- [`docs/developer-guide/testing/e2e.md`](../../../docs/developer-guide/testing/e2e.md)
- [`docs/developer-guide/testing/writing-tests.md`](../../../docs/developer-guide/testing/writing-tests.md)
- [`docs/developer-guide/testing/test-catalog.md`](../../../docs/developer-guide/testing/test-catalog.md)
- [`docs/developer-guide/websockets.md`](../../../docs/developer-guide/websockets.md)

## What this suite is for

Use this suite when the behavior depends on real browser WebSocket clients:

- Admin/TV socket connection and role routing.
- Recovery `welcome` snapshots.
- Lobby and album WebSocket side effects.
- Stage-2 song-state frames such as `song_repeat`, `song_reveal`, `pause`, `answer`, `error_solved`, and `song_next`.
- Duplicate Admin/TV socket protection.
- Disconnect/reconnect and replacement socket behavior.
- Room isolation.
- Browser-observed frame schema validation.

Do not use this folder for visual regression, CSS/layout checks, or full user-facing gameplay walkthroughs. Those belong in separate UI/full-E2E suites.

## Required services

Start the backend with the `e2e` Spring profile:

```bash
cd apps/backend
./mvnw spring-boot:run -Pe2e -De2e.clean=true
```

Start the frontend:

```bash
cd apps/frontend
npm start
```

Run the WebSocket E2E suite:

```bash
cd apps/frontend
npm run e2e:ws
```

Expected `package.json` script:

```json
{
    "e2e:ws": "playwright test \"e2e/specs/(.*/)?websocket-.*\\.spec\\.ts\""
}
```

The pattern is used because the repository actually stores specs below nested folders such as `specs/stage2` or `specs/contracts`.

## Backend fixture API

The suite uses backend-only E2E fixture endpoints exposed under the `e2e` Spring profile:

| Method   | Path                                   | Purpose                                 |
| -------- | -------------------------------------- | --------------------------------------- |
| `POST`   | `/api/e2e/v1/game-fixtures`            | Create a deterministic game fixture.    |
| `DELETE` | `/api/e2e/v1/game-fixtures/{roomCode}` | Delete the fixture room after the test. |

Use the frontend helpers instead of calling these endpoints manually from specs:

- `withGameFixture(...)` for ordinary stage fixtures.
- `withDeterministicFixture(...)` for precise Stage-2 interrupt/seek states.

Every destructive test should create its own fixture room and clean it up in `finally`.

## Folder layout

```text
e2e/
  README.md
  contracts/backend/websocket-contracts/v1/schema/
    _published-frame-registry.schema.json
    *.schema.json
  pages/
    admin-page.ts
    login-page.ts
    tv-page.ts
  specs/
   ... Playwright specs grouped by behavior ...
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

## Helper quick reference

| Helper                               | Purpose                                                        |
| ------------------------------------ | -------------------------------------------------------------- |
| `pages/login-page.ts`                | Stable login interactions for Admin and TV.                    |
| `utils/e2e-session.ts`               | Opens Admin/TV browser contexts and manages socket sessions.   |
| `utils/ws-capture.ts`                | Captures and filters browser WebSocket frames.                 |
| `utils/api-client.ts`                | Calls REST endpoints that trigger WebSocket side effects.      |
| `utils/fixture-api.ts`               | Creates common deterministic game fixtures.                    |
| `utils/deterministic-fixture-api.ts` | Creates precise interrupt/seek fixtures.                       |
| `utils/ws-contracts.ts`              | Validates browser-observed frames against the shared contract. |
| `utils/backend-schema-governance.ts` | Loads and checks bundled WebSocket schema files.               |

## Common commands

Run all WebSocket E2E tests:

```bash
npm run e2e:ws
```

Run one spec:

```bash
npx playwright test e2e/specs/ws-stage2-events.seeded.spec.ts
```

Run one test by title:

```bash
npx playwright test e2e/specs/ws-stage2-events.seeded.spec.ts -g "team pause"
```

Open the HTML report:

```bash
npx playwright show-report
```

Open a failed trace:

```bash
npx playwright show-trace test-results/<failed-test>/trace.zip
```

## Troubleshooting

### Missing `e2e:ws` script

Add this to `apps/frontend/package.json`:

```json
{
    "scripts": {
        "e2e:ws": "playwright test \"e2e/specs/(.*/)?websocket-.*\\.spec\\.ts\""
    }
}
```

### Fixture creation fails

Check that the backend is running with the `e2e` profile:

```bash
cd apps/backend
./mvnw spring-boot:run -Pe2e -De2e.clean=true
```

Also verify that the E2E schema was created and that the fixture API is available.

### Schema validation fails

A schema failure means one of three things:

1. The test produced a frame from an unreachable or invalid game state.
2. The runtime frame is legitimate but the schema is stale.
3. The runtime code changed and the frontend/backend contract needs review.

Do not loosen schemas just to make impossible test states pass.

### Flaky socket tests

Prefer deterministic fixture timestamps over real waits. When testing duplicate sockets, disconnects, or replacement clients, use separate browser contexts for each role/client.
