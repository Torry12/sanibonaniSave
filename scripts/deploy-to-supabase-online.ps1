# -----------------------------------------------------------------------------
# SanibonaniSave - CLOUD DEPLOYMENT SCRIPT
# Version: 1.3 (Ground-zero rebuild, opt-in destructive guard)
# -----------------------------------------------------------------------------

param(
    [switch]$Rebuild
)

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
if (-not $Rebuild) {
    Write-Host ">> Nothing run. 000_CONSOLIDATED_ONLINE_SYNC.sql is a ground-zero rebuild: it drops every" -ForegroundColor Yellow
    Write-Host "   public-schema object on the ONLINE project and deletes the master-seed auth users, which" -ForegroundColor Yellow
    Write-Host "   takes the group, member, contribution and ledger data with it." -ForegroundColor Yellow
    Write-Host "   Dump first:  npx supabase db dump --linked --data-only -f <restore-file>" -ForegroundColor Cyan
    Write-Host "   Then rerun with -Rebuild, or use an additive supabase/online/SYNC_DELTA_*.sql instead." -ForegroundColor Yellow
    exit 1
}

if ($env:SUPABASE_AUTO_CONFIRM -eq "YES") {
    $Confirmation = "YES"
} else {
    $Confirmation = Read-Host "!! WARNING: This will WIPE AND REBUILD the ONLINE project's public schema. Are you sure? (YES/no)"
}

if ($Confirmation -ne "YES") {
    Write-Host "Deployment aborted by user." -ForegroundColor Red
    exit 0
}

# 4. EXECUTION: Run the remote query
Write-Host ">> Initializing remote connection..." -ForegroundColor Gray

# The generated rebuild file guards itself: it aborts unless sanibonani.allow_destructive_rebuild is
# set in the same session, so that no paste of the file can wipe a tier by accident. The opt-in is
# prepended into a payload here rather than uncommented in supabase/online/, because that file is
# generated and a checked-in opt-in is a loaded gun.
$Payload = Join-Path ([IO.Path]::GetTempPath()) ("sanibonani_cloud_rebuild_" + [guid]::NewGuid().ToString("N") + ".sql")
try {
    if (-not (Test-Path $RebuildFile)) {
        throw "Missing $RebuildFile. Regenerate it: .\supabase\scripts\generate_online_sync.ps1"
    }
    Set-Content -Path $Payload -Value "SET sanibonani.allow_destructive_rebuild = 'CONFIRM_DROP_ALL_DATA';" -Encoding ASCII
    Add-Content -Path $Payload -Value (Get-Content $RebuildFile -Raw) -Encoding ASCII

    # Check if project is linked
    if (-not (Test-Path ".supabase/project-ref")) {
        Write-Host "!! Project not linked. Attempting to link to $ProjectRef..." -ForegroundColor Yellow
        npx.cmd -y supabase link --project-ref $ProjectRef --password $env:SUPABASE_DB_PASSWORD
    }

    # Execute the query against the linked project
    Write-Host ">> Executing remote SQL..." -ForegroundColor Cyan
    $output = npx.cmd -y supabase db query --linked --file $Payload

    if ($LASTEXITCODE -ne 0) {
        throw "Remote query failed with exit code $LASTEXITCODE."
    }

    Write-Host $output
    Write-Host ">> DEPLOYMENT SUCCESSFUL: Cloud project is now in sync with local Gold Standard." -ForegroundColor Green
    Write-Host "   The tier holds no fixture data until it is seeded: .\scripts\seed-supabase-online.ps1" -ForegroundColor Yellow
} catch {
    Write-Host "!! DEPLOYMENT FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    Remove-Item $Payload -ErrorAction SilentlyContinue
}

Write-Host ">> Process Complete." -ForegroundColor Cyan
