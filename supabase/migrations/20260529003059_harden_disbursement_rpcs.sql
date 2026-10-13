-- Harden disbursement RPCs against nullable or sentinel admin identifiers
-- sent by older app flows. The caller repositories now omit invalid UUID
-- admin values; these defaults keep the SQL side safe as well.

BEGIN;

CREATE OR REPLACE FUNCTION public.complete_payout_v1(
    p_payout_id UUID,
    p_admin_id UUID DEFAULT NULL,
    p_payout_reference TEXT DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_payout public.payouts;
    v_processed_by UUID := COALESCE(p_admin_id, auth.uid());
BEGIN
    SELECT * INTO v_payout FROM public.payouts WHERE id = p_payout_id;

    IF v_payout.id IS NULL THEN
        RAISE EXCEPTION 'Payout not found';
    END IF;

    IF v_payout.status = 'completed' THEN
        RETURN;
    END IF;

    UPDATE public.payouts
    SET status = 'completed',
        processed_at = NOW(),
        processed_by = COALESCE(v_processed_by, processed_by),
        payout_reference = COALESCE(p_payout_reference, payout_reference),
        updated_at = NOW()
    WHERE id = p_payout_id;

    PERFORM public.record_disbursement_v1(
        v_payout.group_id,
        v_payout.amount,
        'Disbursement: ' || v_payout.bank_name || ' (' || v_payout.account_no || ')',
        'payout',
        p_payout_id
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.pay_burial_claim_v1(
    p_claim_id UUID,
    p_admin_id UUID DEFAULT NULL,
    p_notes TEXT DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_claim public.beneficiary_payout_claims;
    v_reviewed_by UUID := COALESCE(p_admin_id, auth.uid());
BEGIN
    SELECT * INTO v_claim FROM public.beneficiary_payout_claims WHERE id = p_claim_id;

    IF v_claim.id IS NULL THEN
        RAISE EXCEPTION 'Claim not found';
    END IF;

    IF v_claim.status = 'paid' THEN
        RETURN;
    END IF;

    UPDATE public.beneficiary_payout_claims
    SET status = 'paid',
        reviewed_at = NOW(),
        reviewed_by = COALESCE(v_reviewed_by, reviewed_by),
        admin_notes = COALESCE(p_notes, admin_notes),
        updated_at = NOW()
    WHERE id = p_claim_id;

    PERFORM public.record_disbursement_v1(
        v_claim.group_id,
        v_claim.claim_amount,
        'Burial Payout: ' || v_claim.beneficiary_name || ' (Claim #' || SUBSTRING(v_claim.id::text, 1, 8) || ')',
        'burial_claim',
        p_claim_id
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.complete_payout_v1(UUID, UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.pay_burial_claim_v1(UUID, UUID, TEXT) TO authenticated, service_role;

COMMIT;
