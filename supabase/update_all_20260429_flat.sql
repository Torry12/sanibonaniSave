-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — UNIVERSAL DATABASE REPAIR & RESEED (FLAT)
-- Version: 3.0 (Updated April 29, 2026)
-- ─────────────────────────────────────────────────────────────────────────────
-- This script combines:
-- 1. Full Schema Reset (schema.sql)
-- 2. Hardened RLS Policies (rls_policies.sql)
-- 3. Storage Bucket Config (storage_policies.sql)
-- 4. Scenario-Rich Mock Data (reset_with_mock_data.sql)
-- ─────────────────────────────────────────────────────────────────────────────

-- [INTEGRATED SCHEMA.SQL START]
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO public;
GRANT ALL ON SCHEMA public TO anon;
GRANT ALL ON SCHEMA public TO authenticated;
GRANT ALL ON SCHEMA public TO service_role;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE OR REPLACE FUNCTION public.update_updated_at_column() RETURNS TRIGGER AS $$ BEGIN NEW.updated_at = NOW(); RETURN NEW; END; $$ LANGUAGE plpgsql;

CREATE TABLE public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT, email TEXT, role TEXT DEFAULT 'member',
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL, type TEXT DEFAULT 'other',
    province TEXT, city TEXT, township TEXT, description TEXT, logo_emoji TEXT DEFAULT '🤝',
    joining_fee NUMERIC(10,2) DEFAULT 0, monthly_contribution NUMERIC(10,2) DEFAULT 0,
    late_fee NUMERIC(10,2) DEFAULT 0, late_fee_grace_days INTEGER DEFAULT 5,
    probation_months INTEGER DEFAULT 3, payment_due_day INTEGER DEFAULT 28,
    max_members INTEGER DEFAULT 50, current_members INTEGER DEFAULT 0,
    is_public BOOLEAN DEFAULT TRUE, allow_partial_payment BOOLEAN DEFAULT FALSE,
    auto_suspend_after INTEGER DEFAULT 2, bank_name TEXT, account_number TEXT,
    branch_code TEXT, account_type TEXT DEFAULT 'Savings', yoco_public_key TEXT,
    balance NUMERIC(12,2) DEFAULT 0, admin_user_id UUID REFERENCES auth.users(id) NOT NULL,
    fee_status TEXT DEFAULT 'due', registration_paid BOOLEAN DEFAULT FALSE,
    is_platform_suspended BOOLEAN DEFAULT FALSE, constitution_url TEXT,
    constitution_status TEXT DEFAULT 'pending', latitude FLOAT8, longitude FLOAT8, geohash TEXT,
    max_beneficiaries INTEGER DEFAULT 0, beneficiary_increase_pct NUMERIC(5,2) DEFAULT 0,
    goal_amount NUMERIC(12,2) DEFAULT 0, period_months INTEGER DEFAULT 12,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    name TEXT NOT NULL, description TEXT, required_amount NUMERIC(12,2) NOT NULL,
    status TEXT DEFAULT 'inactive', created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    user_id UUID REFERENCES auth.users(id),
    full_name TEXT NOT NULL, id_number TEXT, phone TEXT, email TEXT,
    street TEXT, suburb TEXT, city TEXT, province TEXT, notification_pref TEXT DEFAULT 'both',
    status TEXT DEFAULT 'probation', joined_at TIMESTAMPTZ DEFAULT NOW(), probation_end_at TIMESTAMPTZ,
    profile_photo_url TEXT, document_1_url TEXT, document_1_type TEXT, document_1_status TEXT DEFAULT 'pending',
    document_2_url TEXT, document_2_type TEXT, document_2_status TEXT DEFAULT 'pending',
    document_3_url TEXT, document_3_type TEXT, document_3_status TEXT DEFAULT 'pending',
    document_4_url TEXT, document_4_type TEXT, document_4_status TEXT DEFAULT 'pending',
    document_5_url TEXT, document_5_type TEXT, document_5_status TEXT DEFAULT 'pending',
    beneficiary_count INTEGER DEFAULT 0, beneficiary_over_65_count INTEGER DEFAULT 0,
    monthly_contribution_override NUMERIC(10,2), total_contributions INTEGER DEFAULT 0,
    total_paid NUMERIC(12,2) DEFAULT 0, fcm_token TEXT, member_key TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW(), UNIQUE(group_id, user_id)
);

