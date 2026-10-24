<#
generate_consolidated_schema.ps1

Creates supabase\CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql: GROUND_ZERO plus the cloud-only
overrides, formatted for one paste into the Supabase SQL editor.

Usage (from repo root):
  powershell -ExecutionPolicy Bypass -File .\supabase\scripts\generate_consolidated_schema.ps1

Run supabase\scripts\generate_ground_zero.ps1 first; this script consumes its output
verbatim, so the dashboard artifact can never drift from the baseline.

This script intentionally skips the migration file that duplicates the base schema
(20260514000100_initial_schema.sql) to avoid duplicate CREATE TABLE errors.

It also does not re-inline 30_STORAGE_RLS.sql: GROUND_ZERO already ends with that
module, so re-inlining it applied every storage policy twice and put the online
overrides after storage, which is the reverse of 00_REBUILD_ONLINE.sql order.

Always review the generated SQL before running in production and backup your DB.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$repoRoot  = (Get-Item $scriptDir).Parent.Parent.FullName
$sb        = Join-Path $repoRoot 'supabase'
$dst       = Join-Path $sb 'CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql'

# Cloud-only overrides. GROUND_ZERO already inlines 01, 02, 18, 03, 04, 05 and 30.
$additionalModules = @(
    'online\01_ONLINE_SPECIFIC_SETUP.sql'
)

$utf8 = New-Object System.Text.UTF8Encoding($false)
$sep  = '-- ' + ('=' * 69)
$nl   = "`n"

# 1) Base ground zero schema (the generated concat of the master scripts)
$base = Join-Path $sb 'migrations\00000000000000_GROUND_ZERO.sql'
if (-not (Test-Path $base)) {
    throw "Base schema not found: $base - regenerate GROUND_ZERO from the master scripts first (.\supabase\scripts\generate_ground_zero.ps1)."
}
$baseText = [System.IO.File]::ReadAllText($base, $utf8).Replace("`r`n", "`n").TrimEnd("`n")
if ([string]::IsNullOrWhiteSpace($baseText)) { throw "Base schema is empty: $base" }

# 2) Append the cloud-only overrides under their separator banners.
# Built with an explicit accumulator: a @(foreach ...) would capture any Write-Output from
# inside the loop and splice it into the SQL.
$blocks = @()
foreach ($m in $additionalModules) {
    $p = Join-Path $sb $m
    if (-not (Test-Path $p)) { throw "Module script not found: $p" }
    $body = [System.IO.File]::ReadAllText($p, $utf8).Replace("`r`n", "`n").TrimEnd("`n")
    if ([string]::IsNullOrWhiteSpace($body)) { throw "Module script is empty: $p" }
    # The empty element keeps the historical blank line between the banner and the module
    # body, so regeneration changes nothing but the baseline's "Generated:" date.
    $blocks += (@($sep, "-- Inlined module: supabase\$m", $sep, '', $body) -join $nl)
    Write-Host ("inlined {0,-46} {1,5} lines" -f $m, ($body -split "`n").Count)
}

$text = (@($baseText) + $blocks) -join ($nl + $nl)
$text = ($text -replace "`r`n", "`n").TrimEnd("`n") + "`n"
$text = $text.Replace("`n", "`r`n")
[System.IO.File]::WriteAllText($dst, $text, $utf8)

Write-Output "Consolidated script written to: $dst"
Write-Output "Please review the file before pasting into Supabase SQL editor."
