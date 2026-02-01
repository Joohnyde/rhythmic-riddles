# TV Guide (Public Display)

The TV app is the public display shown to the audience. It is intentionally “read-only”.

## What the TV shows by stage

### Lobby
- list of registered teams

### Album selection
- list of available albums
- which team is choosing now
- which albums were already chosen

### Songs
- album name and song counter
- team scores
- playback state:
  - playing countdown
  - paused (a team buzzed in)
  - reveal moment (answer is shown)
- answering team name when paused

### Winner
- final leaderboard

## Safety rule (no answer leaks)
TV must never display or request:
- full answer metadata
- admin-only recovery actions

If you add a feature, verify TV does not receive answer fields in WebSocket payloads.

## Troubleshooting
- If the TV screen shows a disconnect banner:
  - refresh the TV page
  - ensure backend is running
  - ensure the correct room code is used

The system will remain paused until the Admin resolves the technical pause.

