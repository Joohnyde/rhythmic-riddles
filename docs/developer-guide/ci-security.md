# CI security model

This document defines the security rules for GitHub Actions and software-supply-chain execution. For the CI job layout and merge-qualification flow, see [Continuous integration & merge qualification](ci.md).

## Trust boundary

Pull-request and build code must be treated as untrusted executable input. Tests, Maven/npm tooling, repository scripts, package builders, smoke tests and workflow changes can all execute arbitrary commands on the runner.

The security boundary is therefore based on containment rather than trying to make build code inherently trusted:

- normal CI runs only on GitHub-hosted disposable runners;
- build/test/package jobs receive no reusable release, cloud, signing or deployment credential;
- `GITHUB_TOKEN` remains read-only or narrower unless a separate privileged job has a documented need;
- repository code must not be executed through a privileged `pull_request_target` path.

## Workflow authority

Normal repository qualification uses `pull_request` and `push` for `master`. The periodic Scorecard workflow additionally uses `schedule`. Repository Workflow Execution Protections restrict allowed workflow trigger events independently of workflow YAML.

Do not add `pull_request_target`, `workflow_run`, `issue_comment`, `repository_dispatch`, `workflow_dispatch` or additional scheduled execution merely for convenience. If a future workflow needs another event, review the trust boundary first and change the repository policy deliberately.

Self-hosted runners must not be made available to ordinary pull-request workflows. If they are introduced later, isolate them from untrusted code and from privileged deployment/signing infrastructure.

## Actions and checkout

External Actions are executable dependencies.

- Pin every external Action to a reviewed full 40-character commit SHA.
- Keep the corresponding release version as a comment.
- Let Dependabot manage normal Action-version updates.
- Allow only GitHub-owned Actions and explicitly reviewed third-party Actions in repository settings.
- The selected third-party allowlist contains only reviewed SHA-pinned revisions of Zizmor, Harden-Runner and OpenSSF Scorecard; changes require the same review as workflow changes.
- Use `persist-credentials: false` for checkout unless a narrowly scoped job intentionally pushes.
- Prefer shallow checkout (`fetch-depth: 1`) unless a specific workflow requires history.

A mutable tag such as `@v7` must not be used as the final trusted reference.

## GitHub metadata and shell input

Treat event metadata as hostile input, including pull-request titles/bodies, branch names, issue/comment text and similar actor-controlled fields.

Do not interpolate such values directly into shell source. Pass them through `env:` and quote the resulting shell variable.

```yaml
env:
  PR_TITLE: ${{ github.event.pull_request.title }}
run: printf '%s\n' "$PR_TITLE"
```

Static workflow values evaluated by GitHub itself, such as runner selection or job display names, do not need this transformation unless they are later inserted into shell code.

## Secrets and publication authority

Build, test and package-qualification jobs are intentionally secretless. They must not receive:

- release PATs or publishing tokens;
- cloud/provider credentials;
- licensing-server credentials;
- code-signing private keys;
- long-lived deployment credentials.

Package qualification may build and execute application code, installers and smoke tests, but it must not have release-write authority.

If release publication is added later, keep publication in a separate narrowly privileged job. Prefer no repository checkout there: download the intended trusted artifacts, publish them, and finish. Do not run Maven, npm, tests, installers or arbitrary repository scripts in that privileged publication job.

For future external infrastructure, prefer GitHub OIDC and short-lived provider credentials over stored long-lived keys. Constrain provider trust to the intended repository, workflow/ref and environment.

## Caches, artifacts and external inputs

Caches are performance optimizations, not trusted build inputs. A cache miss must not change what is tested or packaged.

Do not allow a privileged workflow to download and execute artifacts produced by untrusted pull-request execution. Upload/download only explicit intended files; never use the repository root as a convenience artifact.

Large baseline media used by package qualification is maintained as a versioned GitHub Release input. Published baseline releases should be immutable, CI should reference a specific baseline tag, and the downloaded release asset should be verified before extraction. A changed dataset requires a new tag and an explicit workflow update rather than silently replacing an existing release asset.

Other downloaded executable/tool inputs should use the strongest practical integrity mechanism provided by their source (for example package-manager signature verification, immutable digests or published checksums).

## Dependency and code security checks

Dependency Review protects dependency admission on pull requests. Newly introduced known vulnerabilities at **high** or **critical** severity fail the check; lower-severity findings remain visible for triage. License allow/deny rules should only be added after a real product/legal policy exists.

Dependabot version updates remain enabled for Maven, npm and GitHub Actions, together with Dependabot alerts and security updates.

CodeQL uses GitHub's default setup for Java/Kotlin, JavaScript/TypeScript and workflow analysis with the `security-extended` query suite. Secret scanning and push protection are enabled as repository security controls.

These checks are defense in depth. They do not replace least privilege, trust separation or review of privileged workflow changes.


## CI security tooling

### Zizmor

Zizmor provides GitHub Actions-specific static analysis. Low and medium findings remain visible for triage, while high-severity findings block the workflow. The Action is full-SHA pinned and CI does not auto-fix workflow files.

### Harden-Runner

Harden-Runner runs first in substantial Linux qualification jobs to provide runtime visibility and defense in depth. It remains in `audit` mode while expected outbound endpoints, subprocesses, source-tree changes and token recommendations are reviewed. Egress blocking requires a separate decision after a stable baseline is understood.

### OpenSSF Scorecard

OpenSSF Scorecard runs on pushes to `master` and on a weekly schedule as a periodic repository/supply-chain posture report. It is not a pull-request merge gate. The current workflow keeps repository permissions read-only and does not enable public result publishing or SARIF upload.

## Review and maintainer hygiene

Security-sensitive workflow, dependency and build files are covered by CODEOWNERS. Require an independent Code Owner review only when the repository has another trusted reviewer who can provide that approval meaningfully.

At major releases and team changes, maintainers should review and remove unused:

- PATs and Actions secrets;
- SSH/deploy keys;
- OAuth apps and GitHub Apps;
- repository collaborators and other privileged integrations.

Never place secret values or private key material in issues, pull requests or documentation.

See also:

- [Security model](security.md)
- [Continuous integration & merge qualification](ci.md)
- [Release builds](release-builds.md)
