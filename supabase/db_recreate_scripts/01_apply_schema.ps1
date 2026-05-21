<#
01_apply_schema.ps1

Apply the consolidated schema SQL to your database. Requires `psql` on PATH or
that environment variables are provided via `db.env.ps1` (created by 00_set_env.ps1).

Usage (after creating db.env.ps1):
    . .\db.env.ps1    # dot-source the env
    .\01_apply_schema.ps1

If you prefer to pass a full DATABASE_URL instead of env vars, set `$env:DATABASE_URL`
before running the script.
#>

$envFile = Join-Path $PSScriptRoot "db.env.ps1"
if (Test-Path $envFile) {
    Write-Host "Loading environment from $envFile"
    . $envFile
} else {
    Write-Host "No db.env.ps1 found in script folder. Make sure environment variables are set." -ForegroundColor Yellow
}

# Resolve schema file (consolidated file placed at supabase/CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql)
$schemaFile = Join-Path $PSScriptRoot "..\CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql"
if (-not (Test-Path $schemaFile)) {
    Write-Error "Schema file not found: $schemaFile"
    exit 1
}

# Ensure psql is available
if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    Write-Error "psql not found. Install the PostgreSQL client (psql) and ensure it's on PATH."
    exit 2
}

try {
    Write-Host "Applying schema from $schemaFile ..."
    & psql -v ON_ERROR_STOP=1 -f $schemaFile
    if ($LASTEXITCODE -ne 0) { throw "psql returned exit code $LASTEXITCODE" }
    Write-Host "Schema applied successfully." -ForegroundColor Green
} catch {
    Write-Error "Failed to apply schema: $_"
    exit 10
}

