<#
apply_all.ps1 — helper to run the consolidated apply script via psql (PowerShell)
Usage:
  # Set PG_CONN as a connection string, e.g.:
  $env:PG_CONN = "host=localhost port=5432 dbname=postgres user=postgres password=postgres"
  .\supabase\apply_all.ps1

This script will run `psql $PG_CONN -f supabase/00_REBUILD_ALL.sql` and stream output.
#>

param(
    [string]$Conn = $env:PG_CONN
)

if (-not $Conn) {
    Write-Error "PG_CONN not set. Please set environment variable PG_CONN or pass -Conn parameter."
    exit 2
}

# Check for psql
$psql = Get-Command psql -ErrorAction SilentlyContinue
if (-not $psql) {
    Write-Error "psql executable not found in PATH. Please install psql or use Supabase CLI."
    exit 3
}

$scriptPath = Join-Path (Get-Location) 'supabase\00_REBUILD_ALL.sql'
if (-not (Test-Path $scriptPath)) {
    Write-Error "Consolidated master script not found at $scriptPath"
    exit 4
}

Write-Host "Running consolidated rebuild using psql..."
& psql $Conn -f $scriptPath
$exitCode = $LASTEXITCODE
if ($exitCode -eq 0) { Write-Host "REBUILD_ALL completed successfully." -ForegroundColor Green } else { Write-Error "psql exited with code $exitCode"; exit $exitCode }

