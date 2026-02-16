#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$REPO_ROOT"

# ---------- formatting ----------
RED="\033[31m"; GRN="\033[32m"; YEL="\033[33m"; BLU="\033[34m"; DIM="\033[2m"; RST="\033[0m"

ok()   { echo -e "${GRN}✔${RST} $*"; }
warn() { echo -e "${YEL}⚠${RST} $*"; }
fail() { echo -e "${RED}✘${RST} $*"; EXIT_CODE=1; }
info() { echo -e "${BLU}ℹ${RST} $*"; }

EXIT_CODE=0

section() {
  echo
  echo -e "${BLU}== $* ==${RST}"
}

need_cmd() {
  local c="$1"
  if command -v "$c" >/dev/null 2>&1; then ok "Found command: $c"; else fail "Missing command: $c"; fi
}

file_exists() {
  local f="$1"
  if [ -f "$f" ]; then ok "Found file: $f"; else fail "Missing file: $f"; fi
}

dir_exists() {
  local d="$1"
  if [ -d "$d" ]; then ok "Found folder: $d"; else fail "Missing folder: $d"; fi
}

grep_warn() {
  local needle="$1"
  local file="$2"
  local msg="$3"
  if [ -f "$file" ] && grep -q "$needle" "$file"; then ok "$msg"; else warn "$msg (not detected)"; fi
}

# ---------- checks ----------
section "Host prerequisites"
need_cmd docker

if docker info >/dev/null 2>&1; then
  ok "Docker daemon is running"
else
  fail "Docker daemon not reachable (start Docker Desktop / docker service)"
fi

# Docker Compose v2 is built into docker, so check it like this:
if docker compose version >/dev/null 2>&1; then
  ok "Docker Compose available: $(docker compose version | head -n 1)"
else
  fail "Docker Compose not available via 'docker compose'"
fi

section "Project files"
file_exists docker-compose.yml
dir_exists .devcontainer
file_exists .devcontainer/Dockerfile
file_exists .devcontainer/devcontainer.json

