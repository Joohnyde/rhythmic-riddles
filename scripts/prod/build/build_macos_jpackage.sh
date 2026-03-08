#!/usr/bin/env bash
set -euo pipefail

# MacOS locale patch
unset LC_ALL
export LANG=en_US.UTF-8
export LC_CTYPE=en_US.UTF-8
unset LC_COLLATE
unset LC_MESSAGES
unset LC_MONETARY
unset LC_NUMERIC
unset LC_TIME

EMBEDDB="true"
SPRING_PROFILES_ACTIVE="production,embeddb"
EXTRA_ARGUMENT="-Dplatform=macos"

for arg in "$@"; do
  case "$arg" in
    -embeddb=*|--embeddb=*)
      EMBEDDB="${arg#*=}"
      ;;
  esac
done

if [[ "$EMBEDDB" == "false" ]]; then
  SPRING_PROFILES_ACTIVE="production"
  EXTRA_ARGUMENT="-Dembeddb=false"
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../../../" >/dev/null 2>&1 && pwd)"

APP_NAME="cestereg"
APP_VERSION="1.0.0"

BACKEND="${ROOT}/apps/backend"
FRONTEND="${ROOT}/apps/frontend"
DATA_DIR="${ROOT}/data"

DIST="${ROOT}/dist/macos"
INPUT="${DIST}/input"
RESOURCES="${DIST}/resources"
OUT="${DIST}/out"

rm -rf "${DIST}"
mkdir -p "${INPUT}" "${RESOURCES}" "${OUT}"

echo "[1/4] Build Spring Boot jar..."

cd "${BACKEND}"
mvn -Pproduction "${EXTRA_ARGUMENT}" \
  -Dspring.profiles.active="${SPRING_PROFILES_ACTIVE}" \
  clean package

JAR="$(ls -1 target/*.jar | grep -v 'original' | head -n 1)"

cp "${JAR}" "${INPUT}/${APP_NAME}.jar"

cp -a "${DATA_DIR}" "${RESOURCES}/data"

ICON="${ROOT}/scripts/prod/cestereg.icns"
ICON_CMD=()

if [[ -f "${ICON}" ]]; then
  ICON_CMD=(--icon "${ICON}")
  echo "[INFO] Using icon: ${ICON}"
else
  echo "[WARN] No icon found at ${ICON}; packaging without icon."
fi

cd "${ROOT}"

run_jpackage() {
  local label="$1"
  shift

  echo "[INFO] ${label}..."

  jpackage "$@"

  echo "[INFO] Done: ${label}"
}

APP_ARGS=(
  --type app-image
  --name "${APP_NAME}"
  --app-version "${APP_VERSION}"
  --input "${INPUT}"
  --main-jar "${APP_NAME}.jar"
  --dest "${OUT}"
  --arguments "--spring.profiles.active=${SPRING_PROFILES_ACTIVE}"
  --arguments "--app.assets.base-dir=\$APPDIR/resources/data"
)

if [[ -f "${ICON}" ]]; then
  APP_ARGS+=(--icon "${ICON}")
fi

run_jpackage "[2/4] Create macOS app image" "${APP_ARGS[@]}"

APP_IMAGE="${OUT}/${APP_NAME}.app"
PAYLOAD_DIR="${APP_IMAGE}/Contents/app"

mkdir -p "${PAYLOAD_DIR}/resources"
rm -rf "${PAYLOAD_DIR}/resources/data"
mv "${RESOURCES}/data" "${PAYLOAD_DIR}/resources/data"

echo "[INFO] App image created at: ${APP_IMAGE}"

DMG_ARGS=(
  --type dmg
  --name "${APP_NAME}"
  --app-version "${APP_VERSION}"
  --app-image "${APP_IMAGE}"
  --dest "${OUT}"
)

if [[ -f "${ICON}" ]]; then
  DMG_ARGS+=(--icon "${ICON}")
fi

run_jpackage "[3/4] Create DMG installer" "${DMG_ARGS[@]}"

echo "[INFO] DMG created in ${OUT}"

echo
echo "[4/4] Smoke test instructions:"
echo

echo "  Run portable build (.app):"
echo "    ${OUT}/${APP_NAME}.app/Contents/MacOS/${APP_NAME}"
echo

echo "  Or launch normally:"
echo "    open ${OUT}/${APP_NAME}.app"
echo

echo "  Install DMG:"
echo "    open ${OUT}/${APP_NAME}-${APP_VERSION}.dmg"
echo

echo "[INFO] Cleanup build folders (backend target/, frontend dist/)..."

rm -rf "${BACKEND}/target" 2>/dev/null || true
rm -rf "${FRONTEND}/dist" 2>/dev/null || true

echo "[INFO] Done."
echo "[INFO] Artifacts located at: ${OUT}"
