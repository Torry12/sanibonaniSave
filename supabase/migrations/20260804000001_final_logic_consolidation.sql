-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — FINAL LOGIC CONSOLIDATION (v10)
-- Version: 1.0 (August 4, 2026)
-- ─────────────────────────────────────────────────────────────────────────────
-- This migration force-applies the consolidated "Gold Standard" logic to ensure
-- 100% parity between local development and cloud production.

-- 1. SCHEMA DELTAS
-- Ensure group_ledger has the unique constraint to prevent double-accounting
ALTER TABLE public.group_ledger DROP CONSTRAINT IF EXISTS group_ledger_group_id_transaction_id_key;
ALTER TABLE public.group_ledger ADD CONSTRAINT group_ledger_group_id_transaction_id_key UNIQUE(group_id, transaction_id);

-- Ensure platform_settings has correct types
ALTER TABLE public.platform_settings ALTER COLUMN value TYPE NUMERIC;

-- 2. MASTER FUNCTIONS (Authoritative Signatures)

-- Utility: update_updated_at_column
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Auth: handle_new_user
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
DECLARE
    v_full_name TEXT;
BEGIN
  v_full_name := COALESCE(NEW.raw_user_meta_data ->> 'full_name', 'New User');
  IF char_length(v_full_name) < 3 THEN v_full_name := v_full_name || ' (User)'; END IF;

  INSERT INTO public.profiles (id, full_name, email, role)
  VALUES (NEW.id, v_full_name, NEW.email, COALESCE(NEW.raw_user_meta_data ->> 'role', 'member'));
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Security: assert_mfa_for_disbursement_v1
CREATE OR REPLACE FUNCTION public.assert_mfa_for_disbursement_v1(p_amount NUMERIC)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    IF ABS(p_amount) >= 10000.00 THEN
        IF COALESCE(auth.jwt() ->> 'aal', 'aal1') != 'aal2' THEN
            RAISE EXCEPTION 'MFA_REQUIRED' USING
                DETAIL = 'High-value disbursements (>= R10,000) require Multi-Factor Authentication (AAL2).',
                HINT = 'mfa_challenge_required';
        END IF;
    END IF;
END;
$$;

-- Accounting: increment_group_balance
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
    UPDATE public.groups SET balance = balance + p_amount, updated_at = NOW()
    WHERE id = p_group_id RETURNING balance INTO v_new_balance;

    IF NOT FOUND THEN RAISE EXCEPTION 'GROUP_NOT_FOUND'; END IF;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id)
    VALUES (p_group_id, p_transaction_id, p_amount, v_new_balance, p_description, p_category, p_member_id);

    RETURN v_new_balance;
END;
$$;

-- Accounting: record_platform_revenue_v1
CREATE OR REPLACE FUNCTION public.record_platform_revenue_v1(
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id TEXT DEFAULT NULL,
    p_group_id UUID DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_last_balance NUMERIC;
BEGIN
    SELECT balance_after INTO v_last_balance FROM public.platform_ledger ORDER BY created_at DESC LIMIT 1;

    INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category, group_id)
    VALUES (p_transaction_id, p_amount, COALESCE(v_last_balance, 0) + p_amount, p_description, p_category, p_group_id);
END;
$$;

-- RPC: record_contribution_v1
DROP FUNCTION IF EXISTS public.record_contribution_v1(UUID, UUID, NUMERIC, DATE, TIMESTAMPTZ, TEXT, TEXT, TEXT, TEXT);
CREATE OR REPLACE FUNCTION public.record_contribution_v1(
    p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_due_date DATE,
    p_paid_at TIMESTAMPTZ, p_status TEXT, p_tx_id TEXT DEFAULT NULL,
    p_type TEXT DEFAULT 'contribution', p_description TEXT DEFAULT NULL
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

    IF p_description IS NOT NULL THEN
        v_ledger_desc := p_description || ' (' || v_member_name || ')';
    ELSIF p_type IN ('member_fee', 'member_fee_ledger') THEN
        v_ledger_desc := 'Platform Member Fee (' || v_member_name || ')';
    ELSE
        v_ledger_desc := initcap(replace(p_type, '_', ' ')) || ' from ' || v_member_name;
    END IF;

    v_ledger_cat := CASE WHEN p_type IN ('member_fee', 'member_fee_ledger') THEN 'platform_fee' ELSE p_type END;

    IF p_type NOT IN ('member_fee', 'member_fee_ledger', 'registration_contribution') THEN
        SELECT public.increment_group_balance(p_group_id, p_amount, v_ledger_desc, v_ledger_cat, v_contribution.id::text, p_member_id) INTO v_new_balance;
    ELSE
        SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;
        PERFORM public.record_platform_revenue_v1(p_amount, v_ledger_desc, v_ledger_cat, v_contribution.id::text, p_group_id);
    END IF;

    RETURN jsonb_build_object('contribution', to_jsonb(v_contribution), 'new_balance', v_new_balance);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- RPC: record_loan_repayment_v1
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
    IF v_loan.id IS NULL THEN RAISE EXCEPTION 'LOAN_NOT_FOUND'; END IF;
    IF v_loan.total_repaid + p_amount > v_loan.total_to_repay + 0.01 THEN RAISE EXCEPTION 'OVERPAYMENT_PROHIBITED'; END IF;

    INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, payment_method)
    VALUES (p_loan_id, p_member_id, p_group_id, p_amount, p_payment_method)
    RETURNING * INTO v_repayment;

    SELECT public.increment_group_balance(p_group_id, p_amount, 'Loan repayment from ' || v_member_name, 'loan_repayment', v_repayment.id::text, p_member_id) INTO v_new_balance;

    RETURN jsonb_build_object('repayment', to_jsonb(v_repayment), 'new_balance', v_new_balance);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- RPC: approve_and_disburse_loan_v1
