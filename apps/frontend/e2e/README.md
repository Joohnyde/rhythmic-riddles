# WebSocket Playwright E2E

This suite focuses on browser-level WebSocket integration.

It intentionally keeps only high-value, non-redundant coverage:

- Admin/TV login opens correct backend `/ws/{pos}{roomCode}` socket
- duplicate same-role sockets are rejected/silent
- invalid room login does not receive `welcome`
- REST lobby actions emit correct WS frames
- TV-only frames do not leak to Admin
- room A frames do not leak to room B
- self-seeded Stage-2 flow covers `pause`, `answer`, `song_repeat`, `song_reveal`, `song_next`/recovery `welcome`, and `error_solved`
- browser-observed frames validate against frontend contracts, and backend schemas when available
- selector contract verifies critical `data-testid`s after frontend revamp

## Run

Backend:

```bash
cd apps/backend
./mvnw spring-boot:run -Pe2e -De2e.clean=true
```

Frontend:

```bash
cd apps/frontend
npm start
```

Tests:

```bash
cd apps/frontend
npm run e2e:ws
```

## Required package scripts

```json
{
  "e2e": "playwright test",
  "e2e:ws": "playwright test e2e/specs/ws-*.spec.ts",
  "e2e:headed": "playwright test --headed",
  "e2e:debug": "playwright test --debug",
  "e2e:report": "playwright show-report"
}
```

## Why fewer tests than previous iterations?

Previous patches had many duplicate variations of the same assertion, for example several invalid room strings testing the same backend rejection branch. This version keeps representative boundary coverage and uses higher-value end-to-end Stage-2 coverage instead.
