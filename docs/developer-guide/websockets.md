# WebSocket API

This document describes the runtime WebSocket contract between the backend, Admin app, and TV app.

REST endpoints are documented in `api.md`. This file focuses only on WebSocket connection rules, recovery snapshots, and pushed messages.

## Endpoint

```text
ws://{HOST}/ws/{pos}{roomCode}
```

Examples:

```text
ws://localhost:8080/ws/0AKKU
ws://localhost:8080/ws/1AKKU
```

| Part       | Meaning                                     |
| ---------- | ------------------------------------------- |
| `pos=0`    | Admin client                                |
| `pos=1`    | TV client                                   |
| `roomCode` | 4-letter game room code, for example `AKKU` |

The backend rejects the handshake if:

- the room does not exist
- the position is not `0` or `1`
- the same room already has an active socket for that position

Only one Admin socket and one TV socket are expected per room.

## Message basics

All messages are JSON text frames.

Every message has a `type` field:

```json
{
    "type": "song_next"
}
```

There are two broad groups of messages:

| Group               | Description                                                                 |
| ------------------- | --------------------------------------------------------------------------- |
| `welcome` snapshots | Full state snapshots used on connect, reconnect, and some stage transitions |
| runtime events      | Smaller events sent because something happened during the game              |

The frontend should treat `welcome` as a state recovery message. Runtime events should usually update only the part of UI related to that event.

## Message catalog overview

| Type             | Sent to    | When it is sent                                                                       |
| ---------------- | ---------- | ------------------------------------------------------------------------------------- |
| `welcome`        | Admin + TV | Immediately after successful connect/reconnect, and sometimes after stage transitions |
| `new_team`       | TV         | A new team joins the lobby                                                            |
| `kick_team`      | TV         | A team is removed from the game                                                       |
| `button_clicked` | Admin      | An unassigned hardware buzzer is pressed in the lobby                                 |
| `album_picked`   | TV         | Admin/team picks an album/category                                                    |
| `song_next`      | Admin + TV | Next scheduled song starts                                                            |
| `song_repeat`    | Admin + TV | Snippet replay is triggered                                                           |
| `song_reveal`    | Admin + TV | Current song answer should be revealed                                                |
| `pause`          | Admin + TV | Team buzz or system pause happens                                                     |
| `answer`         | Admin + TV | Admin marks a team answer as correct/incorrect                                        |
| `error_solved`   | Admin + TV | System pause/error is resolved and frontend should restore previous scenario          |

# `welcome` message

`welcome` is the most important WebSocket message.

It is not just a greeting. It is a complete context snapshot used by the frontend to recover current game state.

It is sent:

- after Admin connects
- after TV connects
- after reconnect
- when backend wants frontend to rebuild current state after a stage transition

The exact fields depend on the current game stage.

## `welcome` in lobby stage

Example:

```json
{
    "type": "welcome",
    "stage": "lobby",
    "teams": [
        {
            "id": "1a8f01ef-77c8-41e5-a561-3061ee2216c1",
            "name": "Team Cyan",
            "image": "https://example.com/team-cyan.png"
        }
    ]
}
```

| Field   | Type   | Meaning                                 |
| ------- | ------ | --------------------------------------- |
| `type`  | string | Always `welcome`                        |
| `stage` | string | Current stage, here `lobby`             |
| `teams` | array  | Teams currently registered in the lobby |

Team object:

| Field   | Type        | Meaning                                                      |
| ------- | ----------- | ------------------------------------------------------------ |
| `id`    | UUID string | Team id                                                      |
| `name`  | string      | Team display name                                            |
| `image` | string/null | Team image URL or empty/null value depending on current data |

## `welcome` in albums stage

The albums stage is used both while choosing the next album/category and while displaying a choice
that has been picked but not started yet.

Every albums-stage `welcome` contains the complete `albums` array. The `team` and `selected` fields
identify the current Stage 1 sub-state and are mutually exclusive during normal gameplay.
The backend snapshot is authoritative for album membership and picked metadata, but the frontend
normalizes the `albums` collection into one canonical deterministic order before rendering so visual
positions remain stable across refresh and recovery. `stableAlbumOrder` uses canonicalized album name
and category id tie-breaking; raw backend collection order is intentionally not a visual requirement.

