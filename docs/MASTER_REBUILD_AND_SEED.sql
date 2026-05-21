-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — MASTER DATABASE REBUILD & COMPREHENSIVE SEED
-- Version: 25.0 (Consolidated, Robust & Tested)
-- Date: 2026-05-18
--
-- PURPOSE:
--   1. Clean reset of the public schema.
--   2. Re-create all tables, triggers, and RPC functions.
--   3. Configure master RLS security policies.
--   4. Setup Platform Admin (torrymsimango@gmail.com).
--   5. Seed 8 groups with 64 members and financial history.
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

-- 1. CLEAN RESET
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;

-- 2. PERMISSIONS & EXTENSIONS
GRANT ALL ON SCHEMA public TO postgres, public, anon, authenticated, service_role;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 3. UTILITY FUNCTIONS
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (SELECT 1 FROM public.members WHERE group_id = p_group_id AND user_id = auth.uid());
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION public.is_group_admin(p_group_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.groups g
        WHERE g.id = p_group_id AND g.admin_user_id = auth.uid()
    )
    OR EXISTS (
        SELECT 1 FROM public.members m
        JOIN public.profiles p ON p.id = m.user_id
        WHERE m.group_id = p_group_id AND m.user_id = auth.uid() AND p.role = 'group_admin'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

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
            SELECT 1 FROM public.profiles p
            WHERE p.id = auth.uid() AND p.role = 'platform_admin'
        );
END;
$$;

CREATE OR REPLACE FUNCTION public.update_member_count()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        UPDATE public.groups SET current_members = current_members + 1 WHERE id = NEW.group_id;
    ELSIF (TG_OP = 'DELETE') THEN
        UPDATE public.groups SET current_members = GREATEST(0, current_members - 1) WHERE id = OLD.group_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- 4. CORE TABLES
