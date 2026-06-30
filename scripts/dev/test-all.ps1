$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Write-Host "== Running all tests (placeholder) =="

# Backend
if (Test-Path 'apps/quiz-game/backend' -PathType Container) {
  Write-Host "Backend tests..."
  Push-Location 'apps/quiz-game/backend'
  try {
    Write-Host "TODO: mvn test"
  } finally {
    Pop-Location
  }
}

# Frontend
if (Test-Path 'apps/quiz-game/frontend' -PathType Container) {
  Write-Host "Frontend tests..."
  Push-Location 'apps/quiz-game/frontend'
  try {
    Write-Host "TODO: npm test"
  } finally {
    Pop-Location
  }
}

Write-Host "Done."
