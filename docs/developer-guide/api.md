# API conventions (Quiz Game)

This document describes the **Phase 1** API implemented in `apps/quiz-game/backend`.

## Protocols
- Client → server: REST over HTTP
- Server → client: WebSockets (server push)

## Base URLs (local dev)
- Backend: `http://localhost:8080`
- WebSocket: `ws://localhost:8080/ws/{socketPos}{roomCode}  (socketPos: 0=TV, 1=Admin)`

## Auth model (current code)
Right now Phase 1 does **not** enforce an authentication token/password at the API layer.

Instead, the app relies on:
- a short `roomCode` (game code) to select the active game
- keeping Admin/TV UIs separate by convention

**Room code is passed via HTTP header**:
- `ROOM_CODE: <4-char code>`

> Planned: add a password (entered in UI) and/or upgrade to a short-lived token.
> If/when implemented, document it here and in `docs/developer-guide/security.md`.

## REST endpoints

### Conventions
- Most endpoints require `ROOM_CODE` request header.
- JSON requests use `application/json`.
- Errors:
  - Domain errors are returned as JSON string produced by `DerivedException.toString()`
  - Unexpected errors return HTTP 500 with an empty body

> Planned improvement: return a structured error object everywhere (see `error-catalog.md`).

### Endpoint list

| Method | Path | What it does | Inputs |
|---|---|---|---|
| POST | `/igra` | Create a new game (Igra). Returns game code and initial state. | Body: CreateIgraRequest { broj_pjesama, broj_albuma } |
| POST | `/igra/changeState` | Change game stage (0 lobby, 1 albums, 2 songs, 3 finished). | Query: stage_id=int; Header: ROOM_CODE |
| POST | `/tim` | Create a team in a lobby game. | Header: ROOM_CODE; Body: CreateTimRequest { ime, dugme, slika } |
| DELETE | `/tim` | Kick a team. | Header: ROOM_CODE; Query: tim_id=string(uuid) |
| POST | `/kategorija/pick` | Pick an album (category) for a team. | Header: ROOM_CODE; Body: PickAlbumRequest { kategorija_id, tim_id } |
| POST | `/kategorija/start` | Start the selected category (schedule songs). | Header: ROOM_CODE; Query: kategorija=uuid |
| POST | `/odgovor/interrupt` | Interrupt current playback (buzz). If tim omitted => technical pause (app disconnect). | Header: ROOM_CODE; Query: tim=uuid (optional) |
| POST | `/odgovor/answer` | Mark the currently answering team as correct/wrong (resolves the active team interrupt). | Header: ROOM_CODE; Body: AnswerRequest { correct:boolean } |
| PUT | `/odgovor/continue` | Resolve a technical pause interrupt (continue after reconnect). | Header: ROOM_CODE; Query: zadnji=uuid (interrupt id) |
| PUT | `/odgovor/previous_scenario` | Persist the current UI scenario for recovery logic. | Header: ROOM_CODE; Query: scenario=int (0..4) |
| POST | `/redoslijed/refresh` | Replay/refresh current song (used when song ended w/o answer). | Header: ROOM_CODE; Query: zadnji=uuid (schedule/playlist entry id) |
| POST | `/redoslijed/reveal` | Reveal answer for current song and optionally play answer snippet. | Header: ROOM_CODE; Query: zadnji=uuid |
| POST | `/redoslijed/next` | Advance to next song in the schedule. | Header: ROOM_CODE |
| GET | `/pjesma/play` | Fetch the question snippet audio (mp3 bytes). | Query: id=uuid |
| GET | `/pjesma/reveal` | Fetch the answer snippet audio (mp3 bytes). | Query: id=uuid |

## WebSocket

### URL
Connect to:
- `/ws/{socketPos}{roomCode}  (socketPos: 0=TV, 1=Admin)`

Where:
- `socketPos = 0` → TV socket
- `socketPos = 1` → Admin socket
- `roomCode` is the 4-char game code

Example:
- TV: `ws://localhost:8080/ws/0ABCD`
- Admin: `ws://localhost:8080/ws/1ABCD`

The handshake interceptor (`GameCodeExtractor`) validates:
- room exists
- socketPos is `0` or `1`

If validation fails, the handshake is rejected.

### Messages (server → client)
The backend sends JSON payloads with a `type` field.

Known message types:
- `welcome`
- `new_team`
- `kick_team`
- `album_picked`
- `pause`
- `error_solved`
- `answer`
- `song_repeat`
- `song_reveal`
- `song_next`

#### `welcome`
Sent immediately after socket connection is accepted. Contains enough information to rebuild UI state.

Fields vary by `stage`:
- `stage = lobby`: includes `teams`
- `stage = albums`: includes `albums`, `team` (who chooses) or `selected` (chosen-but-not-started)
- `stage = songs`: includes song context, `scores`, and recovery fields like `seek`, `error`, `team`, `prekid_id`, `revealed`, `bravo`
- `stage = winner`: includes final `teams`

See `docs/developer-guide/state-machine-and-recovery.md` for field-level details and recovery rules.

#### Other message types
These are “delta” updates (TV/Admin UIs update without a full refresh), for example:
- `new_team` adds a team
- `kick_team` removes a team
- `pause` pauses playback (team interrupt or technical pause)
- `song_reveal` reveals the answer
- `song_next` transitions to the next scheduled song

The exact JSON shapes are assembled in services (some messages are built as raw JSON strings).
If you change a message contract, update both:
- backend sender code
- both frontends (TV/Admin) socket handlers
- this doc

## OpenAPI / Swagger (recommended next step)
The current backend does **not** expose OpenAPI docs yet.

Recommended:
- add `springdoc-openapi-starter-webmvc-ui`
- publish docs at `/swagger-ui.html` (or `/swagger-ui/index.html`)
- generate static JSON to version inside `docs/` for reviews

