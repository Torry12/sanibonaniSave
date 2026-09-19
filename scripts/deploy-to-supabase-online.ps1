# ─────────────────────────────────────────────────────────────────────────────
# SanibonaniSave — CLOUD DEPLOYMENT SCRIPT
# Version: 1.0 (Hardened Production Sync - August 2026)
# ─────────────────────────────────────────────────────────────────────────────

$ErrorActionPreference = "Stop"

# 1. SETUP: Configuration
$ProjectRef = "aszmodkqpafwtnfufmow"
$RebuildFile = "supabase/online/00_REBUILD_ONLINE.sql"

Write-Host "🚀 STARTING PRODUCTION SYNCHRONIZATION" -ForegroundColor Cyan
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
$Confirmation = Read-Host "⚠️ WARNING: This will run a DESTRUCTIVE RESET on the ONLINE project. Are you sure? (YES/no)"
if ($Confirmation -ne "YES") {
    Write-Host "Deployment aborted by user." -ForegroundColor Red
    exit 0
}

# 4. EXECUTION: Run the remote query
Write-Host "📦 Initializing remote connection..." -ForegroundColor Gray
try {
    # Using npx.cmd to ensure Windows compatibility
    npx.cmd supabase db query --project-ref $ProjectRef --file $RebuildFile

    Write-Host "✅ DEPLOYMENT SUCCESSFUL: Cloud project is now in sync with local Gold Standard." -ForegroundColor Green
} catch {
    Write-Host "❌ DEPLOYMENT FAILED: ${_.Exception.Message}" -ForegroundColor Red
    exit 1
}

Write-Host "🏁 Process Complete." -ForegroundColor Cyan
