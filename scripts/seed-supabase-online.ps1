# -----------------------------------------------------------------------------
# SanibonaniSave - CLOUD SEEDING SCRIPT
# Version: 1.0 (High-Fidelity Production Seeding)
# -----------------------------------------------------------------------------

$ErrorActionPreference = "Stop"

# 1. SETUP: Configuration
$ProjectRef = "aszmodkqpafwtnfufmow"
$SeedFile = "supabase/MASTER_SEED_DATA.sql"

Write-Host ">> STARTING PRODUCTION SEEDING" -ForegroundColor Cyan
Write-Host "Project: $ProjectRef" -ForegroundColor Yellow
Write-Host "Source: $SeedFile" -ForegroundColor Yellow

# 2. VERIFICATION: Ensure we are in the right environment
if (-not (Test-Path "supabase")) {
    throw "Error: Must be run from the repository root."
}

if (-not (Get-Command npx -ErrorAction SilentlyContinue)) {
    throw "Error: Node.js (npx) is required."
}

# 3. SECURITY: Prompt for confirmation
# Skip prompt if automated input is provided
if ($env:SUPABASE_AUTO_CONFIRM -eq "YES") {
    $Confirmation = "YES"
} else {
    $Confirmation = Read-Host "!! WARNING: This will inject MOCK DATA into the ONLINE project. Are you sure? (YES/no)"
}

if ($Confirmation -ne "YES") {
    Write-Host "Seeding aborted by user." -ForegroundColor Red
    exit 0
}

# 4. EXECUTION: Run the remote query
Write-Host ">> Initializing remote connection..." -ForegroundColor Gray
try {
    # Check if project is linked
    if (-not (Test-Path ".supabase/project-ref")) {
        Write-Host "!! Project not linked. Attempting to link to $ProjectRef..." -ForegroundColor Yellow
        npx.cmd supabase link --project-ref $ProjectRef
    }

    # Execute the query against the linked project
    Write-Host ">> Executing remote SQL Seeding..." -ForegroundColor Cyan
    $output = npx.cmd supabase db query --linked --file $SeedFile

    if ($LASTEXITCODE -ne 0) {
        throw "Remote query failed with exit code $LASTEXITCODE."
    }

    Write-Host ">> SEEDING SUCCESSFUL: Cloud project now contains the Version 2.0 Golden Environment." -ForegroundColor Green
} catch {
    Write-Host "!! SEEDING FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ">> Process Complete." -ForegroundColor Cyan
