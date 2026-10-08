# Performance baselines

This directory is for small, reviewable release-baseline outputs (for example startup/recovery JSON) that support the corresponding report under `docs/reports/releases/`.

Rules:

- baseline evidence identifies the exact application SHA/artifact hash and benchmark-script commit;
- serious release data comes from the designated controlled Linux benchmark machine and the CI-built RC artifact;
- at least three comparable repetitions are required for the 0.2.0 release report;
- retain failed/anomalous runs in notes rather than cherry-picking the fastest result;
- do not commit large raw logs, package data directories, database contents or secrets;
- baselines are engineering evidence, not SLA or capacity guarantees.

No 0.2.0 measured baseline is committed by this tooling change. RC values remain **PENDING** until the real release candidate exists.
