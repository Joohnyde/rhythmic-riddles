# Release evidence reports

Reports capture qualification evidence for a specific release candidate/final artifact, with build identity, methodology and environment. They are engineering evidence, not marketing capacity claims or SLA guarantees. **PENDING** fields mean the evidence has not yet been collected.

## Foundation 0.2.0

- [Release qualification](releases/0.2.0-qualification.md): CI/security state, coverage, native smoke, manual platform acceptance, integrity verification and accepted limitations.
- [Controlled performance baseline](releases/0.2.0-performance.md): designated Linux machine, verified CI-built RC, workload, startup/recovery and k6 results from at least three comparable runs.

Follow the [release runbook](../developer-guide/release-process.md) and [performance harness](../../perf/README.md). Record tag, full SHA, build code, artifact SHA-256, tool versions and relevant environment with results. Keep benchmark-script/report commits distinct from the artifact commit when report preparation follows RC testing. Raw baseline summaries may live in [perf/baselines](../../perf/baselines/README.md); do not commit secrets or large transient logs.

Add future release reports under `docs/reports/releases/` and link them here. Templates may merge before an RC exists; release qualification is complete only after actual evidence and human review are recorded.
