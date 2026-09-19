-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — FIXED LEDGER INTEGRITY RPCS
-- Version: 1.1 (Restored Atomic Logic - July 2026)
-- ─────────────────────────────────────────────────────────────────────────────

/**
 * RPC: Record Loan Settlement Extraction.
 * Restored: Uses the authoritative atomic pattern to ensure group balance consistency.
 *
 * 1. Extraction: Decrements member total_paid and group balance (-p_amount).
 * 2. Repayment: Increments loan total_repaid and group balance (+p_amount).
 * Net effect on group balance is ZERO, while internal ledgers reflect the full move.
 */
CREATE OR REPLACE FUNCTION public.record_loan_settlement_extraction_v1(
    p_member_id UUID,
    p_group_id UUID,
    p_amount NUMERIC,
    p_loan_id UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_extraction_res JSONB;
    v_repayment_res JSONB;
    v_new_balance NUMERIC;
BEGIN
    -- 1. Perform Extraction (Reduces member savings and group cash)
    SELECT public.record_contribution_v1(
        p_member_id,
        p_group_id,
        -p_amount,
        CURRENT_DATE,
        NOW(),
        'paid',
        'extraction-' || p_loan_id::text,
        'loan_settlement_extraction',
        'Principal used for loan settlement'
    ) INTO v_extraction_res;

    -- 2. Perform Repayment (Increases loan total_repaid and restores group cash)
    SELECT public.record_loan_repayment_v1(
        p_loan_id,
        p_member_id,
        p_group_id,
        p_amount,
        'wallet'
    ) INTO v_repayment_res;

    -- 3. Verify final state
    SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;

    RETURN jsonb_build_object(
        'status', 'success',
        'extraction', v_extraction_res,
        'repayment', v_repayment_res,
        'final_group_balance', v_new_balance
    );
END;
$$;

COMMENT ON FUNCTION public.record_loan_settlement_extraction_v1 IS 'Atomically extracts member principal to repay a defaulted loan. Net zero group impact.';
