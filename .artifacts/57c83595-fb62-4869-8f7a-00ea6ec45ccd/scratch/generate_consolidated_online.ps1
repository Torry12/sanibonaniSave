$rebuildFile = "supabase/online/00_REBUILD_ONLINE.sql"
$outFile = "supabase/online/CONSOLIDATED_ONLINE_REBUILD.sql"
$lines = Get-Content $rebuildFile

Set-Content -Path $outFile -Value "-- Consolidated Online Rebuild Script (Auto-generated)`n"

foreach ($line in $lines) {
    if ($line -match "\\i '(.+)'") {
        $include = $Matches[1]
        # Resolve path relative to supabase/online/
        $baseDir = "supabase/online/"
        $rawPath = [System.IO.Path]::Combine($baseDir, $include)
        $fullPath = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $rawPath))

        if (Test-Path $fullPath) {
            Write-Host "Including $fullPath"
            Add-Content -Path $outFile -Value "`n-- [START] $include`n"
            Get-Content $fullPath | Add-Content -Path $outFile
            Add-Content -Path $outFile -Value "`n-- [END] $include`n"
        } else {
            Write-Warning "Could not find $fullPath"
        }
    }
}