CREATE OR REPLACE FUNCTION public.approve_and_disburse_loan_v1(p_loan_id UUID, p_admin_id UUID)
RETURNS VOID AS $$
DECLARE
    v_loan RECORD;
    v_member_name TEXT;
    v_group RECORD;
BEGIN
    SELECT * INTO v_loan FROM public.loans WHERE id = p_loan_id;
    IF v_loan.id IS NULL THEN RAISE EXCEPTION 'LOAN_NOT_FOUND'; END IF;
    IF v_loan.status != 'pending' THEN RAISE EXCEPTION 'INVALID_STATUS'; END IF;
    PERFORM public.assert_mfa_for_disbursement_v1(v_loan.amount);
    SELECT * INTO v_group FROM public.groups WHERE id = v_loan.group_id;
    IF v_group.balance < v_loan.amount THEN RAISE EXCEPTION 'INSUFFICIENT_LIQUIDITY'; END IF;
    SELECT full_name INTO v_member_name FROM public.members WHERE id = v_loan.member_id;

    UPDATE public.loans SET status = 'active', reviewed_by = p_admin_id, reviewed_at = NOW(), start_date = NOW(), updated_at = NOW() WHERE id = p_loan_id;
    PERFORM public.increment_group_balance(v_loan.group_id, -v_loan.amount, 'Loan disbursement to ' || v_member_name, 'loan_disbursement', p_loan_id::text, v_loan.member_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- RPC: record_loan_settlement_extraction_v1
CREATE OR REPLACE FUNCTION public.record_loan_settlement_extraction_v1(
    p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_loan_id UUID
) RETURNS JSONB AS $$
DECLARE
    v_ext JSONB;
    v_rep JSONB;
BEGIN
    SELECT public.record_contribution_v1(p_member_id, p_group_id, -p_amount, CURRENT_DATE, NOW(), 'paid', 'ext-' || p_loan_id::text, 'loan_settlement_extraction', 'Principal used for settlement') INTO v_ext;
    SELECT public.record_loan_repayment_v1(p_loan_id, p_member_id, p_group_id, p_amount, 'wallet') INTO v_rep;
    RETURN jsonb_build_object('status', 'success', 'extraction', v_ext, 'repayment', v_rep);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Repair Logic: recalculate_all_group_financials_v1
CREATE OR REPLACE FUNCTION public.recalculate_all_group_financials_v1(p_group_id UUID)
RETURNS JSONB AS $$
DECLARE
    v_m RECORD;
    v_l RECORD;
BEGIN
    FOR v_m IN (SELECT id FROM public.members WHERE group_id = p_group_id) LOOP PERFORM public.fn_sync_single_member_financials(v_m.id); END LOOP;
    FOR v_l IN (SELECT id FROM public.loans WHERE group_id = p_group_id) LOOP PERFORM public.fn_sync_single_loan_totals(v_l.id); END LOOP;
    PERFORM public.recompute_ledger_running_balances_v2(p_group_id);
    RETURN jsonb_build_object('group_id', p_group_id, 'status', 'recalculated');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 3. SECURITY & RLS UPDATES
DO $$
DECLARE v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Platform Admin Bypass" ON public.%I', v_table);
        EXECUTE format('CREATE POLICY "Platform Admin Bypass" ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin())', v_table);
    END LOOP;
END $$;