CREATE TABLE public.beneficiaries (
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    member_id UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    id UUID DEFAULT gen_random_uuid(), full_name TEXT NOT NULL, id_number TEXT,
    relationship TEXT, date_of_birth DATE, is_over_65 BOOLEAN DEFAULT FALSE,
    document_url TEXT, document_status TEXT DEFAULT 'pending',
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW(), PRIMARY KEY (group_id, member_id, id)
);

CREATE TABLE public.contributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    policy_id UUID REFERENCES public.policies(id) ON DELETE SET NULL,
    amount NUMERIC(10,2) NOT NULL, type TEXT DEFAULT 'contribution',
    due_date DATE NOT NULL, paid_at TIMESTAMPTZ, payment_method TEXT DEFAULT 'yoco',
    yoco_transaction_id TEXT, receipt_url TEXT, status TEXT DEFAULT 'due',
    late_fees_applied BOOLEAN DEFAULT FALSE, created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id UUID REFERENCES public.members(id) ON DELETE CASCADE,
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount NUMERIC(12,2) NOT NULL, payment_type TEXT NOT NULL, payment_method TEXT NOT NULL,
    transaction_id TEXT, status TEXT DEFAULT 'pending', processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    member_id UUID REFERENCES public.members(id) ON DELETE CASCADE,
    message TEXT NOT NULL, channel TEXT DEFAULT 'both', trigger_event TEXT DEFAULT 'custom',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.member_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    label TEXT NOT NULL, document_url TEXT NOT NULL, document_type TEXT,
    status TEXT DEFAULT 'pending', created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(member_id, label)
);

CREATE TABLE public.group_actuarial_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    pure_premium NUMERIC(12,2), gross_premium NUMERIC(12,2), reserve_adequacy_pct NUMERIC(10,2),
    solvency_margin_pct NUMERIC(10,2), loss_ratio_pct NUMERIC(10,2), contribution_sufficiency_pct NUMERIC(10,2),
    break_even_members INTEGER, actuarial_present_value NUMERIC(15,2), funding_ratio_pct NUMERIC(10,2),
    payment_rate_pct NUMERIC(10,2), composite_risk_score INTEGER, insolvency_months INTEGER,
    expected_annual_claims NUMERIC(15,2), created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount NUMERIC(12,2) NOT NULL, bank_name TEXT NOT NULL, account_no TEXT NOT NULL,
    branch_code TEXT NOT NULL, status TEXT DEFAULT 'pending', processed_by UUID REFERENCES auth.users(id),
    processed_at TIMESTAMPTZ, yoco_payout_id TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.platform_fees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    fee_type TEXT NOT NULL, amount NUMERIC(10,2) NOT NULL, status TEXT DEFAULT 'due',
    due_date TEXT, paid_at TIMESTAMPTZ, transaction_id TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.platform_settings (
    key TEXT PRIMARY KEY, value NUMERIC NOT NULL, updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.loans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount NUMERIC(12,2) NOT NULL, interest_rate NUMERIC(5,2) DEFAULT 0,
    total_to_repay NUMERIC(12,2) NOT NULL, total_repaid NUMERIC(12,2) DEFAULT 0,
    monthly_repayment NUMERIC(12,2) NOT NULL, start_date DATE, end_date DATE,
    next_payment_date DATE, status TEXT DEFAULT 'pending', purpose TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.loan_repayments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id UUID REFERENCES public.loans(id) ON DELETE CASCADE NOT NULL,
    member_id UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount NUMERIC(12,2) NOT NULL, paid_at TIMESTAMPTZ DEFAULT NOW(),
    payment_method TEXT DEFAULT 'yoco', transaction_id TEXT, created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID NOT NULL, target_member_id UUID, target_group_id UUID,
    action TEXT NOT NULL, details JSONB, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_groups_admin_user_id ON public.groups(admin_user_id);
CREATE INDEX idx_members_group_id ON public.members(group_id);
CREATE INDEX idx_contributions_member_id ON public.contributions(member_id);
CREATE INDEX idx_contributions_group_id ON public.contributions(group_id);

-- Trigger functions
CREATE OR REPLACE FUNCTION public.handle_new_user() RETURNS TRIGGER AS $$ BEGIN INSERT INTO public.profiles (id, full_name, email, role) VALUES (NEW.id, COALESCE(NEW.raw_user_meta_data->>'full_name', ''), NEW.email, COALESCE(NEW.raw_user_meta_data->>'role', 'member')); RETURN NEW; END; $$ LANGUAGE plpgsql SECURITY DEFINER;
CREATE TRIGGER on_auth_user_created AFTER INSERT ON auth.users FOR EACH ROW EXECUTE PROCEDURE public.handle_new_user();

CREATE OR REPLACE FUNCTION public.record_contribution_v1(p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_due_date DATE, p_paid_at TIMESTAMPTZ, p_status TEXT, p_yoco_tx_id TEXT DEFAULT NULL, p_type TEXT DEFAULT 'contribution') RETURNS public.contributions AS $$
DECLARE v_contribution public.contributions;
BEGIN
    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, yoco_transaction_id, type, payment_method) VALUES (p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_yoco_tx_id, p_type, 'yoco') RETURNING * INTO v_contribution;
    UPDATE public.members SET total_contributions = COALESCE(total_contributions, 0) + 1, total_paid = COALESCE(total_paid, 0) + p_amount, updated_at = NOW() WHERE id = p_member_id;
    UPDATE public.groups SET balance = COALESCE(balance, 0) + p_amount, updated_at = NOW() WHERE id = p_group_id;
    RETURN v_contribution;