Example while choosing:

```json
{
    "type": "welcome",
    "stage": "albums",
    "albums": [
        {
            "id": "329144f2-2f14-4c92-97aa-ef20acfdc561",
            "name": "YU Rock",
            "image": "7d26d0ea-dbc1-44fe-976f-88186f86a3aa",
            "pickedByTeam": null,
            "ordinalNumber": null
        },
        {
            "id": "4a59084a-6d64-4583-b56a-e7016ad5939d",
            "name": "Eurovision",
            "image": "bb7e6e7d-6c0a-4f64-a536-f7476ee38ddd",
            "pickedByTeam": "https://example.com/team-cyan.png",
            "ordinalNumber": 1
        }
    ],
    "team": null
}
```

| Field      | Type        | Meaning                                                                         |
| ---------- | ----------- | ------------------------------------------------------------------------------- |
| `type`     | string      | Always `welcome`                                                                |
| `stage`    | string      | Current stage, here `albums`                                                    |
| `albums`   | array       | Complete prepared album/category list for the game                              |
| `team`     | object/null | Present in the picker sub-state; `null` means Admin chooses                     |
| `selected` | object      | Present in the picked-but-not-started sub-state; mutually exclusive with `team` |

Album/category object:

| Field           | Type        | Meaning                                                                                                                                |
| --------------- | ----------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `id`            | UUID string | Category id used for category actions                                                                                                  |
| `name`          | string      | Album display name                                                                                                                     |
| `image`         | UUID string | Album UUID; the matching cover is stored as `<albumId>.<supported extension>` and resolved through `/assets/v1/image/albums/{albumId}` |
| `pickedByTeam`  | string/null | Picker team image value for an already chosen album; `null` when Admin chose it                                                        |
| `ordinalNumber` | number/null | Choice order; `null` means the category is still available                                                                             |

Important rules:

- `albums` is present in every Stage 1 recovery snapshot.
- `ordinalNumber = null` means the album/category is still available.
- `pickedByTeam = null` on an already chosen entry means Admin selected it.
- `team = null` means it is Admin's choice.
- `team = { ... }` means that team currently has the right to choose.
- `selected = { ... }` means a category has been picked but has not started yet. Its `ordinalNumber` is a concrete integer, never `null`.
- `team` and `selected` are mutually exclusive; the strict JSON schema rejects snapshots containing both.
- A defensive stale final-album snapshot may contain neither `team` nor `selected`, but still contains `albums`.
- Album image files are stored using the album UUID as the basename (for example `<albumId>.png` or `<albumId>.webp`); the image endpoint receives that UUID and resolves the stored format.

Example when an album has already been picked but not started:

```json
{
    "type": "welcome",
    "stage": "albums",
    "albums": [
        {
            "id": "329144f2-2f14-4c92-97aa-ef20acfdc561",
            "name": "YU Rock",
            "image": "7d26d0ea-dbc1-44fe-976f-88186f86a3aa",
            "pickedByTeam": null,
            "ordinalNumber": 1
        }
    ],
    "selected": {
        "categoryId": "329144f2-2f14-4c92-97aa-ef20acfdc561",
        "chosenCategoryPreview": {
            "title": "YU Rock",
            "image": "7d26d0ea-dbc1-44fe-976f-88186f86a3aa"
        },
        "pickedByTeam": null,
        "started": false,
        "ordinalNumber": 1
    }
}
```

Selected object:

| Field                         | Type        | Meaning                                                                                   |
| ----------------------------- | ----------- | ----------------------------------------------------------------------------------------- |
| `categoryId`                  | UUID string | Picked category id                                                                        |
| `chosenCategoryPreview`       | object      | Small display object for the picked album/category                                        |
| `chosenCategoryPreview.image` | UUID string | Album UUID used to locate the corresponding stored cover through the album-image endpoint |
| `pickedByTeam`                | object/null | Team that picked it; `null` means Admin picked it                                         |
| `started`                     | boolean     | Whether the category already started                                                      |
| `ordinalNumber`               | number      | Display order/round number                                                                |

## `welcome` in songs stage

The songs stage is the active gameplay stage. This snapshot tells the frontend which song is active, what should be displayed, what timers exist, whether an answer is already revealed, and whether the game is paused.

Basic example:

