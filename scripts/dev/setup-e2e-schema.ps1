$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

param(
  [Parameter(Mandatory = $true)]
  [string]$YamlFile,

  [ValidateSet('true', 'false')]
  [string]$Clean = 'false'
)

$SourceSchema = 'public'
$TargetSchema = 'e2e'

if (-not (Test-Path $YamlFile -PathType Leaf)) {
  Write-Error "YAML file not found: $YamlFile"
  exit 1
}

function Read-YamlValue {
  param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('url', 'username', 'password')]
    [string]$Key
  )

  $text = Get-Content -Raw -Encoding UTF8 $YamlFile

  $patterns = @{
    url = '(?m)^\s*url:\s*(.+?)\s*$'
    username = '(?m)^\s*username:\s*(.+?)\s*$'
    password = '(?m)^\s*password:\s*(.+?)\s*$'
  }

  $match = [regex]::Match($text, $patterns[$Key])
  if (-not $match.Success) {
    return ''
  }

  $value = $match.Groups[1].Value.Trim().Trim('"').Trim("'")

  # Resolve Spring placeholders like ${DB_PASSWORD:default}
  $placeholder = [regex]::Match($value, '^\$\{([^:}]+):?([^}]*)\}$')
  if ($placeholder.Success) {
    $envName = $placeholder.Groups[1].Value
    $default = $placeholder.Groups[2].Value
    $envValue = [Environment]::GetEnvironmentVariable($envName)
    if ($null -ne $envValue -and $envValue -ne '') {
      $value = $envValue
    } else {
      $value = $default
    }
  }

  return $value
}

$JdbcUrl = Read-YamlValue 'url'
$DbUser = Read-YamlValue 'username'
$DbPassword = Read-YamlValue 'password'

if ([string]::IsNullOrWhiteSpace($JdbcUrl)) {
  Write-Error "Could not read spring.datasource.url from $YamlFile"
  exit 1
}

if ([string]::IsNullOrWhiteSpace($DbUser)) {
  Write-Error "Could not read spring.datasource.username from $YamlFile"
  exit 1
}

$dbHostMatch = [regex]::Match($JdbcUrl, '^jdbc:postgresql://([^:/?]+)')
$dbPortMatch = [regex]::Match($JdbcUrl, '^jdbc:postgresql://[^:/?]+:([0-9]+)')
$dbNameMatch = [regex]::Match($JdbcUrl, '^jdbc:postgresql://[^/]+/([^?]+)')

$DbHost = if ($dbHostMatch.Success) { $dbHostMatch.Groups[1].Value } else { $JdbcUrl }
$DbPort = if ($dbPortMatch.Success) { $dbPortMatch.Groups[1].Value } else { '5432' }
$DbName = if ($dbNameMatch.Success) { $dbNameMatch.Groups[1].Value } else { $JdbcUrl }

$env:PGPASSWORD = $DbPassword

Write-Host "Preparing E2E schema"
Write-Host "DB: $DbName"
Write-Host "Host: $DbHost"
Write-Host "Port: $DbPort"
Write-Host "User: $DbUser"
Write-Host "Clean: $Clean"

if ($Clean -eq 'true') {
  Write-Host "Dropping schema $TargetSchema"
  psql `
    -h $DbHost `
    -p $DbPort `
    -U $DbUser `
    -d $DbName `
    -v ON_ERROR_STOP=1 `
    -c "DROP SCHEMA IF EXISTS $TargetSchema CASCADE;"
}

Write-Host "Creating schema $TargetSchema"
psql `
  -h $DbHost `
  -p $DbPort `
  -U $DbUser `
  -d $DbName `
  -v ON_ERROR_STOP=1 `
  -c "CREATE SCHEMA IF NOT EXISTS $TargetSchema;"

$ExistingTables = (& psql `
  -h $DbHost `
  -p $DbPort `
  -U $DbUser `
  -d $DbName `
  -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema='$TargetSchema';").Trim()

if ($ExistingTables -ne '0' -and $Clean -ne 'true') {
  Write-Host "Schema $TargetSchema is not empty. Run with -De2e.clean=true."
  exit 0
}

Write-Host "Copying $SourceSchema schema into $TargetSchema"

$dump = & pg_dump `
  -h $DbHost `
  -p $DbPort `
  -U $DbUser `
  -d $DbName `
  --schema=$SourceSchema `
  --no-owner `
  --no-privileges

$dump = $dump `
  -replace "CREATE SCHEMA $SourceSchema;", "CREATE SCHEMA IF NOT EXISTS $TargetSchema;" `
  -replace "SCHEMA $SourceSchema", "SCHEMA $TargetSchema" `
  -replace "$([regex]::Escape($SourceSchema))\.", "$TargetSchema." `
  -replace "SET search_path = $SourceSchema", "SET search_path = $TargetSchema"

$dump | psql `
  -h $DbHost `
  -p $DbPort `
  -U $DbUser `
  -d $DbName `
  -v ON_ERROR_STOP=1

Write-Host "E2E schema ready."
