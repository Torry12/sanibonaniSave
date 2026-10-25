$outFile = 'supabase/online/000_CONSOLIDATED_ONLINE_SYNC.sql'
Set-Content -Path $outFile -Value '-- Consolidated Online Sync Script'

$baseFiles = @('07_RESET_DATABASE.sql', '01_DATABASE_SCHEMA.sql', '02_FUNCTIONS_AND_TRIGGERS.sql', '03_SECURITY_AND_RLS.sql', '04_PLATFORM_ADMIN_SETUP.sql', '05_PERFORMANCE_VIEWS.sql')
foreach ($f in $baseFiles) {
    $fullPath = Join-Path 'supabase' $f
    Add-Content -Path $outFile -Value "`n-- [START] $f`n"
    if (Test-Path $fullPath) {
        Get-Content $fullPath | Add-Content -Path $outFile
    } else {
        Write-Warning "File not found: $fullPath"
    }
    Add-Content -Path $outFile -Value "`n-- [END] $f`n"
}

$onlineFiles = @('01_ONLINE_SPECIFIC_SETUP.sql', '02_ONLINE_SEED_DATA.sql')
foreach ($f in $onlineFiles) {
    $fullPath = Join-Path 'supabase/online' $f
    Add-Content -Path $outFile -Value "`n-- [START] $f`n"
    if (Test-Path $fullPath) {
        Get-Content $fullPath | Add-Content -Path $outFile
    } else {
         Write-Warning "File not found: $fullPath"
    }
    Add-Content -Path $outFile -Value "`n-- [END] $f`n"
}
Write-Host "Generated $outFile"
