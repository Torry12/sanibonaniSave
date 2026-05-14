-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — CONSOLIDATED DATABASE SCHEMA
-- Version: 4.0 (Consolidated May 2026)
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
    payment_due_day        INTEGER DEFAULT 28 CHECK (payment_due_day >= 1 AND payment_due_day <= 28),
    max_members            INTEGER DEFAULT 50 CHECK (max_members > 0),
    current_members        INTEGER DEFAULT 0 CHECK (current_members >= 0),
    is_public              BOOLEAN DEFAULT TRUE,
    allow_partial_payment  BOOLEAN DEFAULT FALSE,
    auto_suspend_after     INTEGER DEFAULT 2 CHECK (auto_suspend_after > 0),
    bank_name              TEXT,
    account_number         TEXT CHECK (account_number IS NULL OR account_number ~ '^[0-9]{7,11}$'),
    branch_code            TEXT CHECK (branch_code IS NULL OR branch_code ~ '^[0-9]{6}$'),
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
    max_beneficiaries      INTEGER DEFAULT 0 CHECK (max_beneficiaries >= 0),
    beneficiary_increase_pct NUMERIC(5,2) DEFAULT 0 CHECK (beneficiary_increase_pct >= 0),
    goal_amount            NUMERIC(12,2) DEFAULT 0 CHECK (goal_amount >= 0),
    period_months          INTEGER DEFAULT 12 CHECK (period_months > 0),
    loan_interest_rate     NUMERIC(5,2) DEFAULT 0 CHECK (loan_interest_rate >= 0),
    loan_max_amount        NUMERIC(12,2) DEFAULT 0 CHECK (loan_max_amount >= 0),
    loan_max_months        INTEGER DEFAULT 12 CHECK (loan_max_months > 0),
    created_at             TIMESTAMPTZ DEFAULT NOW(),
    updated_at             TIMESTAMPTZ DEFAULT NOW()
);

-- 7. POLICIES (Insurance Policies within Groups)
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

-- 13. MEMBER DOCUMENTS
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
    account_no        TEXT NOT NULL CHECK (account_no ~ '^[0-9]{7,11}$'),
    branch_code       TEXT NOT NULL CHECK (branch_code ~ '^[0-9]{6}$'),
    status            TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'cancelled')),
    processed_by      UUID REFERENCES auth.users(id),
    processed_at      TIMESTAMPTZ,
    yoco_payout_id    TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 16. PLATFORM FEES
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

-- 17. PLATFORM SETTINGS
CREATE TABLE public.platform_settings (
    key   TEXT PRIMARY KEY,
    value NUMERIC NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 18. LOANS
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
    status            TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'active', 'partially_paid', 'completed', 'rejected', 'overdue', 'cancelled')),
    purpose           TEXT,
    contract_url      TEXT,
    surety_amount     NUMERIC(12,2) DEFAULT 0,
    reviewed_by       UUID REFERENCES auth.users(id),
    reviewed_at       TIMESTAMPTZ,
    admin_notes       TEXT,
    rejection_reason  TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 19. LOAN REPAYMENTS
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

-- 20. AUDIT LOGS
CREATE TABLE public.audit_logs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id          UUID NOT NULL,
    target_member_id  UUID,
    target_group_id   UUID,
    action            TEXT NOT NULL,
    details           JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 21. BENEFICIARY PAYOUT CLAIMS
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
    account_no        TEXT NOT NULL CHECK (account_no ~ '^[0-9]{7,11}$'),
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

-- 22. GROUP LEDGER (Double-entry tracking)
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

-- 23. PLATFORM LEDGER
CREATE TABLE public.platform_ledger (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id    UUID,
    amount            NUMERIC(12,2) NOT NULL,
    balance_after     NUMERIC(12,2) NOT NULL,
    description       TEXT NOT NULL,
    category          TEXT NOT NULL,
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 24. INDEXES
CREATE INDEX IF NOT EXISTS idx_groups_admin_user_id ON public.groups(admin_user_id);
CREATE INDEX IF NOT EXISTS idx_members_group_id ON public.members(group_id);
CREATE INDEX IF NOT EXISTS idx_contributions_member_id ON public.contributions(member_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON public.payments(status);
CREATE INDEX IF NOT EXISTS idx_loans_status ON public.loans(status);

-- 22. TRIGGERS & RPCs
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

CREATE OR REPLACE FUNCTION public.record_contribution_v1(
    p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_due_date DATE,
    p_paid_at TIMESTAMPTZ, p_status TEXT, p_yoco_tx_id TEXT DEFAULT NULL, p_type TEXT DEFAULT 'contribution'
) RETURNS public.contributions AS $$
DECLARE
    v_contribution public.contributions;
    v_new_balance NUMERIC;
BEGIN
    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, yoco_transaction_id, type)
    VALUES (p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_yoco_tx_id, p_type)
    RETURNING * INTO v_contribution;

    UPDATE public.members
    SET total_contributions = total_contributions + 1,
        total_paid = total_paid + p_amount
    WHERE id = p_member_id;

    UPDATE public.groups
    SET balance = balance + p_amount
    WHERE id = p_group_id
    RETURNING balance INTO v_new_balance;

    -- Add to Group Ledger
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category)
    VALUES (p_group_id, v_contribution.id, p_amount, v_new_balance,
            p_type || ' from ' || (SELECT full_name FROM public.members WHERE id = p_member_id),
            p_type);

    RETURN v_contribution;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION public.record_loan_repayment_v1(
    p_loan_id UUID, p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_payment_method TEXT DEFAULT 'yoco'
) RETURNS public.loan_repayments AS $$
DECLARE
    v_repayment public.loan_repayments;
    v_new_balance NUMERIC;
BEGIN
    -- Record repayment
    INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, payment_method)
    VALUES (p_loan_id, p_member_id, p_group_id, p_amount, p_payment_method)
    RETURNING * INTO v_repayment;

    -- Update loan status/balance
    UPDATE public.loans
    SET total_repaid = total_repaid + p_amount,
        status = CASE
            WHEN total_repaid + p_amount >= total_to_repay THEN 'completed'::text
            ELSE 'partially_paid'::text
        END,
        updated_at = NOW()
    WHERE id = p_loan_id;

    -- Update group balance
    UPDATE public.groups
    SET balance = balance + p_amount
    WHERE id = p_group_id
    RETURNING balance INTO v_new_balance;

    -- Add to Group Ledger
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category)
    VALUES (p_group_id, v_repayment.id, p_amount, v_new_balance,
            'Loan repayment from ' || (SELECT full_name FROM public.members WHERE id = p_member_id),
            'loan_repayment');

    RETURN v_repayment;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger to log platform revenue
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

CREATE TRIGGER trigger_log_platform_revenue
    AFTER UPDATE ON public.platform_fees
    FOR EACH ROW EXECUTE PROCEDURE public.log_platform_revenue();

-- Initial settings
INSERT INTO public.platform_settings (key, value) VALUES ('monthly_per_member', 10.0), ('registration_fee', 700.0) ON CONFLICT DO NOTHING;
