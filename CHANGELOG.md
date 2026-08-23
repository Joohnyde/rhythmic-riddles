

# Changelog

All notable changes to this project will be documented in this file.

This project does not yet follow a strict release cadence. When tagged releases are introduced,
a SemVer-like versioning scheme will be used.

## Unreleased

### Added
- Comprehensive developer documentation covering API (REST + WebSocket), exception model, state reconstruction, assets architecture, logging strategy, and release workflows.
- Structured hardware documentation including BOM, wiring, soldering guidance, firmware flashing, pairing, and testing.
- Devcontainer documentation with IDE integration guidance (VS Code, IntelliJ, NetBeans profile strategy).
- Editor configuration and formatting standards documentation.
- Build and packaging documentation for cross-platform desktop distribution (Linux, Windows, macOS).
- Root-level README files across key directories to clarify repository structure and responsibilities.
- Added MIME-aware album image delivery through `GET /assets/v1/image/albums/{albumId}`, backed by the filesystem asset gateway and the shared E007/E008 asset error contract.

### E2E testing
- The E2E fixture API - test infrastructure and must remain gated behind the `e2e` Spring profile.
- Seeded Playwright WebSocket E2E testing for the frontend.
- Added detailed E2E guide covering the Spring `e2e` profile, isolated database schema, fixture API, fixture payload rules, interrupt invariants, run commands, schema contracts, and troubleshooting.
- Documented the frontend `apps/frontend/e2e` folder structure and helper responsibilities.
- Updated testing overview, writing guidance, and test catalog to include browser-level WebSocket integration coverage.

### Desktop Packaging Architecture
- Cross‑platform desktop packaging architecture enabling fully self‑contained distribution.
- jpackage-based builds bundling a custom Java runtime via jlink (no system Java required).
- Platform‑specific release scripts for:
  - Linux (`.deb` installer + portable app-image)
  - Windows (`.msi` installer + portable app-image)
  - macOS (`.dmg` installer + portable `.app` bundle)
- Runtime argument injection for production profiles and asset base directory configuration.
- Embedded PostgreSQL distribution strategy allowing the application to run without system PostgreSQL.
- External PostgreSQL mode support for environments where an external database is preferred.
- Asset bundling strategy ensuring static assets are packaged inside the application image and accessible at runtime.
- Persistent AppData / Application Support directories for storing runtime database state.
- Automatic browser launch on application startup for improved desktop UX.
- Graceful shutdown strategy via Spring Boot Actuator integration.

### Build / Release
- Documented production runtime profiles (`production`, `embeddb`) and their behavior.
- Documented embedded vs external PostgreSQL operating modes.
- Implemented a platform-aware Maven profile system supporting:
  - production runtime configuration
  - embedded database mode
  - platform selection (`linux`, `windows`, `macos`)
- Integrated Angular frontend builds into the Maven lifecycle using `frontend-maven-plugin`.
- Embedded static frontend assets into the Spring Boot jar during production builds.
- Introduced cross-platform packaging scripts for generating installers and portable distributions.
- Formalized the release artifact structure for consistent distribution outputs across platforms.
- Ensured platform-specific embedded PostgreSQL binaries are included only where required.
- Injected runtime configuration arguments through packaging scripts rather than hardcoding profiles in the application jar.
- Documented platform-specific build prerequisites, including Windows toolchain requirements (e.g., WiX).
- Improved reproducibility of release builds through standardized packaging workflows.

### DevOps / Infrastructure
- Documented docker-compose usage for development environments.
- Clarified container vs local development workflows.
- Added guidance for serial hardware integration in local and packaged environments.
- Added release documentation describing how to produce distributable artifacts for all supported platforms.
- Prepared groundwork for future CI pipelines for automated multi-platform release builds.

### Testing

