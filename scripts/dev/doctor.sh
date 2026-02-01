#!/usr/bin/env bash
set -euo pipefail

echo "== RhytmicRiddles doctor =="

# Tools
need() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing: $1"; exit 1; }
}

need docker
need git

echo "Docker: $(docker --version)"
echo "Git: $(git --version)"

# Ports check (best-effort)
check_port() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    if lsof -i ":$port" >/dev/null 2>&1; then
      echo "WARN: Port $port is in use."
    else
      echo "OK: Port $port is free."
    fi
  else
    echo "INFO: lsof not found; skipping port check for $port."
  fi
}

check_port 5432
check_port 8080
check_port 4200

echo "Doctor checks complete."
