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

$root = Resolve-Path "..\.."  # script is in supabase/scripts
$repoRoot = $root.Path
Write-Output "Repository root: $repoRoot"

$dst = Join-Path $repoRoot "supabase\CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql"
if (Test-Path $dst) {
    $bak = $dst + ".bak_" + (Get-Date -Format 'yyyyMMddHHmmss')
    Write-Output "Backing up existing $dst to $bak"
    Rename-Item -Path $dst -NewName $bak
}

Write-Output "Creating consolidated SQL: $dst"

# 1) Base consolidated schema
$base = Join-Path $repoRoot "supabase\01_DATABASE_SCHEMA.sql"
if (-not (Test-Path $base)) { throw "Base schema not found: $base" }
Get-Content $base | Add-Content $dst

# 2) Ordered migrations (skip initial_schema which duplicates base)
$migrations = @(
  '20260514000200_security_and_rls.sql',
  '20260514000300_platform_admin_setup.sql',
  '20260514000400_migrations_and_updates.sql',
  '20260514000500_platform_admin_auth_alignment.sql',
  '20260514000600_platform_admin_rls_hotfix.sql',
  '20260514000700_add_rosca_rotation_method.sql',
  '20260514000800_architecture_model_schema_templates.sql',
  '20260514000900_align_validation_constraints_with_app.sql',
  '20260515000000_consolidated_rls_alignment.sql',
  '20260515000100_atomic_balance_updates.sql',
  '20260515000200_disbursement_rpcs.sql',
  '20260515000200_group_voting_and_multi_admin.sql',
  '20260518000100_backfill_group_ledger_from_payments.sql',
  '20260518000200_recompute_group_balances_from_ledger.sql'
)

foreach ($m in $migrations) {
    $p = Join-Path $repoRoot (Join-Path 'supabase\migrations' $m)
    if (Test-Path $p) {
        "`n-- =====================================================================" | Add-Content $dst
        ("-- Inlined migration: $m") | Add-Content $dst
        "-- =====================================================================`n" | Add-Content $dst
        Get-Content $p | Add-Content $dst
    } else {
        Write-Warning "Migration not found: $p (skipping)"
    }
}

Write-Output "Consolidated script written to: $dst"
Write-Output "Please review the file before pasting into Supabase SQL editor."

