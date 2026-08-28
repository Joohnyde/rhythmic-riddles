# State Machine & Reconstruction

This document explains how the backend reconstructs the **current state** when an app reconnects (Admin or TV),
based on the current `GameEntity.stage` and persisted timestamps / interrupt frames.

Source of truth: `GameServiceImpl.contextFetch(roomCode)`

## Why this exists

The TV/Admin clients can disconnect or refresh at any time. On connect (or reconnect), the server immediately sends a
full **context snapshot** that allows the UI to render the correct screen without needing previous client-side state.

The snapshot is a JSON object with:

- `type: "welcome"`
- `stage: "lobby" | "albums" | "songs" | "winner"`
- plus stage-specific fields described below

## Stage mapping (high level)

`GameEntity.stage` → `stage` string returned to clients:

- `0` → `"lobby"`
- `1` → `"albums"`
- `2` → `"songs"`
- `3` → `"winner"`

## Stage 0: Lobby (`stage = "lobby"`)

### When

- `game.getStage() == 0`

### Snapshot fields

- `teams`: list of teams for this room
- `stage: "lobby"`

### Meaning

- Teams can be created and removed.
- Admin decides when to move to album selection.

### Recovery behavior

Reconnect simply resends the current team list.

## Stage 1: Albums (`stage = "albums"`)

Stage 1 has two normal UI sub-states, determined by the last chosen category. Every Stage 1
snapshot includes the complete `albums` list so a reconnecting client can rebuild the album grid
and, when necessary, the selected-album reveal from the same snapshot.
Before rendering, the frontend normalizes that list with `stableAlbumOrder` into one deterministic
canonical order (normalized name, then category id as a tie-breaker). Backend membership and metadata
remain authoritative, but backend array order is **not** a UI positioning contract. The backend currently
emits Stage 1 category projections in deterministic UUID transport order to keep payloads reproducible;
the frontend deliberately ignores that ordering for presentation and applies `stableAlbumOrder`. This
keeps the same logical album at the same rendered index across welcome, refresh, reconnect, recovery,
and `album_picked` updates even when transport order differs from canonical visual order.
The frontend also treats `welcome` as the hydration boundary for live Stage 1 updates: an
`album_picked` received before hydration, for an album outside the current collection, or conflicting
with a different selected album already waiting to start is ignored rather than replacing recovery
state with an impossible transition.

### When

- `game.getStage() == 1`

### Always-present fields

- `stage: "albums"`
- `albums`: prepared categories for this game

Each `albums[].image` value is the **album UUID** used as the basename of the corresponding stored
image asset. Clients pass that UUID directly to `GET /assets/v1/image/albums/{albumId}`; the asset
endpoint resolves whether the stored file is PNG, JPG/JPEG, or WebP and returns the matching MIME type.

### Sub-state A: Selecting a new album (picker turn)

#### Condition

A new selection is needed if:

- `lastChosenCategory == null`, OR
- `lastChosenCategory.isStarted() == true` AND `lastChosenCategory.ordinalNumber != game.maxAlbums`

_(In other words: there is no last selection yet, or the previous selection has already started and
the configured album limit has not been reached.)_

#### Additional snapshot field

- `team`: the next team that should pick, or `null` when Admin picks

`team` is returned as a `CreateTeamResponse` derived from `ChoosingTeam`.

#### UI behavior

- Render the album/category grid from `albums`.
- Show who is picking, if applicable.

### Sub-state B: Album picked but not started yet (choice display)

#### Condition

- `lastChosenCategory != null` AND `lastChosenCategory.isStarted() == false`

#### Additional snapshot field

- `selected`: the chosen album/category (`LastCategory`)

`selected.chosenCategoryPreview.image` carries the same album UUID; the corresponding cover uses
that UUID as its basename and is resolved through the same album-image endpoint.

#### UI behavior

- Keep the album list available in client state.
- Show the selected-album reveal and replay the normal focus transition during recovery.
- Only after this should the admin start the category.
- Recovery deliberately replays the same focus transition as a live selection; it does not jump
  directly to the settled artwork.

### Defensive final-album state

If the final selected category is already started while the persisted game is still in Stage 1,
`contextFetch` returns the normal `stage` and `albums` fields without `team` or `selected`. Normal
stage progression should prevent this state; the shape exists only so recovery remains
self-consistent if persisted state is stale.

### Recovery behavior (stage 1)

