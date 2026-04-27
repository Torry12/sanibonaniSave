-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — MASTER PERMISSION FIX & RLS POLICIES
-- Version: 2.1 (Updated April 19, 2026)
-- ─────────────────────────────────────────────────────────────────────────────
-- CHANGELOG v2.1:
-- - Added policies for group geolocation fields
-- - Updated contribution policies for registration_contribution type
-- - Enhanced platform admin access for all tables
-- ─────────────────────────────────────────────────────────────────────────────
-- Run this script in the Supabase SQL Editor to resolve "Permission Denied" errors.

-- 1. RESTORE SCHEMA & TABLE GRANTS (The "Permission Denied" Fix)
-- This allows the Supabase API to actually "see" the tables.
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES IN SCHEMA public TO postgres, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO postgres, service_role;

-- Grant standard access to API roles (Force re-application)
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO anon, authenticated;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.record_contribution_v1(UUID, UUID, NUMERIC, DATE, TIMESTAMPTZ, TEXT, TEXT, TEXT)
TO authenticated, service_role;

-- Ensure future tables also get these permissions automatically
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon, authenticated, service_role;

-- 2. SCHEMA SAFETY SYNC
-- Ensure groups table has all required columns for the "Safe Column" select
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='groups' AND column_name='is_platform_suspended') THEN
        ALTER TABLE public.groups ADD COLUMN is_platform_suspended BOOLEAN DEFAULT FALSE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='groups' AND column_name='registration_paid') THEN
        ALTER TABLE public.groups ADD COLUMN registration_paid BOOLEAN DEFAULT FALSE;
    END IF;
END $$;
CREATE TABLE IF NOT EXISTS public.beneficiaries (
    group_id             UUID NOT NULL,
    member_id            UUID NOT NULL,
    id                   UUID DEFAULT gen_random_uuid(),
    full_name            TEXT NOT NULL,
    id_number            TEXT,
    relationship         TEXT,
    date_of_birth        DATE,
    is_over_65           BOOLEAN DEFAULT FALSE,
    document_url         TEXT,
    document_status      TEXT DEFAULT 'pending' CHECK (document_status IN ('pending', 'verified', 'rejected')),
    created_at           TIMESTAMPTZ DEFAULT NOW(),
    updated_at           TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (group_id, member_id, id)
);

