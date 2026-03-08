
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
- Build and packaging documentation for cross-platform desktop distribution (Linux, Windows; macOS planned).
- Root-level README files across key directories to clarify repository structure and responsibilities.

### Changed
- Consolidated and removed outdated documentation to align with current implementation.
- Refined asset handling documentation to reflect AssetGateway abstraction and runtime base-dir configuration.
- Clarified database bootstrap and idempotent SQL execution strategy.
- Updated logging documentation to reflect SLF4J + rolling log configuration.
- Standardized documentation navigation via `docs/index.md` as the authoritative entry point.

### Build / Release
- Documented production profiles (`production`, `embeddb`) and runtime behavior.
- Documented embedded vs external PostgreSQL modes.
- Formalized release packaging structure and artifact layout.
- Clarified platform-specific build prerequisites (especially Windows toolchain requirements).

### DevOps / Infrastructure
- Documented docker-compose usage for development environments.
- Clarified container vs local development workflows.
- Added guidance for serial hardware integration in local and packaged environments.


## 0.1.0 – Initial MVP

### Added
- Quiz-game backend + Angular frontend (TV/Admin) baseline implementation.
- PostgreSQL persistence model with recovery-driven interrupt handling.
- WebSocket synchronization between Admin and TV clients.
- Core stage lifecycle: lobby → albums ↔ songs → winner.
