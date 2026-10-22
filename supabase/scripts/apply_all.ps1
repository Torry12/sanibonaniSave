<#
apply_all.ps1 - run the consolidated rebuild (supabase/00_REBUILD_ALL.sql) through psql.

Usage:
  $env:PG_CONN = "host=localhost port=5432 dbname=postgres user=postgres password=postgres"
  .\supabase\scripts\apply_all.ps1                # prompts before it touches anything
  .\supabase\scripts\apply_all.ps1 -WhatIf        # show the resolved command, run nothing

WHAT THIS DOES: 00_REBUILD_ALL.sql starts with \i '07_RESET_DATABASE.sql', which wipes
tables, functions, triggers and views, then re-applies the masters and the seed data. It
is a destructive rebuild of a whole tier, not a sync. Under the project rule, a change to
the masters reaches a tier through a dated delta file, not through this script - so use
this only when you mean to throw away everything in the tier it points at.

Two defects this script used to have, both of which made a failed run look like success:
  * the SQL path was resolved from the current directory, and 00_REBUILD_ALL.sql's 16 \i
    targets are relative to supabase\. Run from the repository root, every include fails.
  * psql was run without ON_ERROR_STOP, so it kept going past each failure and still
    exited 0, and this script then printed "completed successfully".
The working directory is now set from $PSScriptRoot and ON_ERROR_STOP is on, so a broken
include stops the run and its exit code is the truth.
#>

[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [string]$Conn = $env:PG_CONN
)

# Every guard below is `Write-Error ...; exit N`. That works because $ErrorActionPreference
# stays at its default: under 'Stop' each Write-Error would terminate the script and
# PowerShell would return 1 for all of them, turning a diagnosable failure into a guess.

# Resolve everything from where this script lives, never from the caller's directory.
$SupabaseDir = Split-Path -Parent $PSScriptRoot                    # ...\supabase
$scriptPath  = Join-Path $SupabaseDir '00_REBUILD_ALL.sql'

# The local checks come first: telling you the SQL tree is incomplete is more useful than
# complaining about a credential, and it means a dry run needs no connection at all.
if (-not (Test-Path -LiteralPath $scriptPath)) {
    Write-Error "Consolidated rebuild script not found at $scriptPath"
    exit 4
}

# The first include is the reset. If it is not where 00_REBUILD_ALL.sql expects it, the
# run would fail 16 times and, before ON_ERROR_STOP, report success anyway.
$firstInclude = (Select-String -LiteralPath $scriptPath -Pattern "^\\i '([^']+)'" |
                 Select-Object -First 1).Matches.Groups[1].Value
if (-not (Test-Path -LiteralPath (Join-Path $SupabaseDir $firstInclude))) {
    Write-Error "00_REBUILD_ALL.sql includes '$firstInclude' relative to $SupabaseDir, and that file is not there."
    exit 5
}

if (-not $Conn) {
    Write-Error 'PG_CONN not set. Set the environment variable or pass -Conn; this script never echoes it.'
    exit 2
}
if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    Write-Error 'psql not found in PATH.'
    exit 3
}

# Never name the connection string here: it carries the DB password, and the ShouldProcess
# text is echoed into the confirmation prompt (and into any transcript of this session).
if (-not $PSCmdlet.ShouldProcess("the tier PG_CONN points at: drop and recreate everything via $scriptPath", 'rebuild with psql')) {
    exit 0
}

Write-Host "Rebuilding from $scriptPath (working directory $SupabaseDir, ON_ERROR_STOP=1)" -ForegroundColor Cyan
Push-Location -LiteralPath $SupabaseDir
try {
    & psql $Conn -v ON_ERROR_STOP=1 -X --no-psqlrc -f $scriptPath
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($exitCode -ne 0) {
    Write-Error "psql exited with code $exitCode - the rebuild stopped partway, so this tier is in a partial state. Read the output above for the first failing statement."
    exit $exitCode
}
Write-Host 'REBUILD_ALL completed.' -ForegroundColor Green