CREATE TABLE IF NOT EXISTS public.policies (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID NOT NULL,
    name              TEXT NOT NULL,
    description       TEXT,
    required_amount   NUMERIC(12,2) NOT NULL DEFAULT 0,
    status            TEXT DEFAULT 'inactive',
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 3. ENABLE RLS
ALTER TABLE IF EXISTS public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.members ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.beneficiaries ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.contributions ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.policies ENABLE ROW LEVEL SECURITY;

-- 4. HELPER FUNCTIONS
CREATE OR REPLACE FUNCTION public.policy_exists(p_name TEXT, p_table TEXT)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (SELECT 1 FROM pg_policies WHERE policyname = p_name AND tablename = p_table);
END;
$$ LANGUAGE plpgsql;

-- BREAK RECURSION: SECURITY DEFINER functions bypass RLS internally
CREATE OR REPLACE FUNCTION public.is_group_admin(p_group_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.groups
        WHERE id = p_group_id AND admin_user_id = auth.uid()
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.members
        WHERE group_id = p_group_id AND user_id = auth.uid()
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 5. APPLY SELECT POLICIES (Allowing data to flow to the UI)

-- GROUPS: Allow anyone to discover public groups
DO $$ BEGIN
    IF public.policy_exists('Discover Public Groups', 'groups') THEN
        -- Upgrade existing policy (common cause of "seeded data not showing" when app uses anon key)
        ALTER POLICY "Discover Public Groups" ON public.groups TO anon, authenticated;
        ALTER POLICY "Discover Public Groups" ON public.groups USING (is_public = true);
    ELSE
        CREATE POLICY "Discover Public Groups" ON public.groups
        FOR SELECT TO anon, authenticated USING (is_public = true);
    END IF;

    IF NOT public.policy_exists('View Joined Groups', 'groups') THEN
        CREATE POLICY "View Joined Groups" ON public.groups
        FOR SELECT TO authenticated
        USING (admin_user_id = auth.uid() OR public.is_group_member(id));
    END IF;

    IF NOT public.policy_exists('Allow Registration Insert', 'groups') THEN
        CREATE POLICY "Allow Registration Insert" ON public.groups
        FOR INSERT TO authenticated
        WITH CHECK (true);
    END IF;

    IF NOT public.policy_exists('Allow Registration Update', 'groups') THEN
        CREATE POLICY "Allow Registration Update" ON public.groups
        FOR UPDATE TO authenticated
        USING (admin_user_id = auth.uid())
        WITH CHECK (admin_user_id = auth.uid());
    END IF;

    IF NOT public.policy_exists('Service Role All Access', 'groups') THEN
        CREATE POLICY "Service Role All Access" ON public.groups FOR ALL TO service_role USING (true);
    END IF;
END $$;

-- ACTUARIAL METRICS: allow discovery UI to show metrics for public groups (read-only)
-- (Safe: only exposes aggregates; adjust/remove if you want metrics to be members-only.)
DO $$ BEGIN
    IF NOT public.policy_exists('Public View Actuarial Metrics', 'group_actuarial_metrics') THEN
        CREATE POLICY "Public View Actuarial Metrics" ON public.group_actuarial_metrics
        FOR SELECT TO anon, authenticated
        USING (group_id IN (SELECT id FROM public.groups WHERE is_public = true));
    END IF;
END $$;

-- MEMBERS: Allow users to see their own status and admins to see their group
DO $$ BEGIN
    IF NOT public.policy_exists('View Own Member Record', 'members') THEN
        CREATE POLICY "View Own Member Record" ON public.members
        FOR SELECT TO authenticated USING (user_id = auth.uid());
    END IF;

    IF NOT public.policy_exists('Admins View Group Members', 'members') THEN
        CREATE POLICY "Admins View Group Members" ON public.members
        FOR SELECT TO authenticated
        USING (public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Allow Member Registration', 'members') THEN
        CREATE POLICY "Allow Member Registration" ON public.members
        FOR INSERT TO authenticated
        WITH CHECK (true);
    END IF;

    IF NOT public.policy_exists('Allow Member Update', 'members') THEN
        CREATE POLICY "Allow Member Update" ON public.members
        FOR UPDATE TO authenticated
        USING (user_id = auth.uid() OR public.is_group_admin(group_id))
        WITH CHECK (user_id = auth.uid() OR public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Service Role Member Access', 'members') THEN
        CREATE POLICY "Service Role Member Access" ON public.members FOR ALL TO service_role USING (true);
    END IF;
END $$;

-- CONTRIBUTIONS: Essential for Dashboard
DO $$ BEGIN
    IF NOT public.policy_exists('Members View Own Contributions', 'contributions') THEN
        CREATE POLICY "Members View Own Contributions" ON public.contributions
        FOR SELECT TO authenticated
        USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));
    END IF;

    IF NOT public.policy_exists('Admins View Group Contributions', 'contributions') THEN
        CREATE POLICY "Admins View Group Contributions" ON public.contributions
        FOR SELECT TO authenticated
        USING (public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Service Role Contribution Access', 'contributions') THEN
        CREATE POLICY "Service Role Contribution Access" ON public.contributions FOR ALL TO service_role USING (true);
    END IF;
END $$;

-- PROFILES: Essential for role-based UI
DO $$ BEGIN
    IF NOT public.policy_exists('View Own Profile', 'profiles') THEN
        CREATE POLICY "View Own Profile" ON public.profiles
        FOR SELECT TO authenticated USING (auth.uid() = id);
    END IF;

    IF NOT public.policy_exists('Allow Profile Insert', 'profiles') THEN
        CREATE POLICY "Allow Profile Insert" ON public.profiles
        FOR INSERT TO authenticated
        WITH CHECK (auth.uid() = id);
    END IF;

    IF NOT public.policy_exists('Allow Profile Update', 'profiles') THEN
        CREATE POLICY "Allow Profile Update" ON public.profiles
        FOR UPDATE TO authenticated
        USING (auth.uid() = id)
        WITH CHECK (auth.uid() = id);
    END IF;

    IF NOT public.policy_exists('Service Role Profile Access', 'profiles') THEN
        CREATE POLICY "Service Role Profile Access" ON public.profiles FOR ALL TO service_role USING (true);
    END IF;
END $$;

-- 6. ENSURE ADMIN ACCESS (Platform Admins can see everything)
DO $$ BEGIN
    IF NOT public.policy_exists('Platform Admin All Access', 'groups') THEN
        CREATE POLICY "Platform Admin All Access" ON public.groups FOR ALL TO authenticated
        USING (EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'platform_admin'));
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. MEMBER DOCUMENTS POLICIES
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE IF EXISTS public.member_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.payouts ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.notifications ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
    IF NOT public.policy_exists('Members View Own Documents', 'member_documents') THEN
        CREATE POLICY "Members View Own Documents" ON public.member_documents
        FOR SELECT TO authenticated
        USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));
    END IF;

    IF NOT public.policy_exists('Admins View Group Documents', 'member_documents') THEN
        CREATE POLICY "Admins View Group Documents" ON public.member_documents
        FOR SELECT TO authenticated
        USING (public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Allow Document Insert', 'member_documents') THEN
        CREATE POLICY "Allow Document Insert" ON public.member_documents
        FOR INSERT TO authenticated
        WITH CHECK (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()) OR public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Allow Document Update', 'member_documents') THEN
        CREATE POLICY "Allow Document Update" ON public.member_documents
        FOR UPDATE TO authenticated
        USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()) OR public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Service Role Document Access', 'member_documents') THEN
        CREATE POLICY "Service Role Document Access" ON public.member_documents FOR ALL TO service_role USING (true);
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. PAYOUT POLICIES
-- ─────────────────────────────────────────────────────────────────────────────
DO $$ BEGIN
    IF NOT public.policy_exists('Admins View Group Payouts', 'payouts') THEN
        CREATE POLICY "Admins View Group Payouts" ON public.payouts
        FOR SELECT TO authenticated
        USING (public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Platform Admin Payout Access', 'payouts') THEN
        CREATE POLICY "Platform Admin Payout Access" ON public.payouts
        FOR ALL TO authenticated
        USING (EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'platform_admin'));
    END IF;

    IF NOT public.policy_exists('Allow Payout Request', 'payouts') THEN
        CREATE POLICY "Allow Payout Request" ON public.payouts
        FOR INSERT TO authenticated
        WITH CHECK (public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Allow Payout Update', 'payouts') THEN
        CREATE POLICY "Allow Payout Update" ON public.payouts
        FOR UPDATE TO authenticated
        USING (public.is_group_admin(group_id) OR EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'platform_admin'));
    END IF;

    IF NOT public.policy_exists('Service Role Payout Access', 'payouts') THEN
        CREATE POLICY "Service Role Payout Access" ON public.payouts FOR ALL TO service_role USING (true);
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. NOTIFICATION POLICIES
-- ─────────────────────────────────────────────────────────────────────────────
DO $$ BEGIN
    IF NOT public.policy_exists('Members View Group Notifications', 'notifications') THEN
        CREATE POLICY "Members View Group Notifications" ON public.notifications
        FOR SELECT TO authenticated
        USING (
            group_id IN (SELECT group_id FROM public.members WHERE user_id = auth.uid())
            OR group_id IN (SELECT id FROM public.groups WHERE admin_user_id = auth.uid())
        );
    END IF;

    IF NOT public.policy_exists('Allow Notification Insert', 'notifications') THEN
        CREATE POLICY "Allow Notification Insert" ON public.notifications
        FOR INSERT TO authenticated
        WITH CHECK (true);
    END IF;

    IF NOT public.policy_exists('Service Role Notification Access', 'notifications') THEN
        CREATE POLICY "Service Role Notification Access" ON public.notifications FOR ALL TO service_role USING (true);
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 10. BENEFICIARY POLICIES
-- ─────────────────────────────────────────────────────────────────────────────
DO $$ BEGIN
    IF NOT public.policy_exists('Members View Own Beneficiaries', 'beneficiaries') THEN
        CREATE POLICY "Members View Own Beneficiaries" ON public.beneficiaries
        FOR SELECT TO authenticated
        USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));
    END IF;

    IF NOT public.policy_exists('Admins View Group Beneficiaries', 'beneficiaries') THEN
        CREATE POLICY "Admins View Group Beneficiaries" ON public.beneficiaries
        FOR SELECT TO authenticated
        USING (public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Allow Beneficiary Insert', 'beneficiaries') THEN
        CREATE POLICY "Allow Beneficiary Insert" ON public.beneficiaries
        FOR INSERT TO authenticated
        WITH CHECK (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()) OR public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Allow Beneficiary Update', 'beneficiaries') THEN
        CREATE POLICY "Allow Beneficiary Update" ON public.beneficiaries
        FOR UPDATE TO authenticated
        USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()) OR public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Allow Beneficiary Delete', 'beneficiaries') THEN
        CREATE POLICY "Allow Beneficiary Delete" ON public.beneficiaries
        FOR DELETE TO authenticated
        USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()) OR public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Service Role Beneficiary Access', 'beneficiaries') THEN
        CREATE POLICY "Service Role Beneficiary Access" ON public.beneficiaries FOR ALL TO service_role USING (true);
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 11. PAYMENT POLICIES
-- ─────────────────────────────────────────────────────────────────────────────
DO $$ BEGIN
    IF NOT public.policy_exists('Members View Own Payments', 'payments') THEN
        CREATE POLICY "Members View Own Payments" ON public.payments
        FOR SELECT TO authenticated
        USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));
    END IF;

    IF NOT public.policy_exists('Admins View Group Payments', 'payments') THEN
        CREATE POLICY "Admins View Group Payments" ON public.payments
        FOR SELECT TO authenticated
        USING (public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Allow Payment Insert', 'payments') THEN
        CREATE POLICY "Allow Payment Insert" ON public.payments
        FOR INSERT TO authenticated
        WITH CHECK (true);
    END IF;

    IF NOT public.policy_exists('Service Role Payment Access', 'payments') THEN
        CREATE POLICY "Service Role Payment Access" ON public.payments FOR ALL TO service_role USING (true);
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 12. CONTRIBUTION POLICIES (Insert)
-- ─────────────────────────────────────────────────────────────────────────────
DO $$ BEGIN
    IF NOT public.policy_exists('Allow Contribution Insert', 'contributions') THEN
        CREATE POLICY "Allow Contribution Insert" ON public.contributions
        FOR INSERT TO authenticated
        WITH CHECK (true);
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 13. ACTUARIAL METRICS POLICIES
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE IF EXISTS public.group_actuarial_metrics ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
    IF NOT public.policy_exists('Admins View Actuarial Metrics', 'group_actuarial_metrics') THEN
        CREATE POLICY "Admins View Actuarial Metrics" ON public.group_actuarial_metrics
        FOR SELECT TO authenticated
        USING (public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Platform Admin Actuarial Access', 'group_actuarial_metrics') THEN
        CREATE POLICY "Platform Admin Actuarial Access" ON public.group_actuarial_metrics
        FOR ALL TO authenticated
        USING (EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'platform_admin'));
    END IF;

    IF NOT public.policy_exists('Allow Actuarial Insert', 'group_actuarial_metrics') THEN
        CREATE POLICY "Allow Actuarial Insert" ON public.group_actuarial_metrics
        FOR INSERT TO authenticated
        WITH CHECK (public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Service Role Actuarial Access', 'group_actuarial_metrics') THEN
        CREATE POLICY "Service Role Actuarial Access" ON public.group_actuarial_metrics FOR ALL TO service_role USING (true);
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 14. POLICIES TABLE POLICIES (Insurance Policies, not RLS)
-- ─────────────────────────────────────────────────────────────────────────────
DO $$ BEGIN
    IF NOT public.policy_exists('Admins View Group Policies', 'policies') THEN
        CREATE POLICY "Admins View Group Policies" ON public.policies
        FOR SELECT TO authenticated
        USING (public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Members View Group Policies', 'policies') THEN
        CREATE POLICY "Members View Group Policies" ON public.policies
        FOR SELECT TO authenticated
        USING (public.is_group_member(group_id));
    END IF;

    IF NOT public.policy_exists('Allow Policy Insert', 'policies') THEN
        CREATE POLICY "Allow Policy Insert" ON public.policies
        FOR INSERT TO authenticated
        WITH CHECK (public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Allow Policy Update', 'policies') THEN
        CREATE POLICY "Allow Policy Update" ON public.policies
        FOR UPDATE TO authenticated
        USING (public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Service Role Policy Access', 'policies') THEN
        CREATE POLICY "Service Role Policy Access" ON public.policies FOR ALL TO service_role USING (true);
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 15. PLATFORM FEES POLICIES
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE IF EXISTS public.platform_fees ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
    IF NOT public.policy_exists('Admins View Group Platform Fees', 'platform_fees') THEN
        CREATE POLICY "Admins View Group Platform Fees" ON public.platform_fees
        FOR SELECT TO authenticated
        USING (public.is_group_admin(group_id));
    END IF;

    IF NOT public.policy_exists('Platform Admin Platform Fee Access', 'platform_fees') THEN
        CREATE POLICY "Platform Admin Platform Fee Access" ON public.platform_fees
        FOR ALL TO authenticated
        USING (EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'platform_admin'));
    END IF;

    IF NOT public.policy_exists('Allow Platform Fee Insert', 'platform_fees') THEN
        CREATE POLICY "Allow Platform Fee Insert" ON public.platform_fees
        FOR INSERT TO authenticated
        WITH CHECK (true);
    END IF;

    IF NOT public.policy_exists('Allow Platform Fee Update', 'platform_fees') THEN
        CREATE POLICY "Allow Platform Fee Update" ON public.platform_fees
        FOR UPDATE TO authenticated
        USING (public.is_group_admin(group_id) OR EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'platform_admin'));
    END IF;

    IF NOT public.policy_exists('Service Role Platform Fee Access', 'platform_fees') THEN
        CREATE POLICY "Service Role Platform Fee Access" ON public.platform_fees FOR ALL TO service_role USING (true);
    END IF;
END $$;
