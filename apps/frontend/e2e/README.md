# Product and Seeded WebSocket Playwright E2E

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

Do not use this folder for pixel-level visual regression. Most tests remain WebSocket integration tests,
but a small number of high-value Stage 1 journeys intentionally use the real Admin/TV UI when bypassing
the UI would miss the regression boundary (selection confirmation, focus lifecycle, Play, and fresh-TV
recovery). Do not turn every protocol assertion into a duplicate full gameplay walkthrough.

The six journeys in `specs/product/full-product-game-journeys.spec.ts` are the full-product layer. They
use separate Admin/TV contexts and real UI, HTTP, PostgreSQL, game-state and WebSocket behavior. A
profile-gated endpoint substitutes only the unavailable RF/serial device and calls the same
`BuzzerService` boundary as production.

## Required services

Start the repository PostgreSQL service from the repository root. The E2E profile defaults to the
root Docker mapping on `localhost:2345`:

```bash
docker compose up -d db
```

Start the backend with the `e2e` Spring profile:

```bash
cd apps/backend
mvn spring-boot:run -Pe2e -De2e.clean=true
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

Run the shared-state full-product portfolio. Its dedicated Playwright config always uses one worker:

```bash
npm run e2e:product
```

Run frontend unit tests, test-catalog governance, and then the complete Playwright suite:

```bash
npm run test:all
```

The seeded Playwright config caps the shared E2E environment at two workers by default. The product
portfolio is deliberately excluded from that run and then executed separately by `npm run e2e` using
`playwright.product.config.ts` with exactly one worker. `E2E_WORKERS` therefore tunes only the seeded
suite and cannot make product buzzer tests race another active game:

```bash
E2E_WORKERS=2 npm run e2e
```

Expected `package.json` scripts:

```json
{
    "test:all": "npm test -- --watch=false && npm run test:catalog:frontend && npm run e2e",
    "e2e": "npm run e2e:seeded && npm run e2e:product",
    "e2e:seeded": "playwright test",
    "e2e:product": "playwright test --config=playwright.product.config.ts",
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

The product journeys arrange the already-prepared runtime room with `POST /api/v1/games`, then use
`POST /{roomCode}/catalog` only to attach finite media data and `POST /receiver/{buttonCode}` to
substitute the physical receiver. Game/preparation UI is intentionally outside this runtime suite. The
catalog and receiver endpoints do not exist outside the `e2e` Spring profile.

Use the frontend helpers instead of calling these endpoints manually from specs:

- `withGameFixture(...)` for ordinary stage fixtures.
- `withDeterministicFixture(...)` for precise Stage-2 interrupt/seek states.

Every destructive test should create its own fixture room and clean it up in `finally`.

For Stage 1, fixture `categoryNames` can deliberately produce a source order that differs from the
canonical frontend order. Tests must treat backend membership/metadata as authoritative while asserting
visual order through rendered `data-album-id` hooks. The recovery journey uses a brand-new TV browser
context so the result cannot depend on old local component state.

Contract ownership is intentionally non-duplicated: `websocket-schema-governance.spec.ts` owns static
frontend/backend registry and schema-directory equality, while
`websocket-runtime-contract-coverage.seeded.spec.ts` owns reachable browser-observed frame validation.
The frontend registry comes from the exported `GAME_MESSAGE_TYPES` constant used to derive
`GameMessageType`; it is not reconstructed from the backend schema bundle.

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
mvn spring-boot:run -Pe2e -De2e.clean=true
```

Also verify that the E2E schema was created and that the fixture API is available.

### Schema validation fails

A schema failure means one of three things:

1. The test produced a frame from an unreachable or invalid game state.
2. The runtime frame is legitimate but the schema is stale.
3. The runtime code changed and the frontend/backend contract needs review.

Do not loosen schemas just to make impossible test states pass.

### Flaky or unexpectedly slow socket tests

Prefer deterministic fixture timestamps over real waits. When testing duplicate sockets, disconnects, or replacement clients, use separate browser contexts for each role/client.

Do not remove the configured worker cap to make the suite look more parallel. All specs share one backend, database, and frontend server. Excessive Playwright workers can increase total runtime dramatically through integration-stack contention. Use `E2E_WORKERS` for deliberate local/CI tuning and compare the complete suite wall time rather than an individual test in isolation.
