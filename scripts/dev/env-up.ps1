$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $RepoRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  Write-Error 'ERROR: docker not found. Install Docker first.'
  exit 1
}

& docker info *> $null
if ($LASTEXITCODE -ne 0) {
  Write-Error 'ERROR: Docker daemon not reachable. Is Docker running?'
  exit 1
}

if (-not (Test-Path 'docker-compose.yml' -PathType Leaf)) {
  Write-Error 'ERROR: docker-compose.yml not found in repo root.'
  exit 1
}

Write-Host 'Starting dev environment (db + dev)...'
& docker compose up -d --build db dev
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ''
Write-Host '✅ Dev environment is up.'
Write-Host 'DB (host):     localhost:2345'
Write-Host 'DB (docker):   db:5432'
Write-Host 'Frontend:      http://localhost:4200 (when you run Angular)'
Write-Host 'Backend:       http://localhost:8080 (when you run Spring Boot)'
