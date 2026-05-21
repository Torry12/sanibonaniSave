-- CONSOLIDATED_SCHEMA_ONLY.sql
-- Apply base schema and migration functions/triggers only (no seeds).
\echo '--- Start consolidated schema-only apply ---'

-- 1) Base consolidated schema
\echo '\n-- Applying: supabase/01_DATABASE_SCHEMA.sql'
\i 'supabase/01_DATABASE_SCHEMA.sql'

-- 2) Migrations (supabase/migrations) - ordered by filename
\echo '\n-- Applying migrations...'
\i 'supabase/migrations/20260514000100_initial_schema.sql'
\i 'supabase/migrations/20260514000200_security_and_rls.sql'
\i 'supabase/migrations/20260514000300_platform_admin_setup.sql'
\i 'supabase/migrations/20260514000400_migrations_and_updates.sql'
\i 'supabase/migrations/20260514000500_platform_admin_auth_alignment.sql'
\i 'supabase/migrations/20260514000600_platform_admin_rls_hotfix.sql'
\i 'supabase/migrations/20260514000700_add_rosca_rotation_method.sql'
\i 'supabase/migrations/20260514000800_architecture_model_schema_templates.sql'
\i 'supabase/migrations/20260514000900_align_validation_constraints_with_app.sql'
\i 'supabase/migrations/20260515000000_consolidated_rls_alignment.sql'
\i 'supabase/migrations/20260515000100_atomic_balance_updates.sql'
\i 'supabase/migrations/20260515000200_disbursement_rpcs.sql'
\i 'supabase/migrations/20260515000200_group_voting_and_multi_admin.sql'
\i 'supabase/migrations/20260518000100_backfill_group_ledger_from_payments.sql'
\i 'supabase/migrations/20260518000200_recompute_group_balances_from_ledger.sql'

-- 3) Engineering performance optimizations (atomic ops, analytics, error logging)
\echo '\n-- Applying: supabase/39_ENGINEERING_PERFORMANCE_OPTIMIZATIONS.sql'
\i 'supabase/39_ENGINEERING_PERFORMANCE_OPTIMIZATIONS.sql'

-- 4) Architecture model schema templates (event-driven, risk, social credit, etc.)
\echo '\n-- Applying: supabase/28_ARCHITECTURE_MODEL_SCHEMA_TEMPLATES.sql'
\i 'supabase/28_ARCHITECTURE_MODEL_SCHEMA_TEMPLATES.sql'

\echo '\n--- Consolidated schema-only apply complete ---'

