#!/usr/bin/env bash
set -euo pipefail

echo "Starting dev services..."
docker compose -f infra/docker-compose/local-dev.compose.yml up -d
echo "Done."
echo "Postgres: localhost:5432"
echo "pgAdmin: http://localhost:5050 (default creds are placeholders in compose file)"
