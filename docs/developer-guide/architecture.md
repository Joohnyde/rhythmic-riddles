
# Architecture

RhytmicRiddles is a two-screen live quiz system with a physical buzzer input path.

## High-level components

### Quiz Game (Phase 1, implemented)
- **TV frontend**: public view (never shows answers)
- **Admin frontend**: private moderator view
- **Backend**: authoritative state machine + REST + WebSockets
- **Database** (PostgreSQL): durable game state for recovery and auditability
- **Buzzer hardware** (433MHz): USB receiver to Admin laptop, interpreted by the Admin UI

### Prep App (planned)
- **Prep frontend**: manage songs/albums/games
- **Prep backend**: ingest, trim, tag, and store assets

## Runtime deployment model (today)
Single-laptop deployment:
- DB + backend + frontend all run on the host laptop
- TV is driven by HDMI/cast from the laptop
- USB receiver is plugged into the laptop

This is optimized for reliability and zero venue infrastructure.

## Communication model

### Client actions (HTTP)
All user actions are HTTP calls:
- create/kick team
- pick/start albums
- interrupt / answer / continue
- next/reveal/refresh song
- state changes

### Server push (WebSockets)
Two WebSocket sessions per game:
- Admin socket position `0`
- TV socket position `1`

On connect, backend sends `welcome` containing enough state for recovery.

On changes, backend broadcasts delta messages (e.g., `new_team`, `pause`, `song_reveal`).

### Why HTTP + WebSockets?
- HTTP gives idempotent-ish commands, logs, and easy retries.
- WebSockets provide low-latency UI synchronization without polling.

## Persistence model
Backend is stateful-by-design:
- Current game stage and actions are persisted
- “Interrupt” records represent both:
  - team buzz interrupts
  - technical pauses (socket disconnects)
- Recovery derives the correct UI state from persisted events + timestamps

See:
- `docs/developer-guide/state-machine-and-recovery.md`
- `docs/developer-guide/recovery-queries.md`

## Assets (audio/images)
Phase 1 uses local filesystem storage under `./data/` (repo root).

- snippets: `data/audio/snippets/<songUuid>.mp3`
- answers: `data/audio/answers/<songUuid>.mp3`
- team icons: `data/images/teams/<teamUuid>.<ext>`
- album covers: `data/images/albums/<albumUuid>.<ext>`

Assets are **never committed to git**.

See `docs/developer-guide/assets.md`.

## Monorepo reasoning
We keep quiz-game + prep-app in one repository because:
- shared conventions (CI, formatting, docs, scripts)
- shared domain model (songs/albums)
- easier onboarding for a small team

When prep-app is implemented, it should reuse the same engineering standards.

