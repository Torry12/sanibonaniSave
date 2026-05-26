-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — MASTER RLS POLICIES & PERMISSIONS
-- Version: 4.0 (Consolidated May 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. ENABLE RLS ON ALL TABLES
DO $$
DECLARE
    v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', v_table);
    END LOOP;
END $$;

-- 2. HELPER FUNCTIONS
CREATE OR REPLACE FUNCTION public.is_platform_admin()
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
    RETURN
        COALESCE((auth.jwt() -> 'app_metadata' ->> 'role') = 'platform_admin', false)
        OR COALESCE((auth.jwt() -> 'user_metadata' ->> 'role') = 'platform_admin', false)
        OR EXISTS (
            SELECT 1
            FROM public.profiles p
            WHERE p.id = auth.uid()
              AND p.role = 'platform_admin'
        );
END;
$$;

GRANT EXECUTE ON FUNCTION public.is_platform_admin() TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.is_group_admin(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
STABLE
AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.groups
        WHERE id = p_group_id AND admin_user_id = auth.uid()
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
STABLE
AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.members
        WHERE group_id = p_group_id AND user_id = auth.uid()
    );
END;
$$;

-- 3. TABLE GRANTS
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO anon, authenticated;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO anon, authenticated;

-- 4. CORE RLS POLICIES

-- Profiles
DROP POLICY IF EXISTS "Profiles: View own" ON public.profiles;
DROP POLICY IF EXISTS "Profiles: Update own" ON public.profiles;
CREATE POLICY "Profiles: View own" ON public.profiles FOR SELECT TO authenticated USING (auth.uid() = id);
CREATE POLICY "Profiles: Update own" ON public.profiles FOR UPDATE TO authenticated USING (auth.uid() = id);

-- Groups
DROP POLICY IF EXISTS "Groups: Discover public" ON public.groups;
DROP POLICY IF EXISTS "Groups: View joined" ON public.groups;
DROP POLICY IF EXISTS "Groups: Create" ON public.groups;
CREATE POLICY "Groups: Discover public" ON public.groups FOR SELECT TO anon, authenticated USING (is_public = true);
CREATE POLICY "Groups: View joined" ON public.groups FOR SELECT TO authenticated USING (admin_user_id = auth.uid() OR public.is_group_member(id));
CREATE POLICY "Groups: Create" ON public.groups FOR INSERT TO authenticated WITH CHECK (admin_user_id = auth.uid());

-- Members
DROP POLICY IF EXISTS "Members: View own" ON public.members;
DROP POLICY IF EXISTS "Members: Admin view group" ON public.members;
DROP POLICY IF EXISTS "Members: Group members view each other" ON public.members;
CREATE POLICY "Members: View own" ON public.members FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY "Members: Admin view group" ON public.members FOR SELECT TO authenticated USING (public.is_group_admin(group_id));
CREATE POLICY "Members: Group members view each other" ON public.members FOR SELECT TO authenticated USING (public.is_group_member(group_id));

-- Member Documents
DROP POLICY IF EXISTS "Documents: Member view own" ON public.member_documents;
DROP POLICY IF EXISTS "Documents: Admin view group" ON public.member_documents;
DROP POLICY IF EXISTS "Documents: Member upload own" ON public.member_documents;
CREATE POLICY "Documents: Member view own" ON public.member_documents FOR SELECT TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));
CREATE POLICY "Documents: Admin view group" ON public.member_documents FOR SELECT TO authenticated USING (public.is_group_admin(group_id));
CREATE POLICY "Documents: Member upload own" ON public.member_documents FOR INSERT TO authenticated WITH CHECK (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

-- Beneficiary Payout Claims
DROP POLICY IF EXISTS "Claims: Member view own" ON public.beneficiary_payout_claims;
DROP POLICY IF EXISTS "Claims: Admin view group" ON public.beneficiary_payout_claims;
DROP POLICY IF EXISTS "Claims: Member submit own" ON public.beneficiary_payout_claims;
CREATE POLICY "Claims: Member view own" ON public.beneficiary_payout_claims FOR SELECT TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));
CREATE POLICY "Claims: Admin view group" ON public.beneficiary_payout_claims FOR SELECT TO authenticated USING (public.is_group_admin(group_id));
CREATE POLICY "Claims: Member submit own" ON public.beneficiary_payout_claims FOR INSERT TO authenticated WITH CHECK (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

-- Beneficiaries
DROP POLICY IF EXISTS "Beneficiaries: Member view own" ON public.beneficiaries;
CREATE POLICY "Beneficiaries: Member view own" ON public.beneficiaries
FOR SELECT TO authenticated
USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

DROP POLICY IF EXISTS "Beneficiaries: Admin view group" ON public.beneficiaries;
CREATE POLICY "Beneficiaries: Admin view group" ON public.beneficiaries
FOR SELECT TO authenticated
USING (public.is_group_admin(group_id));

DROP POLICY IF EXISTS "Beneficiaries: Member manage own" ON public.beneficiaries;
CREATE POLICY "Beneficiaries: Member manage own" ON public.beneficiaries
FOR ALL TO authenticated
USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()))
WITH CHECK (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

DROP POLICY IF EXISTS "Beneficiaries: Admin manage group" ON public.beneficiaries;
CREATE POLICY "Beneficiaries: Admin manage group" ON public.beneficiaries
FOR ALL TO authenticated
USING (public.is_group_admin(group_id))
WITH CHECK (public.is_group_admin(group_id));

-- Contributions
DROP POLICY IF EXISTS "Contributions: Member view own" ON public.contributions;
CREATE POLICY "Contributions: Member view own" ON public.contributions
FOR SELECT TO authenticated
USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

DROP POLICY IF EXISTS "Contributions: Admin view group" ON public.contributions;
CREATE POLICY "Contributions: Admin view group" ON public.contributions
FOR SELECT TO authenticated
USING (public.is_group_admin(group_id));

DROP POLICY IF EXISTS "Contributions: Group members view group" ON public.contributions;
CREATE POLICY "Contributions: Group members view group" ON public.contributions
FOR SELECT TO authenticated
USING (public.is_group_member(group_id));

-- Payments
DROP POLICY IF EXISTS "Payments: Member view own" ON public.payments;
CREATE POLICY "Payments: Member view own" ON public.payments
FOR SELECT TO authenticated
USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

DROP POLICY IF EXISTS "Payments: Admin view group" ON public.payments;
CREATE POLICY "Payments: Admin view group" ON public.payments
FOR SELECT TO authenticated
USING (public.is_group_admin(group_id));

-- Loans & Repayments
DROP POLICY IF EXISTS "Loans: Member view own" ON public.loans;
CREATE POLICY "Loans: Member view own" ON public.loans
FOR SELECT TO authenticated
USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

DROP POLICY IF EXISTS "Loans: Admin view group" ON public.loans;
CREATE POLICY "Loans: Admin view group" ON public.loans
FOR SELECT TO authenticated
USING (public.is_group_admin(group_id));

DROP POLICY IF EXISTS "Loans: Member request" ON public.loans;
CREATE POLICY "Loans: Member request" ON public.loans
FOR INSERT TO authenticated
WITH CHECK (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

DROP POLICY IF EXISTS "Loan Repayments: Member view own" ON public.loan_repayments;
CREATE POLICY "Loan Repayments: Member view own" ON public.loan_repayments
FOR SELECT TO authenticated
USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

DROP POLICY IF EXISTS "Loan Repayments: Admin view group" ON public.loan_repayments;
CREATE POLICY "Loan Repayments: Admin view group" ON public.loan_repayments
FOR SELECT TO authenticated
USING (public.is_group_admin(group_id));

-- Notifications
DROP POLICY IF EXISTS "Notifications: Member view" ON public.notifications;
CREATE POLICY "Notifications: Member view" ON public.notifications
FOR SELECT TO authenticated
USING (
    member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid())
    OR (member_id IS NULL AND public.is_group_member(group_id))
);

DROP POLICY IF EXISTS "Notifications: Admin manage" ON public.notifications;
CREATE POLICY "Notifications: Admin manage" ON public.notifications
FOR ALL TO authenticated
USING (public.is_group_admin(group_id))
WITH CHECK (public.is_group_admin(group_id));

-- Payouts
DROP POLICY IF EXISTS "Payouts: Admin manage" ON public.payouts;
CREATE POLICY "Payouts: Admin manage" ON public.payouts
FOR ALL TO authenticated
USING (public.is_group_admin(group_id))
WITH CHECK (public.is_group_admin(group_id));

-- Group Ledger
DROP POLICY IF EXISTS "Ledger: Admin view group" ON public.group_ledger;
CREATE POLICY "Ledger: Admin view group" ON public.group_ledger
FOR SELECT TO authenticated
USING (public.is_group_admin(group_id));

-- Audit Logs
DROP POLICY IF EXISTS "Audit Logs: Admin view group" ON public.audit_logs;
CREATE POLICY "Audit Logs: Admin view group" ON public.audit_logs
FOR SELECT TO authenticated
USING (public.is_group_admin(target_group_id));

-- Group Actuarial Metrics
DROP POLICY IF EXISTS "Metrics: Admin view group" ON public.group_actuarial_metrics;
CREATE POLICY "Metrics: Admin view group" ON public.group_actuarial_metrics
FOR SELECT TO authenticated
USING (public.is_group_admin(group_id));

-- Group Level Policies
DROP POLICY IF EXISTS "Policies: View" ON public.policies;
CREATE POLICY "Policies: View" ON public.policies
FOR SELECT TO authenticated
USING (public.is_group_member(group_id) OR public.is_group_admin(group_id));

DROP POLICY IF EXISTS "Policies: Admin manage" ON public.policies;
CREATE POLICY "Policies: Admin manage" ON public.policies
FOR ALL TO authenticated
USING (public.is_group_admin(group_id))
WITH CHECK (public.is_group_admin(group_id));

-- 5. UNIVERSAL PLATFORM ADMIN BYPASS
DO $$
DECLARE v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Platform Admin Bypass" ON public.%I', v_table);
        EXECUTE format('CREATE POLICY "Platform Admin Bypass" ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin())', v_table);
    END LOOP;
END $$;

-- 6. STORAGE BUCKETS & POLICIES
INSERT INTO storage.buckets (id, name, public)
VALUES
    ('avatars', 'avatars', true),
    ('documents', 'documents', false),
    ('constitutions', 'constitutions', false),
    ('loan_contracts', 'loan_contracts', false)
ON CONFLICT DO NOTHING;

DROP POLICY IF EXISTS "Storage: Platform Admin Full Access" ON storage.objects;
CREATE POLICY "Storage: Platform Admin Full Access" ON storage.objects FOR ALL TO authenticated USING (public.is_platform_admin());

-- Documents Bucket (Members, Beneficiaries)
DROP POLICY IF EXISTS "Storage: Member Upload Docs" ON storage.objects;
DROP POLICY IF EXISTS "Storage: Member View Own Docs" ON storage.objects;
DROP POLICY IF EXISTS "Storage: Admin View Group Docs" ON storage.objects;
CREATE POLICY "Storage: Member Upload Docs" ON storage.objects FOR INSERT TO authenticated WITH CHECK (bucket_id = 'documents' AND (storage.foldername(name))[1] = 'members');
CREATE POLICY "Storage: Member View Own Docs" ON storage.objects FOR SELECT TO authenticated USING (bucket_id = 'documents' AND (storage.foldername(name))[1] = 'members');
CREATE POLICY "Storage: Admin View Group Docs" ON storage.objects FOR SELECT TO authenticated USING (bucket_id = 'documents');

-- Beneficiary Documents Folder
DROP POLICY IF EXISTS "Storage: Member Upload Beneficiary Docs" ON storage.objects;
DROP POLICY IF EXISTS "Storage: Member View Beneficiary Docs" ON storage.objects;
CREATE POLICY "Storage: Member Upload Beneficiary Docs" ON storage.objects FOR INSERT TO authenticated WITH CHECK (bucket_id = 'documents' AND (storage.foldername(name))[1] = 'beneficiaries');
CREATE POLICY "Storage: Member View Beneficiary Docs" ON storage.objects FOR SELECT TO authenticated USING (bucket_id = 'documents' AND (storage.foldername(name))[1] = 'beneficiaries');

-- Constitutions Bucket
DROP POLICY IF EXISTS "Storage: Admin Manage Group Constitution" ON storage.objects;
DROP POLICY IF EXISTS "Storage: Member View Group Constitution" ON storage.objects;
CREATE POLICY "Storage: Admin Manage Group Constitution" ON storage.objects FOR ALL TO authenticated USING (bucket_id = 'constitutions' AND public.is_group_admin((storage.foldername(name))[1]::uuid));
CREATE POLICY "Storage: Member View Group Constitution" ON storage.objects FOR SELECT TO authenticated USING (bucket_id = 'constitutions' AND public.is_group_member((storage.foldername(name))[1]::uuid));

-- Loan Contracts Bucket
DROP POLICY IF EXISTS "Storage: Admin Manage Loan Contracts" ON storage.objects;
DROP POLICY IF EXISTS "Storage: Member View Loan Contract" ON storage.objects;
CREATE POLICY "Storage: Admin Manage Loan Contracts" ON storage.objects FOR ALL TO authenticated USING (bucket_id = 'loan_contracts' AND public.is_group_admin((storage.foldername(name))[1]::uuid));
CREATE POLICY "Storage: Member View Loan Contract" ON storage.objects FOR SELECT TO authenticated USING (bucket_id = 'loan_contracts' AND public.is_group_member((storage.foldername(name))[1]::uuid));

