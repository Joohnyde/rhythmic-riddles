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
