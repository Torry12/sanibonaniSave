-- -----------------------------------------------------------------------------
-- Fix: pay_platform_fee_v2 is not idempotent — a retry double-charges the group.
--
-- Problem
-- -----
-- The Kotlin use case built its txId from System.currentTimeMillis(), so a retry
-- after a network timeout generated a *different* tx_id. Because this function had
-- no duplicate check, the retry ran the full body again:
--     1. increment_group_balance(..., -p_amount, ...)   -> balance deducted twice
--     2. record_platform_revenue_v2(...)                 -> revenue booked twice
--     3. platform_fees rows marked paid twice            -> ledger over-reports
--
-- This is real money loss for the group and phantom revenue for the platform.
--
-- Fix
-- ---
-- Two coordinated changes, both needed:
--   a) Kotlin: the txId is now derived deterministically from the fee set being
--      settled (see ProcessGroupPlatformFeePaymentUseCase). An identical retry
--      now presents the *same* p_tx_id.
--   b) This migration: guard the function so that a repeated p_tx_id is recognised
--      and returned as already applied, instead of charging again.
--
-- The guard mirrors the existing, proven pattern in
-- process_complex_payment_v4 (supabase/18_DEPLOY_PROCESS_COMPLEX_PAYMENT_V4.sql).
-- It is placed AFTER the `FOR UPDATE` row lock so two concurrent submissions of the
-- same txId serialise: the second waits, then sees the first's committed ledger row
-- and returns the idempotent result.
--
-- Verification tables
-- -------------------
--   * group_ledger.transaction_id      - written by increment_group_balance
--   * platform_ledger.transaction_id   - written by record_platform_revenue_v2
-- Both are checked so the guard holds regardless of p_from_group_fund.
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.pay_platform_fee_v2(
    p_group_id UUID,
    p_amount NUMERIC,
    p_fee_type TEXT, -- 'monthly', 'registration'
    p_tx_id TEXT DEFAULT NULL,
    p_from_group_fund BOOLEAN DEFAULT TRUE
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_current_bal NUMERIC;
    v_allocated NUMERIC := 0;
    v_fee RECORD;
    v_group_name TEXT;
BEGIN
    -- Row lock first: serialises concurrent submissions of the same tx_id.
    SELECT name, balance INTO v_group_name, v_current_bal FROM public.groups WHERE id = p_group_id FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND: %', p_group_id;
    END IF;

    -- ── Idempotency guard ────────────────────────────────────────────────
    -- A NULL tx_id cannot be deduplicated, so fall through to normal processing;
    -- callers are expected to always supply a deterministic reference.
    IF p_tx_id IS NOT NULL AND (
        EXISTS (SELECT 1 FROM public.group_ledger   WHERE group_id = p_group_id AND transaction_id = p_tx_id)
        OR EXISTS (SELECT 1 FROM public.platform_ledger WHERE transaction_id = p_tx_id)
        OR EXISTS (SELECT 1 FROM public.platform_fees  WHERE group_id = p_group_id AND transaction_id = p_tx_id)
    ) THEN
        RETURN jsonb_build_object(
            'paid', p_amount,
            'remaining_allocated', p_amount,
            'transaction_id', p_tx_id,
            'status', 'idempotent'
        );
    END IF;

    IF p_from_group_fund THEN
        IF v_current_bal < p_amount THEN
            RAISE EXCEPTION '[FIN_002] INSUFFICIENT_FUNDS: Group balance is %', v_current_bal;
        END IF;

        -- Deduct from group
        PERFORM public.increment_group_balance(
            p_group_id,
            -p_amount,
            'Platform ' || p_fee_type || ' fee payment',
            'platform_fee',
            p_tx_id
        );
    END IF;

    -- Record platform revenue
    PERFORM public.record_platform_revenue_v2(
        p_amount,
        'Fee payment from ' || v_group_name,
        p_fee_type || '_fee',
        p_tx_id,
        p_group_id
    );

    -- Waterfall allocation for 'monthly' fees
    IF p_fee_type = 'monthly' THEN
        FOR v_fee IN (
            SELECT id, amount_due, amount_paid
            FROM public.platform_fees
            WHERE group_id = p_group_id AND status != 'paid' AND fee_type = 'monthly'
            ORDER BY due_date ASC
        ) LOOP
            IF p_amount - v_allocated <= 0 THEN EXIT; END IF;

            DECLARE
                v_to_pay NUMERIC := LEAST(v_fee.amount_due - v_fee.amount_paid, p_amount - v_allocated);
            BEGIN
                UPDATE public.platform_fees
                SET amount_paid = amount_paid + v_to_pay,
                    status = CASE WHEN (amount_paid + v_to_pay) >= amount_due THEN 'paid' ELSE status END,
                    paid_at = NOW(),
                    transaction_id = COALESCE(transaction_id, p_tx_id)
                WHERE id = v_fee.id;

                v_allocated := v_allocated + v_to_pay;
            END;
        END LOOP;
    ELSIF p_fee_type = 'registration' THEN
        UPDATE public.groups SET registration_paid = TRUE WHERE id = p_group_id;
    END IF;

    -- Update group standing
    IF NOT EXISTS (SELECT 1 FROM public.platform_fees WHERE group_id = p_group_id AND status != 'paid') THEN
        UPDATE public.groups SET fee_status = 'paid', is_platform_suspended = FALSE WHERE id = p_group_id;
    END IF;

    RETURN jsonb_build_object(
        'paid', p_amount,
        'remaining_allocated', p_amount - v_allocated,
        'transaction_id', p_tx_id,
        'status', 'processed'
    );
END;
$$;