On reconnect, `albums` is always present. The sub-state is then identified by:

- `team` for the picker view;
- `selected` for a picked-but-not-started reveal;
- neither field only for the defensive final-album state described above.

This lets the UI reconstruct Stage 1 without relying on state retained from before the disconnect.
A fresh Stage 1 Store connection resets root state before subscribing, and connection generations
prevent late pick/start completions from an old connection from mutating the recovered page.
`LastCategory.ordinalNumber` is non-null in selected snapshots; only `CategorySimple.ordinalNumber`
uses `null` to mean that an album has not yet been picked.

## Stage 2: Songs (`stage = "songs"`)

Stage 2 is reconstructed from:

- the last played schedule entry (`ScheduleEntity lastPlayedSong`)
- its timestamps (`startedAt`, `revealedAt`)
- interrupt frames (team pauses and system pauses)
- derived playback timing (`seek`, `remaining`)

### When

- `game.getStage() == 2`

### Persisted-state validation

Recovery requires a last-played schedule with a track, album, and song. If that persisted Stage-2 state is missing or incomplete, `contextFetch` rejects recovery with `WrongGameStateException` (`E003`) instead of returning a partial snapshot.

### Always-present fields (base contract)

Stage 2 always begins with:

- `stage: "songs"`
- “default fields” via `putDefaultFields(lastPlayedSong, json)`:
    - `songId`
    - `question`
    - `answer`
    - `scheduleId`
    - `answerDuration`
- `scores`: team scores for the room code (`teamService.getTeamScores(roomCode)`)

**Note on localization:** `question` currently falls back to `"Prepoznaj ovu pjesmu!"` when no custom question exists; there is a TODO to translate.

### Scenarion 0: Post-song revealed

#### Condition

- `lastPlayedSong.getRevealedAt() != null`

#### Snapshot fields (in addition to base contract)

- `revealed: true`
- `bravo`: team id of the correct team (or `null`), from `interruptService.findCorrectAnswer(...)`

#### UI behavior

- Show the answer reveal / “applause moment”.
- Show “progress / next” action.

### Otherwise: Not revealed yet → compute playback timing

If not revealed, the backend computes the effective playback time excluding pauses:

- `seek = interruptService.calculateSeek(startedAt, scheduleId) / 1000.0`
- `remaining = snippetDuration - seek`

#### Scenarion 1: Song finished but not revealed (waiting for admin action)

##### Condition

- `remaining < 0`

##### Snapshot fields

- `revealed: false` (and base contract)

##### UI behavior

- Snippet is over.
- UI should show “Replay” and “Reveal” actions.

#### Otherwise: snippet still in progress → include `seek` & `remaining`

If `remaining >= 0`, the snapshot includes:

- `seek`
- `remaining`

Then we determine whether playback should be paused (interrupts).

### Interrupt reconstruction (who/what paused the game)

The service loads the latest unresolved interrupts relevant to this song:

- `InterruptEntity[] interrupts = interruptService.getLastTwoInterrupts(startedAt, scheduleId)`
    - `interrupts[0]` = last team interrupt
    - `interrupts[1]` = last system interrupt

#### Scenarion 2: Team answering (team interrupt active)

##### Condition

- `teamInterrupt != null` AND `teamInterrupt.isCorrect() == null`

##### Snapshot fields (in addition to base contract + seek/remaining)

- `answeringTeam`: team object (`CreateTeamResponse(team)`)
- `interruptId`: id of the team interrupt

##### UI behavior

- Pause playback.
- Display answering team.
- Admin sees “Correct / Wrong” actions.

#### Scenarion 3: System pause active (technical pause)

##### Condition

- `systemInterrupt != null` AND `systemInterrupt.getResolvedAt() == null`

##### Snapshot fields (in addition to base contract + seek/remaining)

- `error: true`

##### UI behavior

- Show “technical difficulties / disconnected” flow.
- Resume only after system pause is resolved by backend logic.

#### Scenarion 4: Normal playback

If:

- `remaining >= 0`
- no active team interrupt
- no unresolved system interrupt

Then the UI should simply continue playback at `seek`.

## Stage 3: Winner (`stage = "winner"`)

### When

- `game.getStage() == 3`

### Snapshot fields

- `stage: "winner"`
- `scores`: final team scores

### UI behavior

- Display leaderboard / winners.

## Stage transitions and safety gates

