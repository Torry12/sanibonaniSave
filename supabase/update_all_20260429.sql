-- update_all_20260429.sql
-- SanibonaniSave: Consolidated database update script (April 29, 2026)
-- Includes all recent migrations, fixes, and policy updates.
-- Safe to run multiple times (idempotent).

-- 1. Create loans and loan_repayments tables
\i migrations/20260427_create_loans_table.sql

-- 2. Add login audit and 2FA columns to auth.users
\i migrations/20260427_add_login_audit_2fa_to_auth_users.sql

-- 3. Add receipt_url to contributions table
\i migration_2026_04_19_add_receipt_url.sql

-- 4. Fix record_contribution_v1 function and grants
\i migration_2026_04_19_fix_record_contribution_v1.sql

-- 5. Fix typo in constitutions bucket policy
\i storage_policies_update_20260429.sql

-- 6. Restore table grants (permission denied fix)
\i fix_table_grants.sql

-- 7. Quick fix for permission denied and public discovery
\i quick_fix_permission_denied.sql

-- End of update script

``