$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $RepoRoot

if (-not (Test-Path 'docker-compose.yml' -PathType Leaf)) {
  Write-Error 'ERROR: docker-compose.yml not found in repo root.'
  exit 1
}

Write-Host 'Stopping and removing containers (keeping volumes / DB data)...'
& docker compose down
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ''
Write-Host '✅ Containers stopped/removed.'
Write-Host 'ℹ️ DB data is preserved (volumes kept).'
Write-Host '⚠️ If you ever want to wipe DB data: docker compose down -v'