CREATE TABLE public.profiles (
    id          UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name   TEXT CHECK (char_length(full_name) >= 3),
    email       TEXT,
    role        TEXT DEFAULT 'member' CHECK (role IN ('platform_admin', 'group_admin', 'member')),
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.groups (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                   TEXT NOT NULL CHECK (char_length(name) >= 3),
    type                   TEXT NOT NULL DEFAULT 'other' CHECK (type IN ('burial_society', 'stokvel', 'rosca', 'investment_club', 'emergency_fund', 'community_savings', 'tontine', 'other')),
    province               TEXT,
    city                   TEXT,
    township               TEXT,
    description            TEXT,
    logo_emoji             TEXT DEFAULT '🤝',
    joining_fee            NUMERIC(12,2) DEFAULT 0 CHECK (joining_fee >= 0),
    monthly_contribution   NUMERIC(12,2) DEFAULT 0 CHECK (monthly_contribution >= 0),
    late_fee               NUMERIC(12,2) DEFAULT 0 CHECK (late_fee >= 0),
    late_fee_grace_days    INTEGER DEFAULT 5 CHECK (late_fee_grace_days >= 0),
    probation_months       INTEGER DEFAULT 3 CHECK (probation_months >= 0),
    payment_due_day        INTEGER DEFAULT 28 CHECK (payment_due_day >= 1 AND payment_due_day <= 28),
    max_members            INTEGER DEFAULT 50 CHECK (max_members > 0),
    current_members        INTEGER DEFAULT 0 CHECK (current_members >= 0),
    is_public              BOOLEAN DEFAULT TRUE,
    allow_partial_payment  BOOLEAN DEFAULT FALSE,
    auto_suspend_after     INTEGER DEFAULT 2 CHECK (auto_suspend_after > 0),
    bank_name              TEXT,
    account_number         TEXT CHECK (account_number IS NULL OR account_number ~ '^[0-9]{7,13}$'),
    branch_code            TEXT CHECK (branch_code IS NULL OR branch_code ~ '^[0-9]{6}$'),
    account_type           TEXT DEFAULT 'Savings',
    gateway_public_key     TEXT,
    balance                NUMERIC(12,2) DEFAULT 0,
    admin_user_id          UUID REFERENCES auth.users(id) NOT NULL,
    fee_status             TEXT DEFAULT 'due' CHECK (fee_status IN ('paid', 'due', 'warning', 'suspended', 'pending_activation')),
    registration_paid      BOOLEAN DEFAULT FALSE,
    is_platform_suspended  BOOLEAN DEFAULT FALSE,
    constitution_url       TEXT,
    constitution_status    TEXT DEFAULT 'pending' CHECK (constitution_status IN ('pending', 'verified', 'rejected')),
    latitude               FLOAT8,
    longitude              FLOAT8,
    geohash                TEXT,
    max_beneficiaries      INTEGER DEFAULT 0 CHECK (max_beneficiaries >= 0),
    beneficiary_increase_pct NUMERIC(10,2) DEFAULT 0 CHECK (beneficiary_increase_pct >= 0),
    goal_amount            NUMERIC(12,2) DEFAULT 0 CHECK (goal_amount >= 0),
    period_months          INTEGER DEFAULT 12 CHECK (period_months > 0),
    rosca_rotation_method  TEXT NOT NULL DEFAULT 'fixed' CHECK (rosca_rotation_method IN ('fixed', 'random_draw', 'need_based', 'auction')),
    loan_interest_rate     NUMERIC(10,2) DEFAULT 0 CHECK (loan_interest_rate >= 0),
    loan_max_amount        NUMERIC(12,2) DEFAULT 0 CHECK (loan_max_amount >= 0),
    loan_max_months        INTEGER DEFAULT 12 CHECK (loan_max_months > 0),
    created_at             TIMESTAMPTZ DEFAULT NOW(),
    updated_at             TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.members (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id             UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    user_id              UUID REFERENCES auth.users(id),
    full_name            TEXT NOT NULL CHECK (char_length(full_name) >= 2),
    id_number            TEXT CHECK (id_number ~ '^[0-9]{13}$' OR id_number IS NULL),
    phone                TEXT,
    email                TEXT,
    street               TEXT,
    suburb               TEXT,
    city                 TEXT,
    province             TEXT,
    notification_pref    TEXT DEFAULT 'both' CHECK (notification_pref IN ('whatsapp', 'email', 'both')),
    status               TEXT DEFAULT 'probation' CHECK (status IN ('active', 'probation', 'suspended', 'pending_payment')),
    joined_at            TIMESTAMPTZ DEFAULT NOW(),
    probation_end_at     TIMESTAMPTZ,
    profile_photo_url    TEXT,
    document_1_url       TEXT,
    document_1_type      TEXT,
    document_1_status    TEXT DEFAULT 'pending' CHECK (document_1_status IN ('pending', 'verified', 'rejected')),
    document_2_url       TEXT,
    document_2_type      TEXT,
    document_2_status    TEXT DEFAULT 'pending' CHECK (document_2_status IN ('pending', 'verified', 'rejected')),
    document_3_url       TEXT,
    document_3_type      TEXT,
    document_3_status    TEXT DEFAULT 'pending' CHECK (document_3_status IN ('pending', 'verified', 'rejected')),
    document_4_url       TEXT,
    document_4_type      TEXT,
    document_4_status    TEXT DEFAULT 'pending' CHECK (document_4_status IN ('pending', 'verified', 'rejected')),
    document_5_url       TEXT,
    document_5_type      TEXT,
    document_5_status    TEXT DEFAULT 'pending' CHECK (document_5_status IN ('pending', 'verified', 'rejected')),
    beneficiary_count         INTEGER DEFAULT 0 CHECK (beneficiary_count >= 0),
    beneficiary_over_65_count INTEGER DEFAULT 0 CHECK (beneficiary_over_65_count >= 0),
    monthly_contribution_override NUMERIC(10,2) CHECK (monthly_contribution_override >= 0),
    total_contributions  INTEGER DEFAULT 0 CHECK (total_contributions >= 0),
    total_paid           NUMERIC(12,2) DEFAULT 0 CHECK (total_paid >= 0),
    fcm_token            TEXT,
    member_key           TEXT,
    created_at           TIMESTAMPTZ DEFAULT NOW(),
    updated_at           TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(group_id, user_id)
);

CREATE TRIGGER trigger_update_member_count AFTER INSERT OR DELETE ON public.members FOR EACH ROW EXECUTE PROCEDURE public.update_member_count();

CREATE TABLE public.contributions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id              UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id               UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount                 NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    type                   TEXT DEFAULT 'contribution' CHECK (type IN ('contribution', 'joining_fee', 'registration_contribution', 'late_fee')),
    due_date               DATE NOT NULL,
    paid_at                TIMESTAMPTZ,
    payment_method         TEXT DEFAULT 'bank',
    transaction_id         TEXT,
    receipt_url            TEXT,
    status                 TEXT DEFAULT 'due' CHECK (status IN ('paid', 'due', 'overdue', 'partial')),
    late_fees_applied      BOOLEAN DEFAULT FALSE,
    created_at             TIMESTAMPTZ DEFAULT NOW(),
    updated_at             TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.group_ledger (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    transaction_id    UUID,
    amount            NUMERIC(12,2) NOT NULL,
    balance_after     NUMERIC(12,2) NOT NULL,
    description       TEXT NOT NULL,
    category          TEXT NOT NULL,
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.group_polls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    created_by_member_id UUID REFERENCES public.members(id) ON DELETE SET NULL,
    title TEXT NOT NULL CHECK (char_length(trim(title)) >= 3),
    description TEXT,
    status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('draft', 'open', 'closed', 'cancelled')),
    starts_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.group_poll_options (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id UUID NOT NULL REFERENCES public.group_polls(id) ON DELETE CASCADE,
    label TEXT NOT NULL,
    position INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.loans (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id         UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount            NUMERIC(12,2) NOT NULL,
    interest_rate     NUMERIC(10,2) DEFAULT 0,
    total_to_repay    NUMERIC(12,2) NOT NULL,
    total_repaid      NUMERIC(12,2) DEFAULT 0,
    monthly_repayment NUMERIC(12,2) NOT NULL,
    status            TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'active', 'partially_paid', 'completed', 'rejected', 'overdue', 'cancelled')),
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.payouts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount            NUMERIC(12,2) NOT NULL,
    bank_name         TEXT NOT NULL,
    account_no        TEXT NOT NULL,
    branch_code       TEXT NOT NULL,
    status            TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'group_approved', 'processing', 'completed', 'failed', 'cancelled')),
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.group_actuarial_metrics (
    id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id                       UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    pure_premium                   NUMERIC(12,2) DEFAULT 0,
    gross_premium                  NUMERIC(12,2) DEFAULT 0,
    reserve_adequacy_pct           NUMERIC(10,2) DEFAULT 0,
    solvency_ratio                 NUMERIC(10,2) DEFAULT 0,
    created_at                     TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.platform_settings (
    key   TEXT PRIMARY KEY,
    value NUMERIC NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.platform_ledger (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id    UUID,
    amount            NUMERIC(12,2) NOT NULL,
    balance_after     NUMERIC(12,2) NOT NULL,
    description       TEXT NOT NULL,
    category          TEXT NOT NULL,
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 5. RPC BUSINESS LOGIC
CREATE OR REPLACE FUNCTION public.increment_group_balance(
    p_group_id UUID, p_amount NUMERIC, p_description TEXT, p_category TEXT, p_transaction_id UUID DEFAULT NULL
) RETURNS NUMERIC LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE v_new_balance NUMERIC;
BEGIN
    UPDATE public.groups SET balance = balance + p_amount, updated_at = NOW() WHERE id = p_group_id RETURNING balance INTO v_new_balance;
    IF NOT FOUND THEN RAISE EXCEPTION 'Group not found'; END IF;
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category)
    VALUES (p_group_id, p_transaction_id, p_amount, v_new_balance, p_description, p_category);
    RETURN v_new_balance;
END; $$;

CREATE OR REPLACE FUNCTION public.record_contribution_v1(
    p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_due_date DATE,
    p_paid_at TIMESTAMPTZ, p_status TEXT, p_tx_id TEXT DEFAULT NULL, p_type TEXT DEFAULT 'contribution'
) RETURNS public.contributions LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE v_contribution public.contributions; v_member_name TEXT;
BEGIN
    SELECT full_name INTO v_member_name FROM public.members WHERE id = p_member_id AND group_id = p_group_id;
    IF v_member_name IS NULL THEN RAISE EXCEPTION 'Member not found'; END IF;
    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, transaction_id, type)
    VALUES (p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_tx_id, p_type) RETURNING * INTO v_contribution;
    UPDATE public.members SET total_contributions = total_contributions + 1, total_paid = total_paid + p_amount WHERE id = p_member_id;
    PERFORM public.increment_group_balance(p_group_id, p_amount, initcap(replace(p_type, '_', ' ')) || ' from ' || v_member_name, p_type, v_contribution.id);
    RETURN v_contribution;
END; $$;

-- 6. SECURITY (RLS)
DO $$ DECLARE v_t text; BEGIN
    FOR v_t IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', v_t);
        EXECUTE format('CREATE POLICY "Allow All" ON public.%I FOR ALL TO authenticated, anon USING (true) WITH CHECK (true)', v_t);
    END LOOP;
END $$;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO anon, authenticated, service_role;

-- 7. PLATFORM ADMIN SETUP
DO $$ DECLARE v_uid UUID; BEGIN
    SELECT id INTO v_uid FROM auth.users WHERE lower(email) = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_uid IS NULL THEN
        v_uid := gen_random_uuid();
        INSERT INTO auth.users (id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data)
        VALUES (v_uid, 'authenticated', 'authenticated', 'torrymsimango@gmail.com', crypt('torry123M', gen_salt('bf')), now(), '{"role":"platform_admin"}', '{"role":"platform_admin","full_name":"Torry Admin"}');
    END IF;
    INSERT INTO public.profiles (id, full_name, email, role) VALUES (v_uid, 'Torry Admin', 'torrymsimango@gmail.com', 'platform_admin') ON CONFLICT (id) DO UPDATE SET role = 'platform_admin';
END $$;
INSERT INTO public.platform_settings (key, value) VALUES ('monthly_per_member', 12.0), ('registration_fee', 700.0) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

-- 8. COMPREHENSIVE SEEDING
DO $$
DECLARE
    v_admin_id UUID; v_group_id UUID; v_member_id UUID; v_poll_id UUID;
    v_group_index INT; v_member_index INT;
    v_group_types TEXT[] := ARRAY['burial_society','stokvel','rosca','investment_club','emergency_fund','community_savings','tontine','other'];
BEGIN
    SELECT id INTO v_admin_id FROM auth.users WHERE lower(email) = 'torrymsimango@gmail.com' LIMIT 1;
    FOR v_group_index IN 1..8 LOOP
        INSERT INTO public.groups (name, type, province, city, township, description, joining_fee, monthly_contribution, admin_user_id, fee_status, registration_paid)
        VALUES (format('SEED-%s %s', v_group_index, v_group_types[v_group_index]), v_group_types[v_group_index], 'Gauteng', 'Johannesburg', 'Seed Township', 'Testing Group', 150.0, 250.0, v_admin_id, 'paid', true)
        RETURNING id INTO v_group_id;

        PERFORM public.increment_group_balance(v_group_id, 5000.0, 'SEED: Initial Capital', 'system');

        FOR v_member_index IN 1..8 LOOP
            INSERT INTO public.members (group_id, user_id, full_name, email, phone, status, member_key, joined_at)
            VALUES (v_group_id, CASE WHEN v_member_index = 1 THEN v_admin_id ELSE NULL END, format('Member %s-%s', v_group_index, v_member_index), format('seed.m%s.g%s@example.com', v_member_index, v_group_index), '0712345678', 'active', format('MK-SEED-%s-%s', v_group_index, v_member_index), NOW() - interval '6 months')
            RETURNING id INTO v_member_id;
            PERFORM public.record_contribution_v1(v_member_id, v_group_id, 250.0, (CURRENT_DATE - interval '30 days')::DATE, (NOW() - interval '29 days')::TIMESTAMPTZ, 'paid');
        END LOOP;

        INSERT INTO public.group_polls (group_id, title, status) VALUES (v_group_id, 'Test Poll', 'open') RETURNING id INTO v_poll_id;
        INSERT INTO public.group_poll_options (poll_id, label, position) VALUES (v_poll_id, 'Yes', 1), (v_poll_id, 'No', 2);
    END LOOP;
END $$;

COMMIT;

-- 9. VERIFICATION
SELECT 'REBUILD COMPLETE' as status, (SELECT COUNT(*) FROM public.groups) as groups, (SELECT COUNT(*) FROM public.members) as members;