Stage changes go through `isChangeStageLegal(newStage, roomCode)` before being persisted.

Key rules enforced:

- You can’t jump arbitrarily (e.g., lobby → songs is rejected).
- While in stage 2, you can move only to stage 1 (albums) or stage 3 (winner).
- A stage change is blocked if **both apps are not present**:
    - `presenceGateway.areBothPresent(roomCode)` must be true, otherwise an error is thrown (TV must be connected).

After a successful stage change, the service broadcasts a fresh context snapshot (`type: "welcome"`) to clients.

## Room mutation locking

The game row is the synchronization point for mutations inside one room. A lock is taken before the command re-reads and validates mutable game state, so two requests for the same room cannot both act on the same stale state. Different rooms lock different rows and continue independently.

| Operation                 | Lock behavior                | Reason                                                                                                        |
| ------------------------- | ---------------------------- | ------------------------------------------------------------------------------------------------------------- |
| Create game               | None                         | The room does not exist yet.                                                                                  |
| Change stage              | `tryLockGame` (`NOWAIT`)     | Competing stage/gameplay commands must not queue and later act on stale state.                                |
| Create team               | `tryLockGame` (`NOWAIT`)     | Prevents a team from being created while the lobby is concurrently closing.                                   |
| Kick team                 | `tryLockGame` (`NOWAIT`)     | Prevents a team from being removed while the game concurrently leaves the lobby.                              |
| Pick album                | `tryLockGame` (`NOWAIT`)     | Serializes category selection with other room mutations.                                                      |
| Start category            | `tryLockGame` (`NOWAIT`)     | Serializes the transition into song playback.                                                                 |
| Replay song               | `tryLockGame` (`NOWAIT`)     | A replay command is stale if another room mutation already won.                                               |
| Reveal answer             | `tryLockGame` (`NOWAIT`)     | A reveal command is stale if another room mutation already won.                                               |
| Progress / next song      | `tryLockGame` (`NOWAIT`)     | Prevents two progress requests from advancing the schedule twice.                                             |
| `finishAndNext`           | Inherits the `progress` lock | It is an internal continuation of the already locked progress transaction.                                    |
| Team buzz                 | `tryLockGame` (`NOWAIT`)     | A buzz must not wait behind another mutation and execute after its timing/state is stale.                     |
| System interrupt          | Blocking `lockGame`          | A system pause is a must-persist event and waits for an in-flight room mutation.                              |
| Answer team guess         | `tryLockGame` (`NOWAIT`)     | Serializes scoring/resolution with other room mutations.                                                      |
| Resolve system errors     | `tryLockGame` (`NOWAIT`)     | A competing recovery command should fail against the latest state rather than queue.                          |
| Save previous UI scenario | Blocking `lockGame`          | Recovery metadata must wait for the system pause to finish persisting, then updates only an unresolved pause. |
| `contextFetch`            | None                         | Read-only recovery. It does not mutate room state.                                                            |

### Fail-fast room contention

`tryLockGame` uses PostgreSQL `FOR UPDATE NOWAIT`. If another transaction already owns the same room row, the request is rejected immediately as `423 Locked` / `E010 - Room busy` with a message explaining that another request is changing the room and that the client should retry against the latest state. This is intentional: executing a queued gameplay command later can make the command semantically stale.

Blocking `lockGame` is reserved for the two must-persist recovery cases above. Those operations wait instead of being discarded by transient contention.
Service implementations call both locking modes through the shared `core.service.support.RoomLocks` helper so the blocking/fail-fast distinction stays in one place.

### Schedule IDs on replay and reveal

Replay and reveal still receive `scheduleId` from the client, but the ID is treated as a stale-command token rather than as the source of truth. After acquiring the room lock, the backend loads the room's current (`findLastPlayed`) schedule and requires its ID to match the supplied ID. The current schedule is needed for the operation anyway, so this validation does not add another schedule lookup.

### Side effects and transaction commit

Database writes remain inside the service transaction. WebSocket broadcasts and score-cache updates are registered through `TransactionCallbacks.afterCommitOrNow(...)` and run only after a successful commit when Spring transaction synchronization is active. If the transaction rolls back, those callbacks do not run. Direct unit-test calls without an active Spring transaction run the callback immediately.

This means a client is never deliberately broadcast a state that later rolls back. A WebSocket delivery failure after commit cannot roll the database transaction back; reconnect recovery must reconstruct the already committed state.
