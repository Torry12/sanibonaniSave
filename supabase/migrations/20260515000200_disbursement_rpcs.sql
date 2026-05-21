-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — DISBURSEMENT ATOMIC RPCS
-- Date: 2026-05-15
-- Purpose:
--   Provide atomic functions for processing payouts, burial claims, and
--   general disbursements with automatic ledger logging and balance updates.
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

/**
 * Core function for recording any disbursement (outflow).
 * Decrements group balance and records in group_ledger via atomic helper.
 */
CREATE OR REPLACE FUNCTION public.record_disbursement_v1(
    p_group_id UUID,
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id UUID DEFAULT NULL
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    IF p_amount <= 0 THEN
        RAISE EXCEPTION 'Disbursement amount must be positive';
    END IF;

    RETURN public.increment_group_balance(
        p_group_id,
        -p_amount,
        p_description,
        p_category,
        p_transaction_id
    );
END;
$$;

/**
 * specialized RPC to complete a Payout request.
 * Handles status update, processed info, and ledger logging in one transaction.
 */
CREATE OR REPLACE FUNCTION public.complete_payout_v1(
    p_payout_id UUID,
    p_admin_id UUID,
    p_payout_reference TEXT DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_payout public.payouts;
BEGIN
    SELECT * INTO v_payout FROM public.payouts WHERE id = p_payout_id;

    IF v_payout.id IS NULL THEN
        RAISE EXCEPTION 'Payout not found';
    END IF;

    IF v_payout.status = 'completed' THEN
        RETURN; -- Idempotency
    END IF;

    -- 1. Update Payout record
    UPDATE public.payouts
    SET status = 'completed',
        processed_at = NOW(),
        processed_by = p_admin_id,
        payout_reference = COALESCE(p_payout_reference, payout_reference),
        updated_at = NOW()
    WHERE id = p_payout_id;

    -- 2. Record disbursement and update group balance
    PERFORM public.record_disbursement_v1(
        v_payout.group_id,
        v_payout.amount,
        'Disbursement: ' || v_payout.bank_name || ' (' || v_payout.account_no || ')',
        'payout',
        p_payout_id
    );
END;
$$;

/**
 * specialized RPC to pay out a Burial Claim.
 */
CREATE OR REPLACE FUNCTION public.pay_burial_claim_v1(
    p_claim_id UUID,
    p_admin_id UUID,
    p_notes TEXT DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_claim public.beneficiary_payout_claims;
BEGIN
    SELECT * INTO v_claim FROM public.beneficiary_payout_claims WHERE id = p_claim_id;

    IF v_claim.id IS NULL THEN
        RAISE EXCEPTION 'Claim not found';
    END IF;

    IF v_claim.status = 'paid' THEN
        RETURN; -- Idempotency
    END IF;

    -- 1. Update Claim record
    UPDATE public.beneficiary_payout_claims
    SET status = 'paid',
        reviewed_at = NOW(),
        reviewed_by = p_admin_id,
        admin_notes = COALESCE(p_notes, admin_notes),
        updated_at = NOW()
    WHERE id = p_claim_id;

    -- 2. Record disbursement
    PERFORM public.record_disbursement_v1(
        v_claim.group_id,
        v_claim.claim_amount,
        'Burial Payout: ' || v_claim.beneficiary_name || ' (Claim #' || SUBSTRING(v_claim.id::text, 1, 8) || ')',
        'burial_claim',
        p_claim_id
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.record_disbursement_v1(UUID, NUMERIC, TEXT, TEXT, UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.complete_payout_v1(UUID, UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.pay_burial_claim_v1(UUID, UUID, TEXT) TO authenticated, service_role;

COMMIT;
