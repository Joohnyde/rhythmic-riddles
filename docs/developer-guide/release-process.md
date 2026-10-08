# Foundation 0.2.0 release runbook

Human operator source of truth for `.github/workflows/release.yml`. `master` is the default/release branch. Merging a PR does not publish an installer.

## 1. Prepare a candidate

Before tagging, confirm intended Foundation work is merged, CI / Merge Gate is green, required security checks pass, release-blocking findings are resolved/triaged, and CHANGELOG reflects the candidate. Verify protected release-tag creation/update/deletion rules, private reporting, and repository release immutability in GitHub settings. Workflow files alone do not prove those settings are enabled.

Use a clean checkout; stop and preserve local work if `git status --short` is nonempty. From repository root:

```bash
git status --short
git switch master
git pull --ff-only origin master
git fetch --no-tags origin +refs/heads/master:refs/remotes/origin/master
node --test scripts/release/release-lib.test.mjs
node scripts/release/validate-release.mjs --tag v0.2.0-rc.1
cd apps/frontend
npm ci
npm run test:release-contract
cd ../..
```

Validation intentionally expects development source version `0.3.0` and release version `0.2.0`, as configured in `scripts/release/release-config.json`. Do not bump/downgrade one version field independently. The validator checks ancestry and version contracts; it does not verify GitHub settings or green CI/security status.

For subsequent candidates, use the next unused RC number throughout:

```bash
git tag -a v0.2.0-rc.1 -m "Rhythmic Riddles 0.2.0 RC1"
git push origin v0.2.0-rc.1
```

Never move or reuse a release tag. A defect requires a normal issue/PR and a new RC.

## 2. Watch release qualification

In Actions, require tag validation, code quality, Linux/Windows/Intel macOS backend tests, frontend/E2E, firmware, all native package builds/smokes, and SBOM jobs to succeed. Release jobs do not restore dependency caches. Package smoke exercises the native app-image on its build OS; manual installer acceptance is still required.

Publication waits for these jobs, validates local assets, creates a draft, verifies its exact uploaded asset set, then publishes it. An RC is marked prerelease and is not latest. If a run fails, inspect the failing job and any draft before proceeding; do not overwrite published assets or move tags.

Expected downloadable assets:

- Linux x64 `.deb`, Windows x64 `.msi`, Intel macOS x64 `.dmg`;
- three `release-manifest-<platform>-x64.json` files;
- `sbom-backend.cdx.json` and `sbom-frontend.cdx.json`;
- `SHA256SUMS`.

GitHub attestations are associated with the artifacts; they are not an additional installer format. See [release builds](release-builds.md) for native version exceptions and [release helpers](../../scripts/release/README.md) for asset naming.

## 3. Download and verify

Use GitHub CLI authenticated for repository access where needed. In a fresh empty directory for this candidate:

```bash
gh release download v0.2.0-rc.1 --repo Joohnyde/rhythmic-riddles --dir rc-assets
cd rc-assets
sha256sum --check SHA256SUMS
```

On macOS use `shasum -a 256 -c SHA256SUMS`. On Windows PowerShell:

```powershell
Get-Content SHA256SUMS | ForEach-Object {
  if ($_ -notmatch '^([0-9a-f]{64})\s+\*?(.+)$') { throw "Invalid checksum line" }
  $expected = $Matches[1]
  $file = $Matches[2]
  if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
    throw "Checksum mismatch: $file"
  }
}
```

For each intended installer, manifest and SBOM, use its actual downloaded filename:

```bash
gh release verify-asset v0.2.0-rc.1 <filename> --repo Joohnyde/rhythmic-riddles
gh attestation verify <filename> --repo Joohnyde/rhythmic-riddles
```

Inspect the attestation's workflow/ref/commit against the candidate, and the manifests' release tag/version, source version, full SHA, build code and installer hashes. Stop if verification fails. Record commands/results in the [qualification report](../reports/releases/0.2.0-qualification.md).

## 4. Manual acceptance of the exact RC

Install the downloaded installer on Linux, Windows 11 and Intel macOS. Do not substitute a local rebuild or manually patch an artifact. For each platform record machine/OS, artifact hash, result and limitations:

- install and normal launch; Admin and TV open;
- embedded database first-run initialization;
- representative game, audio, album/team images and scoring;
- clean shutdown/restart, persisted state and recovery;
- expected per-user data/log locations and supported uninstall behavior.

Also check Windows shortcuts/Start menu and absence of unexpected Java/PostgreSQL prerequisites; on macOS check DMG mount/application launch and browser opening. Physical RF/USB testing is manual/HIL when the hardware is available. Apple Silicon is outside the native qualification matrix.

A failure returns to issue → PR → green `master` → new RC. Keep each candidate's evidence separate.

## 5. Controlled benchmark and qualification report

On the designated Linux benchmark machine, verify the downloaded RC hash, close noisy applications, record hardware/kernel/power mode and tool versions, then follow the [performance harness instructions](../../perf/README.md). Extract the downloaded `.deb` to obtain its packaged launcher if using the startup harness; do not rebuild it. Run startup/recovery and k6 scenarios at least three comparable times with a declared real production action contract. Record median/range, correctness failures and deviations in the [performance report](../reports/releases/0.2.0-performance.md).

Complete the [qualification report](../reports/releases/0.2.0-qualification.md): build identity, CI/security status, coverage, native smoke, all manual platform results, benchmark and checksum/attestation/SBOM verification, known limitations and accepted risks. Pending fields are not evidence. Have another developer review the documentation and actual results; record the review/PR link. Merge report/release-preparation changes through a normal PR.

## 6. Prepare and tag final

Move the Foundation entries from `Unreleased` into `## [0.2.0] - YYYY-MM-DD`, replacing the placeholder with the actual publication date; leave a fresh `## [Unreleased]`. Remove the preparation note. Confirm reports are complete, relevant preparation changes are reviewed/merged, `master` CI and security checks are green, and no release blocker remains. Report commits may follow the RC commit; confirm any source/build changes have received a new RC before final release.

From a clean checkout:

```bash
git switch master
git pull --ff-only origin master
git fetch --no-tags origin +refs/heads/master:refs/remotes/origin/master
node scripts/release/validate-release.mjs --tag v0.2.0
git tag -a v0.2.0 -m "Rhythmic Riddles 0.2.0"
git push origin v0.2.0
```

The final validator checks for the matching CHANGELOG heading; the real date, report completion and human review remain operator responsibilities. Final qualification rebuilds from the final tag. Do not rename RC binaries into final binaries.

## 7. Verify publication and close Foundation

Require the final workflow to succeed. Download the published final assets into a new directory and repeat checksum, manifest and provenance verification using `v0.2.0`. Check immutable status, complete asset set, final/prerelease flag and release notes/CHANGELOG link. The workflow generates GitHub release notes; review those notes against the CHANGELOG.

Perform a short install/launch smoke on a real machine using the published final artifact. Record final identity and results in the qualification report through a reviewed PR. Link release/report/review evidence to the Foundation issues and milestone, close completed items.
