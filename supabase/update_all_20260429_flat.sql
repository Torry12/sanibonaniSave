-- update_all_20260429_flat.sql
-- SanibonaniSave: Consolidated database update script (April 29, 2026)
-- This script inlines all recent migrations, fixes, and policy updates.
-- Safe to run multiple times (idempotent). Run in Supabase SQL Editor.

-- 1. Create loans and loan_repayments tables
-- ------------------------------------------------------------
-- Migration: Create loans and loan_repayments tables
-- Version: 1.0

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

CREATE INDEX IF NOT EXISTS idx_loans_member_id ON public.loans(member_id);
CREATE INDEX IF NOT EXISTS idx_loans_group_id ON public.loans(group_id);
CREATE INDEX IF NOT EXISTS idx_loans_status ON public.loans(status);
CREATE INDEX IF NOT EXISTS idx_loan_repayments_loan_id ON public.loan_repayments(loan_id);

CREATE TRIGGER trigger_update_loans_updated_at
BEFORE UPDATE ON public.loans
FOR EACH ROW EXECUTE PROCEDURE public.update_updated_at_column();

GRANT SELECT, INSERT, UPDATE, DELETE ON public.loans TO anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.loan_repayments TO anon, authenticated;
GRANT ALL ON public.loans TO service_role;
GRANT ALL ON public.loan_repayments TO service_role;

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

-- 2. Add login audit and 2FA columns to auth.users
-- ------------------------------------------------------------
-- The following section is commented out because Supabase SQL Editor does not have permission to alter auth.users.
-- If you have superuser access, you may uncomment and run these lines in a psql session as the database owner.
/*
ALTER TABLE auth.users
ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS login_attempts INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS two_factor_secret TEXT;

CREATE INDEX IF NOT EXISTS idx_auth_users_role ON auth.users(role);
*/

-- 3. Add receipt_url to contributions table
-- ------------------------------------------------------------
ALTER TABLE public.contributions
ADD COLUMN IF NOT EXISTS receipt_url TEXT;

-- 4. Fix record_contribution_v1 function and grants
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.record_contribution_v1(
    p_member_id UUID,
    p_group_id UUID,
    p_amount NUMERIC,
    p_due_date DATE,
    p_paid_at TIMESTAMPTZ,
    p_status TEXT,
    p_yoco_tx_id TEXT DEFAULT NULL,
    p_type TEXT DEFAULT 'contribution'
) RETURNS public.contributions AS $$
DECLARE
    v_contribution public.contributions;
BEGIN
    -- IMPORTANT: p_type maps to contributions.type, not payment_method.
    INSERT INTO public.contributions (
        member_id,
        group_id,
        amount,
        due_date,
        paid_at,
        status,
        yoco_transaction_id,
        type,
        payment_method
    ) VALUES (
        p_member_id,
        p_group_id,
        p_amount,
        p_due_date,
        p_paid_at,
        p_status,
        p_yoco_tx_id,
        p_type,
        'yoco'
    ) RETURNING * INTO v_contribution;

    UPDATE public.members
    SET total_contributions = COALESCE(total_contributions, 0) + 1,
        total_paid = COALESCE(total_paid, 0) + p_amount,
        updated_at = NOW()
    WHERE id = p_member_id;

    UPDATE public.groups
    SET balance = COALESCE(balance, 0) + p_amount,
        updated_at = NOW()
    WHERE id = p_group_id;

    RETURN v_contribution;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION public.record_contribution_v1(UUID, UUID, NUMERIC, DATE, TIMESTAMPTZ, TEXT, TEXT, TEXT)
TO authenticated, service_role;

-- 5. Fix typo in constitutions bucket policy
-- ------------------------------------------------------------
DROP POLICY IF EXISTS "Admins can upload constitutions" ON storage.objects;

CREATE POLICY "Admins can upload constitutions"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'constitutions' AND
    EXISTS (
        SELECT 1 FROM groups g
        WHERE g.admin_user_id = auth.uid()
        AND g.id::text = (storage.foldername(name))[1]
    )
);

-- 6. Restore table grants (permission denied fix)
-- ------------------------------------------------------------
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES IN SCHEMA public TO postgres, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO postgres, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO anon, authenticated;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO service_role;
SELECT
  has_schema_privilege('anon', 'public', 'usage') AS anon_schema_usage,
  has_table_privilege('anon', 'public.groups', 'select') AS anon_groups_select,
  has_table_privilege('authenticated', 'public.groups', 'select') AS auth_groups_select;

-- 7. Quick fix for permission denied and public discovery
-- ------------------------------------------------------------
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES IN SCHEMA public TO postgres, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO postgres, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO anon, authenticated;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO service_role;
CREATE TABLE IF NOT EXISTS public.platform_settings (
    key TEXT PRIMARY KEY,
    value NUMERIC NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
INSERT INTO public.platform_settings (key, value)
VALUES
    ('monthly_per_member', 10.0),
    ('registration_fee', 700.0)
ON CONFLICT (key) DO NOTHING;
ALTER TABLE IF EXISTS public.groups ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public'
          AND tablename  = 'groups'
          AND policyname = 'Discover Public Groups'
    ) THEN
        ALTER POLICY "Discover Public Groups" ON public.groups TO anon, authenticated;
        ALTER POLICY "Discover Public Groups" ON public.groups USING (is_public = true);
    ELSE
        CREATE POLICY "Discover Public Groups" ON public.groups
        FOR SELECT TO anon, authenticated
        USING (is_public = true);
    END IF;
END $$;
NOTIFY pgrst, 'reload schema';

-- End of update script

