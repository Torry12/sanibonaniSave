<#
02_seed_data.ps1

Seed the database after schema has been applied. By default this runs the "SAFE" seed (non-destructive,
small dataset suitable for development). You can choose `full` or `e2e` for larger seeds.

Usage:
    . .\db.env.ps1          # dot-source environment file created by 00_set_env.ps1
    .\02_seed_data.ps1 -SeedType safe

Or pass a DATABASE_URL env (e.g. for CI):
    $env:DATABASE_URL = 'postgres://postgres:pass@localhost:5432/postgres'
    .\02_seed_data.ps1 -SeedType full

SeedType options:
  safe  - runs the minimal SAFE_SEED (recommended for quick dev)
  e2e   - runs the E2E seed set (larger test dataset used by E2E flows)
  full  - runs the full comprehensive seed (may be slow, for staging/test)
#>

param(
    [ValidateSet('safe','e2e','full')]
    [string]$SeedType = 'safe'
)

$envFile = Join-Path $PSScriptRoot "db.env.ps1"
if (Test-Path $envFile) { . $envFile } else { Write-Host "Warning: db.env.ps1 not found; relying on existing env vars" -ForegroundColor Yellow }

if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    Write-Error "psql not found. Install PostgreSQL client and ensure psql is on PATH."
    exit 2
}

# Map seed types to files in repo
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$safeFile = Join-Path $repoRoot "CONSOLIDATED_FOR_DASHBOARD.sql"    # contains SAFE seed block and additional seeds
$e2eFile  = Join-Path $repoRoot "CONSOLIDATED_FULL.sql"             # full consolidated (may include full test seeds)
$fullFile = Join-Path $repoRoot "CONSOLIDATED_FULL.sql"

switch ($SeedType) {
    'safe' {
        $fileToApply = $safeFile
    }
    'e2e' {
        $fileToApply = $e2eFile
    }
    'full' {
        $fileToApply = $fullFile
    }
}

if (-not (Test-Path $fileToApply)) {
    Write-Error "Seed file not found: $fileToApply"
    exit 1
}

Write-Host "Applying seed file: $fileToApply"
try {
    & psql -v ON_ERROR_STOP=1 -f $fileToApply
    if ($LASTEXITCODE -ne 0) { throw "psql returned exit code $LASTEXITCODE" }
    Write-Host "Seed applied successfully (SeedType=$SeedType)." -ForegroundColor Green
} catch {
    Write-Error "Failed to apply seed file: $_"
    exit 20
}