- Expanded the backend automated test suite with stronger coverage across service, controller, and documentation‑consistency layers.
- Service-layer unit tests remain the primary focus of the test suite and protect the core game rules implemented in:
  - `GameServiceImpl`
  - `InterruptServiceImpl`
  - `ScheduleServiceImpl`
  - `CategoryServiceImpl`
  - `TeamServiceImpl`
  - `SongServiceImpl`
  - `ImageServiceImpl`
- Service tests validate rule-heavy logic including stage transitions, interrupt handling and resolution, schedule progression, category selection, state reconstruction, and WebSocket broadcast side effects.
- Added focused filesystem asset-gateway tests for image extension precedence, MIME mapping, team/album directory resolution, and missing-asset contracts.
- Added structured controller-level tests to verify the HTTP contract of REST endpoints. These tests validate:
  - happy path responses
  - `DerivedException` responses
  - unexpected runtime error responses
  - HTTP status codes, response content, and response media types
  - rejection of malformed requests before service invocation
- Added comprehensive WebSocket tests covering connection lifecycle, audience routing, and broadcast delivery:
  - Introduced stage-2 transition and recovery tests across interrupt, pause, replay, reveal, and next-song flows.
  - Added reconnect and recovery tests ensuring correct state reconstruction and no stale or duplicate emissions.
  - Implemented concurrency and race-condition tests (multi-threaded) for connects, interrupts, stale closes, and reconnect churn.
  - Verified session registry behavior under concurrency, including collision handling, stale-session protection, and room isolation.
  - Added WebSocket broadcast isolation tests to prevent cross-room or cross-audience message leakage.
  - Introduced JSON contract locking tests for all critical WebSocket frames and recovery payloads.
  - Enforced field presence, forbidden fields, and type stability to prevent frontend-breaking payload drift.
- Introduced consistency checks ensuring that documentation and API definitions remain aligned with the implementation, including verification of Swagger/OpenAPI documentation and error-handling conventions.
- Added and maintained a structured test catalog (`test-catalog.md` / `test-catalog.csv`) to document backend test coverage and prevent redundant tests.
- Improved overall test readability and maintainability through clearer naming conventions, stronger assertions, and consistent Mockito static imports.
- WebSocket schema validation
- Added PostgreSQL-backed database integration testing using the production schema, with coverage for persistence queries, game recovery, transaction atomicity, and database invariants.
- Added concurrency integration coverage for simultaneous team buzzes, answers, game progression, album selection, and system-interrupt races.
- Added a small real-HTTP Spring full-stack integration layer covering committed writes, expected business rejection, and PostgreSQL room-lock contention.

### Changed
- Stage 1 recovery snapshots now always include the complete album list; picker and selected-album state are carried alongside it as the active sub-state.
- Album cover files use the album UUID as their basename, while the stored format may be PNG, JPG/JPEG, or WebP; category/selection payloads pass that UUID to the MIME-aware image endpoint for resolution.
- Consolidated and removed outdated documentation to align with current implementation.
- Refined asset handling documentation to reflect AssetGateway abstraction and runtime base-dir configuration.
- Clarified database bootstrap and idempotent SQL execution strategy.
- Updated logging documentation to reflect SLF4J + rolling log configuration.
- Standardized documentation navigation via `docs/index.md` as the authoritative entry point.
- Improved project structure documentation to clarify boundaries between backend, frontend, hardware, and packaging scripts.
- Hardened concurrent room mutations with room-level locking, preventing conflicting user actions while ensuring system interrupts are persisted and nested recovery state remains valid.
- Improved transaction consistency across database state, score caching, and WebSocket broadcasts so failed operations cannot leave partial state or publish uncommitted changes.
- Strengthened room ownership and stale-schedule validation for client-provided persistence identifiers.
- Added a dedicated HTTP 423 room-busy response for conflicting same-room operations and documented the updated API behavior.


## 0.1.0 – Initial MVP

### Added
- Quiz-game backend + Angular frontend (TV/Admin) baseline implementation.
- PostgreSQL persistence model with recovery-driven interrupt handling.
- WebSocket synchronization between Admin and TV clients.
- Core stage lifecycle: lobby → albums ↔ songs → winner.


