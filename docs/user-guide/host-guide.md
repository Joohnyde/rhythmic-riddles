# Host Guide (Admin / Moderator)

This guide describes how to run a RhytmicRiddles event on a single laptop.

## Roles
- **Admin** (moderator): controls game flow, teams, album choices, scoring, recovery actions
- **TV** (public): shows only what players need; never shows answers
- **Players**: press physical 433MHz RF buzzers and answer verbally

## Venue setup checklist
1. Laptop connected to venue TV (HDMI / casting).
2. USB RF receiver plugged into laptop.
3. DB started (Docker) and backend running.
4. Open **two browser windows/profiles**:
   - Admin UI (private)
   - TV UI (public display)

Tip: use a separate browser profile for TV so it can be full-screen without messing up admin tabs.

## Start a game
1. In Admin UI, create a game.
2. Note the generated **room code** (4 chars).
3. TV UI connects using the same code.

Behind the scenes:
- Admin opens WS `ws://<backend>/ws/1<roomCode>`
- TV opens WS `ws://<backend>/ws/0<roomCode>`
- both receive a `welcome` message and render the current stage

## Stage 0 — Lobby
TV shows:
- list of registered teams

Admin can:
- add a team (name + icon + assign button)
- kick a team
- start the album selection stage

### Button assignment (practical)
1. Click “Add team”.
2. Fill the team name.
3. Press a buzzer button once.
4. The receiver prints a code; the system assigns the latest unseen button id to this team.

Hardware details are in `hardware/docs/`.

## Stage 1 — Album selection
TV shows:
- the list of available albums (categories)
- which team is choosing now
- which albums were already chosen and by whom

Admin does:
- select an album (category) for the choosing team
- start the selected album

Notes:
- some picks may be “admin picks” (team not set)
- if the system crashes between pick and start, reconnect restores “selected but not started”

## Stage 2 — Songs
TV shows:
- album name + current song number
- team scores
- countdown / play state
- which team is currently answering (when paused)

Admin sees:
- everything TV sees plus:
  - answer (song title/artist or track-specific answer)
  - buttons for correct / wrong
  - recovery controls (replay/reveal/next) when needed

### Interrupt / answer flow
1. Song starts playing.
2. Team presses buzzer → playback pauses, team name is shown.
3. Team answers verbally.
4. Admin marks correct/wrong.
   - wrong: points decrease and that team cannot buzz again on the same song
   - correct: points increase and answer is revealed

### If nobody answers
When snippet ends without a correct answer, Admin can:
- **Replay** (play snippet again)
- **Reveal** (show answer + play short answer snippet)

## Disconnects / crash recovery
If TV or Admin refreshes/closes unexpectedly:
- backend writes a technical pause interrupt
- reconnect restores state via `welcome`
- game resumes only when the system is in a consistent state

If you see an “app disconnected” banner:
1. restore the missing app (reopen/reconnect)
2. use “Continue” once both are online

## Stage 3 — Winner
TV shows:
- top teams and final standings

Admin ends the event.

