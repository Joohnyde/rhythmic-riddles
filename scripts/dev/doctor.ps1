$ErrorActionPreference = 'Continue'
Set-StrictMode -Version Latest

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir '..\..')
Set-Location $RepoRoot

$ExitCode = 0

function Ok($Message)   { Write-Host "✔ $Message" -ForegroundColor Green }
function Warn($Message) { Write-Host "⚠ $Message" -ForegroundColor Yellow }
function Fail($Message) { Write-Host "✘ $Message" -ForegroundColor Red; $script:ExitCode = 1 }
function Info($Message) { Write-Host "ℹ $Message" -ForegroundColor Blue }

function Section($Message) {
  Write-Host ""
  Write-Host "== $Message ==" -ForegroundColor Blue
}

function Need-Cmd($Command) {
  if (Get-Command $Command -ErrorAction SilentlyContinue) {
    Ok "Found command: $Command"
  } else {
    Fail "Missing command: $Command"
  }
}

function File-Exists($Path) {
  if (Test-Path $Path -PathType Leaf) {
    Ok "Found file: $Path"
  } else {
    Fail "Missing file: $Path"
  }
}

function Dir-Exists($Path) {
  if (Test-Path $Path -PathType Container) {
    Ok "Found folder: $Path"
  } else {
    Fail "Missing folder: $Path"
  }
}

function Grep-Warn($Needle, $File, $Message) {
  if ((Test-Path $File -PathType Leaf) -and ((Get-Content -Raw $File) -match $Needle)) {
    Ok $Message
  } else {
    Warn "$Message (not detected)"
  }
}

function Command-Succeeds([scriptblock]$ScriptBlock) {
  try {
    & $ScriptBlock *> $null
    return $LASTEXITCODE -eq 0 -or $null -eq $LASTEXITCODE
  } catch {
    return $false
  }
}

Section "Host prerequisites"
Need-Cmd docker

if (Command-Succeeds { docker info }) {
  Ok "Docker daemon is running"
} else {
  Fail "Docker daemon not reachable (start Docker Desktop / docker service)"
}

if (Command-Succeeds { docker compose version }) {
  $composeVersion = (docker compose version | Select-Object -First 1)
  Ok "Docker Compose available: $composeVersion"
} else {
  Fail "Docker Compose not available via 'docker compose'"
}

Section "Project files"
File-Exists docker-compose.yml
Dir-Exists .devcontainer
File-Exists .devcontainer/Dockerfile
File-Exists .devcontainer/devcontainer.json

if (Test-Path db -PathType Container) {
  Ok "Found DB init folder: db/"
  $sqlFiles = Get-ChildItem db -Filter '*.sql' -File -ErrorAction SilentlyContinue
  if ($sqlFiles.Count -gt 0) {
    Ok "Found SQL init scripts in db/"
  } else {
    Warn "db/ exists but no *.sql files found"
  }
} else {
  Warn "DB init folder 'db/' not found (if you expect init scripts, create it and mount it)"
}

Section "Devcontainer config sanity"
if (Test-Path .devcontainer/devcontainer.json -PathType Leaf) {
  Grep-Warn '"service"\s*:\s*"dev"' .devcontainer/devcontainer.json "devcontainer.json targets service 'dev'"
  Grep-Warn 'dockerComposeFile' .devcontainer/devcontainer.json "devcontainer.json uses dockerComposeFile"
} else {
  Warn "No .devcontainer/devcontainer.json found"
}

Section "Compose services sanity"
if (Command-Succeeds { docker compose config }) {
  Ok "docker compose config parses successfully"
} else {
  Fail "docker compose config failed (syntax error or invalid compose file)"
}

$services = @(docker compose config --services 2>$null)
if ($services -contains 'db') { Ok "Compose has service: db" } else { Fail "Compose missing service: db" }
if ($services -contains 'dev') { Ok "Compose has service: dev" } else { Fail "Compose missing service: dev" }

Section "Bring up services (non-destructive)"
Info "Ensuring db + dev are up (safe even if already running)"
docker compose up -d db dev *> $null
if ($LASTEXITCODE -eq 0) { Ok "Services started (or already running)" } else { Fail "Could not start services db + dev" }

