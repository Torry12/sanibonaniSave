-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — 02. FUNCTIONS AND TRIGGERS
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

-- 1. UTILITY: Update timestamp
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. MEMBER COUNT TRACKER
CREATE OR REPLACE FUNCTION public.update_member_count()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        UPDATE public.groups SET current_members = current_members + 1 WHERE id = NEW.group_id;
    ELSIF (TG_OP = 'DELETE') THEN
        UPDATE public.groups SET current_members = current_members - 1 WHERE id = OLD.group_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_member_count
AFTER INSERT OR DELETE ON public.members
FOR EACH ROW EXECUTE FUNCTION public.update_member_count();

-- 3. CORE TRIGGER ATTACHMENTS
CREATE TRIGGER update_profiles_updated_at BEFORE UPDATE ON public.profiles FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_groups_updated_at BEFORE UPDATE ON public.groups FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_members_updated_at BEFORE UPDATE ON public.members FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_contributions_updated_at BEFORE UPDATE ON public.contributions FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_payments_updated_at BEFORE UPDATE ON public.payments FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_loans_updated_at BEFORE UPDATE ON public.loans FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_payouts_updated_at BEFORE UPDATE ON public.payouts FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- 4. ATOMIC BALANCE UPDATER
CREATE OR REPLACE FUNCTION public.increment_group_balance(
    p_group_id UUID,
    p_amount NUMERIC
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

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Group not found';
    END IF;

    -- Insert an immutable ledger entry for this change
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category)
    VALUES (p_group_id, NULL, p_amount, v_new_balance, 'Atomic balance update', 'adjustment');

    RETURN v_new_balance;
END;
$$;

-- 5. ATOMIC CONTRIBUTION RECORDER
CREATE OR REPLACE FUNCTION public.record_contribution_v1(
    p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_due_date DATE,
    p_paid_at TIMESTAMPTZ, p_status TEXT, p_tx_id TEXT DEFAULT NULL, p_type TEXT DEFAULT 'contribution'
 ) RETURNS public.contributions AS $$
DECLARE
    v_contribution public.contributions;
    v_new_balance NUMERIC;
BEGIN
    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, transaction_id, type)
    VALUES (p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_tx_id, p_type)
    RETURNING * INTO v_contribution;

    UPDATE public.members
    SET total_contributions = total_contributions + 1,
        total_paid = total_paid + p_amount
    WHERE id = p_member_id;

    SELECT public.increment_group_balance(p_group_id, p_amount) INTO v_new_balance;

    -- Note: increment_group_balance already adds a ledger entry,
    -- but usually we want a specific one for contributions with the contribution ID.
    -- We can update the ledger entry just created or handle it here.
    -- For simplicity in this rebuild, we'll let increment_group_balance handle the ledger.

    RETURN v_contribution;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 6. ATOMIC LOAN REPAYMENT RECORDER
CREATE OR REPLACE FUNCTION public.record_loan_repayment_v1(
    p_loan_id UUID, p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_payment_method TEXT DEFAULT 'bank'
 ) RETURNS public.loan_repayments AS $$
DECLARE
    v_repayment public.loan_repayments;
    v_new_balance NUMERIC;
BEGIN
    -- Record repayment
    INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, payment_method)
    VALUES (p_loan_id, p_member_id, p_group_id, p_amount, p_payment_method)
    RETURNING * INTO v_repayment;

    -- Update loan status/balance
    UPDATE public.loans
    SET total_repaid = total_repaid + p_amount,
        status = CASE
            WHEN total_repaid + p_amount >= total_to_repay THEN 'completed'::text
            ELSE 'partially_paid'::text
        END,
        updated_at = NOW()
    WHERE id = p_loan_id;

    SELECT public.increment_group_balance(p_group_id, p_amount) INTO v_new_balance;

    RETURN v_repayment;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 7. ATOMIC LOAN DISBURSEMENT
CREATE OR REPLACE FUNCTION public.approve_and_disburse_loan_v1(
    p_loan_id UUID,
    p_admin_id UUID,
    p_payment_method TEXT DEFAULT 'bank'
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_loan public.loans;
    v_member_name TEXT;
    v_new_balance NUMERIC;
BEGIN
    SELECT * INTO v_loan FROM public.loans WHERE id = p_loan_id FOR UPDATE;
    IF v_loan.id IS NULL THEN RAISE EXCEPTION 'Loan % not found', p_loan_id; END IF;

    IF v_loan.status IN ('active', 'completed', 'partially_paid') THEN
        RETURN;
    END IF;

    SELECT full_name INTO v_member_name FROM public.members WHERE id = v_loan.member_id;

    UPDATE public.loans
    SET status = 'active',
        reviewed_by = p_admin_id,
        reviewed_at = NOW(),
        updated_at = NOW()
    WHERE id = p_loan_id;

    -- Disbursement (Decrements balance)
    UPDATE public.groups
    SET balance = balance - v_loan.amount,
        updated_at = NOW()
    WHERE id = v_loan.group_id
    RETURNING balance INTO v_new_balance;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category)
    VALUES (v_loan.group_id, v_loan.id, -v_loan.amount, v_new_balance,
            'Loan Disbursement to ' || v_member_name, 'loan_disbursement');
END;
$$;

COMMIT;
