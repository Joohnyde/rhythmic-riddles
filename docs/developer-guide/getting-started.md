# Getting started (local dev)

This guide starts the **quiz-game** stack locally.

## Prerequisites
- Java: **25**
- Maven: (bundled with IDE or system Maven)
- Node: **24.x (recommended: 24.11.1)**
- npm: (comes with Node; project was developed with npm 11.x)
- Docker + Docker Compose (for DB)
- (Optional) VS Code Dev Containers: `.devcontainer/`

## 1) Start the database (Docker)
From repo root:

```bash
./scripts/dev/up.sh
```

This starts:
- Postgres `PostgreSQL 18.1` on `localhost:5432`
- pgAdmin `pgAdmin 9.11 (container image dpage/pgadmin4:9.11)` on `http://localhost:5050` (placeholder creds)

Stop services:

```bash
./scripts/dev/down.sh
```

## 2) Configure backend
Backend config file:
- `apps/quiz-game/backend/src/main/resources/application.yml`

Important fields:
- `spring.datasource.*` (DB connection)
- `app.assets.base-dir` (assets folder)

### Recommended dev setup
Keep assets at repo root under `./data/`, and set:

```yaml
app:
  assets:
    base-dir: ../../../data
```

### Secrets
Do **not** commit real DB passwords.
Prefer using environment variables or a local-only override file.

## 3) Prepare local assets
Create folders:

```bash
mkdir -p data/audio/snippets data/audio/answers data/images/teams data/images/albums
```

Put a couple MP3 snippets in:
- `data/audio/snippets/<songUuid>_p.mp3`
- `data/audio/answers/<songUuid>_o.mp3`

## 4) Run backend
From repo root:

```bash
cd apps/quiz-game/backend
mvn spring-boot:run
```

Default backend port: `8080`

## 5) Run frontend
From repo root:

```bash
cd apps/quiz-game/frontend
npm install
npm start
```

Default frontend dev server: `http://localhost:4200`

## 6) WebSockets
Clients connect to:

- TV: `ws://localhost:8080/ws/0<ROOM_CODE>`
- Admin: `ws://localhost:8080/ws/1<ROOM_CODE>`

## 7) “Doctor” checks
Run:

```bash
./scripts/dev/doctor.sh
```

This script prints common troubleshooting hints.

## Common issues
### Assets not found
Symptom: `NoSuchFileException` pointing to `.../apps/quiz-game/backend/data/...`

Cause: `base-dir` was set to `./data` but assets are in repo root.

Fix:
- set `app.assets.base-dir: ../../../data`

### DB connection fails
- ensure DB container is running (`docker ps`)
- ensure `spring.datasource.url` matches compose file ports

