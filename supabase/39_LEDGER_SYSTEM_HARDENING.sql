-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — LEDGER SYSTEM HARDENING (V2 RPCS)
-- Version: 2.1 (Hardened Disbursements - July 2026)
-- ─────────────────────────────────────────────────────────────────────────────

/**
 * Standardized complete_payout_v2.
 * Hardened with liquidity checks, MFA enforcement, and recipient linkage.
 */
CREATE OR REPLACE FUNCTION public.complete_payout_v2(
    p_payout_id UUID,
    p_admin_id UUID,
    p_payout_reference TEXT DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_payout RECORD;
    v_group RECORD;
    v_new_balance NUMERIC;
BEGIN
    -- 1. Fetch and validate payout
    SELECT * INTO v_payout FROM public.payouts WHERE id = p_payout_id;
    IF v_payout.id IS NULL THEN RAISE EXCEPTION 'Payout record % not found', p_payout_id; END IF;

    IF v_payout.status NOT IN ('pending', 'group_approved', 'processing') THEN
        RAISE EXCEPTION 'Payout is in % status and cannot be completed', v_payout.status;
    END IF;

    -- 🛡️ Security: Enforce MFA for large disbursements (>= R10,000)
    PERFORM public.assert_mfa_for_disbursement_v1(v_payout.amount);

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

    -- 4. Atomic Balance Update & Ledger Entry with Recipient Linkage
    SELECT public.increment_group_balance(
        v_payout.group_id,
        -v_payout.amount,
        'Group disbursement (Ref: ' || COALESCE(p_payout_reference, p_payout_id::text) || ')',
        'payout',
        p_payout_id::text,
        v_payout.member_id -- Recipient member_id linked directly
    ) INTO v_new_balance;

    RETURN jsonb_build_object(
        'status', 'success',
        'new_balance', v_new_balance,
        'payout_id', p_payout_id
    );
END;
$$;

/**
 * Standardized pay_burial_claim_v2.
 */
CREATE OR REPLACE FUNCTION public.pay_burial_claim_v2(
    p_claim_id UUID,
    p_admin_id UUID,
    p_notes TEXT DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_claim RECORD;
    v_group RECORD;
    v_new_balance NUMERIC;
BEGIN
    SELECT * INTO v_claim FROM public.beneficiary_payout_claims WHERE id = p_claim_id;
    IF v_claim.id IS NULL THEN RAISE EXCEPTION 'Claim not found'; END IF;
    IF v_claim.status NOT IN ('approved', 'under_review', 'escalated') THEN RAISE EXCEPTION 'Invalid claim status: %', v_claim.status; END IF;

    PERFORM public.assert_mfa_for_disbursement_v1(v_claim.claim_amount);

    SELECT * INTO v_group FROM public.groups WHERE id = v_claim.group_id;
    IF v_group.balance < v_claim.claim_amount THEN RAISE EXCEPTION 'Insufficient group balance'; END IF;

    UPDATE public.beneficiary_payout_claims
    SET status = 'paid',
        reviewed_by = p_admin_id,
        reviewed_at = NOW(),
        admin_notes = COALESCE(p_notes, admin_notes),
        updated_at = NOW()
    WHERE id = p_claim_id;

    SELECT public.increment_group_balance(
        v_claim.group_id,
        -v_claim.claim_amount,
        'Burial claim payout: ' || v_claim.beneficiary_name,
        'claim',
        p_claim_id::text,
        v_claim.member_id -- Link to the member whose beneficiary received funds
    ) INTO v_new_balance;

    RETURN jsonb_build_object(
        'status', 'success',
        'new_balance', v_new_balance,
        'claim_id', p_claim_id
    );
END;
$$;

/**
 * Standardized approve_and_disburse_loan_v2.
 */
CREATE OR REPLACE FUNCTION public.approve_and_disburse_loan_v2(
    p_loan_id UUID,
    p_admin_id UUID,
    p_payment_method TEXT DEFAULT 'bank'
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_loan RECORD;
    v_member_name TEXT;
    v_group RECORD;
    v_new_balance NUMERIC;
BEGIN
    SELECT * INTO v_loan FROM public.loans WHERE id = p_loan_id;
    IF v_loan.id IS NULL THEN RAISE EXCEPTION 'Loan not found'; END IF;
    IF v_loan.status != 'pending' THEN RAISE EXCEPTION 'Loan is not in pending status'; END IF;

    PERFORM public.assert_mfa_for_disbursement_v1(v_loan.amount);

    SELECT * INTO v_group FROM public.groups WHERE id = v_loan.group_id;
    IF v_group.balance < v_loan.amount THEN RAISE EXCEPTION 'Insufficient group balance for disbursement'; END IF;

    SELECT full_name INTO v_member_name FROM public.members WHERE id = v_loan.member_id;

    UPDATE public.loans
    SET status = 'active',
        reviewed_by = p_admin_id,
        reviewed_at = NOW(),
        start_date = NOW(),
        updated_at = NOW()
    WHERE id = p_loan_id;

    SELECT public.increment_group_balance(
        v_loan.group_id,
        -v_loan.amount,
        'Loan disbursement to ' || v_member_name,
        'loan_disbursement',
        p_loan_id::text,
        v_loan.member_id
    ) INTO v_new_balance;

    RETURN jsonb_build_object(
        'status', 'success',
        'new_balance', v_new_balance,
        'loan_id', p_loan_id
    );
END;
$$;

/**
 * Global repair function (V2).
 * Uses checkpoint-aware recompute for performance.
 */
CREATE OR REPLACE FUNCTION public.recalculate_all_group_financials_v2(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_member RECORD;
    v_loan RECORD;
    v_report JSONB;
    v_group_members_fixed INT := 0;
    v_group_loans_fixed INT := 0;
    v_balance_report JSONB;
BEGIN
    -- 1. Sync all members
    FOR v_member IN SELECT id FROM public.members WHERE group_id = p_group_id
    LOOP
        PERFORM public.fn_sync_single_member_financials(v_member.id);
        v_group_members_fixed := v_group_members_fixed + 1;
    END LOOP;

    -- 2. Sync all loans
    FOR v_loan IN SELECT id FROM public.loans WHERE group_id = p_group_id
    LOOP
        PERFORM public.fn_sync_single_loan_totals(v_loan.id);
        v_group_loans_fixed := v_group_loans_fixed + 1;
    END LOOP;

    -- 3. Audit trail repair (Deep Recompute with Checkpoints)
    -- We first trigger a V1 consolidation to ensure group_ledger is populated from sub-ledgers,
    -- then use the V2 checkpoint recompute for running balances.
    SELECT public.consolidate_group_ledger_v1(p_group_id) INTO v_balance_report;
    PERFORM public.recompute_ledger_running_balances_v2(p_group_id);

    v_report := jsonb_build_object(
        'group_id', p_group_id,
        'members_processed', v_group_members_fixed,
        'loans_processed', v_group_loans_fixed,
        'balance_audit', v_balance_report
    );

    INSERT INTO public.audit_logs (actor_id, target_group_id, action, details)
    VALUES (p_group_id, p_group_id, 'GLOBAL_FINANCIAL_RECALCULATION_V2', v_report);

    RETURN v_report;
END;
$$;

-- Grant permissions
GRANT EXECUTE ON FUNCTION public.complete_payout_v2(UUID, UUID, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.pay_burial_claim_v2(UUID, UUID, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.approve_and_disburse_loan_v2(UUID, UUID, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.recalculate_all_group_financials_v2(UUID) TO authenticated;
