# -----------------------------------------------------------------------------
# SanibonaniSave - CLOUD DEPLOYMENT SCRIPT
# Version: 1.2 (Hardened Production Sync - Fixed Flags)
# -----------------------------------------------------------------------------

$ErrorActionPreference = "Stop"

# 1. SETUP: Configuration
$ProjectRef = "aszmodkqpafwtnfufmow"
$RebuildFile = "supabase/online/000_CONSOLIDATED_ONLINE_SYNC.sql"

Write-Host ">> STARTING PRODUCTION SYNCHRONIZATION" -ForegroundColor Cyan
Write-Host "Project: $ProjectRef" -ForegroundColor Yellow
Write-Host "Source: $RebuildFile" -ForegroundColor Yellow

# 2. VERIFICATION: Ensure we are in the right environment
if (-not (Test-Path "supabase")) {
    throw "Error: Must be run from the repository root."
}

if (-not (Get-Command npx -ErrorAction SilentlyContinue)) {
    throw "Error: Node.js (npx) is required."
}

# 3. SECURITY: Prompt for confirmation
if ($env:SUPABASE_AUTO_CONFIRM -eq "YES") {
    $Confirmation = "YES"
} else {
    $Confirmation = Read-Host "!! WARNING: This will run a DESTRUCTIVE RESET on the ONLINE project. Are you sure? (YES/no)"
}

if ($Confirmation -ne "YES") {
    Write-Host "Deployment aborted by user." -ForegroundColor Red
    exit 0
}

# 4. EXECUTION: Run the remote query
Write-Host ">> Initializing remote connection..." -ForegroundColor Gray
try {
    # Check if project is linked
    if (-not (Test-Path ".supabase/project-ref")) {
        Write-Host "!! Project not linked. Attempting to link to $ProjectRef..." -ForegroundColor Yellow
        npx.cmd -y supabase link --project-ref $ProjectRef --password $env:SUPABASE_DB_PASSWORD
    }

    # Execute the query against the linked project
    Write-Host ">> Executing remote SQL..." -ForegroundColor Cyan
    $output = npx.cmd -y supabase db query --linked --file $RebuildFile

    if ($LASTEXITCODE -ne 0) {
        throw "Remote query failed with exit code $LASTEXITCODE."
    }

    Write-Host ">> DEPLOYMENT SUCCESSFUL: Cloud project is now in sync with local Gold Standard." -ForegroundColor Green
} catch {
    Write-Host "!! DEPLOYMENT FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ">> Process Complete." -ForegroundColor Cyan
