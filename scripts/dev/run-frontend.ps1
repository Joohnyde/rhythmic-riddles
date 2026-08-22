$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $RepoRoot

function Stop-FrontendPort {
  Write-Host 'Stop signal received. Shutting down frontend...'
  & docker exec cestereg-dev bash -lc 'fuser -k 4200/tcp || true' *> $null
}

try {
  & docker compose up -d db dev
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

  # Kill whatever holds 4200 inside the container.
  & docker exec cestereg-dev bash -lc "if ss -lptn | grep -q ':4200'; then echo 'Port 4200 in use inside container. Killing listener...'; fuser -k 4200/tcp || true; fi"
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

  # Run frontend. This stays attached until Angular exits or the user stops the script.
  & docker exec -i cestereg-dev bash -lc "cd apps/frontend && npm ci && npm start"
  exit $LASTEXITCODE
}
finally {
  Stop-FrontendPort
}
