-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — Comprehensive Supabase PostgreSQL Schema (Reset Script)
-- Version: 2.2 (Updated April 19, 2026)
-- ─────────────────────────────────────────────────────────────────────────────
-- CHANGELOG v2.2:
-- - Synced with Kotlin models (Room DB v33)
-- - Contribution types: contribution, joining_fee, registration_contribution, late_fee
-- - Payment types: joining_fee, contribution, late_fee, platform_fee, claim, custom, registration
-- - Platform fee fields: paid_at, transaction_id
-- ─────────────────────────────────────────────────────────────────────────────
-- CHANGELOG v2.1:
-- - Added township field to groups for detailed location
-- - Added geolocation fields (latitude, longitude, geohash) for map display
-- - Updated contribution types to include registration_contribution
-- - Added proper indexes for geohash spatial queries
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. DROP PUBLIC SCHEMA FIRST to remove all foreign key constraints and start fresh
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;

-- 2. RESTORE PERMISSIONS for the new public schema
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO public;
GRANT ALL ON SCHEMA public TO anon;
GRANT ALL ON SCHEMA public TO authenticated;
GRANT ALL ON SCHEMA public TO service_role;

-- 3. Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 4. UTILITY FUNCTIONS
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 5. PROFILES (Global user data synced with Auth)
CREATE TABLE public.profiles (
    id          UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name   TEXT CHECK (char_length(full_name) >= 3),
    email       TEXT CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    role        TEXT DEFAULT 'member' CHECK (role IN ('platform_admin', 'group_admin', 'member')),
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Function to handle new user creation and sync to profiles
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'full_name', NEW.raw_user_meta_data->>'name', ''),
        NEW.email,
        COALESCE(NEW.raw_user_meta_data->>'role', 'member')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger to sync auth.users to public.profiles
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE PROCEDURE public.handle_new_user();

-- 6. GROUPS
CREATE TABLE public.groups (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                   TEXT NOT NULL CHECK (char_length(name) >= 3),
    type                   TEXT NOT NULL DEFAULT 'other' CHECK (type IN ('burial_society', 'stokvel', 'rosca', 'investment_club', 'emergency_fund', 'community_savings', 'tontine', 'other')),
    province               TEXT,
    city                   TEXT,
    township               TEXT,
    description            TEXT,
    logo_emoji             TEXT DEFAULT '🤝',
    joining_fee            NUMERIC(10,2) DEFAULT 0 CHECK (joining_fee >= 0),
    monthly_contribution   NUMERIC(10,2) DEFAULT 0 CHECK (monthly_contribution >= 0),
    late_fee               NUMERIC(10,2) DEFAULT 0 CHECK (late_fee >= 0),
    late_fee_grace_days    INTEGER DEFAULT 5 CHECK (late_fee_grace_days >= 0),
    probation_months       INTEGER DEFAULT 3 CHECK (probation_months >= 0),
    payment_due_day        INTEGER DEFAULT 28 CHECK (payment_due_day >= 1 AND payment_due_day <= 31),
    max_members            INTEGER DEFAULT 50 CHECK (max_members > 0),
    current_members        INTEGER DEFAULT 0 CHECK (current_members >= 0),
    is_public              BOOLEAN DEFAULT TRUE,
    allow_partial_payment  BOOLEAN DEFAULT FALSE,
    auto_suspend_after     INTEGER DEFAULT 2 CHECK (auto_suspend_after > 0),
    bank_name              TEXT,
    account_number         TEXT,
    branch_code            TEXT,
    account_type           TEXT DEFAULT 'Savings',
    yoco_public_key        TEXT,
    balance                NUMERIC(12,2) DEFAULT 0 CHECK (balance >= 0),
    admin_user_id          UUID REFERENCES auth.users(id) NOT NULL,
    fee_status             TEXT DEFAULT 'due' CHECK (fee_status IN ('paid', 'due', 'warning', 'suspended', 'pending_activation')),
    registration_paid      BOOLEAN DEFAULT FALSE,
    is_platform_suspended  BOOLEAN DEFAULT FALSE,
    constitution_url       TEXT,
    constitution_status    TEXT DEFAULT 'pending' CHECK (constitution_status IN ('pending', 'verified', 'rejected')),
    latitude               FLOAT8,
    longitude              FLOAT8,
    geohash                TEXT,

    -- Burial Society / Savings Specific
    max_beneficiaries      INTEGER DEFAULT 0 CHECK (max_beneficiaries >= 0),
    beneficiary_increase_pct NUMERIC(5,2) DEFAULT 0 CHECK (beneficiary_increase_pct >= 0),
    goal_amount            NUMERIC(12,2) DEFAULT 0 CHECK (goal_amount >= 0),
    period_months          INTEGER DEFAULT 12 CHECK (period_months > 0),

    created_at             TIMESTAMPTZ DEFAULT NOW(),
    updated_at             TIMESTAMPTZ DEFAULT NOW()
);

-- 7. POLICIES
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

-- 8. MEMBERS
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

    -- Burial Society / Actuarial Fields
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

