# Getting Started

This is the quick entry point for developers. It intentionally stays short and links to the deeper documents.

## Choose your development mode

### Option A: Dev Containers (recommended)

Best for:

- consistent toolchain across developers
- lowest setup effort
- fewer “it works on my machine” issues

Start here:

- `devcontainers.md` (dev environment + database behavior + scripts + troubleshooting)

---

### Option B: Hybrid (DB in Docker, code on host)

Best for:

- native debugging in your IDE
- contributors who already have Java/Node installed

Basic approach:

1. Start DB via Docker Compose
2. Run backend/frontend locally against the mapped DB port

Details live in:

- `devcontainers.md`
- `database.md`

## Run backend tests

From the repository root on Linux:

```bash
cd apps/backend
mvn -Dplatform=linux test
```

The full backend suite includes tests that start a real embedded PostgreSQL instance. The platform property selects the matching native PostgreSQL binary. Use `-Dplatform=windows` on Windows or `-Dplatform=macos` on macOS. See `testing/db-integration-tests.md` for details.

## Baseline formatting rule

Before committing code:

- ensure EditorConfig is enabled
- see `editorconfig.md`
