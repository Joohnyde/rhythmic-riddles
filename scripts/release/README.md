# Release scripts

Repository-owned helpers for the Foundation `0.2.0` release pipeline.

The repository stays on the `master` branch and the normal development/source version stays
`0.3.0`. Foundation is intentionally released as `0.2.0`; the release manifests record both
identities explicitly.

## Files

- `release-config.json` — Foundation release identity, source identity, branch and supported artifacts.
- `release-lib.mjs` — shared SemVer, Git, build-code, version and hashing helpers.
- `validate-release.mjs` — validates the tag, source-version contract, `master` ancestry and final CHANGELOG.
- `stage-release-artifact.mjs` — copies one native installer to its official release filename and writes its manifest.
- `generate-release-manifest.mjs` — regenerates a manifest for an already staged official artifact.
- `generate-checksums.mjs` — creates `SHA256SUMS` from intended release assets only.
- `validate-release-assets.mjs` — checks the exact final asset set, manifests and checksums.
- `release-lib.test.mjs` — dependency-free Node tests for release identity logic.

## Local tests

From the repository root:

```bash
node --test scripts/release/release-lib.test.mjs
```

## Release validation

The validator needs the protected release branch as a remote-tracking ref:

```bash
git fetch --no-tags origin +refs/heads/master:refs/remotes/origin/master
node scripts/release/validate-release.mjs --tag v0.2.0-rc.1
```

A final `v0.2.0` tag additionally requires a matching `CHANGELOG.md` section.

## Stage one platform artifact

Example for Linux:

```bash
node scripts/release/stage-release-artifact.mjs \
  --tag v0.2.0-rc.1 \
  --platform linux \
  --input dist/linux/out/cestereg_0.2.0_amd64.deb \
  --out-dir release-assets
```

Equivalent release workflow calls use `windows` and `macos`.

The output is named from the release identity and tagged commit timestamp, for example:

```text
cestereg-0.2.0-05J7M4-linux-x64.deb
release-manifest-linux-x64.json
```

## Checksums and final asset validation

After Linux, Windows, macOS artifacts, manifests and the two SBOMs have been collected in one
`release-assets/` directory:

```bash
node scripts/release/generate-checksums.mjs --dir release-assets
node scripts/release/validate-release-assets.mjs --dir release-assets
```

The expected final asset set is:

```text
1 Linux .deb
1 Windows .msi
1 macOS .dmg
3 platform manifests
sbom-backend.cdx.json
sbom-frontend.cdx.json
SHA256SUMS
```

## GitHub Actions outputs

When `GITHUB_OUTPUT` is set, validation/staging/manifest scripts also expose useful outputs for
later workflow steps. They do not write to GitHub or publish a release themselves.
