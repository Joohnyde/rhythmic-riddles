# Performance qualification harness

This directory contains the **Foundation 0.2.0 measurement harness**. It is designed to produce reproducible release evidence, not marketing capacity claims and not tight latency gates on shared CI runners.

## Principles

- Benchmark an **actual supported application contract**. Do not benchmark E2E fixture-only shortcuts and call that production performance.
- Hard-fail correctness/reliability failures; **record latency distributions without release gating** until controlled baselines establish meaningful budgets.
- Serious measurements run on one designated Linux machine against the **CI-built RC artifact**. GitHub-hosted runners remain qualification infrastructure, not authoritative benchmark hardware.
- Run the controlled RC workload at least **three comparable times**. Report median/range; never select only the fastest result.
- Keep the machine, workload, script commit, application commit and artifact SHA-256 with the results.

## Prerequisites

Install a pinned k6 release on the benchmark machine and record the exact version in `docs/reports/releases/0.2.0-performance.md`.

```bash
k6 version
```

Start the packaged RC (or a representative local stack for harness development) and point the scripts at it:

```bash
export BASE_URL=http://127.0.0.1:8080
export WS_URL=ws://127.0.0.1:8080
```

Defaults follow the current production contract used by `scripts/prod/package-smoke.mjs`: `POST /api/v1/games` creates a room and WebSocket clients connect under `/ws/<client><ROOM>`. Admin client id defaults to `0`; TV client id defaults to `1`. Override `ADMIN_CLIENT` / `TV_CLIENT` if that protocol contract changes.

## Scenarios

### 1. Protocol smoke

Creates a real room, then connects Admin and TV clients and requires a valid `welcome` frame from each.

```bash
k6 run perf/k6/smoke.js
```

This is deliberately small enough for local harness verification. It may be run in CI later only if proven fast and stable; it is not required for Foundation PR qualification.

### 2. Concurrent rooms + reconnect churn

Creates several rooms in parallel and repeatedly disconnects/reconnects Admin and TV clients. It records WebSocket connection failures, messages and connection-to-welcome propagation latency.

```bash
ROOMS=4 RECONNECTS=3 k6 run perf/k6/websocket-load.js
```

Increase room count only as a declared workload change. Do not silently change it between release-to-release comparisons.

### 3. Repeated lifecycle + action burst

Creates repeated games across multiple VUs and opens real Admin/TV sockets. Without an action payload it is a lifecycle/reconnection test:

```bash
VUS=2 ITERATIONS=2 GAMES_PER_VU=5 k6 run perf/k6/lifecycle.js
```

For a **real supported Admin action burst**, pass the exact production WebSocket frame and the TV frame type that proves propagation. This is intentionally configuration rather than a fixture-only hard-code, because the application protocol evolves independently of the load harness:

```bash
ACTION_BURST=10 \
ACTION_MESSAGE_JSON='{"type":"<supported-admin-action>","...":"..."}' \
EXPECTED_ACTION_TYPE='<expected-tv-frame-type>' \
EXPECTED_ACTION_FRAMES=10 \
k6 run perf/k6/lifecycle.js
```

Before the 0.2.0 RC benchmark, fill these values from the then-current production WebSocket contract and record them verbatim in the performance report. `EXPECTED_ACTION_FRAMES` defaults to the burst size but can be set to the contractually expected fan-out count if the server intentionally coalesces updates. `ACTION_BURST > 0` refuses to run without the real action JSON/type; this prevents a benchmark from silently substituting a fake action.

## Metrics

The scripts emit normal k6 HTTP metrics plus custom metrics:

- `rooms_created`
- `correctness_failures`
- `websocket_messages`
- `websocket_connection_failures`
- `websocket_missing_messages`
- `websocket_duplicate_messages`
- `websocket_propagation_ms`
- `vu_lifecycle_failed`

k6 supplies `http_req_duration` p50/p95/p99, request count and request error rate. Keep the raw end-of-test summary or `--summary-export` result with release evidence when useful.

Correctness/reliability thresholds are intentionally strict: checks must pass, HTTP errors must be zero, no required WebSocket messages may be missing/duplicated, and VU lifecycles may not fail. **No p95/p99 latency threshold is enforced for 0.2.0.**

## Packaged startup / restart / resources

Use the separate Node harness against the unpacked app-image launcher from the exact CI-built RC:

```bash
node scripts/perf/measure-startup.mjs \
  --app dist/linux/out/cestereg/bin/cestereg \
  --runs 3 \
  --version 0.2.0 \
  --git-sha <full-rc-sha> \
  --artifact <downloaded-ci-built-deb-or-archive> \
  --power-mode '<recorded mode>' \
  --background-services '<recorded assumptions>' \
  --output perf/baselines/0.2.0-rc.1-startup.json
```

By default the script first runs the existing `scripts/prod/package-smoke.mjs` once as a semantic preflight. That preflight is excluded from timings. The measurement then uses an isolated persistent data directory, measures first/cold readiness, creates a real persisted game, cleanly restarts the package, verifies recovery over the Admin WebSocket, and samples aggregate process-tree RSS/%CPU.

For serious release results, run on Linux. The process sampler uses `ps`; Windows/macOS remain manual acceptance environments for 0.2.0 rather than benchmark authorities.

## Reproduction discipline

For the authoritative three-run wrapper, set the real production action contract first:

```bash
RC_LABEL=0.2.0-rc.1 \
BASE_URL=http://127.0.0.1:8080 \
ACTION_BURST=10 \
ACTION_MESSAGE_JSON='<exact production Admin action JSON>' \
EXPECTED_ACTION_TYPE='<expected TV frame type>' \
EXPECTED_ACTION_FRAMES=10 \
scripts/perf/run-release-baseline.sh
```

For every RC benchmark record:

1. machine model, CPU, RAM, OS/kernel and architecture;
2. power/performance mode and relevant background-service assumptions;
3. application version, build code, full Git SHA and artifact SHA-256;
4. k6 version and benchmark-script commit;
5. workload parameters and any action-frame JSON/type used;
6. raw run count and results from at least three comparable repetitions;
7. deviations, failures, retries or environmental anomalies—do not silently discard them.

Baseline JSON files may be stored under `perf/baselines/` when they are useful engineering evidence. Do not commit giant transient k6 logs or package data directories.