```json
{
    "type": "welcome",
    "stage": "songs",
    "songId": "c41f1c21-0fec-463a-b409-e1e7ab9d2229",
    "question": "Prepoznaj ovu pjesmu!",
    "answer": "Answer",
    "scheduleId": "b510f158-34f4-4102-ac8f-6088a3b6d7b9",
    "answerDuration": 8.0,
    "scores": [
        {
            "teamId": "e94c5243-6954-452b-8fd2-f94210fcc13b",
            "image": "team.png",
            "name": "Team A",
            "score": 10,
            "scheduleId": "b510f158-34f4-4102-ac8f-6088a3b6d7b9"
        }
    ],
    "seek": 1.0,
    "remaining": 14.0,
    "revealed": false
}
```

| Field            | Type        | Meaning                                                       |
| ---------------- | ----------- | ------------------------------------------------------------- |
| `type`           | string      | Always `welcome`                                              |
| `stage`          | string      | Current stage, here `songs`                                   |
| `songId`         | UUID string | Current song id                                               |
| `question`       | string      | Question shown for this song                                  |
| `answer`         | string      | Correct answer, already known to frontend for reveal/recovery |
| `scheduleId`     | UUID string | Current schedule entry id                                     |
| `answerDuration` | number      | How long answer phase lasts                                   |
| `scores`         | array       | Current score table                                           |
| `seek`           | number      | Seek seconds, when snippet is still playing                   |
| `remaining`      | number      | Remaining snippet seconds, when snippet is still playing      |
| `revealed`       | boolean     | Whether answer is already revealed                            |
| Scores object:   |

| Field        | Type        | Meaning                                         |
| ------------ | ----------- | ----------------------------------------------- |
| `teamId`     | UUID string | ID of the team                                  |
| `image`      | string      | Icon of the team                                |
| `name`       | string      | Name of the team                                |
| `score`      | number      | Current score of the team                       |
| `scheduleId` | UUID/null   | ID of the last schedule when the team buzzed in |

### Song still playing

When snippet is currently playing, the snapshot may include:

```json
{
    "seek": 4.2,
    "remaining": 10.8
}
```

| Field       | Type   | Meaning                                                  |
| ----------- | ------ | -------------------------------------------------------- |
| `seek`      | number | How many seconds into the snippet playback should resume |
| `remaining` | number | Remaining snippet seconds                                |

The frontend can use these fields after reconnect to continue playback close to the current server state.

### Snippet finished, answer not revealed

```json
{
    "revealed": false
}
```

This means the snippet phase is over, but the answer is not visible yet.

### Answer already revealed

```json
{
    "revealed": true,
    "bravo": "1a8f01ef-77c8-41e5-a561-3061ee2216c1"
}
```

| Field      | Type                             | Meaning                                  |
| ---------- | -------------------------------- | ---------------------------------------- |
| `revealed` | boolean                          | Answer is visible                        |
| `bravo`    | UUID string/null/string `"null"` | Team that answered correctly, or no team |

### Team is answering

When a team buzzes, the snapshot may include:

```json
{
    "answeringTeamId": "946a6c8f-f2eb-4aec-85a9-4bd0c13d9d19",
    "interruptId": "94a9f9fd-daa5-4ee7-9440-11a44b171e3a"
}
```

| Field             | Type             | Meaning                                 |
| ----------------- | ---------------- | --------------------------------------- |
| `answeringTeamId` | UUID string/null | Team currently answering                |
| `interruptId`     | UUID string      | Interrupt id used when resolving answer |

### System pause / error state

When the game is paused because of system behavior, the snapshot may include:

```json
{
    "error": true
}
```

A system pause is represented by an interrupt where the answering team id is `null`.

This is different from a normal team buzz:

- team buzz → interrupt has a answeringTeamId
- system pause/error → interrupt's answeringTeamId is `null`

System pause can happen after unexpected disconnects or other backend safety conditions.

## `welcome` in winner stage

Example:

```json
{
    "type": "welcome",
    "stage": "winner",
    "scores": [
        {
            "teamId": "1a8f01ef-77c8-41e5-a561-3061ee2216c1",
            "image": "team.png",
            "name": "Team A",
            "score": 10,
            "scheduleId": "b510f158-34f4-4102-ac8f-6088a3b6d7b9"
        }
    ]
}
```