# db init scripts are optional, but you asked to verify them
if [ -d db/ ]; then
  ok "Found DB init folder: db/"
  if ls -1 db/*.sql >/dev/null 2>&1; then
    ok "Found SQL init scripts in db/"
  else
    warn "db/ exists but no *.sql files found"
  fi
else
  warn "DB init folder 'db/' not found (if you expect init scripts, create it and mount it)"
fi

section "Devcontainer config sanity"
# informal but useful
if [ -f .devcontainer/devcontainer.json ]; then
  grep_warn "\"service\"\\s*:\\s*\"dev\"" .devcontainer/devcontainer.json "devcontainer.json targets service 'dev'"
  grep_warn "dockerComposeFile" .devcontainer/devcontainer.json "devcontainer.json uses dockerComposeFile"
else
  warn "No .devcontainer/devcontainer.json found"
fi

section "Compose services sanity"
# Check that compose knows about db/dev services
if docker compose config >/dev/null 2>&1; then
  ok "docker compose config parses successfully"
else
  fail "docker compose config failed (syntax error or invalid compose file)"
fi

if docker compose config --services | grep -qx "db"; then ok "Compose has service: db"; else fail "Compose missing service: db"; fi
if docker compose config --services | grep -qx "dev"; then ok "Compose has service: dev"; else fail "Compose missing service: dev"; fi

section "Bring up services (non-destructive)"
info "Ensuring db + dev are up (safe even if already running)"
docker compose up -d db dev >/dev/null
ok "Services started (or already running)"

section "Runtime container checks"
# Check containers are running
if [ "$(docker compose ps -q db | xargs docker inspect -f '{{.State.Running}}' 2>/dev/null)" = "true" ]; then
  ok "db service is running"
else
  fail "db service is NOT running (check: docker compose logs db)"
fi

if [ "$(docker compose ps -q dev | xargs docker inspect -f '{{.State.Running}}' 2>/dev/null)" = "true" ]; then
  ok "dev service is running"
else
  fail "dev service is NOT running (check: docker compose logs dev)"
fi

section "Internal Docker networking + DNS"
# From inside dev container, resolve db
if docker compose exec -T dev sh -lc "getent hosts db >/dev/null 2>&1"; then
  ok "dev -> db DNS resolution works (getent hosts db)"
else
  fail "dev cannot resolve 'db' (DNS). Compose network issue."
fi

# Check db port reachable from dev container
if docker compose exec -T dev sh -lc "pg_isready -h db -p 5432 >/dev/null 2>&1"; then
  ok "dev -> db:5432 responds (pg_isready)"
else
  fail "dev -> db:5432 not responding (pg_isready)"
fi

section "Toolchain inside dev container"
# These should exist inside your dev toolbox image
if docker compose exec -T dev sh -lc "java -version >/dev/null 2>&1"; then ok "Java available in dev container"; else fail "Java missing in dev container"; fi
if docker compose exec -T dev sh -lc "mvn -version >/dev/null 2>&1"; then ok "Maven available in dev container"; else fail "Maven missing in dev container"; fi
if docker compose exec -T dev sh -lc "node -v >/dev/null 2>&1"; then ok "Node available in dev container"; else fail "Node missing in dev container"; fi
if docker compose exec -T dev sh -lc "npm -v >/dev/null 2>&1"; then ok "npm available in dev container"; else fail "npm missing in dev container"; fi
if docker compose exec -T dev sh -lc "ng version >/dev/null 2>&1"; then ok "Angular CLI available in dev container"; else warn "Angular CLI not found (ok if you use npx / project-local CLI)"; fi
if docker compose exec -T dev sh -lc "psql --version >/dev/null 2>&1"; then ok "psql client available in dev container"; else warn "psql client missing in dev container (recommended)"; fi

section "DB init scripts executed (best-effort verification)"
# We read env from the running db container (no hardcoding here)
DB_NAME="$(docker compose exec -T db sh -lc 'printf "%s" "$POSTGRES_DB"')"
DB_USER="$(docker compose exec -T db sh -lc 'printf "%s" "$POSTGRES_USER"')"

if [ -z "$DB_NAME" ] || [ -z "$DB_USER" ]; then
  warn "Could not read POSTGRES_DB/POSTGRES_USER from db container"
else
  ok "DB env detected: db=$DB_NAME user=$DB_USER"
fi

# Check init scripts are mounted (only if you mounted them)
if docker compose exec -T db sh -lc "ls -1 /docker-entrypoint-initdb.d >/dev/null 2>&1"; then
  COUNT="$(docker compose exec -T db sh -lc 'ls -1 /docker-entrypoint-initdb.d 2>/dev/null | wc -l | tr -d " "')"
  if [ "${COUNT:-0}" -gt 0 ]; then
    ok "Init directory has ${COUNT} file(s) mounted in /docker-entrypoint-initdb.d"
  else
    warn "Init directory exists but is empty inside container (mount path may be wrong)"
  fi
else
  warn "No /docker-entrypoint-initdb.d inside db container (you might not have mounted init scripts)"
fi

# Check whether there are any objects in public schema (best-effort)
# This doesn't prove init scripts ran, but it's a good signal.
if docker compose exec -T db sh -lc "psql -U \"$DB_USER\" -d \"$DB_NAME\" -Atc \"select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind in ('r','p','v','m','S');\" >/dev/null 2>&1"; then
  OBJ_COUNT="$(docker compose exec -T db sh -lc "psql -U \"$DB_USER\" -d \"$DB_NAME\" -Atc \"select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind in ('r','p','v','m','S');\" | tr -d ' '")"
  if [ "${OBJ_COUNT:-0}" -gt 0 ]; then
    ok "DB public schema has ${OBJ_COUNT} objects (tables/views/sequences/etc.)"
  else
    warn "DB public schema has 0 objects (init scripts may not have created anything)"
  fi
else
  warn "Could not query DB objects (psql query failed)"
fi

section "NetBeans configuration (informational)"

BACKEND_DIR="apps/backend"
NB_DOCKER_ACTIONS_FILE="$BACKEND_DIR/nbactions-docker.xml"

if [ -d "$BACKEND_DIR" ]; then
  ok "Found backend folder: $BACKEND_DIR"
else
  warn "Backend folder not found at $BACKEND_DIR (expected app/backend)."
fi

if [ -f "$NB_DOCKER_ACTIONS_FILE" ]; then
  ok "Found NetBeans Docker actions file: $NB_DOCKER_ACTIONS_FILE"

  # Informational checks: warn if not matching; do not fail the doctor.
  if grep -q "<actionName>run</actionName>" "$NB_DOCKER_ACTIONS_FILE"; then
    ok "nbactions-docker.xml defines <actionName>run</actionName>"
  else
    warn "nbactions-docker.xml missing <actionName>run</actionName>"
  fi

  # exec plugin goal
  if grep -q "org\.codehaus\.mojo:exec-maven-plugin:3\.1\.0:exec" "$NB_DOCKER_ACTIONS_FILE"; then
    ok "nbactions-docker.xml uses exec-maven-plugin:3.1.0:exec"
  else
    warn "nbactions-docker.xml does not reference exec-maven-plugin:3.1.0:exec"
  fi

  # executable bash (allow whitespace variations like ' bash')
  if grep -Eq "<exec\.executable>\s*bash\s*</exec\.executable>" "$NB_DOCKER_ACTIONS_FILE"; then
    ok "nbactions-docker.xml sets exec.executable to bash"
  else
    warn "nbactions-docker.xml does not set exec.executable to bash (or formatting differs)"
  fi

  # args points to your script path (allow whitespace)
  if grep -Eq "<exec\.args>\s*\.\./\.\./scripts/dev/backend\.sh\s*</exec\.args>" "$NB_DOCKER_ACTIONS_FILE"; then
    ok "nbactions-docker.xml points to ../../scripts/dev/backend.sh"
  else
    warn "nbactions-docker.xml does not point to ../../scripts/dev/backend.sh (or formatting differs)"
  fi

else
  warn "NetBeans Docker actions file not found: $NB_DOCKER_ACTIONS_FILE (fine if not using NetBeans)"
fi


section "VS Code Dev Containers (informational)"

if command -v code >/dev/null 2>&1; then
  ok "VS Code CLI found: $(code --version | head -n 1)"

  if code --list-extensions 2>/dev/null | grep -qx "ms-vscode-remote.remote-containers"; then
    ok "VS Code Dev Containers extension is installed (ms-vscode-remote.remote-containers)"
  else
    warn "VS Code Dev Containers extension NOT detected. Install: ms-vscode-remote.remote-containers"
  fi

  # optional: show version if installed
  if code --list-extensions --show-versions 2>/dev/null | grep -q "^ms-vscode-remote.remote-containers@"; then
    ver="$(code --list-extensions --show-versions 2>/dev/null | grep '^ms-vscode-remote.remote-containers@' | head -n1)"
    info "Dev Containers version: $ver"
  fi
else
  warn "VS Code CLI ('code') not found (fine if not using VS Code)"
fi


section "IntelliJ (informational)"
# We can't reliably detect IntelliJ across installs; do a best-effort check.
if command -v idea >/dev/null 2>&1; then
  ok "IntelliJ launcher 'idea' found"
else
  warn "IntelliJ launcher 'idea' not found (fine if you don't use IntelliJ)"
fi
warn "IntelliJ: recommended workflow is host IDE + run/build inside cestereg-dev via scripts (or attach a terminal to container)."

echo
if [ "$EXIT_CODE" -eq 0 ]; then
  echo -e "${GRN}Doctor result: OK${RST}"
else
  echo -e "${RED}Doctor result: issues found${RST}"
fi

exit "$EXIT_CODE"

