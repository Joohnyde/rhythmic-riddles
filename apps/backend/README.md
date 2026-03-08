# Backend (apps/backend)

Spring Boot backend that provides:
- REST APIs and WebSocket protocol for game control and TV synchronization
- Asset access via the AssetGateway abstraction (audio + images)
- Optional embedded PostgreSQL mode for end‑user desktop deployments
- Production-only behavior such as SPA routing support and browser auto-launch

## Running (development)
The backend is typically run either:
- locally (IDE / Maven), or
- inside the devcontainer environment (recommended for consistent setup)

See the developer documentation for the exact run commands, profiles, and environment setup.

## Key runtime modes
- `production` – desktop/production behavior (localhost binding, SPA routing, etc.)
- `production,embeddb` – production plus embedded PostgreSQL

## Documentation
- API & protocol: `docs/developer-guide/api.md`
- Exceptions: `docs/developer-guide/exceptions.md`
- Assets: `docs/developer-guide/assets.md`
- Dev environment: `docs/developer-guide/devcontainers.md`
- Release builds: `docs/developer-guide/release_builds.md`
