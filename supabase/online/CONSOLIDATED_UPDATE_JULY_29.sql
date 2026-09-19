-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — CONSOLIDATED PRODUCTION UPDATE (JULY 29, 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- [PATCH 1] SCHEMA HARDENING & AUDIT SUPPORT
ALTER TABLE public.group_ledger ADD COLUMN IF NOT EXISTS member_id UUID REFERENCES public.members(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS public.ledger_checkpoints (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    ledger_entry_id   UUID REFERENCES public.group_ledger(id) NOT NULL,
    balance           NUMERIC(12,2) NOT NULL,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(group_id, ledger_entry_id)
);

-- [PATCH 2] HARDENED DISBURSEMENTS (V2)
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
    IF v_payout.id IS NULL THEN RAISE EXCEPTION 'Payout record not found'; END IF;
    IF v_payout.status NOT IN ('pending', 'group_approved', 'processing') THEN
        RAISE EXCEPTION 'Invalid status for completion';
    END IF;
    PERFORM public.assert_mfa_for_disbursement_v1(v_payout.amount);
    SELECT * INTO v_group FROM public.groups WHERE id = v_payout.group_id;
    IF v_group.balance < v_payout.amount THEN RAISE EXCEPTION 'Insufficient balance'; END IF;
    UPDATE public.payouts SET status = 'completed', processed_by = p_admin_id, processed_at = NOW(), updated_at = NOW() WHERE id = p_payout_id;
    SELECT public.increment_group_balance(v_payout.group_id, -v_payout.amount, 'Disbursement', 'payout', p_payout_id::text, v_payout.member_id) INTO v_new_balance;
    RETURN jsonb_build_object('status', 'success', 'new_balance', v_new_balance);
END;
$$;

-- [PATCH 3] HARDENED PAYMENTS (V2)
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
    v_res JSONB;
BEGIN
    IF EXISTS (SELECT 1 FROM public.payments WHERE transaction_id = p_tx_id AND status = 'completed') THEN
        SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;
        RETURN jsonb_build_object('success', true, 'new_balance', v_new_balance);
    END IF;
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
    RETURN jsonb_build_object('success', true, 'new_balance', COALESCE(v_new_balance, 0));
END;
$$;

-- [PATCH 4] REPAIR & SIMULATION
CREATE OR REPLACE FUNCTION public.recalculate_all_group_financials_v2(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_report JSONB;
BEGIN
    SELECT public.consolidate_group_ledger_v1(p_group_id) INTO v_report;
    PERFORM public.recompute_ledger_running_balances_v1(p_group_id);
    RETURN v_report;
END;
$$;

-- GRANTS
GRANT EXECUTE ON FUNCTION public.process_complex_payment_v2(UUID, UUID, NUMERIC, TEXT, JSONB, TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.complete_payout_v2(UUID, UUID, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.recalculate_all_group_financials_v2(UUID) TO authenticated;

RAISE NOTICE 'Production Hardening Update Applied.';