END; $$ LANGUAGE plpgsql SECURITY DEFINER;

-- Grants
GRANT ALL ON SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES IN SCHEMA public TO postgres, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO postgres, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.record_contribution_v1 TO authenticated, service_role;
-- [INTEGRATED SCHEMA.SQL END]

-- [INTEGRATED RLS_POLICIES.SQL START]
CREATE OR REPLACE FUNCTION public.is_platform_admin() RETURNS BOOLEAN AS $$ BEGIN RETURN EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'platform_admin'); END; $$ LANGUAGE plpgsql SECURITY DEFINER;
CREATE OR REPLACE FUNCTION public.is_group_admin(p_group_id UUID) RETURNS BOOLEAN AS $$ BEGIN RETURN EXISTS (SELECT 1 FROM public.groups WHERE id = p_group_id AND admin_user_id = auth.uid()); END; $$ LANGUAGE plpgsql SECURITY DEFINER;
CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id UUID) RETURNS BOOLEAN AS $$ BEGIN RETURN EXISTS (SELECT 1 FROM public.members WHERE group_id = p_group_id AND user_id = auth.uid()); END; $$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE POLICY "Platform admin manage everything" ON public.groups FOR ALL TO authenticated USING (public.is_platform_admin());
CREATE POLICY "Public discover groups" ON public.groups FOR SELECT TO anon, authenticated USING (is_public = true);
CREATE POLICY "View joined groups" ON public.groups FOR SELECT TO authenticated USING (admin_user_id = auth.uid() OR public.is_group_member(id));
CREATE POLICY "Create group" ON public.groups FOR INSERT TO authenticated WITH CHECK (admin_user_id = auth.uid());
CREATE POLICY "Admin update group" ON public.groups FOR UPDATE TO authenticated USING (admin_user_id = auth.uid());

CREATE POLICY "Platform admin members" ON public.members FOR ALL TO authenticated USING (public.is_platform_admin());
CREATE POLICY "Own membership" ON public.members FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY "Admin group members" ON public.members FOR SELECT TO authenticated USING (public.is_group_admin(group_id));

