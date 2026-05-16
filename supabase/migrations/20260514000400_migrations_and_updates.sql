-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — MIGRATION LOG (Historical & Recent)
-- Version: 1.0 (Consolidated May 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Migration: Receipt URL (April 2026)
ALTER TABLE public.contributions ADD COLUMN IF NOT EXISTS receipt_url TEXT;

-- 2. Migration: Loans Table (April 2026)
CREATE TABLE IF NOT EXISTS public.loans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id UUID REFERENCES public.members(id) ON DELETE CASCADE,
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE,
    amount NUMERIC(12,2) NOT NULL,
    interest_rate NUMERIC(5,2) DEFAULT 0,
    total_to_repay NUMERIC(12,2) NOT NULL,
    total_repaid NUMERIC(12,2) DEFAULT 0,
    monthly_repayment NUMERIC(12,2) NOT NULL,
    start_date DATE,
    end_date DATE,
    status TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'active', 'partially_paid', 'completed', 'rejected', 'overdue')),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. Migration: Group Health Scores (May 2026)
CREATE TABLE IF NOT EXISTS public.group_actuarial_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE,
    composite_risk_score INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. Fix: Legacy Payout Status (May 2026)
UPDATE public.payouts SET status = 'group_approved' WHERE status = 'pending';

-- 5. Fix: Audit Log Table (May 2026)
CREATE TABLE IF NOT EXISTS public.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID NOT NULL,
    action TEXT NOT NULL,
    details JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. Migration: Advanced Loan Features (June 2026)
ALTER TABLE public.loans ADD COLUMN IF NOT EXISTS contract_url TEXT;
ALTER TABLE public.loans ADD COLUMN IF NOT EXISTS surety_amount NUMERIC(12,2);
ALTER TABLE public.loans ADD COLUMN IF NOT EXISTS reviewed_by UUID REFERENCES auth.users(id);
ALTER TABLE public.loans ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;
ALTER TABLE public.loans ADD COLUMN IF NOT EXISTS admin_notes TEXT;
ALTER TABLE public.loans ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
ALTER TABLE public.loans ADD COLUMN IF NOT EXISTS next_payment_date DATE;

-- Update status constraint to include 'cancelled' if not already there
ALTER TABLE public.loans DROP CONSTRAINT IF EXISTS loans_status_check;
ALTER TABLE public.loans ADD CONSTRAINT loans_status_check CHECK (status IN ('pending', 'approved', 'active', 'partially_paid', 'completed', 'rejected', 'overdue', 'cancelled'));

-- Loan Disbursement RPC
CREATE OR REPLACE FUNCTION public.disburse_loan_v1(
    p_loan_id UUID, p_admin_id UUID, p_payment_method TEXT DEFAULT 'bank'
) RETURNS public.loans AS $$
DECLARE
    v_loan public.loans;
    v_group_id UUID;
    v_amount NUMERIC;
    v_new_balance NUMERIC;
    v_payment_id UUID;
BEGIN
    SELECT * INTO v_loan FROM public.loans WHERE id = p_loan_id;

    IF v_loan.id IS NULL THEN
        RAISE EXCEPTION 'Loan not found';
    END IF;

    IF v_loan.status != 'approved' THEN
        RAISE EXCEPTION 'Loan must be in approved status to be disbursed. Current status: %', v_loan.status;
    END IF;

    v_group_id := v_loan.group_id;
    v_amount := v_loan.amount;

    -- Update loan status to active
    UPDATE public.loans
    SET status = 'active',
        start_date = CURRENT_DATE,
        end_date = CURRENT_DATE + (INTERVAL '1 month' * ceil(v_loan.total_to_repay / v_loan.monthly_repayment)),
        next_payment_date = CURRENT_DATE + INTERVAL '1 month',
        updated_at = NOW()
    WHERE id = p_loan_id
    RETURNING * INTO v_loan;

    -- Record disbursement in payments table
    INSERT INTO public.payments (group_id, member_id, amount, payment_type, payment_method, status, processed_at)
    VALUES (v_group_id, v_loan.member_id, v_amount, 'loan_disbursement', p_payment_method, 'completed', NOW())
    RETURNING id INTO v_payment_id;

    -- Update group balance
    UPDATE public.groups
    SET balance = balance - v_amount
    WHERE id = v_group_id
    RETURNING balance INTO v_new_balance;

    -- Add to Group Ledger
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category)
    VALUES (v_group_id, v_payment_id, -v_amount, v_new_balance,
            'Loan disbursement to ' || (SELECT full_name FROM public.members WHERE id = v_loan.member_id),
            'loan_disbursement');

    -- Audit log
    INSERT INTO public.audit_logs (actor_id, target_member_id, target_group_id, action, details)
    VALUES (p_admin_id, v_loan.member_id, v_group_id, 'loan_disbursement', jsonb_build_object('loan_id', p_loan_id, 'amount', v_amount));

    RETURN v_loan;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 7. Migration: Specialized Burial Society Claims (July 2026)
CREATE TABLE IF NOT EXISTS public.beneficiary_payout_claims (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    member_id         UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    beneficiary_id    UUID NOT NULL,
    beneficiary_name  TEXT NOT NULL,
    cause_of_death    TEXT NOT NULL,
    date_of_death     DATE NOT NULL,
    claim_amount      NUMERIC(12,2) NOT NULL CHECK (claim_amount > 0),
    bank_name         TEXT NOT NULL,
    account_no        TEXT NOT NULL,
    branch_code       TEXT NOT NULL,
    account_holder    TEXT NOT NULL,
    notes             TEXT,
    status            TEXT DEFAULT 'submitted' CHECK (status IN ('submitted', 'under_review', 'approved', 'paid', 'rejected', 'escalated')),
    reviewed_by       UUID REFERENCES auth.users(id),
    reviewed_at       TIMESTAMPTZ,
    admin_notes       TEXT,
    rejection_reason  TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 8. Migration: Relational Member Documents (July 2026)
CREATE TABLE IF NOT EXISTS public.member_documents (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id         UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    label             TEXT NOT NULL,
    document_url      TEXT NOT NULL,
    document_type     TEXT,
    status            TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'verified', 'rejected')),
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(member_id, label)
);

-- 9. Fix: Constitution Tracking in Groups
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS constitution_url TEXT;
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS constitution_status TEXT DEFAULT 'pending' CHECK (constitution_status IN ('pending', 'verified', 'rejected'));

-- 11. Trigger to log platform revenue
CREATE OR REPLACE FUNCTION public.log_platform_revenue()
RETURNS TRIGGER AS $$
DECLARE
    v_total_revenue NUMERIC;
BEGIN
    IF (NEW.status = 'paid' AND (OLD.status IS NULL OR OLD.status != 'paid')) THEN
        SELECT COALESCE(SUM(amount), 0) INTO v_total_revenue FROM public.platform_fees WHERE status = 'paid';

        INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category)
        VALUES (NEW.id, NEW.amount, v_total_revenue,
                NEW.fee_type || ' fee from group ' || (SELECT name FROM public.groups WHERE id = NEW.group_id),
                NEW.fee_type);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trigger_log_platform_revenue ON public.platform_fees;
CREATE TRIGGER trigger_log_platform_revenue
    AFTER UPDATE ON public.platform_fees
    FOR EACH ROW EXECUTE PROCEDURE public.log_platform_revenue();
