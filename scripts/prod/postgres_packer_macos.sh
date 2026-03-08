#!/usr/bin/env bash
set -euo pipefail

VER="${VER:-18}"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
PROJECT_ROOT_FOLDER="$(cd -- "${SCRIPT_DIR}/../.." >/dev/null 2>&1 && pwd)"

BACKEND_DIR="${PROJECT_ROOT_FOLDER}/apps/backend"
PLATFORM_DIR="${BACKEND_DIR}/src/main/resources/pg-client/macos-x86_64"
TARGET_TGZ="${PLATFORM_DIR}/pg-client.tgz"

B="/tmp/pg-client-bundle"
OUT_TGZ="/tmp/pg-client-macos-x86_64.tgz"

cleanup() {
  rm -rf "${B}" 2>/dev/null || true
  rm -f "${OUT_TGZ}" 2>/dev/null || true
}
trap cleanup EXIT

REAL_PSQL="/Library/PostgreSQL/${VER}/bin/psql"

if [[ ! -x "${REAL_PSQL}" ]]; then
  echo "[ERROR] psql not found: ${REAL_PSQL}"
  exit 1
fi
BIN_DIR="$(cd "$(dirname "$REAL_PSQL")" && pwd)"

rm -rf "${PLATFORM_DIR}"
mkdir -p "${PLATFORM_DIR}"

mkdir -p "${B}/postgresql-${VER}/bin"
mkdir -p "${B}/postgresql-${VER}/lib"

cp "${REAL_PSQL}" "${B}/postgresql-${VER}/bin/psql"

copy_deps() {
  local file="$1"
  local base_dir="$2"

  while IFS= read -r dep; do
    [[ -n "$dep" ]] || continue

    case "$dep" in
      @loader_path/*)
        resolved="$(cd "$(dirname "$file")" && pwd)/${dep#@loader_path/}"
        ;;
      @executable_path/*)
        resolved="${base_dir}/${dep#@executable_path/}"
        ;;
      /*)
        resolved="$dep"
        ;;
      *)
        continue
        ;;
    esac

    [[ -e "$resolved" ]] || continue

    local target="${B}/postgresql-${VER}/lib/$(basename "$resolved")"
    if [[ ! -e "$target" ]]; then
      cp -RL "$resolved" "$target"
      copy_deps "$resolved" "$base_dir"
    fi
  done < <(
    otool -L "$file" | tail -n +2 | awk '{print $1}'
  )
}

copy_deps "$REAL_PSQL" "$BIN_DIR"

tar -C /tmp -czf "${OUT_TGZ}" "$(basename "${B}")"

cp "${OUT_TGZ}" "${TARGET_TGZ}"

echo "[INFO] pg-client bundle created:"
echo "${TARGET_TGZ}"
