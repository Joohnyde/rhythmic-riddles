
# Host Guide (Admin / Moderator)

This guide explains how to run a RhythmicRiddles event using the packaged desktop application.

The system runs locally on a single machine and opens automatically in your browser.


## Roles

- **Admin** – controls the game (teams, albums, scoring, reveals)
- **TV** – public display (audience-facing screen)
- **Players** – press physical buzzers and answer verbally

Admin and TV run in separate browser windows on the same machine.


## Venue Setup Checklist

1. Launch the desktop application (double-click).
2. Wait for the browser to open automatically.
3. Connect laptop to venue TV (HDMI or casting).
4. Plug the USB RF receiver into the laptop.
5. Open two browser windows:
   - Admin (private window)
   - TV (full-screen on venue display)

No Docker, database setup, or manual server startup is required.


## Starting a Game

1. In the Admin view, create a new game.
2. A **room code** is generated.
3. Open the TV view and enter the same room code.

Both views synchronize automatically.


## Stage 0 – Lobby

Admin can:
- Add teams (name + optional image)
- Assign buzzers (press button once during team creation)
- Remove teams
- Start the album selection stage

TV shows:
- Registered teams


## Stage 1 – Album Selection

TV shows:
- Available albums
- Which team is selecting
- Previously chosen albums

Admin:
- Selects an album for the current team
- Starts the album

Notes:
- some picks may be “admin picks” (team not set)
- if the browser refreshes during selection, the system restores the correct state automatically.


## Stage 2 – Songs

TV shows:
- Album name
- Song number
- Scores
- Playback status
- Answering team (when paused)

Admin sees additional controls:
- Correct / Wrong
- Replay
- Reveal
- Next song

### Buzzer Flow

1. Song plays.
2. A team presses the buzzer → playback pauses.
3. Team answers verbally.
4. Admin marks correct or wrong.
5. Scores update automatically.
   - wrong: points decrease and that team cannot buzz again on the same song
   - correct: points increase and answer is revealed


### If nobody answers
When snippet ends without a correct answer, Admin can:
- **Replay** (play snippet again)
- **Reveal** (show answer + play short answer snippet)



## Stage 3 – Winner

TV shows final rankings.

Admin ends the game session.

## Disconnects / crash recovery
If TV or Admin refreshes/closes unexpectedly:
- backend writes a technical pause interrupt
- the system automatically reconstructs the current stage.
- game resumes only when the system is in a consistent state

If you see an “app disconnected” banner:
1. restore the missing app (reopen/reconnect)
2. use “Continue” once both are online
