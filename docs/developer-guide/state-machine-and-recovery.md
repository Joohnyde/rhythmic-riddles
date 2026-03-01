

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

Stage 1 has two UI sub-states, determined by the “last chosen category” record.

### When
- `game.getStage() == 1` 

### Sub-state A: Selecting a new album (picker turn)

#### Condition
A new selection is needed if:

- `lastChosenCategory == null`, OR
- `lastChosenCategory.isStarted() == true` AND `lastChosenCategory.ordinalNumber != game.maxAlbums` 

*(In other words: there is no last selection yet, or the last selection was already started and we haven’t reached the configured album limit.)*

#### Snapshot fields
- `stage: "albums"`
- `albums`: prepared categories for this game
- `team`: the next team that should pick (or `null` if admin choice)

`team` is returned as a `CreateTeamResponse` derived from `ChoosingTeam`. 

#### UI behavior
- Show available albums/categories.
- Show who is picking (if applicable).

### Sub-state B: Album picked but not started yet (choice display)

#### Condition
- `lastChosenCategory != null` AND `lastChosenCategory.isStarted() == false` 

#### Snapshot fields
- `stage: "albums"`
- `selected`: the chosen album/category (`LastCategory`)

#### UI behavior
- Show the “selected album reveal” screen.
- Only after this should the admin “start” the category (which moves into stage 2 later via normal flow).

### Recovery behavior (stage 1)
On reconnect, the server resends either:
- the “picker selection view” payload (Sub-state A), or
- the “picked but not started” payload (Sub-state B),

so the UI can continue exactly where it left off.


## Stage 2: Songs (`stage = "songs"`)

Stage 2 is reconstructed from:
- the last played schedule entry (`ScheduleEntity lastPlayedSong`)
- its timestamps (`startedAt`, `revealedAt`)
- interrupt frames (team pauses and system pauses)
- derived playback timing (`seek`, `remaining`) 

### When
- `game.getStage() == 2` 

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

