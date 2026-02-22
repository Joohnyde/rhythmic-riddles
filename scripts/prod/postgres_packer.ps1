param([string]$VER = "18")

$ErrorActionPreference = "Stop"

$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
$PROJECT_ROOT = Resolve-Path (Join-Path $SCRIPT_DIR "..\..") | Select-Object -ExpandProperty Path

$BACKEND_DIR  = Join-Path $PROJECT_ROOT "apps\backend"
$PLATFORM_DIR = Join-Path $BACKEND_DIR "src\main\resources\pg-client\windows-x86_64"
$TARGET_TGZ   = Join-Path $PLATFORM_DIR "pg-client.tgz"

Write-Host "[INFO] Project root: $PROJECT_ROOT"
Write-Host "[INFO] Packing minimal Postgres client VER=$VER"

# Find psql.exe (prefer PATH)
$psql = (Get-Command psql.exe -ErrorAction SilentlyContinue)?.Source
if (-not $psql) {
  $candidate = "C:\Program Files\PostgreSQL\$VER\bin\psql.exe"
  if (Test-Path $candidate) { $psql = $candidate }
}
if (-not $psql) { throw "psql.exe not found. Install PostgreSQL $VER or add it to PATH." }

$binDir = Split-Path -Parent $psql
$pgRoot = Resolve-Path (Join-Path $binDir "..") | Select-Object -ExpandProperty Path
$libDir = Join-Path $pgRoot "lib"

# Work dirs
$WORK    = Join-Path $env:TEMP "pg-client-work"
$OUT_TGZ = Join-Path $env:TEMP "pg-client-windows-x86_64.tgz"
$TMP_COPY = "$TARGET_TGZ.tmp"

function Reset-Dir($p) {
  if (Test-Path $p) { Remove-Item -Recurse -Force $p }
  New-Item -ItemType Directory -Force $p | Out-Null
}

function Find-DllOnDisk([string]$dllName) {
  $candidates = @(
    (Join-Path $binDir $dllName),
    (Join-Path $libDir $dllName),
    # sometimes OpenSSL ends up near bin in extra folders, but keep simple for now
    $dllName
  )
  foreach ($c in $candidates) {
    if (Test-Path $c) { return (Resolve-Path $c).Path }
  }
  return $null
}

function Get-DependentsDumpbin([string]$exeOrDll) {
  $dumpbin = (Get-Command dumpbin.exe -ErrorAction SilentlyContinue)?.Source
  if (-not $dumpbin) { return $null }

  $out = & $dumpbin /nologo /dependents $exeOrDll 2>$null
  if (-not $out) { return @() }

  # Parse DLL names lines like: "    LIBPQ.dll"
  $dlls = $out | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '^[A-Za-z0-9._-]+\.dll$' }
  return $dlls
}

function Copy-MinimalWithDumpbin([string]$psqlPath, [string]$destBin, [string]$destLib) {
  Write-Host "[INFO] Using dumpbin to copy only required DLLs"

  Copy-Item -Force $psqlPath (Join-Path $destBin "psql.exe")

  $queue = New-Object System.Collections.Generic.Queue[string]
  $seen  = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)

  $queue.Enqueue($psqlPath)

  while ($queue.Count -gt 0) {
    $cur = $queue.Dequeue()
    $deps = Get-DependentsDumpbin $cur
    if ($deps -eq $null) { throw "dumpbin disappeared mid-run" }

    foreach ($dll in $deps) {
      # Skip Windows system DLLs (they exist on every machine)
      if ($dll -match '^(KERNEL32|USER32|ADVAPI32|WS2_32|SHELL32|OLE32|OLEAUT32|CRYPT32|COMDLG32|GDI32|SHLWAPI|MSVCRT|VCRUNTIME|ucrtbase)\.dll$') {
        continue
      }

      if ($seen.Add($dll)) {
        $src = Find-DllOnDisk $dll
        if ($src) {
          # Put DLLs next to psql.exe is simplest for Windows loader.
          Copy-Item -Force $src (Join-Path $destBin $dll)
          $queue.Enqueue($src)
        } else {
          Write-Host "[WARN] Could not locate dependency $dll on disk (may be system-provided)"
        }
      }
    }
  }
}

function Copy-Fallback([string]$destBin, [string]$destLib) {
  Write-Host "[WARN] dumpbin not found; falling back to copying all DLLs from Postgres bin/lib"
  Copy-Item -Force $psql (Join-Path $destBin "psql.exe")
  Copy-Item -Force (Join-Path $binDir "*.dll") $destBin -ErrorAction SilentlyContinue
  if (Test-Path $libDir) {
    Copy-Item -Force (Join-Path $libDir "*.dll") $destBin -ErrorAction SilentlyContinue
  }
}

try {
  # Reset destinations
  Reset-Dir $PLATFORM_DIR
  Reset-Dir $WORK

  $bundleRoot = Join-Path $WORK "pg-client-bundle\postgresql-$VER"
  $destBin = Join-Path $bundleRoot "bin"
  $destLib = Join-Path $bundleRoot "lib"
  New-Item -ItemType Directory -Force $destBin | Out-Null
  New-Item -ItemType Directory -Force $destLib | Out-Null

  # Prefer minimal copy via dumpbin, otherwise fallback
  $hasDumpbin = (Get-Command dumpbin.exe -ErrorAction SilentlyContinue) -ne $null
  if ($hasDumpbin) {
    Copy-MinimalWithDumpbin $psql $destBin $destLib
  } else {
    Copy-Fallback $destBin $destLib
  }

  # Create tgz with single pg-client-bundle/ root
  Write-Host "[INFO] Creating tarball: $OUT_TGZ"
  Push-Location $WORK
  try { tar -czf $OUT_TGZ "pg-client-bundle" } finally { Pop-Location }

  # Install into resources atomically
  Copy-Item -Force $OUT_TGZ $TMP_COPY
  Move-Item -Force $TMP_COPY $TARGET_TGZ

  Write-Host "[INFO] Wrote: $TARGET_TGZ"
}
finally {
  Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $WORK
  Remove-Item -Force -ErrorAction SilentlyContinue $OUT_TGZ, $TMP_COPY
}
