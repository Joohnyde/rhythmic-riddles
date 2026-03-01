# scripts/prod/

Production and release helper scripts.

Typical responsibilities:
- build release jars and bundles
- package desktop artifacts using jpackage
- inject required runtime arguments and asset paths
- produce platform-specific deliverables (Linux/Windows/macOS)

These scripts are run on builder machines (not on end-user devices).

## Documentation
See `docs/developer-guide/release_builds.md`.
