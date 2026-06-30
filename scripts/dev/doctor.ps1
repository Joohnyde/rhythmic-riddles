$ErrorActionPreference = 'Continue'
Set-StrictMode -Version Latest

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $RepoRoot
$script:ExitCode = 0

function Ok($Message) { Write-Host "✔ $Message" -ForegroundColor Green }
function Warn($Message) { Write-Host "⚠ $Message" -ForegroundColor Yellow }
function Fail($Message) { Write-Host "✘ $Message" -ForegroundColor Red; $script:ExitCode = 1 }
function Info($Message) { Write-Host "ℹ $Message" -ForegroundColor Blue }
function Section($Message) { Write-Host ''; Write-Host "== $Message ==" -ForegroundColor Blue }

function Test-CommandExists($Command) {
  if (Get-Command $Command -ErrorAction SilentlyContinue) { Ok "Found command: $Command" } else { Fail "Missing command: $Command" }
}
function Test-FileExists($Path) {
  if (Test-Path $Path -PathType Leaf) { Ok "Found file: $Path" } else { Fail "Missing file: $Path" }
}
function Test-DirExists($Path) {
  if (Test-Path $Path -PathType Container) { Ok "Found folder: $Path" } else { Fail "Missing folder: $Path" }
}
function Test-RegexWarn($Pattern, $Path, $Message) {
  if ((Test-Path $Path -PathType Leaf) -and (Select-String -Path $Path -Pattern $Pattern -Quiet)) { Ok $Message } else { Warn "$Message (not detected)" }
}
function Invoke-Check($SuccessMessage, $FailMessage, [scriptblock]$Command) {
  & $Command *> $null
  if ($LASTEXITCODE -eq 0) { Ok $SuccessMessage } else { Fail $FailMessage }
}
function Invoke-WarnCheck($SuccessMessage, $WarnMessage, [scriptblock]$Command) {
  & $Command *> $null
  if ($LASTEXITCODE -eq 0) { Ok $SuccessMessage } else { Warn $WarnMessage }
}
function Get-ExternalOutput([scriptblock]$Command) {
  $output = & $Command 2>$null
  return ($output -join "`n")
}

