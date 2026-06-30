$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir '..\..')
Set-Location $RepoRoot

function Cleanup {
  Write-Host "Stop signal received. Shutting down backend..."
  docker exec cestereg-dev bash -lc 'fuser -k 8080/tcp || true'
}

try {
  docker compose up -d db dev

  docker exec cestereg-dev bash -lc @'
set -euo pipefail
if ss -lptn | grep -q ":8080"; then
  echo "Port 8080 in use inside container. Killing listener..."
  fuser -k 8080/tcp || true
fi
'@

  docker exec cestereg-dev bash -lc @'
set -euo pipefail
cd apps/backend
mvn spring-boot:run
'@
} finally {
  Cleanup
}
