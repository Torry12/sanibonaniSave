-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — REALTIME PUBLICATION SETUP
-- Version: 1.0 (Mobile Sync Activation)
-- ─────────────────────────────────────────────────────────────────────────────

/**
 * Ensures that the supabase_realtime publication exists and includes all
 * core tables required for mobile reactive updates.
 */

-- 1. Ensure publication exists
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        CREATE PUBLICATION supabase_realtime;
    END IF;
END $$;

-- 2. Add all core tables to the publication
-- We use a loop to handle tables that might not exist yet during incremental development
DO $$
DECLARE
    v_table text;
    v_tables text[] := ARRAY[
        'groups',
        'members',
        'contributions',
        'loans',
        'loan_repayments',
        'payout_requests',
        'beneficiary_payout_claims',
        'app_notifications',
        'group_ledger',
        'bank_transactions',
        'platform_fee_config'
    ];
BEGIN
    FOREACH v_table IN ARRAY v_tables LOOP
        -- Check if table exists before trying to add it
        IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = v_table) THEN
            EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', v_table);
        END IF;
    EXCEPTION WHEN others THEN
        -- Ignore errors (e.g. if table is already in publication)
        RAISE NOTICE 'Skipping %: %', v_table, SQLERRM;
    END LOOP;
END $$;
