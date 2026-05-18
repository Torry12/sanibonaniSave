<#
apply_in_order.ps1 — runs each included file from a consolidated driver one-by-one using psql.
Usage:
  $env:PG_CONN = "host=... dbname=... user=... password=..."; .\supabase\apply_in_order.ps1 -Driver supabase/CONSOLIDATED_FULL.sql

Behavior:
  - Parses the driver file for "\\i 'path'" includes.
  - Executes each referenced file individually with `psql $PG_CONN -f <file>`.
  - Stops on first error and writes a per-file log to supabase/apply_logs/<timestamp>.log
#>

param(
    [string]$Driver = 'supabase/CONSOLIDATED_FULL.sql',
    [string]$Conn = $env:PG_CONN
)

if (-not (Test-Path $Driver)) {
    Write-Error "Driver file $Driver not found."; exit 2
}
if (-not $Conn) { Write-Error "PG_CONN not set. Set env var or pass -Conn"; exit 3 }

# ensure psql is available
$psql = Get-Command psql -ErrorAction SilentlyContinue
if (-not $psql) {
    Write-Error "psql not found in PATH."; exit 4
}

# prepare log dir
$logDir = Join-Path (Split-Path -Parent $Driver) 'apply_logs'
if (-not (Test-Path $logDir)) { New-Item -Path $logDir -ItemType Directory | Out-Null }
$timestamp = (Get-Date).ToString('yyyyMMdd_HHmmss')
$logFile = Join-Path $logDir "apply_$timestamp.log"

# read driver and extract include lines
$includes = Select-String -Path $Driver -Pattern "^\\i\s+'(.+)'" -AllMatches | ForEach-Object { $_.Matches } | ForEach-Object { $_.Groups[1].Value }
if (-not $includes) { Write-Error "No includes found in driver $Driver"; exit 5 }

Write-Host "Applying $($includes.Count) files listed in $Driver"
Add-Content -Path $logFile -Value "Applying driver: $Driver`nStarted: $(Get-Date)`n" -Encoding UTF8

foreach ($file in $includes) {
    if (-not (Test-Path $file)) { Add-Content -Path $logFile -Value "SKIP: $file not found`n"; Write-Host "SKIP: $file not found"; continue }
    Add-Content -Path $logFile -Value "-- Running: $file`n"
    Write-Host "Running: $file"

    & psql $Conn -f $file 2>&1 | Tee-Object -Variable output
    $exit = $LASTEXITCODE
    Add-Content -Path $logFile -Value ($output -join "`n")

    if ($exit -ne 0) {
        Add-Content -Path $logFile -Value "ERROR: $file exited with code $exit`n"
        Write-Error "File $file failed with exit code $exit. See $logFile for details."
        exit $exit
    } else {
        Add-Content -Path $logFile -Value "OK: $file completed successfully`n"
        Write-Host "OK: $file"
    }
}

Add-Content -Path $logFile -Value "Completed: $(Get-Date)`n"
Write-Host "All files applied successfully. Log: $logFile"

