# RhytmicRiddles

RhytmicRiddles is a live, pub-quiz style music guessing game. A **10s snippet** plays on a big screen (TV app). Teams **buzz in** using 433MHz RF buttons and verbally answer (typically *artist + title*). A moderator runs the game from a separate **Admin app** that controls flow, scoring, and fairness.

This repository is an **"enterprise-around-the-code"** setup: documentation, developer onboarding, strict CI, quality gates, and reproducible environments—so a 3-person team can ship reliably without heavy process.

---

## System overview

**Clients**
- **TV app (public)**: shows only the information needed for players (no answers).
- **Admin app (private)**: controls the game (teams, albums, playback, scoring, recovery actions).
- **Players (physical)**: 433MHz RF button transmitter (no software UI).

**Backend**
- Spring Boot service that:
  - owns game state and persistence (PostgreSQL)
  - exposes REST endpoints for all client actions
  - pushes async state updates to clients via WebSockets (server → client)

**Prep-app (planned)**
- A separate product for admins to prepare songs/albums and create games: trimming audio, labeling metadata, album grouping, etc.

> Internal codename: **cestereg** (inside joke). Product name: **RhytmicRiddles**.

---

## Repository structure (monorepo)

This is a monorepo containing:
- `apps/quiz-game/` — the live game (backend + frontend)
- `apps/prep-app/` — the preparation tool (backend + frontend; scaffolding only for now)
- `docs/` — user guide + developer guide + architecture decisions
- `hardware/` — RF receiver firmware + wiring + troubleshooting docs
- `infra/` — docker-compose, optional reverse-proxy configs
- `scripts/` — onboarding, doctor checks, test runners

Why monorepo:
- single place for docs, workflow, CI, and shared standards (formatting, lint, release)
- consistent onboarding and automation
- easier future sharing of contracts / common UI / shared libraries

If at any point these products diverge heavily, we can split into multiple repos; monorepo doesn't block that.

---

## Quickstart (dev)

### Option A — Dev Containers (recommended)
This gives you the same versions across machines and a "one click" environment:

1. Install Docker Desktop / Docker Engine
2. Install VS Code + "Dev Containers" extension
3. Open repo in VS Code → **Reopen in Container**

Then run:
- `./scripts/dev/doctor.sh` (health checks)
- `./scripts/dev/up.sh` (starts dev services)
- `./scripts/dev/test-all.sh` (runs all tests)

### Option B — Local tools (without containers)
Prereqs:
- Java: **JDK 25.0.1** (or whatever is specified in `docs/developer-guide/getting-started.md`)
- Node: **24.11.1**
- npm: **11.6.2**
- PostgreSQL: **18.1**

Run:
- Start DB (see `infra/docker-compose/local-dev.compose.yml`)
- Backend: `cd apps/quiz-game/backend && mvn spring-boot:run`
- Frontend: `cd apps/quiz-game/frontend && npm ci && npm start`

Ports (default):
- Backend: `http://localhost:8080`
- Frontend: `http://localhost:4200`

---

## Asset storage (audio + images)

Assets are **NOT** stored in git.

- Dev/laptop mode stores assets on **local filesystem** under `./data/`
- Containers persist via Docker volumes
- DB stores only metadata + relative paths/keys

See: `docs/developer-guide/assets-storage.md`

---

## Documentation

Start here: `docs/index.md`

Key docs:
- Developer onboarding: `docs/developer-guide/getting-started.md`
- State machine + recovery: `docs/developer-guide/state-machine-and-recovery.md`
- API conventions: `docs/developer-guide/api.md`
- Error catalog: `docs/developer-guide/error-catalog.md`
- Logging policy: `docs/developer-guide/logging.md`

---

## Contributing & workflow

See `CONTRIBUTING.md`.

Highlights:
- One branch per feature: `name/1234_short_description`
- Commit messages start with `[1234 Name] ...`
- PR title format: `[feature_1234] ...` / `[bugfix_1234] ...`
- CI must pass before merge

---

## Roadmap

- Prep-app MVP: trimming + metadata + albums + game creation
- Stronger auth (token after password login)
- Formal event log model for recovery (ADR planned)

See GitHub Projects board.

---

## License

License: currently **TBD** (see `LICENSE`). Do not redistribute commercially until a license is chosen.
