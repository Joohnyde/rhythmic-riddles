#!/usr/bin/env bash
set -euo pipefail

docker compose up -d db dev

docker compose exec dev bash -lc "
cd apps/frontend
npm install --prefer-offline
npm start
"

