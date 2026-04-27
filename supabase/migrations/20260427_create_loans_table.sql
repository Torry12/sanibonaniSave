-- Migration: Create loans and loan_repayments tables
-- Version: 1.0

-- 1. LOANS TABLE
CREATE TABLE IF NOT EXISTS public.loans (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id         UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount            NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    interest_rate     NUMERIC(5,2) DEFAULT 0 CHECK (interest_rate >= 0),
    total_to_repay    NUMERIC(12,2) NOT NULL CHECK (total_to_repay >= amount),
    total_repaid      NUMERIC(12,2) DEFAULT 0 CHECK (total_repaid >= 0),
    monthly_repayment NUMERIC(12,2) NOT NULL CHECK (monthly_repayment > 0),
    start_date        DATE,
    end_date          DATE,
    next_payment_date DATE,
    status            TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'active', 'partially_paid', 'completed', 'rejected', 'overdue')),
    purpose           TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 2. LOAN REPAYMENTS TABLE
CREATE TABLE IF NOT EXISTS public.loan_repayments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id           UUID REFERENCES public.loans(id) ON DELETE CASCADE NOT NULL,
    member_id         UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount            NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    paid_at           TIMESTAMPTZ DEFAULT NOW(),
    payment_method    TEXT DEFAULT 'yoco',
    transaction_id    TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 3. INDEXES
CREATE INDEX IF NOT EXISTS idx_loans_member_id ON public.loans(member_id);
CREATE INDEX IF NOT EXISTS idx_loans_group_id ON public.loans(group_id);
CREATE INDEX IF NOT EXISTS idx_loans_status ON public.loans(status);
CREATE INDEX IF NOT EXISTS idx_loan_repayments_loan_id ON public.loan_repayments(loan_id);

-- 4. TRIGGER FOR UPDATING updated_at
CREATE TRIGGER trigger_update_loans_updated_at
BEFORE UPDATE ON public.loans
FOR EACH ROW EXECUTE PROCEDURE public.update_updated_at_column();

-- 5. GRANTS
GRANT SELECT, INSERT, UPDATE, DELETE ON public.loans TO anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.loan_repayments TO anon, authenticated;
GRANT ALL ON public.loans TO service_role;
GRANT ALL ON public.loan_repayments TO service_role;

-- 6. RLS POLICIES (Basic)
ALTER TABLE public.loans ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.loan_repayments ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Members can view their own loans" ON public.loans
FOR SELECT USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

CREATE POLICY "Admins can view group loans" ON public.loans
FOR SELECT USING (group_id IN (SELECT id FROM public.groups WHERE admin_user_id = auth.uid()));

CREATE POLICY "Members can view their own repayments" ON public.loan_repayments
FOR SELECT USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

CREATE POLICY "Admins can view group repayments" ON public.loan_repayments
FOR SELECT USING (group_id IN (SELECT id FROM public.groups WHERE admin_user_id = auth.uid()));