-- 9. BENEFICIARIES
CREATE TABLE public.beneficiaries (
    group_id             UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    member_id            UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    id                   UUID DEFAULT gen_random_uuid(),
    full_name            TEXT NOT NULL CHECK (char_length(full_name) >= 2),
    id_number            TEXT CHECK (id_number ~ '^[0-9]{13}$' OR id_number IS NULL),
    relationship         TEXT,
    date_of_birth        DATE,
    is_over_65           BOOLEAN DEFAULT FALSE,
    document_url         TEXT,
    document_status      TEXT DEFAULT 'pending' CHECK (document_status IN ('pending', 'verified', 'rejected')),
    created_at           TIMESTAMPTZ DEFAULT NOW(),
    updated_at           TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (group_id, member_id, id)
);

-- 10. CONTRIBUTIONS
CREATE TABLE public.contributions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id              UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id               UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    policy_id              UUID REFERENCES public.policies(id) ON DELETE SET NULL,
    amount                 NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    type                   TEXT DEFAULT 'contribution' CHECK (type IN ('contribution', 'joining_fee', 'registration_contribution', 'late_fee')),
    due_date               DATE NOT NULL,
    paid_at                TIMESTAMPTZ,
    payment_method         TEXT DEFAULT 'yoco',
    yoco_transaction_id    TEXT,
    -- Optional: some clients/policies expect a receipt URL for payment proofs.
    -- Nullable for backward compatibility.
    receipt_url            TEXT,
    status                 TEXT DEFAULT 'due' CHECK (status IN ('paid', 'due', 'overdue', 'partial')),
    late_fees_applied      BOOLEAN DEFAULT FALSE,
    created_at             TIMESTAMPTZ DEFAULT NOW(),
    updated_at             TIMESTAMPTZ DEFAULT NOW()
);

-- 11. PAYMENTS
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

-- 12. NOTIFICATIONS
CREATE TABLE public.notifications (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id       UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    member_id      UUID REFERENCES public.members(id) ON DELETE CASCADE,
    message        TEXT NOT NULL,
    channel        TEXT DEFAULT 'both' CHECK (channel IN ('whatsapp', 'email', 'both')),
    trigger_event  TEXT DEFAULT 'custom',
    created_at     TIMESTAMPTZ DEFAULT NOW()
);

-- 13. MEMBER DOCUMENTS (Deep relational tracking)
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

-- 14. ACTUARIAL SNAPSHOTS
CREATE TABLE public.group_actuarial_metrics (
    id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id                       UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    pure_premium                   NUMERIC(12,2) DEFAULT 0,
    gross_premium                  NUMERIC(12,2) DEFAULT 0,
    reserve_adequacy_pct           NUMERIC(10,2) DEFAULT 0,
    solvency_margin_pct            NUMERIC(10,2) DEFAULT 0,
    loss_ratio_pct                 NUMERIC(10,2) DEFAULT 0,
    contribution_sufficiency_pct   NUMERIC(10,2) DEFAULT 0,
    break_even_members             INTEGER DEFAULT 0,
    actuarial_present_value        NUMERIC(15,2) DEFAULT 0,
    funding_ratio_pct              NUMERIC(10,2) DEFAULT 0,
    payment_rate_pct               NUMERIC(10,2) DEFAULT 0,
    composite_risk_score           INTEGER DEFAULT 0,
    insolvency_months              INTEGER DEFAULT 0,
    expected_annual_claims         NUMERIC(15,2) DEFAULT 0,
    created_at                     TIMESTAMPTZ DEFAULT NOW()
);

-- 15. PAYOUTS
CREATE TABLE public.payouts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount            NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    bank_name         TEXT NOT NULL,
    account_no        TEXT NOT NULL,
    branch_code       TEXT NOT NULL,
    status            TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'cancelled')),
    processed_by      UUID REFERENCES auth.users(id),
    processed_at      TIMESTAMPTZ,
    yoco_payout_id    TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 16. PLATFORM FEES (Group registration and monthly fees)
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

-- ── INDEXES FOR PERFORMANCE ──

-- Groups indexes
CREATE INDEX IF NOT EXISTS idx_groups_admin_user_id ON public.groups(admin_user_id);
CREATE INDEX IF NOT EXISTS idx_groups_is_public ON public.groups(is_public);
CREATE INDEX IF NOT EXISTS idx_groups_fee_status ON public.groups(fee_status);
CREATE INDEX IF NOT EXISTS idx_groups_geohash ON public.groups(geohash);

-- Members indexes
CREATE INDEX IF NOT EXISTS idx_members_group_id ON public.members(group_id);
CREATE INDEX IF NOT EXISTS idx_members_user_id ON public.members(user_id);
CREATE INDEX IF NOT EXISTS idx_members_status ON public.members(status);

-- Contributions indexes
CREATE INDEX IF NOT EXISTS idx_contributions_member_id ON public.contributions(member_id);
CREATE INDEX IF NOT EXISTS idx_contributions_group_id ON public.contributions(group_id);
CREATE INDEX IF NOT EXISTS idx_contributions_status ON public.contributions(status);
CREATE INDEX IF NOT EXISTS idx_contributions_due_date ON public.contributions(due_date);

