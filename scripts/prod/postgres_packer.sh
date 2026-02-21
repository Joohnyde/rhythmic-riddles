#!/usr/bin/env bash
set -euo pipefail

VER="${VER:-18}"
VER="${VER//$'\r'/}"

# Script is at: PROJECT_ROOT_FOLDER/scripts/prod/postgres_packer.sh
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
PROJECT_ROOT_FOLDER="$(cd -- "${SCRIPT_DIR}/../.." >/dev/null 2>&1 && pwd)"

BACKEND_DIR="${PROJECT_ROOT_FOLDER}/apps/backend"
PLATFORM_DIR="${BACKEND_DIR}/src/main/resources/pg-client/linux-x86_64"
TARGET_TGZ="${PLATFORM_DIR}/pg-client.tgz"

B="/tmp/pg-client-bundle"
OUT_TGZ="/tmp/pg-client-linux-x86_64.tgz"
TMP_COPY="${TARGET_TGZ}.tmp"

cleanup() {
  rm -rf "${B}" 2>/dev/null || true
  rm -f "${OUT_TGZ}" "${TMP_COPY}" 2>/dev/null || true
}
trap cleanup EXIT

echo "[INFO] Project root: ${PROJECT_ROOT_FOLDER}"
echo "[INFO] Backend dir:   ${BACKEND_DIR}"
echo "[INFO] Target res:    ${PLATFORM_DIR}"
echo "[INFO] Packing minimal Postgres client VER=${VER}"

# --- sanity ---
if [[ ! -d "${BACKEND_DIR}" ]]; then
  echo "[ERROR] Backend dir not found: ${BACKEND_DIR}"
  exit 1
fi

REAL_PSQL="/usr/lib/postgresql/${VER}/bin/psql"
if [[ ! -x "${REAL_PSQL}" ]]; then
  echo "[ERROR] Real psql not found/executable: ${REAL_PSQL}"
  echo "        (Note: 'psql' in PATH is often a wrapper. We need the real binary.)"
  exit 1
fi

# --- reset destination (idempotent) ---
echo "[INFO] Resetting destination folder: ${PLATFORM_DIR}"
rm -rf "${PLATFORM_DIR}"
mkdir -p "${PLATFORM_DIR}"

# --- create bundle layout ---
echo "[INFO] Creating bundle root in ${B}"
mkdir -p "${B}/postgresql-${VER}/bin"
mkdir -p "${B}/postgresql-${VER}/lib"
mkdir -p "${B}/share-postgresql/${VER}"

# copy psql
echo "[INFO] Copying psql -> bundle"
cp -a "${REAL_PSQL}" "${B}/postgresql-${VER}/bin/psql"
chmod +x "${B}/postgresql-${VER}/bin/psql" || true

# copy minimal share (optional but tiny; safe to skip if missing)
if [[ -d "/usr/share/postgresql/${VER}" ]]; then
  echo "[INFO] Copying /usr/share/postgresql/${VER} -> bundle/share-postgresql/${VER}"
  cp -a "/usr/share/postgresql/${VER}/." "${B}/share-postgresql/${VER}/" 2>/dev/null || true
else
  echo "[WARN] /usr/share/postgresql/${VER} not found; skipping"
fi

# copy CA certs (optional)
if [[ -f "/etc/ssl/certs/ca-certificates.crt" ]]; then
  echo "[INFO] Copying CA certs -> bundle/ca-certificates.crt"
  cp -a "/etc/ssl/certs/ca-certificates.crt" "${B}/" || true
fi

# --- collect dynamic libs needed by psql ---
echo "[INFO] Collecting shared library deps via ldd"
# ldd output formats vary; this grabs absolute paths after '=>', and absolute paths at line start.
mapfile -t LIBS < <(
  ldd "${REAL_PSQL}" \
  | awk '
      $1 ~ /^\// { print $1; next }
      /=> \// {
        for (i=1; i<=NF; i++) if ($i ~ /^\//) { print $i; break }
      }' \
  | sort -u \
  | grep -Ev '(^|/)(ld-linux[^/]*\.so(\.[0-9]+)*)$' \
  | grep -Ev '(^|/)(libc\.so(\.[0-9]+)*)$' \
  | grep -Ev '(^|/)(libpthread\.so(\.[0-9]+)*)$' \
  | grep -Ev '(^|/)(libdl\.so(\.[0-9]+)*)$' \
  | grep -Ev '(^|/)(librt\.so(\.[0-9]+)*)$' \
  | grep -Ev '(^|/)(libm\.so(\.[0-9]+)*)$' \
  | grep -Ev '(^|/)(libresolv\.so(\.[0-9]+)*)$'
)