CREATE POLICY "Platform admin settings" ON public.platform_settings FOR ALL TO authenticated USING (public.is_platform_admin());
CREATE POLICY "Public view settings" ON public.platform_settings FOR SELECT TO anon, authenticated USING (true);
-- [INTEGRATED RLS_POLICIES.SQL END]

-- [INTEGRATED STORAGE_POLICIES.SQL START]
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types) VALUES ('avatars', 'avatars', true, 3145728, '{image/*}'), ('documents', 'documents', false, 3145728, '{image/*,application/pdf}'), ('constitutions', 'constitutions', true, 5242880, '{application/pdf}') ON CONFLICT (id) DO UPDATE SET public = EXCLUDED.public, file_size_limit = EXCLUDED.file_size_limit, allowed_mime_types = EXCLUDED.allowed_mime_types;
-- [INTEGRATED STORAGE_POLICIES.SQL END]

-- [INTEGRATED RESET_WITH_MOCK_DATA.SQL START]
DELETE FROM auth.users;
INSERT INTO auth.users (id, aud, role, email, encrypted_password, email_confirmed_at, raw_user_meta_data) VALUES
('1b8aca84-c136-4c1b-b024-902584ae80d8', 'authenticated', 'authenticated', 'torrymsimango@gmail.com', extensions.crypt('torry123M', extensions.gen_salt('bf')), NOW(), '{"full_name": "Torry Msimango", "role": "platform_admin"}'),
('a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'authenticated', 'authenticated', 'admin1@test.com', extensions.crypt('password123', extensions.gen_salt('bf')), NOW(), '{"full_name": "John Admin", "role": "group_admin"}'),
('a2a2a2a2-a2a2-a2a2-a2a2-a2a2a2a2a2a2', 'authenticated', 'authenticated', 'admin2@test.com', extensions.crypt('password123', extensions.gen_salt('bf')), NOW(), '{"full_name": "Jane Admin", "role": "group_admin"}'),
('b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1', 'authenticated', 'authenticated', 'member1@test.com', extensions.crypt('password123', extensions.gen_salt('bf')), NOW(), '{"full_name": "Sipho Nkosi", "role": "member"}'),
('b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2', 'authenticated', 'authenticated', 'member2@test.com', extensions.crypt('password123', extensions.gen_salt('bf')), NOW(), '{"full_name": "Thandi Dlamini", "role": "member"}');

INSERT INTO public.platform_settings (key, value) VALUES ('monthly_per_member', 10.0), ('registration_fee', 700.0) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

INSERT INTO public.groups (id, name, type, province, city, township, logo_emoji, joining_fee, monthly_contribution, admin_user_id, fee_status, registration_paid, balance) VALUES
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'Ubuntu Burial Society', 'burial_society', 'Gauteng', 'Johannesburg', 'Soweto', '🕊️', 250, 150, 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'paid', TRUE, 5000),
('c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 'Imali Yethu Stokvel', 'stokvel', 'KwaZulu-Natal', 'Durban', 'Umlazi', '💰', 100, 500, 'a2a2a2a2-a2a2-a2a2-a2a2-a2a2a2a2a2a2', 'paid', TRUE, 12000);

INSERT INTO public.members (id, group_id, user_id, full_name, status, total_contributions, total_paid) VALUES
('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d101', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'John Admin', 'active', 6, 900),
('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d102', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1', 'Sipho Nkosi', 'active', 4, 600),
('d2d2d2d2-d2d2-d2d2-d2d2-d2d2d2d2d201', 'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 'a2a2a2a2-a2a2-a2a2-a2a2-a2a2a2a2a2a2', 'Jane Admin', 'active', 8, 4000);

INSERT INTO public.loans (member_id, group_id, amount, total_to_repay, monthly_repayment, status, purpose) VALUES
('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d102', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 5000, 5250, 437.5, 'active', 'Education');

NOTIFY pgrst, 'reload schema';
-- [INTEGRATED RESET_WITH_MOCK_DATA.SQL END]
