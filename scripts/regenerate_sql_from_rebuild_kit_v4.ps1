# Regenerate SQL Proxy Files from supabase/rebuild_kit_v4
# Usage: Run this script from the project root to update all proxy/aggregate SQL files.

$ErrorActionPreference = 'Stop'

# Paths
$root = Split-Path -Parent $MyInvocation.MyCommand.Definition
$kit = Join-Path $root 'supabase\rebuild_kit_v4'

# Helper: Write header and concatenate files
function Write-ProxyFile {
    param(
        [string]$OutFile,
        [string[]]$SourceFiles,
        [string]$Header
    )
    Write-Host "Generating $OutFile ..."
    Set-Content -Path $OutFile -Value $Header
    foreach ($src in $SourceFiles) {
        Add-Content -Path $OutFile -Value "`n-- [INCLUDED FROM] $src`n"
        Get-Content $src | Add-Content -Path $OutFile
    }
}

# 1. schema.sql
$schemaHeader = @'
-- ─────────────────────────────────────────────────────────────
-- SanibonaniSave — Canonical Schema (tables, indexes, functions, triggers)
-- Source of truth: supabase/rebuild_kit_v4/01_TABLES_AND_INDEXES.sql + 02_FUNCTIONS_AND_TRIGGERS.sql
-- DO NOT EDIT DIRECTLY. Regenerate from rebuild_kit_v4.
-- ─────────────────────────────────────────────────────────────
'@
Write-ProxyFile -OutFile (Join-Path $root 'schema.sql') -SourceFiles @(
    (Join-Path $kit '01_TABLES_AND_INDEXES.sql'),
    (Join-Path $kit '02_FUNCTIONS_AND_TRIGGERS.sql')
) -Header $schemaHeader

# 2. rls.sql
$rlsHeader = @'
-- ─────────────────────────────────────────────────────────────
-- SanibonaniSave — Security, Grants, RLS Policies
-- Source of truth: supabase/rebuild_kit_v4/04_SECURITY_AND_RLS.sql
-- DO NOT EDIT DIRECTLY. Regenerate from rebuild_kit_v4.
-- ─────────────────────────────────────────────────────────────
'@
Write-ProxyFile -OutFile (Join-Path $root 'rls.sql') -SourceFiles @(
    (Join-Path $kit '04_SECURITY_AND_RLS.sql')
) -Header $rlsHeader

# 3. engineering.sql
$engHeader = @'
-- ─────────────────────────────────────────────────────────────
-- SanibonaniSave — Engineering/Performance Views
-- Source of truth: supabase/rebuild_kit_v4/03_PERFORMANCE_VIEWS.sql
-- DO NOT EDIT DIRECTLY. Regenerate from rebuild_kit_v4.
-- ─────────────────────────────────────────────────────────────
'@
Write-ProxyFile -OutFile (Join-Path $root 'engineering.sql') -SourceFiles @(
    (Join-Path $kit '03_PERFORMANCE_VIEWS.sql')
) -Header $engHeader

# 4. consolidated_full.sql
$fullHeader = @'
-- ─────────────────────────────────────────────────────────────
-- SanibonaniSave — Full DB Reset (Destructive, for local/dev/CI)
-- Source of truth: supabase/rebuild_kit_v4/00_SCHEMA_RESET.sql + all canonical SQL files
-- DO NOT EDIT DIRECTLY. Regenerate from rebuild_kit_v4.
-- ─────────────────────────────────────────────────────────────
'@
Write-ProxyFile -OutFile (Join-Path $root 'consolidated_full.sql') -SourceFiles @(
    (Join-Path $kit '00_SCHEMA_RESET.sql'),
    (Join-Path $kit '01_TABLES_AND_INDEXES.sql'),
    (Join-Path $kit '02_FUNCTIONS_AND_TRIGGERS.sql'),
    (Join-Path $kit '03_PERFORMANCE_VIEWS.sql'),
    (Join-Path $kit '04_SECURITY_AND_RLS.sql'),
    (Join-Path $kit '05_SEED_DATA.sql')
) -Header $fullHeader

Write-Host "All proxy SQL files regenerated from supabase/rebuild_kit_v4."

