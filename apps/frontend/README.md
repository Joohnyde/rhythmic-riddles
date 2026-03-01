# Frontend (apps/frontend)

Angular SPA containing both operator screens:
- **Admin** (host controls)
- **TV** (audience display)

In production builds the frontend is bundled into the backend jar and served as static resources by Spring Boot.

## Development
Frontend can be run:
- locally (Node + Angular CLI), or
- containerized via devcontainers (preferred for shared environment consistency)

## Notes
When the frontend runs inside a container, the dev server must listen on `0.0.0.0` and the port must be forwarded to the host browser.

## Documentation
- Dev environment & IDE integration: `docs/developer-guide/devcontainers.md`
- Packaging & SPA routing behavior: `docs/developer-guide/release_builds.md`
- User-facing operation: `docs/user-guide/`
