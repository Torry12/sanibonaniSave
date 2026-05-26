-- SEED_99_CLEANUP_TEST_DATA.sql
-- Systematic cleanup of test data to allow fresh seed runs.
-- Uses CASCADE where possible, but handles auth.users carefully.

DO $$
BEGIN
    -- 1. Remove test auth users (excluding platform admin if needed, or wipe all @test.com/@example.com)
    -- Warning: auth.users deletion triggers profile deletion via CASCADE in many setups
    DELETE FROM auth.users
    WHERE email LIKE '%@test.com'
       OR email LIKE '%@example.com'
       OR email = 'platform.admin@sanibonani.com';

    -- 2. Truncate public tables to ensure clean slate
    -- (auth.users deletion might have missed some orphaned public data if FKs were SET NULL)
    TRUNCATE TABLE public.audit_logs CASCADE;
    TRUNCATE TABLE public.group_ledger CASCADE;
    TRUNCATE TABLE public.platform_ledger CASCADE;
    TRUNCATE TABLE public.payouts CASCADE;
    TRUNCATE TABLE public.payments CASCADE;
    TRUNCATE TABLE public.contributions CASCADE;
    TRUNCATE TABLE public.loans CASCADE;
    TRUNCATE TABLE public.beneficiary_payout_claims CASCADE;
    TRUNCATE TABLE public.beneficiaries CASCADE;
    TRUNCATE TABLE public.members CASCADE;
    TRUNCATE TABLE public.groups CASCADE;
    TRUNCATE TABLE public.profiles CASCADE;

    RAISE NOTICE 'Test data cleanup completed.';
END $$;