-- Beneficiaries indexes
CREATE INDEX IF NOT EXISTS idx_beneficiaries_member_id ON public.beneficiaries(member_id);

-- Payments indexes
CREATE INDEX IF NOT EXISTS idx_payments_member_id ON public.payments(member_id);
CREATE INDEX IF NOT EXISTS idx_payments_group_id ON public.payments(group_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON public.payments(status);

-- Payouts indexes
CREATE INDEX IF NOT EXISTS idx_payouts_group_id ON public.payouts(group_id);
CREATE INDEX IF NOT EXISTS idx_payouts_status ON public.payouts(status);

-- Platform Fees indexes
CREATE INDEX IF NOT EXISTS idx_platform_fees_group_id ON public.platform_fees(group_id);
CREATE INDEX IF NOT EXISTS idx_platform_fees_status ON public.platform_fees(status);

-- Notifications indexes
CREATE INDEX IF NOT EXISTS idx_notifications_group_id ON public.notifications(group_id);
CREATE INDEX IF NOT EXISTS idx_notifications_member_id ON public.notifications(member_id);

-- Member Documents indexes
CREATE INDEX IF NOT EXISTS idx_member_documents_member_id ON public.member_documents(member_id);
CREATE INDEX IF NOT EXISTS idx_member_documents_group_id ON public.member_documents(group_id);

-- ── TRIGGER FUNCTIONS ──

-- Update Member Beneficiary Counts
CREATE OR REPLACE FUNCTION public.update_member_beneficiary_counts()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        UPDATE public.members
        SET beneficiary_count = beneficiary_count + 1,
            beneficiary_over_65_count = CASE WHEN NEW.is_over_65 THEN beneficiary_over_65_count + 1 ELSE beneficiary_over_65_count END
        WHERE id = NEW.member_id;
    ELSIF (TG_OP = 'DELETE') THEN
        UPDATE public.members
        SET beneficiary_count = beneficiary_count - 1,
            beneficiary_over_65_count = CASE WHEN OLD.is_over_65 THEN beneficiary_over_65_count - 1 ELSE beneficiary_over_65_count END
        WHERE id = OLD.member_id;
    ELSIF (TG_OP = 'UPDATE') THEN
        IF (OLD.is_over_65 != NEW.is_over_65) THEN
            UPDATE public.members
            SET beneficiary_over_65_count = CASE
                WHEN NEW.is_over_65 THEN beneficiary_over_65_count + 1
                ELSE beneficiary_over_65_count - 1
            END
            WHERE id = NEW.member_id;
        END IF;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_member_beneficiary_counts
AFTER INSERT OR DELETE OR UPDATE ON public.beneficiaries
FOR EACH ROW EXECUTE PROCEDURE public.update_member_beneficiary_counts();

-- Update Group Member Counts
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

CREATE TRIGGER trigger_update_member_count AFTER INSERT OR DELETE ON public.members FOR EACH ROW EXECUTE PROCEDURE public.update_member_count();

-- ── RPCs ──

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
    INSERT INTO public.contributions (
        member_id, group_id, amount, due_date, paid_at, status, yoco_transaction_id, type, payment_method
    ) VALUES (
        p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_yoco_tx_id, p_type, 'yoco'
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

-- ── STORAGE ──
-- Note: Buckets must be managed via Supabase Dashboard or API, but we ensure policies exist.
-- DO NOT delete from storage.objects directly in SQL. Use storage.emptyBucket() via API.

-- ── SEEDING PLATFORM ADMIN ──
-- We clear auth.users only when we are sure public schema is gone.
DELETE FROM auth.users;

INSERT INTO auth.users (
    id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, confirmation_token
) VALUES (
    '1b8aca84-c136-4c1b-b024-902584ae80d8',
    'authenticated',
    'authenticated',
    'torryymsimango@gmail.com',
    extensions.crypt('torry123M', extensions.gen_salt('bf')),
    NOW(),
    '{"provider": "email", "providers": ["email"]}',
    '{"full_name": "torry123", "role": "platform_admin"}',
    NOW(),
    NOW(),
    ''
);

-- 17. PLATFORM SETTINGS (Global config)
CREATE TABLE public.platform_settings (
    key   TEXT PRIMARY KEY,
    value NUMERIC NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Seed platform settings
INSERT INTO public.platform_settings (key, value) VALUES
('monthly_per_member', 10.0),
('registration_fee', 700.0)
ON CONFLICT (key) DO NOTHING;

-- 99. FINAL: RESTORE TABLE GRANTS (required for PostgREST / Android app visibility)
-- If these GRANTS are missing, the app will fail with: "permission denied for table groups"
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES IN SCHEMA public TO postgres, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO postgres, service_role;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO anon, authenticated;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated;

-- Ensure future tables/sequences created by this script (or migrations) inherit correct privileges
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO service_role;

