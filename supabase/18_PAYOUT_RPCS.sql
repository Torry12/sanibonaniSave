-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — PAYOUT RPCS
-- Version: 1.0 (July 2026)
-- ─────────────────────────────────────────────────────────────────────────────

/**
 * Atomically completes a payout request.
 * Updates the payout record, deducts the amount from the group balance,
 * and creates a corresponding ledger entry.
 */
CREATE OR REPLACE FUNCTION public.complete_payout_v1(
    p_payout_id UUID,
    p_admin_id UUID,
    p_payout_reference TEXT DEFAULT NULL
) RETURNS VOID AS $$
DECLARE
    v_payout RECORD;
    v_group RECORD;
BEGIN
    -- 1. Fetch and validate payout
    SELECT * INTO v_payout FROM public.payouts WHERE id = p_payout_id;
    IF v_payout.id IS NULL THEN RAISE EXCEPTION 'Payout record % not found', p_payout_id; END IF;

    -- Allowed transition states
    IF v_payout.status NOT IN ('group_approved', 'processing') THEN
        RAISE EXCEPTION 'Payout is in % status and cannot be completed', v_payout.status;
    END IF;

    -- 2. Validate Liquidity
    SELECT * INTO v_group FROM public.groups WHERE id = v_payout.group_id;
    IF v_group.balance < v_payout.amount THEN
        RAISE EXCEPTION 'Insufficient group balance (Available: %, Requested: %)', v_group.balance, v_payout.amount;
    END IF;

    -- 3. Update Payout record
    UPDATE public.payouts
    SET status = 'completed',
        processed_by = p_admin_id,
        processed_at = NOW(),
        payout_reference = COALESCE(p_payout_reference, payout_reference),
        updated_at = NOW()
    WHERE id = p_payout_id;

    -- 4. Atomic Balance Update & Ledger Entry
    PERFORM public.increment_group_balance(
        v_payout.group_id,
        -v_payout.amount,
        'Group disbursement (Ref: ' || COALESCE(p_payout_reference, p_payout_id::text) || ')',
        'payout',
        p_payout_id::text
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
