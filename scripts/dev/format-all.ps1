$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$BackendDir = if ($env:BACKEND_DIR) { $env:BACKEND_DIR } else { 'apps/backend' }
$FrontendDir = if ($env:FRONTEND_DIR) { $env:FRONTEND_DIR } else { 'apps/frontend' }
$RootDir = Resolve-Path (Join-Path $PSScriptRoot '..\..')

Write-Host "===> Repo root: $RootDir"
Write-Host "===> Backend dir: $BackendDir"
Write-Host "===> Frontend dir: $FrontendDir"
Write-Host ''

function Invoke-InDirectory {
  param(
    [Parameter(Mandatory=$true)][string]$Directory,
    [Parameter(Mandatory=$true)][scriptblock]$Command
  )
  Push-Location $Directory
  try { & $Command }
  finally { Pop-Location }
}

function Run-Backend {
  $dir = Join-Path $RootDir $BackendDir
  if (-not (Test-Path $dir -PathType Container)) {
    Write-Host "===> Backend dir not found, skipping: $dir"
    return
  }

  Write-Host '===> [Backend] Formatting (Spotless apply)'
  Invoke-InDirectory $dir { & mvn -B -ntp spotless:apply; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }

  Write-Host '===> [Backend] Running checkers (Spotless check + Checkstyle in CI profile)'
  Invoke-InDirectory $dir { & mvn -B -ntp spotless:check; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }
  Invoke-InDirectory $dir { & mvn -B -ntp checkstyle:check; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }
  Write-Host ''
}

function Run-Frontend {
  $dir = Join-Path $RootDir $FrontendDir
  if (-not (Test-Path $dir -PathType Container)) {
    Write-Host "===> Frontend dir not found, skipping: $dir"
    return
  }

  Write-Host '===> [Frontend] Installing deps (npm ci if lockfile exists, otherwise npm i)'
  if (Test-Path (Join-Path $dir 'package-lock.json') -PathType Leaf) {
    Invoke-InDirectory $dir { & npm ci; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }
  } else {
    Invoke-InDirectory $dir { & npm install; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }
  }

  Write-Host '===> [Frontend] Formatting (Prettier write)'
  Invoke-InDirectory $dir { & npm run format; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }

  Write-Host '===> [Frontend] Auto-fixing lint where possible (ESLint --fix)'
  Invoke-InDirectory $dir { & npm run lint:fix; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }

  Write-Host '===> [Frontend] Running checkers (Prettier check + ESLint)'
  Invoke-InDirectory $dir { & npm run format:check; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }
  Invoke-InDirectory $dir { & npm run lint; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }
  Write-Host ''
}

Write-Host '===> Running format-all'
Run-Backend
Run-Frontend
Write-Host '===> Done ✅'
