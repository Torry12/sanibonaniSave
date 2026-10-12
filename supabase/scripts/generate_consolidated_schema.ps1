<#
generate_consolidated_schema.ps1

Creates a single consolidated SQL script suitable for pasting into the
Supabase SQL editor by inlining the base schema and ordered migrations.

Usage (from repo root):
  powershell -ExecutionPolicy Bypass -File .\supabase\scripts\generate_consolidated_schema.ps1

Output:
  supabase\CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql

This script intentionally skips the migration file that duplicates the base schema
(20260514000100_initial_schema.sql) to avoid duplicate CREATE TABLE errors.

Always review the generated SQL before running in production and backup your DB.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$repoRoot = (Get-Item $scriptDir).Parent.Parent.FullName
Write-Output "Repository root: $repoRoot"

$dst = Join-Path $repoRoot "supabase\CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql"
if (Test-Path $dst) {
    $bak = $dst + ".bak_" + (Get-Date -Format 'yyyyMMddHHmmss')
    Write-Output "Backing up existing $dst to $bak"
    Rename-Item -Path $dst -NewName $bak
}

Write-Output "Creating consolidated SQL: $dst"

# 1) Base ground zero schema
$base = Join-Path $repoRoot "supabase\migrations\00000000000000_GROUND_ZERO.sql"
if (-not (Test-Path $base)) {
    $base = Join-Path $repoRoot "supabase\01_DATABASE_SCHEMA.sql"
}
if (-not (Test-Path $base)) { throw "Base schema not found: $base" }
Get-Content $base | Add-Content $dst

# 2) Additional SQL modules if present
$additionalModules = @(
  'supabase\online\01_ONLINE_SPECIFIC_SETUP.sql',
  'supabase\30_STORAGE_RLS.sql'
)

foreach ($m in $additionalModules) {
    $p = Join-Path $repoRoot $m
    if (Test-Path $p) {
        "`n-- =====================================================================" | Add-Content $dst
        ("-- Inlined module: $m") | Add-Content $dst
        "-- =====================================================================`n" | Add-Content $dst
        Get-Content $p | Add-Content $dst
    }
}

Write-Output "Consolidated script written to: $dst"
Write-Output "Please review the file before pasting into Supabase SQL editor."

