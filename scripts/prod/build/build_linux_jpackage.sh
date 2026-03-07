#!/usr/bin/env bash
set -euo pipefail

EMBEDDB="true"   # default
SPRING_PROFILES_ACTIVE="production,embeddb"
EXTRA_ARGUMENT="-Dplatform=linux"

for arg in "$@"; do
  case "$arg" in
    -embeddb=*|--embeddb=*)
      EMBEDDB="${arg#*=}"
      ;;
    *)
      echo "[WARN] Unknown arg: $arg"
      ;;
  esac
done

if [[ "$EMBEDDB" != "true" && "$EMBEDDB" != "false" ]]; then
  echo "[ERROR] embeddb must be true or false, got: $EMBEDDB"
  exit 1
fi

echo "[INFO] EMBEDDB=$EMBEDDB"
if [[ "$EMBEDDB" == "false" ]]; then
    SPRING_PROFILES_ACTIVE="production"
    EXTRA_ARGUMENT="-Dembeddb=false"
    echo "[INFO] SPRING_PROFILES_ACTIVE=$SPRING_PROFILES_ACTIVE  EXTRA_ARGUMENT=$EXTRA_ARGUMENT"
fi

# Resolve project root: script is PROJECT_ROOT_FOLDER/scripts/prod/build_linux_jpackage.sh
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../../../" >/dev/null 2>&1 && pwd)"

APP_NAME="cestereg"
APP_VERSION="0.0.1"

BACKEND="${ROOT}/apps/backend"
FRONTEND="${ROOT}/apps/frontend"
DATA_DIR="${ROOT}/data"

DIST="${ROOT}/dist/linux"
INPUT="${DIST}/input"
RESOURCES="${DIST}/resources"
OUT="${DIST}/out"

rm -rf "${DIST}"
mkdir -p "${INPUT}" "${RESOURCES}" "${OUT}"

echo "[1/5] Build Spring Boot jar (production, linux platform)…"
cd "${BACKEND}"
mvn -Pproduction "${EXTRA_ARGUMENT}" -Dspring.profiles.active="${SPRING_PROFILES_ACTIVE}" clean package



# Pick the jar. If you have both "plain" and "boot" jars, this usually grabs the right one.
JAR="$(ls -1 target/*.jar | grep -v 'original' | head -n 1)"
if [[ ! -f "${JAR}" ]]; then
  echo "[ERROR] Could not find built jar under ${BACKEND}/target"
  exit 1
fi
echo "[INFO] Using jar: ${JAR}"

echo "[2/5] Prepare jpackage input + resources…"
cp -f "${JAR}" "${INPUT}/${APP_NAME}.jar"

if [[ ! -d "${DATA_DIR}" ]]; then
  echo "[ERROR] data/ folder not found at: ${DATA_DIR}"
  exit 1
fi
cp -a "${DATA_DIR}" "${RESOURCES}/data"

# Optional icon (PNG). Put it at: PROJECT_ROOT_FOLDER/scripts/prod/cestereg.png
ICON_PNG="${ROOT}/scripts/prod/cestereg.png"
ICON_ARG=()
if [[ -f "${ICON_PNG}" ]]; then
  ICON_ARG=(--icon "${ICON_PNG}")
  echo "[INFO] Using icon: ${ICON_PNG}"
else
  echo "[WARN] No icon found at ${ICON_PNG}; packaging without icon."
fi

echo "[3/5] Create portable app-image (no Java required)…"
cd "${ROOT}"

# IMPORTANT:
# For Spring Boot fat jars, main class is usually not your @SpringBootApplication,
# so we let jpackage use -jar by specifying --main-jar and setting main-class to empty is not allowed.
# jpackage will run: <runtime>/bin/java -jar <app>.jar plus your arguments.
jpackage \
  --type app-image \
  --name "${APP_NAME}" \
  --app-version "${APP_VERSION}" \
  --input "${INPUT}" \
  --main-jar "${APP_NAME}.jar" \
  --dest "${OUT}" \
  "${ICON_ARG[@]}" \
  --arguments "--spring.profiles.active=${SPRING_PROFILES_ACTIVE}" \
  --arguments "--app.assets.base-dir=\$APPDIR/resources/data"

APP_IMAGE="${OUT}/${APP_NAME}"
test -d "$APP_IMAGE/lib/app" || { echo "BAD: app-image not found at $APP_IMAGE"; exit 1; }

PAYLOAD_DIR="${APP_IMAGE}/lib/app/resources"
rm -rf "${PAYLOAD_DIR}"
mkdir -p "${PAYLOAD_DIR}"

mv "${RESOURCES}/data" "${PAYLOAD_DIR}/data"
rmdir "${RESOURCES}" 2>/dev/null || true

test -f "$PAYLOAD_DIR/data/README.md" || { echo "BAD: assets not copied"; exit 1; }

echo "[INFO] App-image created at: ${OUT}/${APP_NAME}"

echo "[4/5] (Optional) Create .deb installer…"
# Requires dpkg-deb tooling (usually installed on Mint). If it fails, skip or install build essentials.
if command -v dpkg-deb >/dev/null 2>&1; then
    jpackage \
      --type deb \
      --name "${APP_NAME}" \
      --app-version "${APP_VERSION}" \
      --app-image "${OUT}/${APP_NAME}" \
      --dest "${OUT}" \
      "${ICON_ARG[@]}" \
      --linux-shortcut
  echo "[INFO] .deb created in: ${OUT}"
else
  echo "[WARN] dpkg-deb not found; skipping .deb build."
fi

echo "[5/5] Smoke test instructions:"
echo "  Run portable build:"
echo "    ${OUT}/${APP_NAME}/bin/${APP_NAME}"
echo
echo "  Install deb (if built):"
echo "    sudo dpkg -i ${OUT}/${APP_NAME}_${APP_VERSION}_amd64.deb"

echo "[INFO] Cleanup build folders (backend target/, frontend dist/)..."

# Backend build output
rm -rf "${BACKEND}/target" 2>/dev/null || true

# Angular build output (adjust if your frontend path differs)
rm -rf "${FRONTEND}/dist" 2>/dev/null || true


