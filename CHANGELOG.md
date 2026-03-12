
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

#### Desktop Packaging Architecture
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

#### Build & Release Infrastructure
- Platform‑aware Maven profile system supporting:
  - `production`
  - `embeddb`
  - platform selection (`linux`, `windows`, `macos`)
- Angular frontend build integrated into Maven lifecycle using `frontend-maven-plugin`.
- Static frontend assets embedded into Spring Boot jar during production builds.
- Packaging scripts standardized across platforms for reproducible builds.
- Release artifact structure defined for consistent distribution outputs.
- Runtime configuration arguments injected via packaging scripts instead of hardcoding profiles in the jar.

### Changed
- Consolidated and removed outdated documentation to align with current implementation.
- Refined asset handling documentation to reflect AssetGateway abstraction and runtime base-dir configuration.
- Clarified database bootstrap and idempotent SQL execution strategy.
- Updated logging documentation to reflect SLF4J + rolling log configuration.
- Standardized documentation navigation via `docs/index.md` as the authoritative entry point.
- Improved project structure documentation to clarify boundaries between backend, frontend, hardware, and packaging scripts.

### Build / Release
- Documented production profiles (`production`, `embeddb`) and runtime behavior.
- Documented embedded vs external PostgreSQL modes.
- Formalized release packaging structure and artifact layout.
- Clarified platform-specific build prerequisites (especially Windows toolchain requirements such as WiX).
- Introduced cross-platform packaging scripts to generate installers and portable builds.
- Ensured platform-specific embedded PostgreSQL binaries are included only where needed.
- Improved reproducibility of release builds by standardizing packaging workflows.

### DevOps / Infrastructure
- Documented docker-compose usage for development environments.
- Clarified container vs local development workflows.
- Added guidance for serial hardware integration in local and packaged environments.
- Added release documentation describing how to produce distributable artifacts for all supported platforms.
- Prepared groundwork for future CI pipelines for automated multi-platform release builds.

## 0.1.0 – Initial MVP

### Added
- Quiz-game backend + Angular frontend (TV/Admin) baseline implementation.
- PostgreSQL persistence model with recovery-driven interrupt handling.
- WebSocket synchronization between Admin and TV clients.
- Core stage lifecycle: lobby → albums ↔ songs → winner.
