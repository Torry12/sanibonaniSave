$ErrorActionPreference = "Stop"

function Invoke-Query {
    param([string]$File)
    Write-Host "Running $File..."
    npx supabase db query -f "supabase\$File"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to run $File"
    }
}

Invoke-Query "07_RESET_DATABASE.sql"
Invoke-Query "01_DATABASE_SCHEMA.sql"
Invoke-Query "02_FUNCTIONS_AND_TRIGGERS.sql"
Invoke-Query "03_SECURITY_AND_RLS.sql"
Invoke-Query "04_PLATFORM_ADMIN_SETUP.sql"
Invoke-Query "05_PERFORMANCE_VIEWS.sql"
Invoke-Query "06_SEED_DATA.sql"

Write-Host "Database completely rebuilt successfully!" -ForegroundColor Green
