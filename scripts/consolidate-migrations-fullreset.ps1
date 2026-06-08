# PowerShell script to automate destructive full-reset SQL consolidation
# Usage: ./scripts/consolidate-migrations-fullreset.ps1

param(
    [string]$MigrationsDir = "C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/supabase/migrations",
    [string]$ConsolidatedTemplate = "C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/consolidated/CONSOLIDATED_FULL_RESET.sql",
    [string]$ConsolidatedOut = "C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/consolidated/CONSOLIDATED_FULL_RESET.inlined.sql"
)

function Inline-Migrations {
    param(
        [string]$TemplatePath,
        [string]$OutputPath,
        [string]$MigrationsDir
    )
    $template = Get-Content $TemplatePath -Raw

    $migrationFiles = @(
        "20260514000100_initial_schema.sql",
        "20260514000200_security_and_rls.sql",
        "20260514000300_platform_admin_setup.sql",
        "20260514000500_platform_admin_auth_alignment.sql",
        "20260514000400_migrations_and_updates.sql",
        "20260515000000_consolidated_rls_alignment.sql",
        "20260514000600_platform_admin_rls_hotfix.sql",
        "20260515000200_group_voting_and_multi_admin.sql",
        "20260515000100_atomic_balance_updates.sql",
        "20260515000200_disbursement_rpcs.sql",
        "20260518000100_backfill_group_ledger_from_payments.sql",
        "20260518000200_recompute_group_balances_from_ledger.sql",
        "20260519000100_extended_voting_logic.sql",
        "20260514000900_align_validation_constraints_with_app.sql",
        "20260514000800_architecture_model_schema_templates.sql",
        "20260514000700_add_rosca_rotation_method.sql",
        "2026-05-19T-ensure-unique-group-name.sql",
        "2026-05-19T-ensure-extensions-and-constraints.sql"
    )

    foreach ($file in $migrationFiles) {
        $marker = "...full content of $file..."
        $sql = Get-Content (Join-Path $MigrationsDir $file) -Raw
        $template = $template -replace [regex]::Escape($marker), $sql
    }
    Set-Content -Path $OutputPath -Value $template
    Write-Host "Inlined migrations into $OutputPath"
}

Inline-Migrations -TemplatePath $ConsolidatedTemplate -OutputPath $ConsolidatedOut -MigrationsDir $MigrationsDir
