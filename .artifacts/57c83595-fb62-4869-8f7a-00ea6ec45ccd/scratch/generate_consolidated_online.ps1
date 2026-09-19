$rebuildFile = "supabase/online/00_REBUILD_ONLINE.sql"
$outFile = "supabase/online/CONSOLIDATED_ONLINE_REBUILD.sql"
$lines = Get-Content $rebuildFile

Set-Content -Path $outFile -Value "-- Consolidated Online Rebuild Script (Auto-generated)`n"

foreach ($line in $lines) {
    if ($line -match "\\i '(.+)'") {
        $include = $Matches[1]
        # Resolve path relative to supabase/online/
        $path = Join-Path "supabase/online/" $include
        if (Test-Path $path) {
            Write-Host "Including $path"
            Add-Content -Path $outFile -Value "`n-- [START] $include`n"
            Get-Content $path | Add-Content -Path $outFile
            Add-Content -Path $outFile -Value "`n-- [END] $include`n"
        } else {
            Write-Warning "Could not find $path"
        }
    }
}
