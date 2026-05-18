-- Migration: 20260518000200_recompute_group_balances_from_ledger.sql
-- Purpose: recompute groups.balance from public.group_ledger in a safe, traceable way.
-- This script:
--  1) Aggregates ledger amounts per group into a staging temp table.
--  2) Iterates over each group and updates its balance to the aggregated value, recording old/new values into a persistent audit table `migration_balance_recompute_audit` for traceability.
-- Run only after you have validated the ledger contents (for example: after running the backfill migration).
-- IMPORTANT: BACKUP your DB first and run during maintenance window.

-- Create an audit table if it doesn't exist so we keep a record of the migration results.
CREATE TABLE IF NOT EXISTS public.migration_balance_recompute_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL,
    old_balance NUMERIC(20,2),
    new_balance NUMERIC(20,2) NOT NULL,
    diff NUMERIC(20,2) NOT NULL,
    migrated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

DO $$
DECLARE
    rec RECORD;
    v_old_balance NUMERIC(20,2);
    v_new_balance NUMERIC(20,2);
    v_updated_count INT := 0;
BEGIN
    -- Build a temp table of new balances computed from group_ledger.
    CREATE TEMP TABLE tmp_group_new_balances ON COMMIT DROP AS
    SELECT gl.group_id, SUM(gl.amount)::numeric(20,2) AS new_balance
    FROM public.group_ledger gl
    GROUP BY gl.group_id;

    RAISE NOTICE 'Computed % groups in tmp_group_new_balances', (SELECT count(*) FROM tmp_group_new_balances);

    -- Iterate and update per-group; this produces many small UPDATE statements rather than one huge statement.
    FOR rec IN SELECT group_id, new_balance FROM tmp_group_new_balances LOOP
        SELECT balance INTO v_old_balance FROM public.groups WHERE id = rec.group_id;
        v_new_balance := rec.new_balance;

        -- Update the group's balance to the recomputed value.
        UPDATE public.groups
        SET balance = v_new_balance,
            updated_at = NOW()
        WHERE id = rec.group_id;

        GET DIAGNOSTICS v_updated_count = ROW_COUNT;

        -- Record the change for auditing (regardless of whether balance changed) so migration is traceable.
        INSERT INTO public.migration_balance_recompute_audit (group_id, old_balance, new_balance, diff, migrated_at)
        VALUES (rec.group_id, v_old_balance, v_new_balance, COALESCE(v_new_balance,0) - COALESCE(v_old_balance,0), NOW());

        RAISE NOTICE 'Group % updated: old_balance=%, new_balance=%', rec.group_id, v_old_balance, v_new_balance;
    END LOOP;

    RAISE NOTICE 'Recompute complete. % audit rows inserted.', (SELECT count(*) FROM public.migration_balance_recompute_audit WHERE migrated_at > now() - interval '1 day');
END$$;

-- After running:
--  - Inspect public.migration_balance_recompute_audit for the changes.
--  - Sample groups where diff != 0 to validate correctness:
--      SELECT * FROM public.migration_balance_recompute_audit WHERE diff <> 0 ORDER BY abs(diff) DESC LIMIT 50;
--  - If you need to revert to old balances the audit table has previous values and you can re-apply them.

-- End of file

