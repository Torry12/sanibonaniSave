-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — DATABASE SCHEMA (STRUCTURE ONLY)
-- Version: 26.0
-- ─────────────────────────────────────────────────────────────────────────────

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
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
STABLE
AS $$
BEGIN
    RETURN EXISTS (SELECT 1 FROM public.members WHERE group_id = p_group_id AND user_id = auth.uid());
END;
$$;

CREATE OR REPLACE FUNCTION public.is_group_admin(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
STABLE
AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1
        FROM public.groups g
        WHERE g.id = p_group_id
          AND g.admin_user_id = auth.uid()
    )
    OR EXISTS (
        SELECT 1
        FROM public.members m
        JOIN public.profiles p ON p.id = m.user_id
        WHERE m.group_id = p_group_id
          AND m.user_id = auth.uid()
          AND p.role = 'group_admin'
    );
END;
$$;

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
    name                   TEXT UNIQUE NOT NULL CHECK (char_length(name) >= 3),
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

CREATE TABLE public.beneficiaries (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id             UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    member_id            UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    full_name            TEXT NOT NULL CHECK (char_length(full_name) >= 2),
    id_number            TEXT CHECK (id_number ~ '^[0-9]{13}$' OR id_number IS NULL),
    relationship         TEXT,
    date_of_birth        DATE,
    is_over_65           BOOLEAN DEFAULT FALSE,
    document_url         TEXT,
    document_status      TEXT DEFAULT 'pending' CHECK (document_status IN ('pending', 'verified', 'rejected')),
    created_at           TIMESTAMPTZ DEFAULT NOW(),
    updated_at           TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (group_id, member_id, id_number)
);

CREATE TABLE public.payments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id         UUID REFERENCES public.members(id) ON DELETE CASCADE,
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount            NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    payment_type      TEXT NOT NULL CHECK (payment_type IN ('joining_fee', 'contribution', 'late_fee', 'platform_fee', 'claim', 'custom', 'registration')),
    payment_method    TEXT NOT NULL CHECK (payment_method IN ('yoco', 'bank', 'cash', 'other')),
    transaction_id    TEXT,
    status            TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'refunded')),
    processed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.member_documents (
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

CREATE TABLE public.notifications (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id       UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    member_id      UUID REFERENCES public.members(id) ON DELETE CASCADE,
    message        TEXT NOT NULL,
    channel        TEXT DEFAULT 'both' CHECK (channel IN ('whatsapp', 'email', 'both')),
    trigger_event  TEXT DEFAULT 'custom',
    created_at     TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.beneficiary_payout_claims (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    member_id         UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    beneficiary_id    UUID NOT NULL,
    beneficiary_name  TEXT NOT NULL,
    cause_of_death    TEXT NOT NULL,
    date_of_death     DATE NOT NULL,
    claim_amount      NUMERIC(12,2) NOT NULL CHECK (claim_amount > 0),
    bank_name         TEXT NOT NULL,
    account_no        TEXT NOT NULL CHECK (account_no ~ '^[0-9]{7,13}$'),
    branch_code       TEXT NOT NULL CHECK (branch_code ~ '^[0-9]{6}$'),
    account_holder    TEXT NOT NULL,
    notes             TEXT,
    status            TEXT DEFAULT 'submitted' CHECK (status IN ('submitted', 'under_review', 'approved', 'paid', 'rejected', 'escalated')),
    reviewed_by       UUID REFERENCES auth.users(id),
    reviewed_at       TIMESTAMPTZ,
    admin_notes       TEXT,
    rejection_reason  TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW()
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
    solvency_margin_pct            NUMERIC(10,2) DEFAULT 0,
    solvency_ratio                 NUMERIC(10,2) DEFAULT 0,
    created_at                     TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.policies (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    name              TEXT NOT NULL,
    description       TEXT,
    required_amount   NUMERIC(12,2) NOT NULL CHECK (required_amount > 0),
    status            TEXT DEFAULT 'inactive' CHECK (status IN ('active', 'partial', 'inactive')),
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.platform_fees (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    fee_type          TEXT NOT NULL CHECK (fee_type IN ('registration', 'monthly')),
    amount            NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
    status            TEXT DEFAULT 'due' CHECK (status IN ('paid', 'due', 'warning', 'suspended')),
    due_date          TEXT,
    paid_at           TIMESTAMPTZ,
    transaction_id    TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.loan_repayments (
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

CREATE TABLE public.audit_logs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id          UUID NOT NULL,
    target_member_id  UUID,
    target_group_id   UUID,
    action            TEXT NOT NULL,
    details           JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
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

-- Enable RLS on all tables
DO $$
DECLARE
    v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', v_table);
    END LOOP;
END $$;

-- Table Grants
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO anon, authenticated;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO anon, authenticated;

-- Core Policies
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

-- Universal Platform Admin Bypass
DO $$
DECLARE v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Platform Admin Bypass" ON public.%I', v_table);
        EXECUTE format('CREATE POLICY "Platform Admin Bypass" ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin())', v_table);
    END LOOP;
END $$;

