$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $RepoRoot

Write-Host '== Running all tests (placeholder) =='

if (Test-Path 'apps/quiz-game/backend' -PathType Container) {
  Write-Host 'Backend tests...'
  Push-Location 'apps/quiz-game/backend'
  try { Write-Host 'TODO: mvn test' }
  finally { Pop-Location }
}

if (Test-Path 'apps/quiz-game/frontend' -PathType Container) {
  Write-Host 'Frontend tests...'
  Push-Location 'apps/quiz-game/frontend'
  try { Write-Host 'TODO: npm test' }
  finally { Pop-Location }
}

Write-Host 'Done.'
