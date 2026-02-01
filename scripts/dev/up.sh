#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$REPO_ROOT"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker not found. Install Docker first."
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker daemon not reachable. Is Docker running?"
  exit 1
fi

if [ ! -f docker-compose.yml ]; then
  echo "ERROR: docker-compose.yml not found in repo root."
  exit 1
fi

echo "Starting dev environment (db + dev)..."
docker compose up -d --build db dev

echo
echo "✅ Dev environment is up."
echo "DB (host):     localhost:2345"
echo "DB (docker):   db:5432"
echo "Frontend:      http://localhost:4200 (when you run Angular)"
echo "Backend:       http://localhost:8080 (when you run Spring Boot)"