if [[ ${#LIBS[@]} -eq 0 ]]; then
  echo "[WARN] No libs detected by ldd (psql might be static?)."
else
  echo "[INFO] Copying ${#LIBS[@]} libs into bundle lib/"
fi

for p in "${LIBS[@]}"; do
  # ignore linux-vdso and non-existent
  [[ -e "$p" ]] || continue

  # Copy both the link (if any) and its resolved target (if different)
  base="$(basename "$p")"
  cp -L --preserve=mode,timestamps "$p" "${B}/postgresql-${VER}/lib/${base}" 2>/dev/null || true
  #cp -L --preserve=mode,timestamps "$p" "${B}/postgresql-${VER}/lib/${base}" 2>/dev/null || true

  real="$(readlink -f "$p" || true)"
  if [[ -n "${real}" && "${real}" != "${p}" && -e "${real}" ]]; then
    base2="$(basename "$real")"
    cp -L --preserve=mode,timestamps "$real" "${B}/postgresql-${VER}/lib/${base2}" 2>/dev/null || true
    #cp -L --preserve=mode,timestamps "$real" "${B}/postgresql-${VER}/lib/${base2}" 2>/dev/null || true
  fi
done

# Never ship glibc / loader libraries (they are tied to the target OS and can break with GLIBC_PRIVATE errors)
rm -f "${B}/postgresql-${VER}/lib/libc.so."* \
      "${B}/postgresql-${VER}/lib/ld-linux"* \
      "${B}/postgresql-${VER}/lib/libpthread.so."* \
      "${B}/postgresql-${VER}/lib/libdl.so."* \
      "${B}/postgresql-${VER}/lib/librt.so."* \
      "${B}/postgresql-${VER}/lib/libm.so."* \
      "${B}/postgresql-${VER}/lib/libresolv.so."* 2>/dev/null || true

# Sanity: your Java extractor does not preserve symlinks, so the bundle should not contain symlinks in lib/.
echo "[INFO] Symlinks in bundle lib/ (should be empty):"
find "${B}/postgresql-${VER}/lib" -type l -print 2>/dev/null || true

# --- pack tar ---
echo "[INFO] Creating tarball: ${OUT_TGZ}"
tar -C /tmp -czf "${OUT_TGZ}" "$(basename "${B}")"
# NOTE: Do not use tar --dereference/-h here; it can chase symlinks outside the bundle.
# We dereference symlinks during copying (cp -L) so the archive contains real files only.

# verify expected entry exists (awk exact match)
EXPECTED="pg-client-bundle/postgresql-${VER}/bin/psql"
echo "[INFO] Verifying tarball contains: ${EXPECTED}"
if ! tar -tzf "${OUT_TGZ}" | tr -d '\r' | awk -v expected="$EXPECTED" '($0==expected){found=1} END{exit !found}'; then
  echo "[ERROR] Tarball missing expected entry: ${EXPECTED}"
  echo "[INFO] Entries containing 'psql':"
  tar -tzf "${OUT_TGZ}" | grep -F "psql" || true
  exit 1
fi

# --- install into backend resources atomically ---
echo "[INFO] Installing bundle -> ${TARGET_TGZ}"
cp -f "${OUT_TGZ}" "${TMP_COPY}"
mv -f "${TMP_COPY}" "${TARGET_TGZ}"

# checksum
if command -v sha256sum >/dev/null 2>&1; then
  echo "[INFO] sha256(pg-client.tgz)=$(sha256sum "${TARGET_TGZ}" | awk '{print $1}')"
fi

echo "[INFO] Done."
