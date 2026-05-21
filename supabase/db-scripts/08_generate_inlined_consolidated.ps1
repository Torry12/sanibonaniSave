# 08_generate_inlined_consolidated.ps1
# Generates a single inlined SQL file that concatenates the canonical
# consolidated schema, rls/security, performance helpers, and safe seeds.
# It also optionally archives the redundant files by moving them to supabase/archived_sql.
# Usage:
#   PowerShell -NoProfile -ExecutionPolicy Bypass -File .\08_generate_inlined_consolidated.ps1 -OutFile ../CONSOLIDATED_INLINE.sql -Archive true
param(
    [string]$OutFile = "../CONSOLIDATED_INLINE.sql",
    [bool]$Archive = $false
)

$RepoSupabase = Join-Path $PSScriptRoot ".."
$OutPath = Join-Path $RepoSupabase $OutFile
$Parts = @(
    "CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql",
    "02_SECURITY_AND_RLS.sql",
    "39_ENGINEERING_PERFORMANCE_OPTIMIZATIONS.sql",
    "CONSOLIDATED_FOR_DASHBOARD_SCHEMA_PLUS_SAFE_SEED.sql"
)

Write-Host "Generating inlined consolidated SQL: $OutPath"
if (Test-Path $OutPath) { Remove-Item $OutPath -Force }

Add-Content -Path $OutPath -Value "-- Inlined consolidated SQL generated on $(Get-Date -Format o)"
foreach ($p in $Parts) {
    $full = Join-Path $RepoSupabase $p
    if (Test-Path $full) {
        Add-Content -Path $OutPath -Value "\n-- ========== Begin: $p =========="
        Get-Content -Path $full | Add-Content -Path $OutPath
        Add-Content -Path $OutPath -Value "\n-- ========== End: $p =========="
    } else {
        Write-Warning "Missing expected file: $p"
    }
}

Write-Host "Inlined file written: $OutPath"

if ($Archive) {
    $ArchiveDir = Join-Path $RepoSupabase "archived_sql"
    New-Item -ItemType Directory -Path $ArchiveDir -Force | Out-Null
    $Redundant = @(
        '01_DATABASE_SCHEMA.sql','03_PLATFORM_ADMIN_SETUP.sql','04_MIGRATIONS_AND_UPDATES.sql','04_PLATFORM_ADMIN_AUTH_ALIGNMENT.sql',
        '07_MISSING_TABLES_AND_COLUMNS.sql','08_SMALL_STRESS_TEST_SEED.sql','09_COMPREHENSIVE_TEST_SEED.sql','10_SCALED_TEST_SEED_300.sql',
        '11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql','12_VERIFY_TEST_DATA_10_GROUPS_100_MEMBERS.sql','13_CREATE_TEST_LOGIN_PROFILES.sql',
        '14_VERIFY_TEST_LOGIN_PROFILES.sql','15_CLEANUP_TEST_LOGIN_PROFILES.sql','16_PLATFORM_MEMBER_BEHAVIOR_INSIGHTS.sql',
        '17_SEED_DEBUG_LOGIC_SCENARIOS.sql','18_VERIFY_DEBUG_LOGIC_SCENARIOS.sql','19_CLEANUP_DEBUG_LOGIC_SCENARIOS.sql',
        '20_RUN_DEBUG_LOGIC_SEED_AND_VERIFY.sql','21_RUN_DEBUG_LOGIC_CLEANUP.sql','22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql',
        '23_VERIFY_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql','24_PLATFORM_ADMIN_RLS_HOTFIX.sql','25_SEED_FULL_APP_E2E.sql',
        '26_VERIFY_FULL_APP_E2E.sql','27_ADD_ROSCA_ROTATION_METHOD.sql','28_ARCHITECTURE_MODEL_SCHEMA_TEMPLATES.sql',
        '29_ALIGN_VALIDATION_CONSTRAINTS_WITH_APP.sql','30_CONSOLIDATED_RLS_ALIGNMENT.sql','31_ATOMIC_BALANCE_UPDATES.sql',
        '32_GROUP_VOTING_AND_MULTI_ADMIN.sql','33_REBUILD_DATABASE_WITH_TEST_SEED.sql','33_REMOVE_YOCO_FROM_DISBURSEMENTS.sql',
        '35_FULL_SYSTEM_REBUILD.sql','36_DASHBOARD_FULL_REBUILD.sql','37_DASHBOARD_SEED_DATA.sql','38_DASHBOARD_COMPREHENSIVE_SEED.sql',
        '40_ADD_BENEFICIARY_FACE_PHOTO.sql'
    )
    foreach ($r in $Redundant) {
        $src = Join-Path $RepoSupabase $r
        if (Test-Path $src) {
            $dst = Join-Path $ArchiveDir $r
            Write-Host "Archiving $r -> archived_sql/$r"
            Move-Item -Path $src -Destination $dst -Force
        }
    }
    Write-Host "Archive complete. Archived files moved to: $ArchiveDir"
}

