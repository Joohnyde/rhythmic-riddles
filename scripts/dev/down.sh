#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$REPO_ROOT"

if [ ! -f docker-compose.yml ]; then
  echo "ERROR: docker-compose.yml not found in repo root."
  exit 1
fi

echo "Stopping and removing containers (keeping volumes / DB data)..."
docker compose down

echo
echo "✅ Containers stopped/removed."
echo "ℹ️ DB data is preserved (volumes kept)."
echo "⚠️ If you ever want to wipe DB data: docker compose down -v"