Section 'Host prerequisites'
Test-CommandExists docker
Invoke-Check 'Docker daemon is running' 'Docker daemon not reachable (start Docker Desktop / docker service)' { & docker info }
$composeVersion = Get-ExternalOutput { & docker compose version }
if ($LASTEXITCODE -eq 0) { Ok "Docker Compose available: $($composeVersion.Split("`n")[0])" } else { Fail "Docker Compose not available via 'docker compose'" }

Section 'Project files'
Test-FileExists 'docker-compose.yml'
Test-DirExists '.devcontainer'
Test-FileExists '.devcontainer/Dockerfile'
Test-FileExists '.devcontainer/devcontainer.json'

if (Test-Path 'db' -PathType Container) {
  Ok 'Found DB init folder: db/'
  if (Get-ChildItem 'db' -Filter '*.sql' -File -ErrorAction SilentlyContinue) { Ok 'Found SQL init scripts in db/' } else { Warn 'db/ exists but no *.sql files found' }
} else {
  Warn "DB init folder 'db/' not found (if you expect init scripts, create it and mount it)"
}

Section 'Devcontainer config sanity'
if (Test-Path '.devcontainer/devcontainer.json' -PathType Leaf) {
  Test-RegexWarn '"service"\s*:\s*"dev"' '.devcontainer/devcontainer.json' "devcontainer.json targets service 'dev'"
  Test-RegexWarn 'dockerComposeFile' '.devcontainer/devcontainer.json' 'devcontainer.json uses dockerComposeFile'
} else {
  Warn 'No .devcontainer/devcontainer.json found'
}

Section 'Compose services sanity'
Invoke-Check 'docker compose config parses successfully' 'docker compose config failed (syntax error or invalid compose file)' { & docker compose config }
$services = @(Get-ExternalOutput { & docker compose config --services } | ForEach-Object { $_.Split("`n") } | Where-Object { $_ })
if ($services -contains 'db') { Ok 'Compose has service: db' } else { Fail 'Compose missing service: db' }
if ($services -contains 'dev') { Ok 'Compose has service: dev' } else { Fail 'Compose missing service: dev' }

Section 'Bring up services (non-destructive)'
Info 'Ensuring db + dev are up (safe even if already running)'
& docker compose up -d db dev *> $null
if ($LASTEXITCODE -eq 0) { Ok 'Services started (or already running)' } else { Fail 'Could not start services db + dev' }

Section 'Runtime container checks'
$dbId = (Get-ExternalOutput { & docker compose ps -q db }).Trim()
if ($dbId -and ((Get-ExternalOutput { & docker inspect -f '{{.State.Running}}' $dbId }).Trim() -eq 'true')) { Ok 'db service is running' } else { Fail 'db service is NOT running (check: docker compose logs db)' }
$devId = (Get-ExternalOutput { & docker compose ps -q dev }).Trim()
if ($devId -and ((Get-ExternalOutput { & docker inspect -f '{{.State.Running}}' $devId }).Trim() -eq 'true')) { Ok 'dev service is running' } else { Fail 'dev service is NOT running (check: docker compose logs dev)' }

Section 'Internal Docker networking + DNS'
Invoke-Check 'dev -> db DNS resolution works (getent hosts db)' "dev cannot resolve 'db' (DNS). Compose network issue." { & docker compose exec -T dev sh -lc 'getent hosts db >/dev/null 2>&1' }
Invoke-Check 'dev -> db:5432 responds (pg_isready)' 'dev -> db:5432 not responding (pg_isready)' { & docker compose exec -T dev sh -lc 'pg_isready -h db -p 5432 >/dev/null 2>&1' }

Section 'Toolchain inside dev container'
Invoke-Check 'Java available in dev container' 'Java missing in dev container' { & docker compose exec -T dev sh -lc 'java -version >/dev/null 2>&1' }
Invoke-Check 'Maven available in dev container' 'Maven missing in dev container' { & docker compose exec -T dev sh -lc 'mvn -version >/dev/null 2>&1' }
Invoke-Check 'Node available in dev container' 'Node missing in dev container' { & docker compose exec -T dev sh -lc 'node -v >/dev/null 2>&1' }
Invoke-Check 'npm available in dev container' 'npm missing in dev container' { & docker compose exec -T dev sh -lc 'npm -v >/dev/null 2>&1' }
Invoke-WarnCheck 'Angular CLI available in dev container' 'Angular CLI not found (ok if you use npx / project-local CLI)' { & docker compose exec -T dev sh -lc 'ng version >/dev/null 2>&1' }
Invoke-WarnCheck 'psql client available in dev container' 'psql client missing in dev container (recommended)' { & docker compose exec -T dev sh -lc 'psql --version >/dev/null 2>&1' }

Section 'DB init scripts executed (best-effort verification)'
$DbName = (Get-ExternalOutput { & docker compose exec -T db sh -lc 'printf "%s" "$POSTGRES_DB"' }).Trim()
$DbUser = (Get-ExternalOutput { & docker compose exec -T db sh -lc 'printf "%s" "$POSTGRES_USER"' }).Trim()
if ([string]::IsNullOrWhiteSpace($DbName) -or [string]::IsNullOrWhiteSpace($DbUser)) { Warn 'Could not read POSTGRES_DB/POSTGRES_USER from db container' } else { Ok "DB env detected: db=$DbName user=$DbUser" }

& docker compose exec -T db sh -lc 'ls -1 /docker-entrypoint-initdb.d >/dev/null 2>&1' *> $null
if ($LASTEXITCODE -eq 0) {
  $countText = (Get-ExternalOutput { & docker compose exec -T db sh -lc 'ls -1 /docker-entrypoint-initdb.d 2>/dev/null | wc -l | tr -d " "' }).Trim()
  $count = if ($countText) { [int]$countText } else { 0 }
  if ($count -gt 0) { Ok "Init directory has $count file(s) mounted in /docker-entrypoint-initdb.d" } else { Warn 'Init directory exists but is empty inside container (mount path may be wrong)' }
} else {
  Warn 'No /docker-entrypoint-initdb.d inside db container (you might not have mounted init scripts)'
}

$query = "select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind in ('r','p','v','m','S');"
& docker compose exec -T db sh -lc "psql -U \"$DbUser\" -d \"$DbName\" -Atc \"$query\" >/dev/null 2>&1" *> $null
if ($LASTEXITCODE -eq 0) {
  $objCount = (Get-ExternalOutput { & docker compose exec -T db sh -lc "psql -U \"$DbUser\" -d \"$DbName\" -Atc \"$query\" | tr -d ' '" }).Trim()
  if ([int]$objCount -gt 0) { Ok "DB public schema has $objCount objects (tables/views/sequences/etc.)" } else { Warn 'DB public schema has 0 objects (init scripts may not have created anything)' }
} else {
  Warn 'Could not query DB objects (psql query failed)'
}

Section 'NetBeans configuration (informational)'
$BackendDir = 'apps/backend'
$NbDockerActionsFile = Join-Path $BackendDir 'nbactions-docker.xml'
if (Test-Path $BackendDir -PathType Container) { Ok "Found backend folder: $BackendDir" } else { Warn "Backend folder not found at $BackendDir (expected app/backend)." }
if (Test-Path $NbDockerActionsFile -PathType Leaf) {
  Ok "Found NetBeans Docker actions file: $NbDockerActionsFile"
  Test-RegexWarn '<actionName>run</actionName>' $NbDockerActionsFile 'nbactions-docker.xml defines <actionName>run</actionName>'
  Test-RegexWarn 'org\.codehaus\.mojo:exec-maven-plugin:3\.1\.0:exec' $NbDockerActionsFile 'nbactions-docker.xml uses exec-maven-plugin:3.1.0:exec'
  Test-RegexWarn '<exec\.executable>\s*bash\s*</exec\.executable>' $NbDockerActionsFile 'nbactions-docker.xml sets exec.executable to bash'
  Test-RegexWarn '<exec\.args>\s*\.\./\.\./scripts/dev/backend\.sh\s*</exec\.args>' $NbDockerActionsFile 'nbactions-docker.xml points to ../../scripts/dev/run-backend.sh'
} else {
  Warn "NetBeans Docker actions file not found: $NbDockerActionsFile (fine if not using NetBeans)"
}

Section 'VS Code Dev Containers (informational)'
if (Get-Command code -ErrorAction SilentlyContinue) {
  $codeVersion = (Get-ExternalOutput { & code --version }).Split("`n")[0]
  Ok "VS Code CLI found: $codeVersion"
  $extensions = Get-ExternalOutput { & code --list-extensions }
  if (($extensions -split "`n") -contains 'ms-vscode-remote.remote-containers') { Ok 'VS Code Dev Containers extension is installed (ms-vscode-remote.remote-containers)' } else { Warn 'VS Code Dev Containers extension NOT detected. Install: ms-vscode-remote.remote-containers' }
  $extVersions = Get-ExternalOutput { & code --list-extensions --show-versions }
  $ver = ($extVersions -split "`n" | Where-Object { $_ -match '^ms-vscode-remote\.remote-containers@' } | Select-Object -First 1)
  if ($ver) { Info "Dev Containers version: $ver" }
} else {
  Warn "VS Code CLI ('code') not found (fine if not using VS Code)"
}

Section 'IntelliJ (informational)'
if (Get-Command idea -ErrorAction SilentlyContinue) { Ok "IntelliJ launcher 'idea' found" } else { Warn "IntelliJ launcher 'idea' not found (fine if you don't use IntelliJ)" }
Warn 'IntelliJ: recommended workflow is host IDE + run/build inside cestereg-dev via scripts (or attach a terminal to container).'

Write-Host ''
if ($script:ExitCode -eq 0) { Write-Host 'Doctor result: OK' -ForegroundColor Green } else { Write-Host 'Doctor result: issues found' -ForegroundColor Red }
exit $script:ExitCode
