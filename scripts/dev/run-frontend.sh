#!/usr/bin/env bash
set -euo pipefail

# 1. Guarantee we are in the repo root so docker compose finds the .yml file
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

cleanup() {
  echo "Stop signal received. Shutting down frontend..."
  docker exec cestereg-dev bash -lc 'fuser -k 4200/tcp || true'
}

# 2. Catch the Stop button signals
trap cleanup SIGINT SIGTERM EXIT

docker compose up -d db dev

# kill whatever holds 4200 inside container (idempotent)
docker exec cestereg-dev bash -lc '
set -euo pipefail
if ss -lptn | grep -q ":4200"; then
  echo "Port 4200 in use inside container. Killing listener..."
  fuser -k 4200/tcp || true
fi
'

# run frontend (no TTY allocation)
docker exec cestereg-dev bash -lc '
set -euo pipefail
cd apps/frontend
npm install --prefer-offline
npm start
' & wait $!
