-- CONSOLIDATED_SYNCED_SCHEMA.sql
-- Version: 5.0 (Fully Synced with App Models - May 2026)
-- Canonical source of truth for SanibonaniSave database.

-- 1. DROP PUBLIC SCHEMA FIRST
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;

-- 2. RESTORE PERMISSIONS
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

-- 5. PROFILES
CREATE TABLE public.profiles (
    id          UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name   TEXT CHECK (char_length(full_name) >= 3),
    email       TEXT CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    role        TEXT DEFAULT 'member' CHECK (role IN ('platform_admin', 'group_admin', 'member')),
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

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
    goal_amount            NUMERIC(12,2) DEFAULT 0 CHECK (goal_amount >= 0),
    period_months          INTEGER DEFAULT 12 CHECK (period_months > 0),
    admin_user_id          UUID REFERENCES auth.users(id) NOT NULL,
    fee_status             TEXT DEFAULT 'due' CHECK (fee_status IN ('paid', 'due', 'warning', 'suspended', 'pending_activation', 'overdue')),
    registration_paid      BOOLEAN DEFAULT FALSE,
    rosca_rotation_method  TEXT NOT NULL DEFAULT 'fixed' CHECK (rosca_rotation_method IN ('fixed', 'random_draw', 'need_based', 'auction')),
    max_beneficiaries      INTEGER DEFAULT 0 CHECK (max_beneficiaries >= 0),
    beneficiary_increase_pct NUMERIC(5,2) DEFAULT 0 CHECK (beneficiary_increase_pct >= 0),
    latitude               FLOAT8,
    longitude              FLOAT8,
    geohash                TEXT,
    is_platform_suspended  BOOLEAN DEFAULT FALSE,
    constitution_url       TEXT,
    constitution_status    TEXT DEFAULT 'pending' CHECK (constitution_status IN ('pending', 'verified', 'rejected')),
    loan_interest_rate     NUMERIC(5,2) DEFAULT 0 CHECK (loan_interest_rate >= 0),
    loan_max_amount        NUMERIC(12,2) DEFAULT 0 CHECK (loan_max_amount >= 0),
    loan_max_months        INTEGER DEFAULT 12 CHECK (loan_max_months > 0),
    created_at             TIMESTAMPTZ DEFAULT NOW(),
    updated_at             TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(name)
);

-- 7. MEMBERS
CREATE TABLE public.members (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id             UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    user_id              UUID REFERENCES auth.users(id),
    full_name            TEXT NOT NULL CHECK (char_length(full_name) >= 2),
    id_number            TEXT CHECK (id_number ~ '^[0-9]{13}$' OR id_number IS NULL),
    phone                TEXT NOT NULL,
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
    UNIQUE(group_id, user_id),
    UNIQUE(group_id, member_key)
);

-- 8. BENEFICIARIES
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
    face_photo_url       TEXT,
    document_status      TEXT DEFAULT 'pending' CHECK (document_status IN ('pending', 'verified', 'rejected')),
    created_at           TIMESTAMPTZ DEFAULT NOW(),
    updated_at           TIMESTAMPTZ DEFAULT NOW()
);

-- 9. CONTRIBUTIONS
CREATE TABLE public.contributions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id              UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id               UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    policy_id              UUID,
    amount                 NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    type                   TEXT DEFAULT 'contribution' CHECK (type IN ('contribution', 'joining_fee', 'registration_contribution', 'late_fee', 'member_fee_ledger')),
    due_date               DATE NOT NULL,
    paid_at                TIMESTAMPTZ,
    payment_method         TEXT DEFAULT 'bank' CHECK (payment_method IN ('yoco', 'stitch', 'payfast', 'bank', 'cash', 'wallet', 'other')),
    transaction_id         TEXT,
    status                 TEXT DEFAULT 'due' CHECK (status IN ('paid', 'due', 'overdue', 'partial')),
    late_fees_applied      BOOLEAN DEFAULT FALSE,
    created_at             TIMESTAMPTZ DEFAULT NOW(),
    updated_at             TIMESTAMPTZ DEFAULT NOW()
);

-- 10. PAYMENTS
CREATE TABLE public.payments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id         UUID REFERENCES public.members(id) ON DELETE CASCADE,
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount            NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    payment_type      TEXT NOT NULL CHECK (payment_type IN ('joining_fee', 'contribution', 'late_fee', 'platform_fee', 'claim', 'loan_repayment', 'loan_disbursement', 'custom', 'registration')),
    payment_method    TEXT NOT NULL CHECK (payment_method IN ('yoco', 'stitch', 'payfast', 'bank', 'cash', 'wallet', 'other')),
    transaction_id    TEXT,
    status            TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'refunded')),
    processed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 11. LOANS
CREATE TABLE public.loans (
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
    contract_url      TEXT,
    surety_amount     NUMERIC(12,2),
    status            TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'active', 'partially_paid', 'completed', 'rejected', 'overdue', 'cancelled')),
    purpose           TEXT,
    reviewed_by       UUID REFERENCES auth.users(id),
    reviewed_at       TIMESTAMPTZ,
    admin_notes       TEXT,
    rejection_reason  TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 12. BENEFICIARY PAYOUT CLAIMS
