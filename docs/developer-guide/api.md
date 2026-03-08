# API (v1 contract)
**Swagger UI (local):** `http://localhost:8080/swagger-ui/index.html`

This is the **developer-facing runtime contract** for a small team (REST + Assets + WebSocket).  
For detailed error codes and exception types, see **[exceptions.md](#exceptions)**.



## Protocols and base paths

**Base URL**
```
http://{HOST}:{PORT}
```

**REST base**
```
/api/v1
```

**Assets base**
```
/assets/v1
```

**WebSocket**
```
ws://{HOST}/ws/{pos}{roomCode}
```


## Naming conventions

### REST endpoint naming
Rules used across the project:

- **Versioned base path**: `/api/v1/...`, `/assets/v1/...`
- **Nouns, plural collections**: `/games`, `/teams`, `/categories`, `/schedules`, `/interrupts`
- **Hierarchy for ownership** (resource nesting):
  - `/games/{roomCode}/teams`
  - `/games/{roomCode}/categories/{categoryId}`
- **Actions as sub-resources** (imperative verbs only where needed):
  - `/pick`, `/start`, `/replay`, `/reveal`, `/next`, `/answer`, `/resolve`
- **Path identifiers are stable IDs** (UUID or roomCode)
- **HTTP verbs**
  - `POST` → create/trigger action
  - `PUT` → update/replace state (e.g., stage, scenario)
  - `DELETE` → remove resource

### Request/response JSON naming
- Request/response fields are **camelCase** (e.g., `answeringTeamId`, `previousScenario`).

### Identifier formats
| Identifier | Type | Rule / example |
|---|---|---|
| `roomCode` | string | 4 uppercase letters, e.g. `AKKU` |
| `songId` | UUID | RFC 4122 |
| `categoryId` | UUID | RFC 4122 |
| `scheduleId` | UUID | RFC 4122 |
| `teamId` | UUID | RFC 4122 |
| `answerId` | UUID | RFC 4122 (interrupt id used in answer endpoint) |



## Authentication (current + planned)

### Current behavior
- REST endpoints are currently callable without JWT.
- WebSocket handshake is accepted only if:
  - the `roomCode` exists
  - the slot prefix (`pos`) is valid: `0` or `1`

### Planned behavior
- Login form: `roomCode` + `password`
- Backend returns a JWT token
- Client stores token (local/session storage), refresh uses token; invalid token → login screen
- WebSocket should validate the same token (recommended)



## Error handling

REST endpoints can throw **domain exceptions** that are meant to be returned to the frontend.

### How it works in code
- Domain exceptions extend `DerivedException`
- `DerivedException.toString()` produces a JSON **string payload** used as the response body

### Error response shape
```json
{"error":"E004 - App not reachable" ,"message":"TV app has to be connected to proceed"}
```

- `error` = stable code + short title (frontend-friendly)
- `message` = detail string (debug/UX)

➡️ For the **full list of error codes**, mapping to HTTP status codes, and each exception type, see **exceptions.md**.



## Endpoint matrix

> Click an endpoint to jump to details.

| Domain | Method | Path | Purpose |
|---|---:|---|---|
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

---

## REST endpoint details

### POST /api/v1/games
Creates a game instance and returns a new room code.

Request:
```json
{ "maxSongs": 10, "maxAlbums": 10 }
```

Response:
```json
{ "roomCode": "AKKU" }
```

---

### PUT /api/v1/games/{roomCode}/stage
Changes game stage. (UI updates are broadcast via WebSocket.)

Request:
```json
{ "stageId": 1 }
```

Response: `200` (empty)

---

### POST /api/v1/games/{roomCode}/teams
Creates a team (lobby stage).

Request:
```json
{ "name":"Team Cyan", "buttonCode":"BTN-001", "image":"https://example.com/team.png" }
```

Response:
```json
{ "id":"<uuid>", "name":"Team Cyan", "image":"https://example.com/team.png" }
```

WS side-effect:
- TV receives `new_team`

---

### DELETE /api/v1/games/{roomCode}/teams/{teamId}
Kicks a team (lobby stage).

Response: `200` (empty)

WS side-effect:
- TV receives `kick_team`

---

### PUT /api/v1/games/{roomCode}/categories/{categoryId}/pick
Picks an album/category to be played next.

Request:
```json
{ "teamId":"<uuid|null>" }
```

Response: `LastCategory` (selection preview)

WS side-effect:
- TV receives `album_picked`

---

### POST /api/v1/games/{roomCode}/categories/{categoryId}/start
Starts category:
- selects tracks
- creates schedules
- starts stage-2 playback flow (broadcasts fresh context snapshot via WS)

Response: `200` (empty)

---

### POST /api/v1/games/{roomCode}/schedules/{scheduleId}/replay
Replays the snippet for the schedule.

Response: `200` (empty)

WS side-effect:
- broadcast `song_repeat`

---

### POST /api/v1/games/{roomCode}/schedules/{scheduleId}/reveal
Reveals the answer for the schedule.

Response: `200` (empty)

WS side-effect:
- broadcast `song_reveal`

---

### POST /api/v1/games/{roomCode}/schedules/next
Advances to the next song or transitions out of the category/game.

Response: `200` (empty)

WS side-effect:
- broadcast `song_next` when a next schedule exists
- otherwise broadcast fresh context snapshot (`type:"welcome"`) for the new stage

---

### POST /api/v1/games/{roomCode}/interrupts
Creates an interrupt:
- `teamId` present → team buzz
- `teamId` null → system pause

Request:
```json
{ "teamId":"<uuid|null>" }
```

Response: `200` (empty)

WS side-effect:
- broadcast `pause`

---

### POST /api/v1/games/{roomCode}/interrupts/{answerId}/answer
Resolves a team interrupt as correct/incorrect (scoring + potential reveal).

Request:
```json
{ "correct": true }
```

Response: `200` (empty)

WS side-effect:
- broadcast `answer`

---

### POST /api/v1/games/{roomCode}/interrupts/system/resolve
Resolves all unresolved system pauses for a schedule.

Request:
```json
{ "scheduleId":"<uuid>" }
```

Response: `200` (empty)

WS side-effect:
- broadcast `error_solved`

---

### PUT /api/v1/games/{roomCode}/ui/scenario
Persists UI scenario for recovery (stored on the most recent system interrupt).

Request:
```json
{ "scenario": 2 }
```

Response: `200` (empty)



## Asset endpoints

### GET /assets/v1/audio/snippets/{songId}
Returns snippet MP3 as `audio/mpeg` with:
```
Accept-Ranges: bytes
```

---

### GET /assets/v1/audio/answers/{songId}
Returns answer MP3 as `audio/mpeg` with:
```
Accept-Ranges: bytes
```



## WebSocket

### Endpoint
```
ws://{HOST}/ws/{pos}{roomCode}
```

- `pos=0` → ADMIN
- `pos=1` → TV

### Bootstrap message
On connect, server sends a **context snapshot**:
- `type: "welcome"`
- `stage: "lobby" | "albums" | "songs" | "winner"`
- plus stage-specific fields

### Message catalog (events)
All messages contain `type`.

- `new_team` (TV)
- `kick_team` (TV)
- `album_picked` (TV)
- `song_next` (Admin + TV)
- `song_repeat` (Admin + TV)
- `song_reveal` (Admin + TV)
- `pause` (Admin + TV)
- `answer` (Admin + TV)
- `error_solved` (Admin + TV)

## WebSocket

### Endpoint and routing

WS endpoint:
```
ws://{HOST}/ws/{pos}{roomCode}
```

- `pos=0` → **ADMIN**
- `pos=1` → **TV**

Examples:
```
ws://localhost:8080/ws/0AKKU  (Admin)
ws://localhost:8080/ws/1AKKU  (TV)
```

Handshake is rejected if:
- room does not exist
- pos is not `0` or `1`

### Session policy

Per room, **only one session per client type** is accepted.  
If a slot is already occupied, a new connection is rejected and closed.

### Bootstrap message (on connect)

Immediately after connect, server sends a **context snapshot** built by `GameService.contextFetch(roomCode)`.

This snapshot always includes:
- `type: "welcome"`
- `stage: "lobby" | "albums" | "songs" | "winner"`
- plus stage-specific fields

#### Context snapshot: lobby (`stage="lobby"`)
```json
{
  "type":"welcome",
  "stage":"lobby",
  "teams":[ { "id":"...", "name":"...", "image":"..." } ]
}
```

#### Context snapshot: albums (`stage="albums"`)
Possible shapes:

**A) Selecting a new album**
```json
{
  "type":"welcome",
  "stage":"albums",
  "albums":[ ... ],
  "team": { "id":"...", "name":"...", "image":"..." }
}
```
`team` is the next choosing team (may be `null`).