Section "Runtime container checks"
$dbContainer = (docker compose ps -q db 2>$null | Select-Object -First 1)
if ($dbContainer) {
  $dbRunning = (docker inspect -f '{{.State.Running}}' $dbContainer 2>$null | Select-Object -First 1)
  if ($dbRunning -eq 'true') { Ok "db service is running" } else { Fail "db service is NOT running (check: docker compose logs db)" }
} else {
  Fail "db service is NOT running (check: docker compose logs db)"
}

$devContainer = (docker compose ps -q dev 2>$null | Select-Object -First 1)
if ($devContainer) {
  $devRunning = (docker inspect -f '{{.State.Running}}' $devContainer 2>$null | Select-Object -First 1)
  if ($devRunning -eq 'true') { Ok "dev service is running" } else { Fail "dev service is NOT running (check: docker compose logs dev)" }
} else {
  Fail "dev service is NOT running (check: docker compose logs dev)"
}

Section "Internal Docker networking + DNS"
if (Command-Succeeds { docker compose exec -T dev sh -lc "getent hosts db >/dev/null 2>&1" }) {
  Ok "dev -> db DNS resolution works (getent hosts db)"
} else {
  Fail "dev cannot resolve 'db' (DNS). Compose network issue."
}

if (Command-Succeeds { docker compose exec -T dev sh -lc "pg_isready -h db -p 5432 >/dev/null 2>&1" }) {
  Ok "dev -> db:5432 responds (pg_isready)"
} else {
  Fail "dev -> db:5432 not responding (pg_isready)"
}

Section "Toolchain inside dev container"
if (Command-Succeeds { docker compose exec -T dev sh -lc "java -version >/dev/null 2>&1" }) { Ok "Java available in dev container" } else { Fail "Java missing in dev container" }
if (Command-Succeeds { docker compose exec -T dev sh -lc "mvn -version >/dev/null 2>&1" }) { Ok "Maven available in dev container" } else { Fail "Maven missing in dev container" }
if (Command-Succeeds { docker compose exec -T dev sh -lc "node -v >/dev/null 2>&1" }) { Ok "Node available in dev container" } else { Fail "Node missing in dev container" }
if (Command-Succeeds { docker compose exec -T dev sh -lc "npm -v >/dev/null 2>&1" }) { Ok "npm available in dev container" } else { Fail "npm missing in dev container" }
if (Command-Succeeds { docker compose exec -T dev sh -lc "ng version >/dev/null 2>&1" }) { Ok "Angular CLI available in dev container" } else { Warn "Angular CLI not found (ok if you use npx / project-local CLI)" }
if (Command-Succeeds { docker compose exec -T dev sh -lc "psql --version >/dev/null 2>&1" }) { Ok "psql client available in dev container" } else { Warn "psql client missing in dev container (recommended)" }

Section "DB init scripts executed (best-effort verification)"
$DbName = (docker compose exec -T db sh -lc 'printf "%s" "$POSTGRES_DB"' 2>$null)
$DbUser = (docker compose exec -T db sh -lc 'printf "%s" "$POSTGRES_USER"' 2>$null)

if ([string]::IsNullOrWhiteSpace($DbName) -or [string]::IsNullOrWhiteSpace($DbUser)) {
  Warn "Could not read POSTGRES_DB/POSTGRES_USER from db container"
} else {
  Ok "DB env detected: db=$DbName user=$DbUser"
}

if (Command-Succeeds { docker compose exec -T db sh -lc "ls -1 /docker-entrypoint-initdb.d >/dev/null 2>&1" }) {
  $countRaw = (docker compose exec -T db sh -lc 'ls -1 /docker-entrypoint-initdb.d 2>/dev/null | wc -l | tr -d " "' 2>$null)
  $count = 0
  [int]::TryParse(($countRaw | Select-Object -First 1), [ref]$count) | Out-Null
  if ($count -gt 0) {
    Ok "Init directory has $count file(s) mounted in /docker-entrypoint-initdb.d"
  } else {
    Warn "Init directory exists but is empty inside container (mount path may be wrong)"
  }
} else {
  Warn "No /docker-entrypoint-initdb.d inside db container (you might not have mounted init scripts)"
}

