-- Migration: 20260518000100_backfill_group_ledger_from_payments.sql
-- Purpose: one-off backfill to create missing group_ledger rows derived from existing payments/contributions/disbursements/transactions.
-- Run only once (review first), ensure you have a DB backup before running.
-- Strategy:
--  1) For each known source table (contributions, payments, disbursements, transactions) -- if the table exists -- we attempt to insert rows into public.group_ledger for source rows that do not yet have a matching transaction_id in group_ledger.
--  2) The insertion tries to preserve the original transaction_id where available; otherwise uses the source id as a synthetic transaction id.
--  3) Disbursements are treated as negative amounts.
--  4) The script is defensive and will skip tables that don't match expected columns (it will print notices).

-- IMPORTANT: run in a maintenance window and verify results. You may want to run in a read-only test environment first.

DO $$
DECLARE
    tbl TEXT;
    v_sql TEXT;
    v_cnt INTEGER;
BEGIN
    -- Process known canonical source tables explicitly with proper sign handling.
    FOR tbl IN SELECT unnest(ARRAY[
        'contributions',          -- inflows (+)
        'payments',               -- inflows (+)
        'loan_repayments',        -- inflows (+)
        'payouts',                -- outflows (-)
        'beneficiary_payout_claims' -- outflows (-)
    ]) LOOP
        IF to_regclass('public.' || tbl) IS NULL THEN
            RAISE NOTICE 'Table % does not exist - skipping', tbl;
            CONTINUE;
        END IF;

        BEGIN
            -- Choose sign: payouts and beneficiary_payout_claims are treated as negative amounts
            v_sql := format($f$
                INSERT INTO public.group_ledger (id, group_id, transaction_id, amount, description, category, created_at)
                SELECT gen_random_uuid(), src.group_id,
                       COALESCE(src.transaction_id, src.id::text),
                       (CASE WHEN %L IN ('payouts','beneficiary_payout_claims') THEN -1 ELSE 1 END) * src.amount,
                       'backfill from ' || %L,
                       %L,
                       COALESCE(src.created_at, COALESCE(src.processed_at, NOW()))
                FROM public.%I src
                WHERE src.group_id IS NOT NULL
                  AND COALESCE(src.amount,0) <> 0
                  AND NOT EXISTS (
                        SELECT 1 FROM public.group_ledger gl
                        WHERE gl.transaction_id IS NOT NULL
                          AND gl.transaction_id = COALESCE(src.transaction_id, src.id::text)
                  );
            $f$, tbl, tbl, tbl, tbl);

            EXECUTE v_sql;
            GET DIAGNOSTICS v_cnt = ROW_COUNT;
            RAISE NOTICE 'Inserted % rows into group_ledger from %', v_cnt, tbl;
        EXCEPTION WHEN undefined_column OR undefined_table THEN
            RAISE NOTICE 'Skipping table % due to unexpected schema (missing expected columns).', tbl;
        WHEN OTHERS THEN
            RAISE NOTICE 'Error while processing %, skipping. Error: %', tbl, sqlerrm;
        END;
    END LOOP;

    RAISE NOTICE 'Backfill complete. Please validate group_ledger contents and consider running recompute_group_balances migration next.';
END$$;

-- After running this migration, validate by sampling:
--  SELECT group_id, count(*) FROM public.group_ledger GROUP BY group_id ORDER BY count DESC LIMIT 10;
--  SELECT id, balance FROM public.groups ORDER BY id LIMIT 10;
-- Then run the recompute migration to set groups.balance from group_ledger (if desired).

-- End of file

