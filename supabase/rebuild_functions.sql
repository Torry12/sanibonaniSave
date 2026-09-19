-- 17_ATOMIC_PAYMENT_PROCESSING.sql
CREATE OR REPLACE FUNCTION public.process_complex_payment_v1(
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
    v_total_allocated NUMERIC := 0;
    v_member_name TEXT;
    v_final_res JSONB;
BEGIN
    IF EXISTS (SELECT 1 FROM public.payments WHERE transaction_id = p_tx_id AND status = 'completed') THEN
        SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;
        RETURN jsonb_build_object('success', true, 'new_balance', v_new_balance, 'transaction_id', p_tx_id, 'message', 'Duplicate transaction ignored');
    END IF;
    SELECT full_name INTO v_member_name FROM public.members WHERE id = p_member_id;
    IF v_member_name IS NULL THEN RAISE EXCEPTION 'Member not found'; END IF;
    SELECT SUM((val->>'amount')::NUMERIC) INTO v_total_allocated FROM jsonb_array_elements(p_allocations) AS val;
    IF ABS(v_total_allocated - p_total_amount) > 0.01 THEN RAISE EXCEPTION 'Total amount % does not match sum of allocations %', p_total_amount, v_total_allocated; END IF;
    INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at)
    VALUES (p_member_id, p_group_id, p_total_amount, p_payment_type, p_method, p_tx_id, 'completed', NOW());
    FOR v_allocation IN SELECT * FROM jsonb_to_recordset(p_allocations) AS x(type text, amount numeric, loan_id uuid, due_date date)
    LOOP
        IF v_allocation.amount <= 0 THEN CONTINUE; END IF;
        IF v_allocation.type = 'loan_repayment' THEN
            v_final_res := public.record_loan_repayment_v1(v_allocation.loan_id, p_member_id, p_group_id, v_allocation.amount, p_method);
            v_new_balance := (v_final_res->>'new_balance')::NUMERIC;
        ELSE
            v_final_res := public.record_contribution_v1(p_member_id, p_group_id, v_allocation.amount, v_allocation.due_date, NOW(), 'paid', p_tx_id, v_allocation.type);
            v_new_balance := (v_final_res->>'new_balance')::NUMERIC;
        END IF;
    END LOOP;
    IF v_new_balance IS NULL THEN SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id; END IF;
    RETURN jsonb_build_object('success', true, 'new_balance', v_new_balance, 'transaction_id', p_tx_id);
END;
$$;

