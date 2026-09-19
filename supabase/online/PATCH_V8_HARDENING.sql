-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — PRODUCTION HARDENING PATCH (V8)
-- July 29, 2026
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. SCHEMA ENHANCEMENTS
ALTER TABLE public.group_ledger ADD COLUMN IF NOT EXISTS member_id UUID REFERENCES public.members(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS public.ledger_checkpoints (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    ledger_entry_id   UUID REFERENCES public.group_ledger(id) NOT NULL,
    balance           NUMERIC(12,2) NOT NULL,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(group_id, ledger_entry_id)
);

-- 2. DISBURSEMENT HARDENING (V2)

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
    SELECT * INTO v_payout FROM public.payouts WHERE id = p_payout_id;
    IF v_payout.id IS NULL THEN RAISE EXCEPTION 'Payout record % not found', p_payout_id; END IF;
    IF v_payout.status NOT IN ('pending', 'group_approved', 'processing') THEN
        RAISE EXCEPTION 'Payout is in % status and cannot be completed', v_payout.status;
    END IF;
    PERFORM public.assert_mfa_for_disbursement_v1(v_payout.amount);
    SELECT * INTO v_group FROM public.groups WHERE id = v_payout.group_id;
    IF v_group.balance < v_payout.amount THEN
        RAISE EXCEPTION 'Insufficient group balance';
    END IF;
    UPDATE public.payouts
    SET status = 'completed',
        processed_by = p_admin_id,
        processed_at = NOW(),
        payout_reference = COALESCE(p_payout_reference, payout_reference),
        updated_at = NOW()
    WHERE id = p_payout_id;
    SELECT public.increment_group_balance(
        v_payout.group_id,
        -v_payout.amount,
        'Group disbursement (Ref: ' || COALESCE(p_payout_reference, p_payout_id::text) || ')',
        'payout',
        p_payout_id::text,
        v_payout.member_id
    ) INTO v_new_balance;
    RETURN jsonb_build_object('status', 'success', 'new_balance', v_new_balance, 'payout_id', p_payout_id);
END;
$$;

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
    IF v_claim.status NOT IN ('approved', 'under_review', 'escalated') THEN RAISE EXCEPTION 'Invalid claim status'; END IF;
    PERFORM public.assert_mfa_for_disbursement_v1(v_claim.claim_amount);
    SELECT * INTO v_group FROM public.groups WHERE id = v_claim.group_id;
    IF v_group.balance < v_claim.claim_amount THEN RAISE EXCEPTION 'Insufficient group balance'; END IF;
    UPDATE public.beneficiary_payout_claims SET status = 'paid', reviewed_by = p_admin_id, reviewed_at = NOW(), admin_notes = COALESCE(p_notes, admin_notes), updated_at = NOW() WHERE id = p_claim_id;
    SELECT public.increment_group_balance(v_claim.group_id, -v_claim.claim_amount, 'Burial claim payout: ' || v_claim.beneficiary_name, 'claim', p_claim_id::text, v_claim.member_id) INTO v_new_balance;
    RETURN jsonb_build_object('status', 'success', 'new_balance', v_new_balance, 'claim_id', p_claim_id);
END;
$$;

-- 3. PAYMENT HARDENING (V2)

CREATE OR REPLACE FUNCTION public.process_complex_payment_v2(
    p_group_id UUID,
    p_member_id UUID,
    p_total_amount NUMERIC,
    p_tx_id TEXT,
    p_allocations JSONB,
    p_method TEXT DEFAULT 'bank',
    p_payment_type TEXT DEFAULT 'contribution'
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_new_balance NUMERIC;
    v_allocation RECORD;
    v_member_name TEXT;
    v_res JSONB;
BEGIN
    IF EXISTS (SELECT 1 FROM public.payments WHERE transaction_id = p_tx_id AND status = 'completed') OR
       EXISTS (SELECT 1 FROM public.group_ledger WHERE transaction_id = p_tx_id) THEN
        SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;
        RETURN jsonb_build_object('success', true, 'new_balance', v_new_balance, 'transaction_id', p_tx_id, 'message', 'Duplicate transaction ignored');
    END IF;

    SELECT full_name INTO v_member_name FROM public.members WHERE id = p_member_id;
    IF v_member_name IS NULL THEN RAISE EXCEPTION 'Member not found'; END IF;

    INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at)
    VALUES (p_member_id, p_group_id, p_total_amount, p_payment_type, p_method, p_tx_id, 'completed', NOW());

    FOR v_allocation IN SELECT * FROM jsonb_to_recordset(p_allocations) AS x(type text, amount numeric, loan_id uuid, due_date date, description text)
    LOOP
        IF v_allocation.amount <= 0 THEN CONTINUE; END IF;
        IF v_allocation.type = 'loan_repayment' THEN
            v_res := public.record_loan_repayment_v1(v_allocation.loan_id, p_member_id, p_group_id, v_allocation.amount, p_method);
            v_new_balance := (v_res->>'new_balance')::NUMERIC;
        ELSE
            v_res := public.record_contribution_v1(p_member_id, p_group_id, v_allocation.amount, v_allocation.due_date, NOW(), 'paid', p_tx_id, v_allocation.type, v_allocation.description);
            v_new_balance := (v_res->>'new_balance')::NUMERIC;
        END IF;
    END LOOP;

    IF v_new_balance IS NULL THEN SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id; END IF;

    INSERT INTO public.audit_logs (actor_id, target_group_id, target_member_id, action, details)
    VALUES (p_member_id, p_group_id, p_member_id, 'COMPLEX_PAYMENT_EXECUTED', jsonb_build_object('transaction_id', p_tx_id, 'allocations', p_allocations));

    RETURN jsonb_build_object('success', true, 'new_balance', v_new_balance, 'transaction_id', p_tx_id);
END;
$$;

-- 4. SIMULATION ENGINE (V2)

CREATE OR REPLACE FUNCTION public.simulate_bulk_activity_v2(
    p_group_id UUID,
    p_months INT DEFAULT 6,
    p_member_count INT DEFAULT 10
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_member_id UUID;
    v_month_idx INT;
    v_member_idx INT;
    v_contrib_amt NUMERIC;
    v_due_date DATE;
    v_res JSONB;
BEGIN
    SELECT monthly_contribution INTO v_contrib_amt FROM public.groups WHERE id = p_group_id;
    FOR v_member_idx IN 1..p_member_count LOOP
        INSERT INTO public.members (group_id, full_name, email, status, joined_at, member_key)
        VALUES (p_group_id, 'Sim Member ' || v_member_idx, 'sim.' || v_member_idx || '.' || p_group_id || '@example.com', 'active', now() - (p_months || ' months')::interval, 'SIM-KEY-' || p_group_id || '-' || v_member_idx)
        ON CONFLICT (group_id, member_key) DO NOTHING;
    END LOOP;
    FOR v_month_idx IN 1..p_months LOOP
        v_due_date := (now() - ((p_months - v_month_idx) || ' months')::interval)::date;
        FOR v_member_id IN SELECT id FROM public.members WHERE group_id = p_group_id LOOP
            IF random() < 0.85 THEN
                PERFORM public.record_contribution_v1(v_member_id, p_group_id, CASE WHEN random() < 0.9 THEN v_contrib_amt ELSE v_contrib_amt / 2 END, v_due_date, v_due_date + interval '12 hours', 'paid', 'sim-bulk-' || v_member_id || '-' || v_month_idx, 'contribution');
            END IF;
        END LOOP;
    END LOOP;
    SELECT public.recalculate_all_group_financials_v2(p_group_id) INTO v_res;
    RETURN jsonb_build_object('status', 'success', 'months_simulated', p_months);
END;
$$;

-- 5. REPAIR & AUDIT (V2)

CREATE OR REPLACE FUNCTION public.recalculate_all_group_financials_v2(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_member RECORD;
    v_loan RECORD;
    v_report JSONB;
    v_balance_report JSONB;
BEGIN
    FOR v_member IN SELECT id FROM public.members WHERE group_id = p_group_id LOOP PERFORM public.fn_sync_single_member_financials(v_member.id); END LOOP;
    FOR v_loan IN SELECT id FROM public.loans WHERE group_id = p_group_id LOOP PERFORM public.fn_sync_single_loan_totals(v_loan.id); END LOOP;
    SELECT public.consolidate_group_ledger_v1(p_group_id) INTO v_balance_report;
    PERFORM public.recompute_ledger_running_balances_v2(p_group_id);
    v_report := jsonb_build_object('group_id', p_group_id, 'balance_audit', v_balance_report);
    INSERT INTO public.audit_logs (actor_id, target_group_id, action, details) VALUES (p_group_id, p_group_id, 'GLOBAL_FINANCIAL_RECALCULATION_V2', v_report);
    RETURN v_report;
END;
$$;

-- GRANTS
GRANT EXECUTE ON FUNCTION public.process_complex_payment_v2(UUID, UUID, NUMERIC, TEXT, JSONB, TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.complete_payout_v2(UUID, UUID, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.pay_burial_claim_v2(UUID, UUID, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.simulate_bulk_activity_v2(UUID, INT, INT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.recalculate_all_group_financials_v2(UUID) TO authenticated;
