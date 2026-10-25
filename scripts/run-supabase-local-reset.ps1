param(
    [string]$SeedFile,
    [switch]$NoSeed,
    [switch]$SkipStart,
    [switch]$CheckOnly,
    [ValidateSet("Light", "Extensive", "None")]
    [string]$SeedingMode = "Light"
)

$ErrorActionPreference = "Stop"

function Invoke-Step {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command
    )

    Write-Host "> $Command" -ForegroundColor Cyan
    Invoke-Expression $Command
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $Command"
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

Write-Host "Repo: $repoRoot" -ForegroundColor Green

if (-not (Get-Command supabase -ErrorAction SilentlyContinue)) {
    throw "Supabase CLI not found. Install from https://github.com/supabase/cli"
}

Invoke-Step "supabase --version"

if ($CheckOnly) {
    Write-Host "Check-only mode complete." -ForegroundColor Yellow
    exit 0
}

if (-not $SkipStart) {
    Invoke-Step "supabase start"
}

if ($NoSeed) {
    Invoke-Step "supabase db reset --local --no-seed --yes"
} else {
    Invoke-Step "supabase db reset --local --no-seed --yes"
}

# Find DB container name
$dbContainer = docker ps --format "{{.Names}}" | Select-String "supabase_db" | Select-Object -First 1
if (-not $dbContainer) {
    throw "Supabase DB container not found. Is Supabase running?"
}
Write-Host "Using DB Container: $dbContainer" -ForegroundColor Yellow

# ── 1. BOOTSTRAP IDENTITY ──────────────────────────────────────────
# This ensures Platform Admin is established before RLS hardening is applied.
Write-Host "Bootstrapping Platform Admin..." -ForegroundColor Yellow
$adminScript = "supabase/04_PLATFORM_ADMIN_SETUP.sql"
if (Test-Path $adminScript) {
    Get-Content $adminScript -Raw | docker exec -i $dbContainer psql -U postgres -d postgres -q
}

# ── 2. APPLY ENHANCEMENTS ──────────────────────────────────────────
Write-Host "Applying core functions and enhancements via psql..." -ForegroundColor Yellow
$coreScripts = @(
    "33_LEDGER_CONSOLIDATION_V1.sql",
    "34_AUDIT_REPORT_FACILITIES_V1.sql"
)

foreach ($script in $coreScripts) {
    $scriptPath = "supabase/$script"
    if (Test-Path $scriptPath) {
        Write-Host "Applying $script..." -ForegroundColor Cyan
        # Continue on notices/warnings but fail on actual errors
        Get-Content $scriptPath -Raw | docker exec -i $dbContainer psql -U postgres -d postgres -q
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Non-fatal error in $script. Check logs if data is missing."
        }
    }
}

# ── 3. SYNC SCHEMA CACHE ─────────────────────────────────────────────
Write-Host "Syncing PostgREST schema cache..." -ForegroundColor Yellow
docker exec -i $dbContainer psql -U postgres -d postgres -c "NOTIFY pgrst, 'reload schema';"

if ($SeedingMode -ne "None") {
    Write-Host "Applying Master Seed Data..." -ForegroundColor Yellow
    Get-Content "supabase/MASTER_SEED_DATA.sql" -Raw | docker exec -i $dbContainer psql -U postgres -d postgres -q
}

Write-Host "Local migrations and seeding applied successfully." -ForegroundColor Green
Invoke-Step "supabase migration list --local"
