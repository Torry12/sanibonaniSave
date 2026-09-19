-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — CLAIM RPCS
-- Version: 1.0 (July 2026)
-- ─────────────────────────────────────────────────────────────────────────────

/**
 * Atomically completes a burial claim payment.
 * Deducts the claim amount from the group balance and creates a ledger entry.
 */
CREATE OR REPLACE FUNCTION public.pay_burial_claim_v1(
    p_claim_id UUID,
    p_admin_id UUID,
    p_notes TEXT DEFAULT NULL
) RETURNS VOID AS $$
DECLARE
    v_claim RECORD;
    v_group RECORD;
BEGIN
    -- 1. Fetch and validate claim
    SELECT * INTO v_claim FROM public.beneficiary_payout_claims WHERE id = p_claim_id;
    IF v_claim.id IS NULL THEN RAISE EXCEPTION 'Claim record % not found', p_claim_id; END IF;

    -- Allowed transition states
    IF v_claim.status NOT IN ('approved', 'under_review', 'escalated') THEN
        RAISE EXCEPTION 'Claim is in % status and cannot be paid', v_claim.status;
    END IF;

    -- 2. Validate Liquidity
    SELECT * INTO v_group FROM public.groups WHERE id = v_claim.group_id;
    IF v_group.balance < v_claim.claim_amount THEN
        RAISE EXCEPTION 'Insufficient group balance (Available: %, Requested: %)', v_group.balance, v_claim.claim_amount;
    END IF;

    -- 3. Update Claim record
    UPDATE public.beneficiary_payout_claims
    SET status = 'paid',
        reviewed_by = p_admin_id,
        reviewed_at = NOW(),
        admin_notes = COALESCE(p_notes, admin_notes),
        updated_at = NOW()
    WHERE id = p_claim_id;

    -- 4. Atomic Balance Update & Ledger Entry
    PERFORM public.increment_group_balance(
        v_claim.group_id,
        -v_claim.claim_amount,
        'Burial claim payout: ' || v_claim.beneficiary_name,
        'claim',
        p_claim_id::text
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