if (-not [string]::IsNullOrWhiteSpace($DbName) -and -not [string]::IsNullOrWhiteSpace($DbUser)) {
  $query = "select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind in ('r','p','v','m','S');"
  if (Command-Succeeds { docker compose exec -T db sh -lc "psql -U `"$DbUser`" -d `"$DbName`" -Atc `"$query`" >/dev/null 2>&1" }) {
    $objCountRaw = (docker compose exec -T db sh -lc "psql -U `"$DbUser`" -d `"$DbName`" -Atc `"$query`" | tr -d ' '" 2>$null)
    $objCount = 0
    [int]::TryParse(($objCountRaw | Select-Object -First 1), [ref]$objCount) | Out-Null
    if ($objCount -gt 0) {
      Ok "DB public schema has $objCount objects (tables/views/sequences/etc.)"
    } else {
      Warn "DB public schema has 0 objects (init scripts may not have created anything)"
    }
  } else {
    Warn "Could not query DB objects (psql query failed)"
  }
} else {
  Warn "Could not query DB objects (missing DB env)"
}

Section "NetBeans configuration (informational)"

$BackendDir = 'apps/backend'
$NbDockerActionsFile = Join-Path $BackendDir 'nbactions-docker.xml'

if (Test-Path $BackendDir -PathType Container) {
  Ok "Found backend folder: $BackendDir"
} else {
  Warn "Backend folder not found at $BackendDir (expected app/backend)."
}

if (Test-Path $NbDockerActionsFile -PathType Leaf) {
  Ok "Found NetBeans Docker actions file: $NbDockerActionsFile"
  $nb = Get-Content -Raw $NbDockerActionsFile

  if ($nb -match '<actionName>run</actionName>') {
    Ok "nbactions-docker.xml defines <actionName>run</actionName>"
  } else {
    Warn "nbactions-docker.xml missing <actionName>run</actionName>"
  }

  if ($nb -match 'org\.codehaus\.mojo:exec-maven-plugin:3\.1\.0:exec') {
    Ok "nbactions-docker.xml uses exec-maven-plugin:3.1.0:exec"
  } else {
    Warn "nbactions-docker.xml does not reference exec-maven-plugin:3.1.0:exec"
  }

  if ($nb -match '<exec\.executable>\s*bash\s*</exec\.executable>') {
    Ok "nbactions-docker.xml sets exec.executable to bash"
  } else {
    Warn "nbactions-docker.xml does not set exec.executable to bash (or formatting differs)"
  }

  if ($nb -match '<exec\.args>\s*\.\./\.\./scripts/dev/backend\.sh\s*</exec\.args>') {
    Ok "nbactions-docker.xml points to ../../scripts/dev/run-backend.sh"
  } else {
    Warn "nbactions-docker.xml does not point to ../../scripts/dev/run-backend.sh (or formatting differs)"
  }
} else {
  Warn "NetBeans Docker actions file not found: $NbDockerActionsFile (fine if not using NetBeans)"
}

Section "VS Code Dev Containers (informational)"

if (Get-Command code -ErrorAction SilentlyContinue) {
  $codeVersion = (code --version | Select-Object -First 1)
  Ok "VS Code CLI found: $codeVersion"

  $extensions = @(code --list-extensions 2>$null)
  if ($extensions -contains 'ms-vscode-remote.remote-containers') {
    Ok "VS Code Dev Containers extension is installed (ms-vscode-remote.remote-containers)"
  } else {
    Warn "VS Code Dev Containers extension NOT detected. Install: ms-vscode-remote.remote-containers"
  }

  $extensionsWithVersions = @(code --list-extensions --show-versions 2>$null)
  $devContainerVersion = $extensionsWithVersions | Where-Object { $_ -match '^ms-vscode-remote\.remote-containers@' } | Select-Object -First 1
  if ($devContainerVersion) {
    Info "Dev Containers version: $devContainerVersion"
  }
} else {
  Warn "VS Code CLI ('code') not found (fine if not using VS Code)"
}

Section "IntelliJ (informational)"
if (Get-Command idea -ErrorAction SilentlyContinue) {
  Ok "IntelliJ launcher 'idea' found"
} else {
  Warn "IntelliJ launcher 'idea' not found (fine if you don't use IntelliJ)"
}
Warn "IntelliJ: recommended workflow is host IDE + run/build inside cestereg-dev via scripts (or attach a terminal to container)."

Write-Host ""
if ($ExitCode -eq 0) {
  Write-Host "Doctor result: OK" -ForegroundColor Green
} else {
  Write-Host "Doctor result: issues found" -ForegroundColor Red
}

exit $ExitCode
