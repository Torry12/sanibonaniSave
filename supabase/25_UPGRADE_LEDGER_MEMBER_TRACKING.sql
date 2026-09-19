-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — UPGRADE LEDGER MEMBER TRACKING
-- Version: 1.0 (High-Resolution Member Statements)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Add member_id to group_ledger
ALTER TABLE public.group_ledger ADD COLUMN member_id UUID REFERENCES public.members(id) ON DELETE SET NULL;

-- 2. Update increment_group_balance to accept member_id
CREATE OR REPLACE FUNCTION public.increment_group_balance(
    p_group_id UUID,
    p_amount NUMERIC,
    p_description TEXT DEFAULT 'Atomic balance update',
    p_category TEXT DEFAULT 'adjustment',
    p_transaction_id TEXT DEFAULT NULL,
    p_member_id UUID DEFAULT NULL
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

    IF NOT FOUND THEN RAISE EXCEPTION 'Group not found'; END IF;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id)
    VALUES (p_group_id, p_transaction_id, p_amount, v_new_balance, p_description, p_category, p_member_id);

    RETURN v_new_balance;
END;
$$;

-- 3. Update record_contribution_v1 to pass member_id
CREATE OR REPLACE FUNCTION public.record_contribution_v1(
    p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_due_date DATE,
    p_paid_at TIMESTAMPTZ, p_status TEXT, p_tx_id TEXT DEFAULT NULL, p_type TEXT DEFAULT 'contribution'
 ) RETURNS JSONB AS $$
DECLARE
    v_contribution RECORD;
    v_new_balance NUMERIC;
    v_member_name TEXT;
    v_ledger_desc TEXT;
    v_ledger_cat TEXT;
BEGIN
    SELECT full_name INTO v_member_name FROM public.members WHERE id = p_member_id;

    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, transaction_id, type)
    VALUES (p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_tx_id, p_type)
    RETURNING * INTO v_contribution;

    IF p_type = 'member_fee' THEN
        v_ledger_desc := 'Platform Member Fee (' || v_member_name || ')';
        v_ledger_cat := 'platform_fee';
    ELSE
        v_ledger_desc := initcap(replace(p_type, '_', ' ')) || ' from ' || v_member_name;
        v_ledger_cat := p_type;
    END IF;

    IF p_type != 'member_fee' THEN
        SELECT public.increment_group_balance(
            p_group_id,
            p_amount,
            v_ledger_desc,
            v_ledger_cat,
            v_contribution.id::text,
            p_member_id
        ) INTO v_new_balance;
    ELSE
        SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;
        PERFORM public.record_platform_revenue_v1(p_amount, v_ledger_desc, v_ledger_cat, v_contribution.id::text);
    END IF;

    RETURN jsonb_build_object(
        'contribution', to_jsonb(v_contribution),
        'new_balance', v_new_balance
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 4. Update record_loan_repayment_v1 to pass member_id
CREATE OR REPLACE FUNCTION public.record_loan_repayment_v1(
    p_loan_id UUID, p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_payment_method TEXT DEFAULT 'bank'
) RETURNS JSONB AS $$
DECLARE
    v_repayment RECORD;
    v_member_name TEXT;
    v_new_balance NUMERIC;
    v_loan RECORD;
BEGIN
    SELECT full_name INTO v_member_name FROM public.members WHERE id = p_member_id;

    SELECT * INTO v_loan FROM public.loans WHERE id = p_loan_id;
    IF v_loan.id IS NULL THEN RAISE EXCEPTION 'Loan not found'; END IF;

    IF v_loan.total_repaid + p_amount > v_loan.total_to_repay THEN
        RAISE EXCEPTION 'Repayment amount exceeds the remaining loan balance';
    END IF;

    INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, payment_method)
    VALUES (p_loan_id, p_member_id, p_group_id, p_amount, p_payment_method)
    RETURNING * INTO v_repayment;

    v_new_balance := public.increment_group_balance(
        p_group_id,
        p_amount,
        'Loan repayment from ' || v_member_name,
        'loan_repayment',
        v_repayment.id::text,
        p_member_id
    );

    RETURN jsonb_build_object(
        'repayment', to_jsonb(v_repayment),
        'new_balance', v_new_balance
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 5. Update complete_payout_v1 to pass member_id
CREATE OR REPLACE FUNCTION public.complete_payout_v1(
    p_payout_id UUID,
    p_admin_id UUID,
    p_payout_reference TEXT DEFAULT NULL
) RETURNS VOID AS $$
DECLARE
    v_payout RECORD;
    v_group RECORD;
BEGIN
    SELECT * INTO v_payout FROM public.payouts WHERE id = p_payout_id;
    IF v_payout.id IS NULL THEN RAISE EXCEPTION 'Payout record % not found', p_payout_id; END IF;
    IF v_payout.status NOT IN ('group_approved', 'processing') THEN RAISE EXCEPTION 'Payout is in % status and cannot be completed', v_payout.status; END IF;
    SELECT * INTO v_group FROM public.groups WHERE id = v_payout.group_id;
    IF v_group.balance < v_payout.amount THEN RAISE EXCEPTION 'Insufficient group balance'; END IF;

    UPDATE public.payouts
    SET status = 'completed',
        processed_by = p_admin_id,
        processed_at = NOW(),
        payout_reference = COALESCE(p_payout_reference, payout_reference),
        updated_at = NOW()
    WHERE id = p_payout_id;

    PERFORM public.increment_group_balance(
        v_payout.group_id,
        -v_payout.amount,
        'Group disbursement (Ref: ' || COALESCE(p_payout_reference, p_payout_id::text) || ')',
        'payout',
        p_payout_id::text,
        v_payout.member_id -- Track which member received the funds
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 6. Update pay_burial_claim_v1 to pass member_id
CREATE OR REPLACE FUNCTION public.pay_burial_claim_v1(
    p_claim_id UUID,
    p_admin_id UUID,
    p_notes TEXT DEFAULT NULL
) RETURNS VOID AS $$
DECLARE
    v_claim RECORD;
    v_group RECORD;
BEGIN
    SELECT * INTO v_claim FROM public.beneficiary_payout_claims WHERE id = p_claim_id;
    IF v_claim.id IS NULL THEN RAISE EXCEPTION 'Claim not found'; END IF;
    IF v_claim.status NOT IN ('approved', 'under_review', 'escalated') THEN RAISE EXCEPTION 'Invalid claim status'; END IF;
    SELECT * INTO v_group FROM public.groups WHERE id = v_claim.group_id;
    IF v_group.balance < v_claim.claim_amount THEN RAISE EXCEPTION 'Insufficient group balance'; END IF;

    UPDATE public.beneficiary_payout_claims SET status = 'paid', reviewed_by = p_admin_id, reviewed_at = NOW(), admin_notes = COALESCE(p_notes, admin_notes), updated_at = NOW() WHERE id = p_claim_id;

    PERFORM public.increment_group_balance(
        v_claim.group_id,
        -v_claim.claim_amount,
        'Burial claim payout: ' || v_claim.beneficiary_name,
        'claim',
        p_claim_id::text,
        v_claim.member_id -- Track which member's beneficiary received the funds
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