**B) Album picked but not started (choice display)**
```json
{
  "type":"welcome",
  "stage":"albums",
  "selected": {
    "categoryId":"...",
    "chosenCategoryPreview": { "title":"...", "image":"..." },
    "pickedByTeam": { "id":"...", "name":"...", "image":"..." },
    "started":false,
    "ordinalNumber":1
  }
}
```

#### Context snapshot: songs (`stage="songs"`)
Common fields are always present:

```json
{
  "type":"welcome",
  "stage":"songs",
  "songId":"...",
  "question":"Prepoznaj ovu pjesmu!",
  "answer":"...",
  "scheduleId":"...",
  "answerDuration": 8.0,
  "scores":[ ... ]
}
```

Additional fields depend on current state:

- **Post-song revealed**
  - `revealed: true`
  - `bravo: "<teamId|null>"` (team who answered correctly)

- **Snippet finished but not revealed yet**
  - `revealed: false`

- **Snippet playing (not finished)**
  - `seek: <seconds>`
  - `remaining: <seconds>`

- **Team currently answering**
  - `answeringTeam: { id, name, image }`
  - `interruptId: "<uuid>"`

- **System pause active**
  - `error: true`

#### Context snapshot: winner (`stage="winner"`)
```json
{
  "type":"welcome",
  "stage":"winner",
  "scores":[ ... ]
}
```

