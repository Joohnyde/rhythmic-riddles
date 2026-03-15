

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
- Service tests validate rule-heavy logic including stage transitions, interrupt handling and resolution, schedule progression, category selection, state reconstruction, and WebSocket broadcast side effects.
- Added structured controller-level tests to verify the HTTP contract of REST endpoints. These tests validate:
  - happy path responses
  - `DerivedException` responses
  - unexpected runtime error responses
  - HTTP status codes, response content, and response media types
  - rejection of malformed requests before service invocation
- Standardized controller test organization (one test file per controller with nested suites) and removed overlapping or redundant tests.
- Introduced consistency checks ensuring that documentation and API definitions remain aligned with the implementation, including verification of Swagger/OpenAPI documentation and error-handling conventions.
- Added and maintained a structured test catalog (`test-catalog.md` / `test-catalog.csv`) to document backend test coverage and prevent redundant tests.
- Improved overall test readability and maintainability through clearer naming conventions, stronger assertions, and consistent Mockito static imports.

### Changed
- Consolidated and removed outdated documentation to align with current implementation.
- Refined asset handling documentation to reflect AssetGateway abstraction and runtime base-dir configuration.
- Clarified database bootstrap and idempotent SQL execution strategy.
- Updated logging documentation to reflect SLF4J + rolling log configuration.
- Standardized documentation navigation via `docs/index.md` as the authoritative entry point.
- Improved project structure documentation to clarify boundaries between backend, frontend, hardware, and packaging scripts.

## 0.1.0 – Initial MVP

### Added
- Quiz-game backend + Angular frontend (TV/Admin) baseline implementation.
- PostgreSQL persistence model with recovery-driven interrupt handling.
- WebSocket synchronization between Admin and TV clients.
- Core stage lifecycle: lobby → albums ↔ songs → winner.
