$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# -----------------------------
# Config: adjust these if needed
# -----------------------------
$BackendDir = if ($env:BACKEND_DIR) { $env:BACKEND_DIR } else { 'apps/backend' }
$FrontendDir = if ($env:FRONTEND_DIR) { $env:FRONTEND_DIR } else { 'apps/frontend' }

# If your backend is at repo root, set BACKEND_DIR="."
# If your frontend is elsewhere, set FRONTEND_DIR accordingly.

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Resolve-Path (Join-Path $ScriptDir '..\..')

Write-Host "===> Repo root: $RootDir"
Write-Host "===> Backend dir: $BackendDir"
Write-Host "===> Frontend dir: $FrontendDir"
Write-Host ""

function Run-Backend {
  $dir = Join-Path $RootDir $BackendDir
  if (-not (Test-Path $dir -PathType Container)) {
    Write-Host "===> Backend dir not found, skipping: $dir"
    return
  }

  Write-Host "===> [Backend] Formatting (Spotless apply)"
  Push-Location $dir
  try { mvn -B -ntp spotless:apply } finally { Pop-Location }

  Write-Host "===> [Backend] Running checkers (Spotless check + Checkstyle in CI profile)"
  Push-Location $dir
  try { mvn -B -ntp spotless:check } finally { Pop-Location }

  Push-Location $dir
  try { mvn -B -ntp checkstyle:check } finally { Pop-Location }

  Write-Host ""
}

function Run-Frontend {
  $dir = Join-Path $RootDir $FrontendDir
  if (-not (Test-Path $dir -PathType Container)) {
    Write-Host "===> Frontend dir not found, skipping: $dir"
    return
  }

  Write-Host "===> [Frontend] Installing deps (npm ci if lockfile exists, otherwise npm i)"
  Push-Location $dir
  try {
    if (Test-Path (Join-Path $dir 'package-lock.json') -PathType Leaf) {
      npm ci
    } else {
      npm install
    }
  } finally { Pop-Location }

  Write-Host "===> [Frontend] Formatting (Prettier write)"
  Push-Location $dir
  try { npm run format } finally { Pop-Location }

  Write-Host "===> [Frontend] Auto-fixing lint where possible (ESLint --fix)"
  Push-Location $dir
  try { npm run lint:fix } finally { Pop-Location }

  Write-Host "===> [Frontend] Running checkers (Prettier check + ESLint)"
  Push-Location $dir
  try { npm run format:check } finally { Pop-Location }

  Push-Location $dir
  try { npm run lint } finally { Pop-Location }

  Write-Host ""
}

Write-Host "===> Running format-all"
Run-Backend
Run-Frontend
Write-Host "===> Done ✅"
