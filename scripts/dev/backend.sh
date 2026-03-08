#!/usr/bin/env bash
set -euo pipefail

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
'