-- 18_PAYOUT_RPCS.sql
CREATE OR REPLACE FUNCTION public.process_rosca_payout_v1(
    p_group_id UUID,
    p_member_id UUID,
    p_total_pot NUMERIC,
    p_loan_deductions JSONB,
    p_payout_amount NUMERIC,
    p_payout_data JSONB
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_loan RECORD;
    v_payout_id UUID;
BEGIN
    FOR v_loan IN SELECT * FROM jsonb_to_recordset(p_loan_deductions) AS x(loan_id uuid, amount numeric)
    LOOP
        IF v_loan.amount > 0 THEN
            PERFORM public.increment_group_balance(p_group_id, -v_loan.amount, 'ROSCA Pot Loan Offset', 'rosca_payout_offset', 'payout-offset-' || v_loan.loan_id::text, p_member_id);
            PERFORM public.record_loan_repayment_v1(v_loan.loan_id, p_member_id, p_group_id, v_loan.amount, 'wallet');
        END IF;
    END LOOP;
    IF p_payout_amount > 0 THEN
        INSERT INTO public.payouts (group_id, member_id, amount, bank_name, account_no, branch_code, status)
        VALUES (p_group_id, p_member_id, p_payout_amount, p_payout_data->>'bank_name', p_payout_data->>'account_no', p_payout_data->>'branch_code', 'pending')
        RETURNING id INTO v_payout_id;
    END IF;
    INSERT INTO public.audit_logs (actor_id, target_member_id, target_group_id, action, details)
    VALUES (p_group_id, p_member_id, p_group_id, 'ROSCA_PAYOUT_INITIATED', jsonb_build_object('pot', p_total_pot, 'deductions', p_loan_deductions, 'payout', p_payout_amount));
    RETURN jsonb_build_object('success', true, 'payout_id', v_payout_id);
END;
$$;

-- 27_FINANCIAL_SYSTEM_HARDENING.sql
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
    v_new_balance NUMERIC;
BEGIN
    PERFORM public.record_contribution_v1(p_member_id, p_group_id, -p_amount, CURRENT_DATE, NOW(), 'paid', 'settlement-' || p_loan_id::text, 'loan_settlement_extraction');
    PERFORM public.record_loan_repayment_v1(p_loan_id, p_member_id, p_group_id, p_amount, 'wallet');
    SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;
    RETURN jsonb_build_object('status', 'success', 'group_balance', v_new_balance);
END;
$$;

-- 33_LEDGER_CONSOLIDATION_V1.sql
CREATE OR REPLACE FUNCTION public.recompute_ledger_running_balances_v1(p_group_id UUID)
RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_entry RECORD;
    v_running_balance NUMERIC := 0;
BEGIN
    FOR v_entry IN SELECT id, amount FROM public.group_ledger WHERE group_id = p_group_id ORDER BY created_at ASC, id ASC
    LOOP
        v_running_balance := v_running_balance + v_entry.amount;
        UPDATE public.group_ledger SET balance_after = v_running_balance WHERE id = v_entry.id;
    END LOOP;
    UPDATE public.groups SET balance = v_running_balance, updated_at = NOW() WHERE id = p_group_id;
    RETURN v_running_balance;
END;
$$;

CREATE OR REPLACE FUNCTION public.consolidate_group_ledger_v1(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_reconstructed_count INT := 0;
    v_final_balance NUMERIC;
    v_report JSONB;
BEGIN
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT c.group_id, CAST(c.id AS TEXT), c.amount, 0, initcap(replace(c.type, '_', ' ')) || ' from ' || m.full_name, c.type, c.member_id, c.paid_at
    FROM public.contributions c JOIN public.members m ON c.member_id = m.id
    WHERE c.group_id = p_group_id AND c.status = 'paid' AND c.type NOT IN ('member_fee')
      AND NOT EXISTS (SELECT 1 FROM public.group_ledger l WHERE l.group_id = p_group_id AND l.transaction_id = CAST(c.id AS TEXT))
    ON CONFLICT (group_id, transaction_id) WHERE transaction_id IS NOT NULL DO NOTHING;
    GET DIAGNOSTICS v_reconstructed_count = ROW_COUNT;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT lr.group_id, CAST(lr.id AS TEXT), lr.amount, 0, 'Loan repayment from ' || m.full_name, 'loan_repayment', lr.member_id, lr.paid_at
    FROM public.loan_repayments lr JOIN public.members m ON lr.member_id = m.id
    WHERE lr.group_id = p_group_id AND NOT EXISTS (SELECT 1 FROM public.group_ledger l WHERE l.group_id = p_group_id AND l.transaction_id = CAST(lr.id AS TEXT))
    ON CONFLICT (group_id, transaction_id) WHERE transaction_id IS NOT NULL DO NOTHING;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT p.group_id, CAST(p.id AS TEXT), -p.amount, 0, 'Group disbursement (Ref: ' || COALESCE(p.payout_reference, p.id::text) || ')', 'payout', p.member_id, p.processed_at
    FROM public.payouts p WHERE p.group_id = p_group_id AND p.status = 'completed'
      AND NOT EXISTS (SELECT 1 FROM public.group_ledger l WHERE l.group_id = p_group_id AND l.transaction_id = CAST(p.id AS TEXT))
    ON CONFLICT (group_id, transaction_id) WHERE transaction_id IS NOT NULL DO NOTHING;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT bc.group_id, CAST(bc.id AS TEXT), -bc.claim_amount, 0, 'Burial claim payout: ' || bc.beneficiary_name, 'claim', bc.member_id, bc.reviewed_at
    FROM public.beneficiary_payout_claims bc WHERE bc.group_id = p_group_id AND bc.status = 'paid'
      AND NOT EXISTS (SELECT 1 FROM public.group_ledger l WHERE l.group_id = p_group_id AND l.transaction_id = CAST(bc.id AS TEXT))
    ON CONFLICT (group_id, transaction_id) WHERE transaction_id IS NOT NULL DO NOTHING;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT loan.group_id, 'disburse-' || CAST(loan.id AS TEXT), -loan.amount, 0, 'Loan disbursement to ' || m.full_name, 'loan_disbursement', loan.member_id, COALESCE(loan.reviewed_at, loan.created_at)
    FROM public.loans loan JOIN public.members m ON loan.member_id = m.id
    WHERE loan.group_id = p_group_id AND loan.status NOT IN ('pending', 'rejected')
      AND NOT EXISTS (SELECT 1 FROM public.group_ledger l WHERE l.group_id = p_group_id AND l.transaction_id = 'disburse-' || CAST(loan.id AS TEXT))
    ON CONFLICT (group_id, transaction_id) WHERE transaction_id IS NOT NULL DO NOTHING;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, created_at)
    SELECT pf.group_id, 'pf-deduct-' || CAST(pf.id AS TEXT), -pf.amount_paid, 0, 'Platform ' || pf.fee_type || ' fee deduction', 'platform_fee', pf.paid_at
    FROM public.platform_fees pf WHERE pf.group_id = p_group_id AND pf.status = 'paid'
      AND NOT EXISTS (SELECT 1 FROM public.group_ledger l WHERE l.group_id = p_group_id AND l.transaction_id = 'pf-deduct-' || CAST(pf.id AS TEXT))
    ON CONFLICT (group_id, transaction_id) WHERE transaction_id IS NOT NULL DO NOTHING;

    SELECT public.recompute_ledger_running_balances_v1(p_group_id) INTO v_final_balance;
    SELECT public.perform_group_reconciliation_v1(p_group_id) INTO v_report;
    RETURN jsonb_build_object('group_id', p_group_id, 'final_balance', v_final_balance, 'reconciliation_report', v_report, 'entries_reconstructed', v_reconstructed_count);
END;
$$;
