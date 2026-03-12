#!/usr/bin/env bash
set -euo pipefail

# 1. Guarantee we are in the repo root so docker compose finds the .yml file
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

cleanup() {
  echo "Stop signal received. Shutting down backend..."
  docker exec cestereg-dev bash -lc 'fuser -k 8080/tcp || true'
}

# 2. Catch the Stop button signals
trap cleanup SIGINT SIGTERM EXIT

docker compose up -d db dev

# kill whatever holds 8080 inside container (idempotent)
docker exec cestereg-dev bash -lc '
set -euo pipefail
if ss -lptn | grep -q ":8080"; then
  echo "Port 8080 in use inside container. Killing listener..."
  fuser -k 8080/tcp || true
fi
'

# run backend (no TTY allocation)
docker exec cestereg-dev bash -lc '
set -euo pipefail
cd apps/backend
mvn spring-boot:run
' & wait $!
