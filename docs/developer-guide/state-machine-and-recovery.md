# State machine & crash recovery

This doc explains **how the system derives UI state** after reconnects and how each stage behaves.

Source of truth:
- `Igra.status` (0..3)
- `Kategorija` selection rows
- `Redoslijed` schedule rows
- `Odgovor` (interrupts: team or technical)

The backend uses `IgraService.contextFetch(roomCode)` to create the `welcome` payload.

---

## Stages

### Stage 0 — Lobby (`status = 0`, `stage = "lobby"`)
Payload:
- `teams`: list of teams

Meaning:
- teams can be created/kicked
- admin decides when to start album selection

Recovery:
- simply re-send current team list

---

### Stage 1 — Albums (`status = 1`, `stage = "albums"`)
Two sub-states exist:

#### 1) Picking in progress
Condition (in code):
- last category is null, or last category is `started = true` and we have not reached `brojAlbuma`

Payload:
- `albums`: categories prepared for the game
- `team`: which team chooses next (or `null` if admin choice)

#### 2) Picked but not started (between-state)
Condition:
- last category exists AND `started = false`

Payload:
- `selected`: the last chosen category (album)

Recovery rules:
- if UI reconnects here, TV/Admin must show the “selected album” reveal
- only then can admin start the category

---

### Stage 2 — Songs (`status = 2`, `stage = "songs"`)
This is the complex stage.

Backend always determines:
- last scheduled item (`Redoslijed zadnja`)
- default “now playing” fields via `IgraService.getDefault(...)`
- `scores` via `tim_interface.getTeamScores(code)`

Then it branches:

#### A) Song answer already revealed (between-state)
Condition:
- `zadnja.getVremeKraja() != null`

Payload:
- `revealed: true`
- `bravo`: who answered correctly (if any)
- plus default song fields + scores

UI behavior:
- show answer / applause moment
- then transition to next song

#### B) Song finished with no answer (UI should show replay/reveal)
Condition:
- computed `remaining < 0`

Payload:
- `revealed: false`
- plus default song fields + scores

UI behavior:
- show “Replay” and “Reveal” actions

#### C) Song still in progress (needs seek + pause analysis)
Condition:
- `remaining >= 0`

Payload includes:
- `seek`: current position (seconds)
- `remaining`: seconds remaining (best-effort)
- and then one of:

##### C1) Team is answering (team interrupt active)
Condition:
- latest interrupt with a team exists and `tacan == null`

Payload:
- `team`: the answering team
- `prekid_id`: the interrupt id

UI behavior:
- pause playback
- show answering team and correct/wrong buttons (admin)

##### C2) Technical pause is active
Condition:
- latest pause interrupt exists and `vremeResen == null` (no team)

Payload:
- `error: true`

UI behavior:
- show “App disconnected” / “Continue” flow
- only continue when both sockets are back

##### C3) Normal playback
No active interrupt and not finished:
- continue playback at `seek`

---

### Stage 3 — Winner (`status = 3`, `stage = "winner"`)
Payload:
- `teams`

UI behavior:
- show leaderboard / winners

---

## Scenario persistence (UI recovery)
UI sometimes needs to distinguish internal “sub-scenarios” not directly inferable from DB timestamps.
The backend stores a small `scenario` int (0..4) to restore UI reliably.

Scenarios:
- `0` Play the answer
- `1` Show replay/next buttons
- `2` Show the team currently answering
- `3` Show “An app has disconnected”
- `4` Play the snippet

If you change any scenario mapping, update:
- frontend UI logic
- docs/user guides
- tests

---

## Testing checklist (must-have)
- reconnect TV/Admin in every stage + sub-state above
- ensure seek is stable across disconnects
- ensure “Continue” only works when both sockets are back
- ensure TV never receives answers

See `docs/developer-guide/testing-strategy.md`.