| Field    | Type   | Meaning                      |
| -------- | ------ | ---------------------------- |
| `type`   | string | Always `welcome`             |
| `stage`  | string | Current stage, here `winner` |
| `scores` | array  | Final score table            |

# Runtime message details

## `new_team`

Sent to TV when a team joins.

```json
{
    "type": "new_team",
    "team": {
        "id": "1a8f01ef-77c8-41e5-a561-3061ee2216c1",
        "name": "Team Cyan",
        "image": "https://example.com/team-cyan.png"
    }
}
```

| Field  | Type   | Meaning           |
| ------ | ------ | ----------------- |
| `type` | string | Always `new_team` |
| `team` | object | Created team      |

## `kick_team`

Sent to TV when a team is removed.

```json
{
    "type": "kick_team",
    "uuid": "1a8f01ef-77c8-41e5-a561-3061ee2216c1"
}
```

| Field  | Type        | Meaning            |
| ------ | ----------- | ------------------ |
| `type` | string      | Always `kick_team` |
| `uuid` | UUID string | Removed team id    |

## `button_clicked`

Sent only to Admin during the lobby when a hardware buzzer that is not yet assigned to a team is pressed. Admin uses the code to link the physical buzzer to the team currently being configured. Assigned buzzer presses are not broadcast from the lobby.

```json
{
    "type": "button_clicked",
    "buttonCode": 1671
}
```

| Field        | Type    | Meaning                                                                        |
| ------------ | ------- | ------------------------------------------------------------------------------ |
| `type`       | string  | Always `button_clicked`                                                        |
| `buttonCode` | integer | Numeric hardware buzzer code emitted by the current local receiver integration |

## `album_picked`

Sent to TV when an album/category is picked.

```json
{
    "type": "album_picked",
    "selected": {
        "categoryId": "329144f2-2f14-4c92-97aa-ef20acfdc561",
        "chosenCategoryPreview": {
            "title": "YU Rock",
            "image": "7d26d0ea-dbc1-44fe-976f-88186f86a3aa"
        },
        "pickedByTeam": null,
        "started": false,
        "ordinalNumber": 1
    }
}
```

| Field      | Type   | Meaning                                                                         |
| ---------- | ------ | ------------------------------------------------------------------------------- |
| `type`     | string | Always `album_picked`                                                           |
| `selected` | object | Selected album/category details; `ordinalNumber` is a required non-null integer |

`selected.chosenCategoryPreview.image` contains the album UUID. The corresponding cover is stored using that UUID as its basename and is resolved through `GET /assets/v1/image/albums/{albumId}`.

## `song_next`

Broadcast when the next song starts.

```json
{
    "type": "song_next",
    "songId": "57ce7e23-229b-48a2-89d1-810bb74b0001",
    "question": "Prepoznaj ovu pjesmu!",
    "answer": "Song Name",
    "scheduleId": "0aa1d4fa-2bc3-4c72-a532-3343538cda92",
    "answerDuration": 8,
    "remaining": 15
}
```

| Field            | Type        | Meaning                                                  |
| ---------------- | ----------- | -------------------------------------------------------- |
| `type`           | string      | Always `song_next`                                       |
| `songId`         | UUID string | Song to play                                             |
| `question`       | string      | Question to show                                         |
| `answer`         | string      | Correct answer                                           |
| `scheduleId`     | UUID string | Schedule entry id                                        |
| `answerDuration` | number      | Answer phase duration                                    |
| `remaining`      | number      | Full snippet duration / remaining playback time at start |

## `song_repeat`

Broadcast when snippet replay is triggered.

```json
{
    "type": "song_repeat",
    "remaining": 15
}
```

| Field       | Type   | Meaning                    |
| ----------- | ------ | -------------------------- |
| `type`      | string | Always `song_repeat`       |
| `remaining` | number | Snippet duration to replay |

## `song_reveal`

Broadcast when the current answer should be revealed.

```json
{
    "type": "song_reveal"
}
```

`song_reveal` intentionally has no answer field. The frontend already has the answer from the current `welcome` or `song_next` message.

When this message arrives, frontend should:

- show the answer
- stop/finish active timers
- move UI into reveal state
- keep using the current `songId`, `scheduleId`, `question`, and `answer` already present in state

