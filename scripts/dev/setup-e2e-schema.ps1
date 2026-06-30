$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

param(
  [Parameter(Mandatory=$true)][string]$YamlFile,
  [string]$Clean = 'false'
)

$SourceSchema = 'public'
$TargetSchema = 'e2e'

if (-not (Test-Path $YamlFile -PathType Leaf)) {
  Write-Error "YAML file not found: $YamlFile"
  exit 1
}

function Read-YamlValue {
  param([Parameter(Mandatory=$true)][ValidateSet('url','username','password')][string]$Key)

  $text = Get-Content -LiteralPath $YamlFile -Raw -Encoding UTF8
  $patterns = @{
    url = '(?m)^\s*url:\s*(.+?)\s*$'
    username = '(?m)^\s*username:\s*(.+?)\s*$'
    password = '(?m)^\s*password:\s*(.+?)\s*$'
  }

  $match = [regex]::Match($text, $patterns[$Key])
  if (-not $match.Success) { return '' }

  $value = $match.Groups[1].Value.Trim().Trim('"').Trim("'")
  $placeholder = [regex]::Match($value, '^\$\{([^:}]+):?([^}]*)\}$')
  if ($placeholder.Success) {
    $envName = $placeholder.Groups[1].Value
    $default = $placeholder.Groups[2].Value
    $envValue = [Environment]::GetEnvironmentVariable($envName)
    if ($null -ne $envValue) { $value = $envValue } else { $value = $default }
  }

  return $value
}

$JdbcUrl = Read-YamlValue url
$DbUser = Read-YamlValue username
$DbPassword = Read-YamlValue password

if ([string]::IsNullOrWhiteSpace($JdbcUrl)) {
  Write-Error "Could not read spring.datasource.url from $YamlFile"
  exit 1
}

if ([string]::IsNullOrWhiteSpace($DbUser)) {
  Write-Error "Could not read spring.datasource.username from $YamlFile"
  exit 1
}

$DbHost = [regex]::Replace($JdbcUrl, '^jdbc:postgresql://([^:/?]+).*$', '$1')
$DbPort = [regex]::Replace($JdbcUrl, '^jdbc:postgresql://[^:/?]+:([0-9]+).*$', '$1')
$DbName = [regex]::Replace($JdbcUrl, '^jdbc:postgresql://[^/]+/([^?]+).*$', '$1')

if ($DbPort -eq $JdbcUrl) { $DbPort = '5432' }
$env:PGPASSWORD = $DbPassword

Write-Host 'Preparing E2E schema'
Write-Host "DB: $DbName"
Write-Host "Host: $DbHost"
Write-Host "Port: $DbPort"
Write-Host "User: $DbUser"
Write-Host "Clean: $Clean"

if ($Clean -eq 'true') {
  Write-Host "Dropping schema $TargetSchema"
  & psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -c "DROP SCHEMA IF EXISTS $TargetSchema CASCADE;"
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "Creating schema $TargetSchema"
& psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -c "CREATE SCHEMA IF NOT EXISTS $TargetSchema;"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$ExistingTables = (& psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema='$TargetSchema';").Trim()
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (($ExistingTables -ne '0') -and ($Clean -ne 'true')) {
  Write-Host "Schema $TargetSchema is not empty. Run with -De2e.clean=true."
  exit 0
}

Write-Host "Copying $SourceSchema schema into $TargetSchema"

$dump = & pg_dump -h $DbHost -p $DbPort -U $DbUser -d $DbName --schema=$SourceSchema --no-owner --no-privileges
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$rewritten = $dump |
  ForEach-Object { $_ -replace "CREATE SCHEMA $SourceSchema;", "CREATE SCHEMA IF NOT EXISTS $TargetSchema;" } |
  ForEach-Object { $_ -replace "SCHEMA $SourceSchema", "SCHEMA $TargetSchema" } |
  ForEach-Object { $_ -replace "$SourceSchema\.", "$TargetSchema." } |
  ForEach-Object { $_ -replace "SET search_path = $SourceSchema", "SET search_path = $TargetSchema" }

$rewritten | & psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host 'E2E schema ready.'
