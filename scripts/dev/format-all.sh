#!/usr/bin/env bash
set -euo pipefail

# -----------------------------
# Config: adjust these if needed
# -----------------------------
BACKEND_DIR="${BACKEND_DIR:-apps/backend}"
FRONTEND_DIR="${FRONTEND_DIR:-apps/frontend}"

# If your backend is at repo root, set BACKEND_DIR="."
# If your frontend is elsewhere, set FRONTEND_DIR accordingly.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

echo "===> Repo root: ${ROOT_DIR}"
echo "===> Backend dir: ${BACKEND_DIR}"
echo "===> Frontend dir: ${FRONTEND_DIR}"
echo

run_backend() {
  local dir="${ROOT_DIR}/${BACKEND_DIR}"
  if [[ ! -d "${dir}" ]]; then
    echo "===> Backend dir not found, skipping: ${dir}"
    return 0
  fi

  echo "===> [Backend] Formatting (Spotless apply)"
  (cd "${dir}" && mvn -B -ntp spotless:apply)

  echo "===> [Backend] Running checkers (Spotless check + Checkstyle in CI profile)"
  # Spotless:check ensures nothing is left unformatted
  (cd "${dir}" && mvn -B -ntp spotless:check)

  # Checkstyle should be configured in a CI profile (so local build/run is not blocked)
  # If you used a different profile name, change -Pci accordingly.
  (cd "${dir}" && mvn -B -ntp -Pci checkstyle:check)

  echo
}

run_frontend() {
  local dir="${ROOT_DIR}/${FRONTEND_DIR}"
  if [[ ! -d "${dir}" ]]; then
    echo "===> Frontend dir not found, skipping: ${dir}"
    return 0
  fi

  echo "===> [Frontend] Installing deps (npm ci if lockfile exists, otherwise npm i)"
  if [[ -f "${dir}/package-lock.json" ]]; then
    (cd "${dir}" && npm ci)
  else
    (cd "${dir}" && npm install)
  fi

  echo "===> [Frontend] Formatting (Prettier write)"
  (cd "${dir}" && npm run format)

  echo "===> [Frontend] Auto-fixing lint where possible (ESLint --fix)"
  # This will remove unused imports; unused variables usually won't be auto-deleted (by design).
  (cd "${dir}" && npm run lint:fix)

  echo "===> [Frontend] Running checkers (Prettier check + ESLint)"
  (cd "${dir}" && npm run format:check)
  (cd "${dir}" && npm run lint)

  echo
}

echo "===> Running format-all"
run_backend
run_frontend
echo "===> Done ✅"