## `pause`

Broadcast when a team buzzes or when system pause happens.

Team buzz example:

```json
{
    "type": "pause",
    "answeringTeamId": "1a8f01ef-77c8-41e5-a561-3061ee2216c1",
    "interruptId": "94a9f9fd-daa5-4ee7-9440-11a44b171e3a"
}
```

System pause example:

```json
{
    "type": "pause",
    "answeringTeamId": "null",
    "interruptId": "94a9f9fd-daa5-4ee7-9440-11a44b171e3a"
}
```

| Field             | Type                        | Meaning                                        |
| ----------------- | --------------------------- | ---------------------------------------------- |
| `type`            | string                      | Always `pause`                                 |
| `answeringTeamId` | UUID string/string `"null"` | Team that buzzed, or `"null"` for system pause |
| `interruptId`     | UUID string                 | Interrupt id                                   |

Important:

If the interrupt team id is `null`, this is a system pause/error, not a team answer.

## `answer`

Broadcast when Admin resolves a team answer.

```json
{
    "type": "answer",
    "teamId": "1a8f01ef-77c8-41e5-a561-3061ee2216c1",
    "scheduleId": "0aa1d4fa-2bc3-4c72-a532-3343538cda92",
    "correct": true
}
```

| Field        | Type        | Meaning                        |
| ------------ | ----------- | ------------------------------ |
| `type`       | string      | Always `answer`                |
| `teamId`     | UUID string | Team whose answer was resolved |
| `scheduleId` | UUID string | Related schedule               |
| `correct`    | boolean     | Whether answer was correct     |

## `error_solved`

Broadcast when system pause/error is resolved.

```json
{
    "type": "error_solved",
    "previousScenario": 2
}
```

| Field              | Type   | Meaning                                          |
| ------------------ | ------ | ------------------------------------------------ |
| `type`             | string | Always `error_solved`                            |
| `previousScenario` | number | UI scenario to restore after resolving the error |

# JSON schema contracts

The executable schema files live under:

```text
src/test/resources/websocket-contracts/v1/schema/
```

Example files:

```text
album_picked.schema.json
answer.schema.json
button_clicked.schema.json
error_solved.schema.json
kick_team.schema.json
new_team.schema.json
pause.schema.json
song_next.schema.json
song_repeat.schema.json
song_reveal.schema.json
welcome.schema.json
```

These schemas are used by tests to validate real WebSocket frames. The frontend E2E bundle contains
a copy used at runtime; schema-governance tests compare the complete frontend/backend schema filename
sets and semantic JSON contents, including `_published-frame-registry.schema.json`. The frontend's
actual runtime `GAME_MESSAGE_TYPES` constant is also compared with that published registry, and the
`GameMessageType` union is derived from the constant, so compile-time handling and runtime governance
share one frontend source of truth. Runtime coverage deliberately triggers a real Stage 1 pick and
validates the observed `album_picked` frame against the bundled schema.

The markdown document explains the protocol for humans. The JSON schemas enforce the protocol in tests.

## Adding or changing a message

When a WebSocket message changes, update:

1. backend message generation
2. the frontend `GAME_MESSAGE_TYPES` registry / message handling
3. related JSON schema and published-frame registry
4. integration/schema tests
5. this document

Breaking changes should create a new schema folder version, for example:

```text
src/test/resources/websocket-contracts/v2/schema/
```

Examples of breaking changes:

- removing a field
- renaming a field
- changing field type
- changing message meaning
- changing who receives the message

Small compatible additions can stay in `v1` only if frontend safely ignores the new field.

# Browser-level schema validation

The Playwright WebSocket E2E suite validates browser-observed backend frames against the shared schema files bundled under:

```text
apps/frontend/e2e/contracts/backend/websocket-contracts/v1/schema
```

The registry file is:

```text
_published-frame-registry.schema.json
```

The bundled frontend copy is checked against the backend schema source by the schema-governance suite so browser tests cannot silently validate against a stale contract.

When a WebSocket frame payload changes, update the runtime code, schema file, bundled frontend schema copy, frontend handling, and at least one browser-level test path together.

Schema validation should only be performed for frames produced through reachable game states. Do not weaken a schema to accept a frame produced by an impossible test setup.
