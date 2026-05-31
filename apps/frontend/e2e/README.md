# Seeded WebSocket Playwright E2E

This suite targets browser-level WebSocket integration using the backend E2E fixture API on branch `100_e2e_test`.

The important backend endpoints are:

- `POST /api/e2e/v1/game-fixtures` — creates a deterministic game fixture from an `E2eGameFixtureRequest` payload.
- `DELETE /api/e2e/v1/game-fixtures/{roomCode}` — removes the fixture room after the test.

Each destructive test creates its own room and deletes it afterwards, so Stage-2 tests no longer depend on one shared mutable `SONG` room.

## Coverage added/recovered

- Recovery `welcome` snapshots for lobby, albums, songs/listening, songs/revealed, and winner fixtures.
- Album-stage `album_picked` and start-category transition.
- Stage-2 `song_repeat`, `song_reveal`, `pause`, `answer`, `error_solved`, and `song_next`/recovery behavior.
- Stage-2 disconnect/reconnect recovery.
- Duplicate TV protection during Stage 2.
- Room isolation and TV/Admin routing.
- Contract validation for browser-observed Stage-2 frames.

## Run

```bash
cd apps/backend
./mvnw spring-boot:run -Pe2e -De2e.clean=true

cd apps/frontend
npm run e2e:ws
```

If your `package.json` is missing the script:

```json
"e2e:ws": "playwright test e2e/specs/ws-*.spec.ts"
```
