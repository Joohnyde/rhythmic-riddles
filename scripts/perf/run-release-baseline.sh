#!/usr/bin/env bash
set -euo pipefail

# Reproducible wrapper for the controlled Linux RC benchmark.
# It intentionally does not install k6 or start the application; those are operator-controlled inputs.

: "${BASE_URL:=http://127.0.0.1:8080}"
: "${RC_LABEL:?Set RC_LABEL, e.g. 0.2.0-rc.1}"
: "${RUNS:=3}"
: "${ACTION_BURST:?Set ACTION_BURST to a positive representative burst size}"
: "${ACTION_MESSAGE_JSON:?Set ACTION_MESSAGE_JSON to an exact supported production Admin frame}"
: "${EXPECTED_ACTION_TYPE:?Set EXPECTED_ACTION_TYPE to the TV frame type proving propagation}"
: "${EXPECTED_ACTION_FRAMES:=$ACTION_BURST}"

if ! command -v k6 >/dev/null 2>&1; then
  echo "k6 is required and must be installed/pinned by the benchmark operator." >&2
  exit 1
fi
if ! [[ "$RUNS" =~ ^[1-9][0-9]*$ ]]; then
  echo "RUNS must be a positive integer; received: $RUNS" >&2
  exit 1
fi
if ! [[ "$ACTION_BURST" =~ ^[1-9][0-9]*$ ]] || ! [[ "$EXPECTED_ACTION_FRAMES" =~ ^[1-9][0-9]*$ ]]; then
  echo "ACTION_BURST and EXPECTED_ACTION_FRAMES must be positive integers." >&2
  exit 1
fi

out_dir="perf/baselines/${RC_LABEL}"
mkdir -p "$out_dir"

printf 'k6 version: '
k6 version
printf 'BASE_URL: %s\n' "$BASE_URL"
printf 'RC_LABEL: %s\n' "$RC_LABEL"
printf 'runs per serious scenario: %s\n' "$RUNS"

# Fast correctness preflight once.
BASE_URL="$BASE_URL" k6 run \
  --summary-export "$out_dir/smoke.json" \
  perf/k6/smoke.js

for run in $(seq 1 "$RUNS"); do
  echo "=== websocket/reconnect run ${run}/${RUNS} ==="
  BASE_URL="$BASE_URL" k6 run \
    --summary-export "$out_dir/websocket-${run}.json" \
    perf/k6/websocket-load.js

  echo "=== lifecycle/action run ${run}/${RUNS} ==="
  BASE_URL="$BASE_URL" k6 run \
    --summary-export "$out_dir/lifecycle-${run}.json" \
    perf/k6/lifecycle.js
done

echo "Raw k6 summaries written under $out_dir"
echo "Record machine/build/artifact identity and representative median/range in docs/reports/releases/0.2.0-performance.md."