---

### Message catalog

All events are JSON text frames with a `type` field.

> Click a message type to jump to details.

| Category | Type | Purpose |
|---|---|---|
| Teams | [`new_team`](#ws-new_team) | Team created (TV) |
| Teams | [`kick_team`](#ws-kick_team) | Team removed (TV) |
| Albums | [`album_picked`](#ws-album_picked) | Album/category picked (TV) |
| Songs | [`song_next`](#ws-song_next) | Next song started (Admin + TV) |
| Songs | [`song_repeat`](#ws-song_repeat) | Snippet replay triggered (Admin + TV) |
| Songs | [`song_reveal`](#ws-song_reveal) | Answer reveal triggered (Admin + TV) |
| Interrupts | [`pause`](#ws-pause) | Team buzz or system pause (Admin + TV) |
| Interrupts | [`answer`](#ws-answer) | Guess resolved (Admin + TV) |
| Interrupts | [`error_solved`](#ws-error_solved) | System pause resolved (Admin + TV) |

---

### Message details

<a id="ws-new_team"></a>
#### `new_team`
Sent to TV when a team is created.
```json
{ "type":"new_team", "team": { "id":"...", "name":"...", "image":"..." } }
```

<a id="ws-kick_team"></a>
#### `kick_team`
Sent to TV when a team is removed.
```json
{ "type":"kick_team", "uuid":"<teamId>" }
```

<a id="ws-album_picked"></a>
#### `album_picked`
Sent to TV when a category is picked.
```json
{ "type":"album_picked", "selected": <LastCategory> }
```

<a id="ws-song_next"></a>
#### `song_next`
Broadcast when next song starts.
```json
{
  "type":"song_next",
  "songId":"...",
  "question":"...",
  "answer":"...",
  "scheduleId":"...",
  "answerDuration": 8.0,
  "remaining": 15.0
}
```
`remaining` is set to the full snippet duration.

<a id="ws-song_repeat"></a>
#### `song_repeat`
Broadcast when replay is triggered.
```json
{ "type":"song_repeat", "remaining": 15.0 }
```

<a id="ws-song_reveal"></a>
#### `song_reveal`
Broadcast when reveal is triggered.
```json
{ "type":"song_reveal" }
```

> Stage transitions (e.g., 1→2, 2→1, 2→3) are broadcast as a **fresh context snapshot** (`type:"welcome"`).

<a id="ws-pause"></a>
#### `pause`
Broadcast when a team buzzes or the system pauses the game.
```json
{
  "type":"pause",
  "answeringTeamId":"<teamId|null>",
  "interruptId":"<interruptId>"
}
```
`answeringTeamId=null` means **system** interrupt.

<a id="ws-answer"></a>
#### `answer`
Broadcast when admin resolves a guess as correct/incorrect.
```json
{
  "type":"answer",
  "teamId":"<teamId>",
  "scheduleId":"<scheduleId>",
  "correct": true
}
```

<a id="ws-error_solved"></a>
#### `error_solved`
Broadcast when system errors are resolved.
```json
{
  "type":"error_solved",
  "previousScenario": 2
}
```

---

### Disconnect behavior (stage-2 safety)

When a socket closes:
- If stage is **2** and close code is **not** `1000` (SPA navigation), the backend triggers a **system interrupt** (`teamId=null`) to pause the game.
- Close code `1000` is treated as normal navigation and does not trigger pause.
