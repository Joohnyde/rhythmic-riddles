param(
  [string]$embeddb = "true"
)

$ErrorActionPreference = "Stop"

function Info($m) { Write-Host "[INFO] $m" }
function Warn($m) { Write-Host "[WARN] $m" -ForegroundColor Yellow }
function Err($m)  { Write-Host "[ERROR] $m" -ForegroundColor Red }

if ($embeddb -ne "true" -and $embeddb -ne "false") {
  Err "embeddb must be true or false, got: $embeddb"
  exit 1
}

# Defaults (embedded)
$SPRING_PROFILES_ACTIVE = "production,embeddb"
$MVN_ARGS = @("-Pproduction", "-Dplatform=windows")

Info "EMBEDDB=$embeddb"
if ($embeddb -eq "false") {
  $SPRING_PROFILES_ACTIVE = "production"
  # Keep platform=windows AND disable embedded db packaging
  $MVN_ARGS = @("-Pproduction", "-Dplatform=windows", "-Dembeddb=false")
  Info "SPRING_PROFILES_ACTIVE=$SPRING_PROFILES_ACTIVE  MVN_ARGS=$($MVN_ARGS -join ' ')"
}

# Resolve project root: script is PROJECT_ROOT\scripts\prod\build_windows_jpackage.ps1
$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
$ROOT = Resolve-Path (Join-Path $SCRIPT_DIR "..\..\..") | Select-Object -ExpandProperty Path

$APP_NAME = "cestereg"
$APP_VERSION = "0.0.1"

$BACKEND = Join-Path $ROOT "apps\backend"
$FRONTEND = Join-Path $ROOT "apps\frontend"
$DATA_DIR = Join-Path $ROOT "data"

$DIST = Join-Path $ROOT "dist\windows"
$INPUT = Join-Path $DIST "input"
$RESOURCES = Join-Path $DIST "resources"
$OUT = Join-Path $DIST "out"

# Clean dist
if (Test-Path $DIST) { Remove-Item -Recurse -Force $DIST }
New-Item -ItemType Directory -Force $INPUT, $RESOURCES, $OUT | Out-Null

Info "[1/5] Build Spring Boot jar (production, windows platform)…"
Push-Location $BACKEND
try {
  # Note: -Dspring.profiles.active here is only for build-time if you rely on it;
  # runtime profile is passed to jpackage via --arguments below.
  & mvn @MVN_ARGS "-Dspring.profiles.active=$SPRING_PROFILES_ACTIVE" clean package
} finally {
  Pop-Location
}

# Pick the jar (exclude original)
$targetDir = Join-Path $BACKEND "target"
$jar = Get-ChildItem -Path $targetDir -Filter "*.jar" |
  Where-Object { $_.Name -notmatch "original" } |
  Select-Object -First 1

if (-not $jar) {
  Err "Could not find built jar under $targetDir"
  exit 1
}
Info "Using jar: $($jar.FullName)"

Info "[2/5] Prepare jpackage input + resources…"
Copy-Item -Force $jar.FullName (Join-Path $INPUT "$APP_NAME.jar")

if (-not (Test-Path $DATA_DIR)) {
  Err "data/ folder not found at: $DATA_DIR"
  exit 1
}
Copy-Item -Recurse -Force $DATA_DIR (Join-Path $RESOURCES "data")

# Optional icon (.ico). Put it at: PROJECT_ROOT\scripts\prod\cestereg.ico
$ICON_ICO = Join-Path $ROOT "scripts\prod\cestereg.ico"
$ICON_ARGS = @()
if (Test-Path $ICON_ICO) {
  $ICON_ARGS = @("--icon", $ICON_ICO)
  Info "Using icon: $ICON_ICO"
} else {
  Warn "No icon found at $ICON_ICO; packaging without icon."
}

Info "[3/5] Create portable app-image (no Java required)…"

# jpackage will run: <runtime>\bin\java -jar <app>.jar plus your arguments.
# Note: $APPDIR placeholder is expanded by jpackage at runtime (works on Windows too).
& jpackage `
  --type app-image `
  --name $APP_NAME `
  --app-version $APP_VERSION `
  --input $INPUT `
  --main-jar "$APP_NAME.jar" `
  --dest $OUT `
  @ICON_ARGS `
  --arguments "--spring.profiles.active=$SPRING_PROFILES_ACTIVE" `
  --arguments "--app.assets.base-dir=`$APPDIR/resources/data"

$appImage = Join-Path $OUT $APP_NAME
if (-not (Test-Path $appImage)) {
  Err "BAD: app-image folder not found at $appImage"
  exit 1
}

# Find the directory that contains your jar inside the app-image
$jarInImage = Get-ChildItem -Path $appImage -Recurse -File -Filter "$APP_NAME.jar" | Select-Object -First 1
if (-not $jarInImage) {
  Err "BAD: Could not locate $APP_NAME.jar inside app-image at $appImage"
  exit 1
}

# jpackage expands $APPDIR at runtime to the directory containing the app jar.
# So we must place resources under: <jarDir>\resources\data
$jarDir = Split-Path -Parent $jarInImage.FullName
$payloadDir = Join-Path $jarDir "resources"

if (Test-Path $payloadDir) { Remove-Item -Recurse -Force $payloadDir }
New-Item -ItemType Directory -Force $payloadDir | Out-Null

Move-Item -Force (Join-Path $RESOURCES "data") (Join-Path $payloadDir "data")

# Best-effort cleanup
try { Remove-Item -Recurse -Force $RESOURCES } catch {}

$readme = Join-Path $payloadDir "data\README.md"
if (-not (Test-Path $readme)) {
  Err "BAD: assets not copied (missing $readme)"
  exit 1
}

Info "App-image created at: $appImage"
Info "Assets placed at:     $(Join-Path $payloadDir 'data')"

Info "[4/5] Create Windows EXE installer..."
try {
  & jpackage `
    --type exe `
    --name $APP_NAME `
    --app-version $APP_VERSION `
    --app-image $appImage `
    --dest $OUT `
    @ICON_ARGS `
    --win-menu `
    --win-shortcut
  Info "EXE created in: $OUT"
} catch {
  Warn "EXE build failed; skipping installer build."
}

Info "[5/5] Smoke test instructions:"
Write-Host "  Run portable build:"
Write-Host "    $OUT\$APP_NAME\$APP_NAME.exe"

Info "Cleanup build folders (backend target/, frontend dist/)..."

# Backend build output
$backendTarget = Join-Path $BACKEND "target"
if (Test-Path $backendTarget) { Remove-Item -Recurse -Force $backendTarget }

# Angular build output
$frontendDist = Join-Path $FRONTEND "dist"
if (Test-Path $frontendDist) { Remove-Item -Recurse -Force $frontendDist }
