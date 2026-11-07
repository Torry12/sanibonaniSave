<#
generate_ground_zero.ps1

Rebuilds supabase\migrations\00000000000000_GROUND_ZERO.sql by inlining the structural
master scripts in the order applied by supabase\00_REBUILD_ALL.sql and
supabase\online\00_REBUILD_ONLINE.sql.

Deterministic by design: masters in, verbatim module bodies out, so the only line that
changes between runs is the "Generated:" date. A regeneration that shows content diffs
always means the masters really changed - never the generator.

Usage (from repo root):
  powershell -ExecutionPolicy Bypass -File .\supabase\scripts\generate_ground_zero.ps1

Output:
  supabase\migrations\00000000000000_GROUND_ZERO.sql

This file intentionally contains no SQL semantics of its own. Edit the masters.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$repoRoot  = (Get-Item $scriptDir).Parent.Parent.FullName
$sb        = Join-Path $repoRoot 'supabase'
$dst       = Join-Path $sb 'migrations\00000000000000_GROUND_ZERO.sql'

$version = '2.2 (Regenerated from the authoritative master scripts)'

# Structural modules, in rebuild order. 07_RESET_DATABASE.sql (destructive) and every
# SEED_*.sql fixture are excluded: dev data is not part of the schema baseline.
$modules = @(
    '01_DATABASE_SCHEMA.sql',
    '02_FUNCTIONS_AND_TRIGGERS.sql',
    '18_DEPLOY_PROCESS_COMPLEX_PAYMENT_V4.sql',
    '03_SECURITY_AND_RLS.sql',
    '04_PLATFORM_ADMIN_SETUP.sql',
    '05_PERFORMANCE_VIEWS.sql',
    '30_STORAGE_RLS.sql'
)

$utf8 = New-Object System.Text.UTF8Encoding($false)
# Build the non-ASCII characters at run time so this .ps1 stays pure ASCII and reads the
# same under Windows PowerShell 5.1 (ANSI) and pwsh 7 (UTF-8).
$rule   = '-- ' + ([string][char]0x2500 * 77)
$em     = [string][char]0x2014
$nl     = "`n"

$headerTemplate = @'
@RULE@
-- SanibonaniSave @EM@ GROUND ZERO MASTER MIGRATION (Consolidated)
-- Generated: @DATE@
-- Version: @VERSION@
--
-- Applied in this order, mirroring supabase/00_REBUILD_ALL.sql and
-- supabase/online/00_REBUILD_ONLINE.sql:@LIST@

--
-- Deliberately excluded: 07_RESET_DATABASE.sql (destructive) and every SEED_*.sql
-- (dev fixtures are not part of the schema baseline).
--
-- This baseline is the ONLY file that belongs in supabase/migrations/. "supabase db
-- reset" applies that directory after the baseline, so any extra file there overwrites
-- whatever the masters just declared. Three idempotency migrations used to sit here and
-- silently stripped the FIN_002 / FIN_003 / SEC_002 guards from increment_group_balance,
-- pay_platform_fee_v2 and record_loan_repayment_v1 on every local reset; their logic is
-- folded into 02_FUNCTIONS_AND_TRIGGERS.sql, so they now live in supabase/legacy_migrations/
-- where nothing applies them. Add a structural change by editing a master and regenerating,
-- never by dropping another file next to this one.
@RULE@
'@

$moduleList = ($modules | ForEach-Object { "$nl--   $_" }) -join ''
$header = $headerTemplate.Replace("`r`n", "`n").Replace('@RULE@', $rule).
    Replace('@EM@', $em).Replace('@VERSION@', $version).
    Replace('@DATE@', (Get-Date -Format 'yyyy-MM-dd')).Replace('@LIST@', $moduleList)

$blocks = @()
foreach ($m in $modules) {
    $p = Join-Path $sb $m
    if (-not (Test-Path $p)) { throw "Master script not found: $p" }
    $body = [System.IO.File]::ReadAllText($p, $utf8).Replace("`r`n", "`n").TrimEnd("`n")
    if ([string]::IsNullOrWhiteSpace($body)) { throw "Master script is empty: $p" }
    $blocks += (@($rule, "-- INLINED MODULE: $m", $rule, $body) -join $nl)
    Write-Output ("inlined {0,-46} {1,5} lines" -f $m, ($body -split "`n").Count)
}

$text = (@($header) + $blocks) -join ($nl + $nl)
$text = ($text -replace "`r`n", "`n").TrimEnd("`n") + "`n"
$text = $text.Replace("`n", "`r`n")
[System.IO.File]::WriteAllText($dst, $text, $utf8)

Write-Output "GROUND_ZERO written to: $dst"
Write-Output "Regenerate the dashboard artifact too: .\supabase\scripts\generate_consolidated_schema.ps1"
