-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — ATOMIC BALANCE UPDATES
-- Date: 2026-05-15
-- Purpose:
--   Provide atomic functions for incrementing/decrementing group balances
--   to prevent race conditions in financial operations.
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

/**
 * Atomically increments or decrements a group's balance.
 * Returns the new balance.
 */
CREATE OR REPLACE FUNCTION public.increment_group_balance(
    p_group_id UUID,
    p_amount NUMERIC
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_new_balance NUMERIC;
BEGIN
    UPDATE public.groups
    SET balance = balance + p_amount,
        updated_at = NOW()
    WHERE id = p_group_id
    RETURNING balance INTO v_new_balance;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Group not found';
    END IF;

    -- Persist ledger entry for auditability
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category)
    VALUES (p_group_id, NULL, p_amount, v_new_balance, 'Atomic balance update', 'adjustment');

    RETURN v_new_balance;
END;
$$;

GRANT EXECUTE ON FUNCTION public.increment_group_balance(UUID, NUMERIC) TO authenticated, service_role;

COMMIT;
