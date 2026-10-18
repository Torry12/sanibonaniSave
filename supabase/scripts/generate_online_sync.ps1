<#
generate_online_sync.ps1

Rebuilds supabase\online\000_CONSOLIDATED_ONLINE_SYNC.sql: the cloud paste-able twin of
supabase\online\00_REBUILD_ONLINE.sql. The orchestrator's relative \i paths do not resolve
in the Supabase SQL editor, so this file inlines the same modules in the same order.

Deterministic by design: masters in, verbatim module bodies out, so the only line that
changes between runs is the "Generated:" date. A regeneration that shows content diffs
always means the masters really changed - never the generator.

Usage (from repo root):
  powershell -ExecutionPolicy Bypass -File .\supabase\scripts\generate_online_sync.ps1

Output:
  supabase\online\000_CONSOLIDATED_ONLINE_SYNC.sql

The preamble carries the destructive-rebuild guard: section 1 drops every public-schema
object, and the DO block aborts unless sanibonani.allow_destructive_rebuild is set. No
SEED_*.sql fixture is ever inlined here - a cloud project must not be re-seeded.

This file intentionally contains no SQL semantics of its own beyond that guard. Edit the
masters.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$repoRoot  = (Get-Item $scriptDir).Parent.Parent.FullName
$sb        = Join-Path $repoRoot 'supabase'
$dst       = Join-Path $sb 'online\000_CONSOLIDATED_ONLINE_SYNC.sql'

$generated = (Get-Date -Format 'yyyy-MM-dd') + ' from the master scripts, version 2.2'

# Modules in exactly the order 00_REBUILD_ONLINE.sql \i's them. Each entry is
# @{ Path = <repo-relative>; Desc = <banner text> }.
$modules = @(
    @{ Path = '07_RESET_DATABASE.sql';                    Desc = 'RESET: wipes every public-schema object (guarded, see above)' },
    @{ Path = '01_DATABASE_SCHEMA.sql';                   Desc = 'SCHEMA: definitive table structures and extensions' },
    @{ Path = '02_FUNCTIONS_AND_TRIGGERS.sql';            Desc = 'FUNCTIONS: authoritative business logic and triggers' },
    @{ Path = '18_DEPLOY_PROCESS_COMPLEX_PAYMENT_V4.sql'; Desc = 'ADVANCED RPCs: complex payment processing v4' },
    @{ Path = '03_SECURITY_AND_RLS.sql';                  Desc = 'SECURITY: global RLS policies' },
    @{ Path = '04_PLATFORM_ADMIN_SETUP.sql';              Desc = 'ADMIN SETUP: platform administrator bootstrap' },
    @{ Path = '05_PERFORMANCE_VIEWS.sql';                 Desc = 'VIEWS: analytics and reporting snapshots' },
    @{ Path = 'online\01_ONLINE_SPECIFIC_SETUP.sql';      Desc = 'ONLINE SPECIFIC: production overrides' },
    @{ Path = '30_STORAGE_RLS.sql';                       Desc = 'STORAGE: bucket policies and provisioning' }
)

$utf8 = New-Object System.Text.UTF8Encoding($false)
# Build the non-ASCII characters at run time so this .ps1 stays pure ASCII and reads the
# same under Windows PowerShell 5.1 (ANSI) and pwsh 7 (UTF-8).
$rule = '-- ' + ([string][char]0x2500 * 77)
$nl   = "`n"

$preambleTemplate = @'
@RULE@
-- SanibonaniSave - ONLINE (Supabase Cloud) FULL REBUILD
-- Generated: @DATE@
@RULE@
-- HOW THIS FILE IS BUILT
--   It is the concatenation of the modules listed by supabase/online/00_REBUILD_ONLINE.sql,
--   in that order, so it can be pasted into the Supabase SQL editor where \i paths do not
--   resolve. Edit the masters (and GROUND_ZERO), then regenerate this file; never hand-edit
--   module bodies here - hand-pasted copies here are what caused the 2026-10-14 drift.
--
-- WARNING: DESTRUCTIVE
--   Section 1 drops every trigger, function, view, table and enum in public, and deletes the
--   master-seed auth users. It is wrapped in a guard that aborts unless you explicitly opt in
--   below. For an additive sync of an existing tier, use online/SYNC_DELTA_20261014.sql
--   instead - it never drops data.
--
-- NOT INCLUDED: any SEED_*.sql fixture. A cloud project must never be re-seeded from here.
@RULE@

-- UNCOMMENT THE NEXT LINE ONLY IF YOU INTEND TO WIPE AND REBUILD THIS TIER:
-- SET sanibonani.allow_destructive_rebuild = 'CONFIRM_DROP_ALL_DATA';

DO $$
BEGIN
    IF coalesce(current_setting('sanibonani.allow_destructive_rebuild', true), '')
       <> 'CONFIRM_DROP_ALL_DATA' THEN
        RAISE EXCEPTION
            'DESTRUCTIVE REBUILD BLOCKED: this script drops every public-schema object and '
            'deletes the master-seed auth users. Add  SET sanibonani.allow_destructive_rebuild = '
            '''CONFIRM_DROP_ALL_DATA'';  at the top of this script to proceed, or use '
            'online/SYNC_DELTA_20261014.sql for a non-destructive, additive sync.'
            USING ERRCODE = 'raise_exception';
    END IF;
END $$;
'@

$preamble = $preambleTemplate.Replace("`r`n", "`n").Replace('@RULE@', $rule).Replace('@DATE@', $generated)

$blocks = @()
foreach ($m in $modules) {
    $p = Join-Path $sb $m.Path
    if (-not (Test-Path $p)) { throw "Master script not found: $p" }
    $body = [System.IO.File]::ReadAllText($p, $utf8).Replace("`r`n", "`n").TrimEnd("`n")
    if ([string]::IsNullOrWhiteSpace($body)) { throw "Master script is empty: $p" }
    $blocks += (@($rule, "-- MODULE $($m.Path) - $($m.Desc)", $rule, $body) -join $nl)
    Write-Output ("inlined {0,-46} {1,5} lines" -f $m.Path, ($body -split "`n").Count)
}

$text = (@($preamble) + $blocks) -join ($nl + $nl)
$text = ($text -replace "`r`n", "`n").TrimEnd("`n") + "`n"
$text = $text.Replace("`n", "`r`n")
[System.IO.File]::WriteAllText($dst, $text, $utf8)

Write-Output "Online sync script written to: $dst"
Write-Output "Review every line before pasting into the Supabase SQL editor."
