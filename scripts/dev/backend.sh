#!/usr/bin/env bash
set -euo pipefail

docker compose up -d db dev

docker exec cestereg-dev bash -lc "cd apps/backend && mvn spring-boot:run"
