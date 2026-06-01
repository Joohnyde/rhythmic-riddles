
# API (v1 contract)

**Swagger UI (local):** `http://localhost:8080/swagger-ui/index.html`

This is the **developer-facing runtime contract** for REST and asset endpoints.

WebSocket connection rules, recovery snapshots, pushed messages, and JSON schema contracts are documented in **[websockets.md](#websockets)**.

For detailed error codes and exception types, see **[exceptions.md](#exceptions)**.


# Protocols and base paths

## Base URL

```text
http://{HOST}:{PORT}
```

## REST base

```text
/api/v1
```

## Assets base

```text
/assets/v1
```

## E2E fixture API base

The fixture API is test infrastructure. It is available only when the backend runs with the `e2e` Spring profile.

```text
/api/e2e/v1
```

## WebSocket

```text
ws://{HOST}/ws/{pos}{roomCode}
```

For full WebSocket runtime behavior and message contracts, see:

```text
docs/developer-guide/websockets.md
```


# Naming conventions

## REST endpoint naming

Rules used across the project:

- Versioned base paths:
  - `/api/v1/...`
  - `/assets/v1/...`
  - `/api/e2e/v1/...` for test-only fixture infrastructure

- Test-only fixture resources:
  - `/api/e2e/v1/game-fixtures`
  - `/api/e2e/v1/game-fixtures/{roomCode}`

- Noun-based resources:
  - `/games`
  - `/teams`
  - `/categories`
  - `/schedules`
  - `/interrupts`

- Nested ownership:
  - `/games/{roomCode}/teams`
  - `/games/{roomCode}/categories/{categoryId}`

- Action subresources:
  - `/pick`
  - `/start`
  - `/replay`
  - `/reveal`
  - `/next`
  - `/answer`
  - `/resolve`

- Stable path identifiers:
  - UUID
  - roomCode

## Request/response JSON naming

JSON uses camelCase naming:

```json
{
  "answeringTeamId": "...",
  "previousScenario": 2
}
```


# Identifier formats

| Identifier | Type | Example |
|---|---|---|
| `roomCode` | string | 4 uppercase letters, e.g. `AKKU` |
| `songId` | UUID | RFC 4122 |
| `categoryId` | UUID | RFC 4122 |
| `scheduleId` | UUID | RFC 4122 |
| `teamId` | UUID | RFC 4122 |
| `answerId` | UUID | RFC 4122 (interrupt id used in answer endpoint) |


# Authentication

## Current behavior

REST endpoints currently work without JWT authentication.

WebSocket handshake is accepted only if:
- room exists
- socket position is valid (`0` or `1`)

## Planned behavior

Planned authentication flow:

1. frontend sends `roomCode + password`
2. backend returns JWT token
3. frontend stores token
4. reconnect uses existing token
5. invalid token returns client to login screen

WebSocket authentication should eventually validate the same token.


# Error handling

REST endpoints may throw domain exceptions intended for frontend consumption.

## Exception model

- domain exceptions extend `DerivedException`
- `DerivedException.toString()` produces a JSON response payload

Example:

```json
{
  "error":"E004 - App not reachable",
  "message":"TV app has to be connected to proceed"
}
```

| Field | Meaning |
|---|---|
| `error` | Stable error code + short title |
| `message` | Human-readable detail message |

For the full error catalog and HTTP mappings, see `exceptions.md`.


# Endpoint matrix

| Domain | Method | Path | Purpose |
|---|---|---|---|
| Games | POST | [`/api/v1/games`](#post-apiv1games) | Create a new game room |
| Games | PUT | [`/api/v1/games/{roomCode}/stage`](#put-apiv1gamesroomcodestage) | Change game stage |
| Teams | POST | [`/api/v1/games/{roomCode}/teams`](#post-apiv1gamesroomcodeteams) | Create a team |
| Teams | DELETE | [`/api/v1/games/{roomCode}/teams/{teamId}`](#delete-apiv1gamesroomcodeteamsteamid) | Kick a team |
| Categories | PUT | [`/api/v1/games/{roomCode}/categories/{categoryId}/pick`](#put-apiv1gamesroomcodecategoriescategoryidpick) | Pick an album/category |
| Categories | POST | [`/api/v1/games/{roomCode}/categories/{categoryId}/start`](#post-apiv1gamesroomcodecategoriescategoryidstart) | Start category (create schedules, start first song) |
| Schedules | POST | [`/api/v1/games/{roomCode}/schedules/{scheduleId}/replay`](#post-apiv1gamesroomcodeschedulesscheduleidreplay) | Replay snippet |
| Schedules | POST | [`/api/v1/games/{roomCode}/schedules/{scheduleId}/reveal`](#post-apiv1gamesroomcodeschedulesscheduleidreveal) | Reveal answer |
| Schedules | POST | [`/api/v1/games/{roomCode}/schedules/next`](#post-apiv1gamesroomcodeschedulesnext) | Next song / transition |
| Interrupts | POST | [`/api/v1/games/{roomCode}/interrupts`](#post-apiv1gamesroomcodeinterrupts) | Create interrupt (team buzz or system pause) |
| Interrupts | POST | [`/api/v1/games/{roomCode}/interrupts/{answerId}/answer`](#post-apiv1gamesroomcodeinterruptsansweridanswer) | Answer interrupt (correct/incorrect + scoring) |
| Interrupts | POST | [`/api/v1/games/{roomCode}/interrupts/system/resolve`](#post-apiv1gamesroomcodeinterruptssystemresolve) | Resolve system pauses |
| UI | PUT | [`/api/v1/games/{roomCode}/ui/scenario`](#put-apiv1gamesroomcodeuiscenario) | Persist UI scenario for recovery |
| Assets | GET | [`/assets/v1/audio/snippets/{songId}`](#get-assetsv1audiosnippetssongid) | Snippet MP3 |
| Assets | GET | [`/assets/v1/audio/answers/{songId}`](#get-assetsv1audioanswerssongid) | Answer MP3 |
| E2E fixtures | POST | [`/api/e2e/v1/game-fixtures`](#post-apie2ev1game-fixtures) | Create a deterministic game fixture for browser tests |
| E2E fixtures | DELETE | [`/api/e2e/v1/game-fixtures/{roomCode}`](#delete-apie2ev1game-fixturesroomcode) | Delete a fixture room and dependent runtime state |



# REST endpoint details

## POST /api/v1/games

Creates a game room.

Request:

```json
{
  "maxSongs": 10,
  "maxAlbums": 10
}
```

Response:

```json
{
  "roomCode": "AKKU"
}
```


## PUT /api/v1/games/{roomCode}/stage

Changes game stage. (UI updates are broadcast via WebSocket.)

Request:

```json
{
  "stageId": 1
}
```

Response:
- `200 OK`

WS side-effect:
- clients may receive fresh `welcome` snapshot


## POST /api/v1/games/{roomCode}/teams

Creates a new team.

Request:

```json
{
  "name":"Team Cyan",
  "buttonCode":"BTN-001",
  "image":"https://example.com/team.png"
}
```

Response:

```json
{
  "id":"uuid",
  "name":"Team Cyan",
  "image":"https://example.com/team.png"
}
```

WS side-effect:
- TV receives `new_team`


## DELETE /api/v1/games/{roomCode}/teams/{teamId}

Removes a team.

Response:
- `200 OK`

WS side-effect:
- TV receives `kick_team`


## PUT /api/v1/games/{roomCode}/categories/{categoryId}/pick

Picks an album/category.

Request:

```json
{
  "teamId":"uuid|null"
}
```

Response:
- `LastCategory` preview object

WS side-effect:
- TV receives `album_picked`


## POST /api/v1/games/{roomCode}/categories/{categoryId}/start

Starts category flow:
- selects tracks
- creates schedules
- starts song stage

Response:
- `200 OK`

WS side-effect:
- clients receive fresh `welcome` snapshot


## POST /api/v1/games/{roomCode}/schedules/{scheduleId}/replay

Replays snippet.

Response:
- `200 OK`

WS side-effect:
- broadcast `song_repeat`


## POST /api/v1/games/{roomCode}/schedules/{scheduleId}/reveal

Reveals answer.

Response:
- `200 OK`

WS side-effect:
- broadcast `song_reveal`


## POST /api/v1/games/{roomCode}/schedules/next

Starts next song or transitions stage.

Response:
- `200 OK`

WS side-effect:
- broadcast `song_next`
- or fresh `welcome` snapshot on stage transition


## POST /api/v1/games/{roomCode}/interrupts

Creates interrupt.

Request:

```json
{
  "teamId":"uuid|null"
}
```

Behavior:
- teamId present → team buzz
- teamId null → system pause

Response:
- `200 OK`

WS side-effect:
- broadcast `pause`


## POST /api/v1/games/{roomCode}/interrupts/{answerId}/answer

Resolves answer correctness.

Request:

```json
{
  "correct": true
}
```

Response:
- `200 OK`

WS side-effect:
- broadcast `answer`


## POST /api/v1/games/{roomCode}/interrupts/system/resolve

Resolves system pause/error.

Request:

```json
{
  "scheduleId":"uuid"
}
```

Response:
- `200 OK`

WS side-effect:
- broadcast `error_solved`


## PUT /api/v1/games/{roomCode}/ui/scenario

Persists current UI scenario for reconnect recovery.

Request:

```json
{
  "scenario": 2
}
```

Response:
- `200 OK`


# Asset endpoints

## GET /assets/v1/audio/snippets/{songId}

Returns snippet MP3.

Headers:

```text
Content-Type: audio/mpeg
Accept-Ranges: bytes
```


## GET /assets/v1/audio/answers/{songId}

Returns full answer MP3.

Headers:

```text
Content-Type: audio/mpeg
Accept-Ranges: bytes
```


# E2E fixture API endpoint details

The E2E fixture API is test infrastructure. It is available only when the backend runs with the `e2e` Spring profile.

```text
/api/e2e/v1/game-fixtures
```

These endpoints are not part of the production runtime contract. They exist so Playwright tests can create isolated deterministic rooms instead of relying on shared mutable rooms such as `SONG`, `ALBM`, or `WINR`.

The fixture API should be used through frontend test helpers whenever possible:

- `withGameFixture(...)`
- `withDeterministicFixture(...)`
- `createGameFixture(...)`
- `deleteGameFixture(...)`

For the full seeded E2E testing guide, fixture invariants, interrupt rules, and authoring rules, see:

```text
docs/developer-guide/testing/e2e.md
docs/developer-guide/testing/writing-tests.md
```

## POST /api/e2e/v1/game-fixtures

Creates a deterministic game fixture for browser-level E2E tests.

This endpoint persists a complete game graph into the active E2E schema. The created room can immediately be opened by real Admin and TV browser clients through the normal frontend login flow. Tests then trigger normal REST actions and assert the WebSocket frames delivered to those browser clients.

### Request body

High-level shape:

```json
{
  "id": "uuid",
  "roomCode": "TST1",
  "maxSongs": 2,
  "maxAlbums": 3,
  "stage": 2,
  "teams": [],
  "categories": []
}
```

Field summary:

| Field | Type | Required | Meaning |
|---|---:|---:|---|
| `id` | UUID | yes | Game id to persist. Tests usually generate this. |
| `roomCode` | string | yes | Room code used by Admin/TV clients. Must be unique for the test. |
| `maxSongs` | integer | yes | Number of songs selected per picked album. |
| `maxAlbums` | integer | yes | Number of albums/categories used by the game. |
| `stage` | integer | yes | Game stage to seed. Common values: lobby, albums, songs, winner. |
| `teams` | array | yes | Teams belonging to the room. |
| `categories` | array | yes | Categories/albums/tracks/schedules for the room. |

### Team objects

```json
{
  "id": "uuid",
  "buttonCode": "BTN-A",
  "name": "Team A",
  "image": "https://example.com/team-a.png"
}
```

| Field | Meaning |
|---|---|
| `id` | Team UUID. |
| `buttonCode` | Hardware/button identifier used by the game. |
| `name` | Display name. |
| `image` | Optional image URL used by the frontend. |

### Category objects

```json
{
  "id": "uuid",
  "pickedByTeamId": "uuid",
  "ordinalNumber": 1,
  "done": false,
  "album": {}
}
```

| Field | Meaning |
|---|---|
| `id` | Category UUID. |
| `pickedByTeamId` | Team that picked this album, or `null` if it was an admin. |
| `ordinalNumber` | Pick order. `null` means the category has not been chosen yet. |
| `done` | Whether all scheduled tracks for the category have been completed. |
| `album` | Album payload with tracks. |

Important: `ordinalNumber` is what distinguishes chosen categories from unchosen categories. A game can have many albums while only some have been chosen. Unchosen albums should use `ordinalNumber = null`.

### Album and track objects

```json
{
  "id": "uuid",
  "name": "Album A",
  "customQuestion": "Album question",
  "tracks": [
    {
      "customAnswer": "Answer 1",
      "schedule": {}
    }
  ]
}
```

Track payloads are intentionally small in the current fixture API. Tests should not send obsolete song metadata fields.

| Field | Meaning |
|---|---|
| `customAnswer` | Answer text for the track. |
| `schedule` | Schedule object if this track has already been scheduled, otherwise `null`. |

Audio files are not uploaded through this endpoint. E2E tests can reuse stable test audio or application defaults. The fixture should point the game state at deterministic tracks/schedules; it should not test audio storage.

### Schedule objects

```json
{
  "id": "uuid",
  "trackId": "uuid",
  "startedAt": "2026-06-01T12:00:00",
  "revealedAt": null,
  "ordinalNumber": 1,
  "interrupts": []
}
```

| Field | Meaning |
|---|---|
| `id` | Schedule UUID. |
| `trackId` | Track UUID associated with the schedule. |
| `startedAt` | Backend-local `LocalDateTime` when playback started, or `null` if not started. |
| `revealedAt` | Backend-local `LocalDateTime` when answer was revealed, or `null`. |
| `ordinalNumber` | Song order inside the picked category. |
| `interrupts` | Historical or ongoing interrupts for the schedule. |

For Stage-2 listening fixtures, at least one schedule should have `startedAt != null`. At most one schedule should have `startedAt != null` and `revealedAt == null`.

Do not generate timestamps with `new Date(...).toISOString().slice(0, 19)` if the backend expects local `LocalDateTime`. That turns a UTC instant into a fake local timestamp. Use local date-time formatting.

### Interrupt objects

```json
{
  "id": "uuid",
  "teamId": null,
  "arrivedAt": "2026-06-01T12:00:01",
  "resolvedAt": null,
  "correct": null,
  "score": null,
  "scenario": 4
}
```

| Field | Meaning |
|---|---|
| `id` | Interrupt UUID. |
| `teamId` | Team UUID for a team buzz, or `null` for a system pause/crash. |
| `arrivedAt` | When the interrupt started. Required for every interrupt. |
| `resolvedAt` | When the interrupt was resolved, or `null` if still ongoing. |
| `correct` | Team answer correctness after resolution. Only meaningful for resolved team interrupts. |
| `score` | Team score after answering. Only meaningful for team interrupts. |
| `scenario` | Previous/recoverable UI scenario for system interrupts. |

Interrupt fixture rules are strict because invalid interrupt states create misleading browser tests:

- every interrupt must have `arrivedAt`;
- team interrupts must have `teamId`;
- system interrupts must have `teamId = null`;
- at most one ongoing team interrupt can exist;
- if an ongoing team interrupt and ongoing system interrupts coexist, the team interrupt must have arrived first;
- exactly one unresolved system interrupt should carry a valid scenario marker;
- valid recoverable system scenarios are `0`, `1`, `2`, and `4`;
- scenario `3` should not be used as a recoverable song substate;
- team interrupts should not carry `scenario`;
- when a team answer resolves a layered state, the team interrupt and layered system interrupts should resolve at the same `resolvedAt`.

### Example request: songs/listening room

```json
{
  "id": "7f8ef2e7-9adc-4f31-8f7e-6f8e5d2cf201",
  "roomCode": "TST1",
  "maxSongs": 2,
  "maxAlbums": 3,
  "stage": 2,
  "teams": [
    {
      "id": "0cf4d245-e90b-40db-a9ff-6671f33d8f0f",
      "buttonCode": "BTN-A",
      "name": "Team A",
      "image": "https://example.com/team-a.png"
    },
    {
      "id": "7e9e1af4-f91b-46a2-b0dd-07cb7c8e7f91",
      "buttonCode": "BTN-B",
      "name": "Team B",
      "image": "https://example.com/team-b.png"
    }
  ],
  "categories": [
    {
      "id": "db57930d-5418-4554-b6f9-df10333db873",
      "pickedByTeamId": "0cf4d245-e90b-40db-a9ff-6671f33d8f0f",
      "ordinalNumber": 1,
      "done": false,
      "album": {
        "id": "76b2e2de-8be8-43a4-94e2-37559d08bb5f",
        "name": "E2E Album 1",
        "customQuestion": "E2E Album Question",
        "tracks": [
          {
            "customAnswer": "E2E Answer 1",
            "schedule": {
              "id": "f3b6f82f-83e9-46d0-ad75-8468a63ee8ff",
              "trackId": "a2ed9d43-86d5-42a2-b961-6d72bb66ea54",
              "startedAt": "2026-06-01T12:00:00",
              "revealedAt": null,
              "ordinalNumber": 1,
              "interrupts": []
            }
          }
        ]
      }
    }
  ]
}
```

### Response

Successful response:

```text
200 OK
```

### Error behavior

| Status | Meaning |
|---|---|
| `400 Bad Request` | Fixture payload is structurally invalid or violates validator rules. |
| `500 Internal Server Error` | Usually means fixture infrastructure failed: invalid persistence order, missing parent entity, foreign-key violation, schema mismatch, or cascade issue. |

A `500` from this endpoint should normally be treated as a test-infrastructure bug, not as an expected application outcome.

## DELETE /api/e2e/v1/game-fixtures/{roomCode}

Deletes a deterministic fixture room and dependent runtime state.

This endpoint is the cleanup pair for `POST /api/e2e/v1/game-fixtures`. It allows every destructive Playwright test to leave the E2E schema clean for the next test.

### Path parameters

| Name | Type | Meaning |
|---|---|---|
| `roomCode` | string | The seeded room code to delete. |

Example:

```text
DELETE /api/e2e/v1/game-fixtures/TST1
```

### Response

Successful response:

```text
200 OK
```


# WebSocket reference

REST endpoints in this document may trigger WebSocket side-effects.

The canonical WebSocket protocol documentation is:

```text
docs/developer-guide/websockets.md
```

That document contains:
- connection endpoint and slot rules
- welcome recovery snapshots
- runtime message catalog
- field explanations
- reconnect/disconnect behavior
- JSON schema contracts
- schema versioning rules
- protocol governance guidelines
