# scripts/prod/

Production and release helper scripts.

Typical responsibilities:

- build release jars and bundles
- package desktop artifacts using jpackage
- inject required runtime arguments and asset paths
- produce platform-specific deliverables (Linux/Windows/macOS)

These scripts are run on builder machines (not on end-user devices).

## Documentation

See `docs/developer-guide/release-builds.md`. The fast build contract is
`cd apps/frontend && npm run test:release-contract`; the native packaged-product runner is
`node scripts/prod/package-smoke.mjs --app <launcher>` and must execute on the platform that built the
app image.