CREATE TABLE public.beneficiary_payout_claims (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    member_id         UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    beneficiary_id    UUID NOT NULL,
    beneficiary_name  TEXT NOT NULL,
    face_photo_url    TEXT,
    cause_of_death    TEXT NOT NULL,
    date_of_death     DATE NOT NULL,
    claim_amount      NUMERIC(12,2) NOT NULL CHECK (claim_amount > 0),
    bank_name         TEXT NOT NULL,
    account_no        TEXT NOT NULL CHECK (account_no ~ '^[0-9]{7,13}$'),
    branch_code       TEXT NOT NULL CHECK (branch_code ~ '^[0-9]{6}$'),
    account_holder    TEXT NOT NULL,
    notes             TEXT,
    status            TEXT DEFAULT 'submitted' CHECK (status IN ('submitted', 'under_review', 'escalated', 'approved', 'rejected', 'paid', 'cancelled')),
    reviewed_by       UUID REFERENCES auth.users(id),
    reviewed_at       TIMESTAMPTZ,
    admin_notes       TEXT,
    rejection_reason  TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 13. GROUP LEDGER
CREATE TABLE public.group_ledger (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    transaction_id    TEXT,
    amount            NUMERIC(12,2) NOT NULL,
    balance_after     NUMERIC(12,2) NOT NULL,
    description       TEXT NOT NULL,
    category          TEXT NOT NULL,
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 14. POLLS & VOTING
CREATE TABLE public.group_polls (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id               UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    created_by_member_id   UUID REFERENCES public.members(id) ON DELETE SET NULL,
    title                  TEXT NOT NULL,
    description            TEXT,
    type                   TEXT DEFAULT 'general' CHECK (type IN ('general', 'fee_change', 'rosca_order', 'loan_approval', 'member_removal')),
    status                 TEXT DEFAULT 'open' CHECK (status IN ('draft', 'open', 'closed', 'cancelled')),
    effect_status          TEXT DEFAULT 'none' CHECK (effect_status IN ('none', 'pending', 'applied', 'failed')),
    effect_data            JSONB,
    allow_multiple_choice  BOOLEAN DEFAULT FALSE,
    starts_at              TIMESTAMPTZ,
    ends_at                TIMESTAMPTZ,
    created_at             TIMESTAMPTZ DEFAULT NOW(),
    updated_at             TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.group_poll_options (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id     UUID REFERENCES public.group_polls(id) ON DELETE CASCADE NOT NULL,
    label       TEXT NOT NULL,
    position    INTEGER DEFAULT 1,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.group_poll_votes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id     UUID REFERENCES public.group_polls(id) ON DELETE CASCADE NOT NULL,
    option_id   UUID REFERENCES public.group_poll_options(id) ON DELETE CASCADE NOT NULL,
    member_id   UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id    UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(poll_id, member_id)
);

-- 15. AUDIT LOGS
CREATE TABLE public.audit_logs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id          UUID NOT NULL,
    target_member_id  UUID,
    target_group_id   UUID,
    action            TEXT NOT NULL,
    details           JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 16. NOTIFICATIONS
CREATE TABLE public.notifications (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id       UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    member_id      UUID REFERENCES public.members(id) ON DELETE CASCADE,
    message        TEXT NOT NULL,
    channel        TEXT DEFAULT 'both' CHECK (channel IN ('whatsapp', 'email', 'both')),
    trigger_event  TEXT DEFAULT 'custom',
    created_at     TIMESTAMPTZ DEFAULT NOW()
);

-- 17. PLATFORM SETTINGS
CREATE TABLE public.platform_settings (
    key   TEXT PRIMARY KEY,
    value NUMERIC NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 18. PLATFORM LEDGER
CREATE TABLE public.platform_ledger (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id    UUID,
    amount            NUMERIC(12,2) NOT NULL,
    balance_after     NUMERIC(12,2) NOT NULL,
    description       TEXT NOT NULL,
    category          TEXT NOT NULL,
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 19. RPCs & TRIGGERS
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

GRANT EXECUTE ON FUNCTION public.increment_group_balance(UUID, NUMERIC) TO authenticated, service_role;

-- 20. INITIAL DATA
INSERT INTO public.platform_settings (key, value) VALUES ('monthly_per_member', 10.0), ('registration_fee', 700.0) ON CONFLICT DO NOTHING;

-- 21. RLS (Minimal "Allow All for Dev" - In production, replace with secure policies)
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow all for dev" ON public.profiles FOR ALL USING (true);

ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow all for dev" ON public.groups FOR ALL USING (true);

ALTER TABLE public.members ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow all for dev" ON public.members FOR ALL USING (true);

ALTER TABLE public.contributions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow all for dev" ON public.contributions FOR ALL USING (true);

ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow all for dev" ON public.payments FOR ALL USING (true);

ALTER TABLE public.loans ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow all for dev" ON public.loans FOR ALL USING (true);

ALTER TABLE public.beneficiary_payout_claims ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow all for dev" ON public.beneficiary_payout_claims FOR ALL USING (true);

ALTER TABLE public.group_polls ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow all for dev" ON public.group_polls FOR ALL USING (true);

ALTER TABLE public.group_poll_options ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow all for dev" ON public.group_poll_options FOR ALL USING (true);

ALTER TABLE public.group_poll_votes ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow all for dev" ON public.group_poll_votes FOR ALL USING (true);
