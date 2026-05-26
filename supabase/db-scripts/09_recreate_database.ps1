# 09_recreate_database.ps1
<#
Purpose: Recreate a development/staging database from consolidated artifacts in this repo.
This runner applies schema, functions, RLS/security, performance helpers and safe seed in a
controlled, idempotent order. It is intended for development/staging only. DO NOT run
this on production without review and backups.

Usage (PowerShell):
  PowerShell -NoProfile -ExecutionPolicy Bypass -File .\09_recreate_database.ps1 -Host <host> -Port <port> -User <user> -Database <db> -Password <password>

Options:
  -Host       Database host
  -Port       Database port (default 5432)
  -User       Database user
  -Database   Database name
  -Password   Database password (or set PGPASSWORD env var before running)
  -DryRun     If present, commands will be printed but not executed
  -SkipSeed   If present, will not run the SAFE seed (useful for quick schema-only apply)

This script runs psql commands to apply the following files (in order):
  1) CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql
  2) 02_SECURITY_AND_RLS.sql
  3) 39_ENGINEERING_PERFORMANCE_OPTIMIZATIONS.sql
  4) CONSOLIDATED_FOR_DASHBOARD_SCHEMA_PLUS_SAFE_SEED.sql (unless -SkipSeed)

The script checks that expected files exist, and exits on first failure.
#>

param(
    [Parameter(Mandatory=$true)] [string]$Host,
    [Parameter(Mandatory=$false)] [int]$Port = 5432,
    [Parameter(Mandatory=$true)] [string]$User,
    [Parameter(Mandatory=$true)] [string]$Database,
    [Parameter(Mandatory=$false)] [string]$Password,
    [switch]$DryRun,
    [switch]$SkipSeed
)

$RepoRoot = Split-Path -Parent $PSScriptRoot
$SupabaseDir = Join-Path $RepoRoot 'supabase'
$Files = @(
    'CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql',
    '02_SECURITY_AND_RLS.sql',
    '39_ENGINEERING_PERFORMANCE_OPTIMIZATIONS.sql',
    'CONSOLIDATED_FOR_DASHBOARD_SCHEMA_PLUS_SAFE_SEED.sql'
)

function Fail([string]$msg){ Write-Host "ERROR: $msg" -ForegroundColor Red; exit 1 }

# Locate psql
$psql = 'psql'
if (-not (Get-Command $psql -ErrorAction SilentlyContinue)) {
    # Try common SDK path for Windows
    $candidate = "$env:LOCALAPPDATA\Android\Sdk\postgres\bin\psql.exe"
    if (Test-Path $candidate) { $psql = $candidate }
}

Write-Host "Using psql: $psql"
Write-Host "Target DB: $User@$Host:$Port/$Database"
Write-Host "DryRun: $DryRun	 SkipSeed: $SkipSeed"

foreach ($f in $Files) {
    $full = Join-Path $SupabaseDir $f
    if (-not (Test-Path $full)) {
        Fail "Required file missing: $full"
    }
}

# Helper to run a single psql file
function Run-File([string]$filePath) {
    Write-Host "\n==> Applying: $filePath"
    if ($DryRun) { return }
    if ($Password) { $env:PGPASSWORD = $Password }
    $args = "-h", $Host, "-p", $Port.ToString(), "-U", $User, "-d", $Database, "-f", $filePath

    $proc = Start-Process -FilePath $psql -ArgumentList $args -NoNewWindow -Wait -PassThru -ErrorAction Stop
    if ($proc.ExitCode -ne 0) {
        Fail "psql returned exit code $($proc.ExitCode) while executing $filePath"
    }
}

# Execution order
Run-File (Join-Path $SupabaseDir 'CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql')
Run-File (Join-Path $SupabaseDir '02_SECURITY_AND_RLS.sql')
Run-File (Join-Path $SupabaseDir '39_ENGINEERING_PERFORMANCE_OPTIMIZATIONS.sql')
if (-not $SkipSeed) {
    Run-File (Join-Path $SupabaseDir 'CONSOLIDATED_FOR_DASHBOARD_SCHEMA_PLUS_SAFE_SEED.sql')
} else {
    Write-Host "Skipping SAFE seed as requested."
}

Write-Host "\nAll steps completed successfully."

# End of script

