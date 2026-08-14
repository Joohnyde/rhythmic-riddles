$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $RepoRoot

function Stop-BackendPort {
  Write-Host 'Stop signal received. Shutting down backend...'
  & docker exec cestereg-dev bash -lc 'fuser -k 8080/tcp || true' *> $null
}

try {
  & docker compose up -d db dev
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

  # Kill whatever holds 8080 inside the container.
  & docker exec cestereg-dev bash -lc "if ss -lptn | grep -q ':8080'; then echo 'Port 8080 in use inside container. Killing listener...'; fuser -k 8080/tcp || true; fi"
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

  # Run backend. This stays attached until Spring Boot exits or the user stops the script.
  & docker exec -i cestereg-dev bash -lc "cd apps/backend && mvn spring-boot:run"
  exit $LASTEXITCODE
}
finally {
  Stop-BackendPort
}
