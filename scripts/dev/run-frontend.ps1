$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir '..\..')
Set-Location $RepoRoot

function Cleanup {
  Write-Host "Stop signal received. Shutting down frontend..."
  docker exec cestereg-dev bash -lc 'fuser -k 4200/tcp || true'
}

try {
  docker compose up -d db dev

  docker exec cestereg-dev bash -lc @'
set -euo pipefail
if ss -lptn | grep -q ":4200"; then
  echo "Port 4200 in use inside container. Killing listener..."
  fuser -k 4200/tcp || true
fi
'@

  docker exec cestereg-dev bash -lc @'
set -euo pipefail
cd apps/frontend
npm install --prefer-offline
npm start
'@
} finally {
  Cleanup
}
