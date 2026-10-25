<#
deploy_core.ps1 — Deploy core SanibonaniSave SQL infrastructure in correct order.
Usage:
  $env:PG_CONN = "postgresql://user:pass@host:port/db"
  .\supabase\deploy_core.ps1
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
    Write-Error "psql executable not found in PATH."
    exit 3
}

$CoreScripts = @(
    "base/01_schema.sql",
    "base/02_functions.sql",
    "base/03_security.sql",
    "admin/01_platform_admin_setup.sql",
    "views/01_performance_views.sql"
)

Write-Host "Starting Core Database Deployment..." -ForegroundColor Cyan

foreach ($script in $CoreScripts) {
    $scriptPath = Join-Path (Get-Location) "supabase\$script"
    if (Test-Path $scriptPath) {
        Write-Host "Applying: $script" -ForegroundColor Yellow
        & psql $Conn -f $scriptPath
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Failed to apply $script"
            exit $LASTEXITCODE
        }
    } else {
        Write-Warning "Script not found: $scriptPath"
    }
}

Write-Host "Deployment Completed Successfully." -ForegroundColor Green
