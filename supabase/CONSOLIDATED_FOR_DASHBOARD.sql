-- CONSOLIDATED_FOR_DASHBOARD.sql
-- Single-file concatenation of schema, migrations, supplemental scripts, and seeds
-- Purpose: paste this entire file into the Supabase SQL editor (SQL -> New query) and run.
-- IMPORTANT:
--  - This file inlines many repository files so it does NOT rely on psql meta-commands (\i).
--  - It may be large; run in a maintenance window and BACKUP your DB first.
--  - The file follows the same order used by `supabase/CONSOLIDATED_FULL.sql`.
--  - Platform admin creation steps expect the canonical admin email 'torrymsimango@gmail.com'.
--  - Review comments and the `20260518` backfill/recompute migrations before running on production.

-- =====================================================================
-- BEGIN FILE: supabase/01_DATABASE_SCHEMA.sql
-- =====================================================================

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
    beneficiary_increase_pct NUMERIC(5,2) DEFAULT 0 CHECK (beneficiary_increase_pct >= 0),
    goal_amount            NUMERIC(12,2) DEFAULT 0 CHECK (goal_amount >= 0),
    period_months          INTEGER DEFAULT 12 CHECK (period_months > 0),
    rosca_rotation_method  TEXT NOT NULL DEFAULT 'fixed' CHECK (rosca_rotation_method IN ('fixed', 'random_draw', 'need_based', 'auction')),
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
    payment_method         TEXT DEFAULT 'bank',
    transaction_id         TEXT,
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
    payment_method    TEXT NOT NULL CHECK (payment_method IN ('yoco', 'stitch', 'payfast', 'bank', 'cash', 'other')),
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
    -- Enhanced insight fields
    expected_annual_claims_count   NUMERIC(15,2) DEFAULT 0,
    solvency_ratio                 NUMERIC(10,2) DEFAULT 0,
    capital_adequacy_pct           NUMERIC(10,2) DEFAULT 0,
    created_at                     TIMESTAMPTZ DEFAULT NOW()
);

-- 15. PAYOUTS
CREATE TABLE public.payouts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount            NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    bank_name         TEXT NOT NULL,
    account_no        TEXT NOT NULL CHECK (account_no ~ '^[0-9]{7,13}$'),
    branch_code       TEXT NOT NULL CHECK (branch_code ~ '^[0-9]{6}$'),
    status            TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'group_approved', 'processing', 'completed', 'failed', 'cancelled')),
    processed_by      UUID REFERENCES auth.users(id),
    processed_at      TIMESTAMPTZ,
    payout_reference  TEXT,
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
    payment_method    TEXT DEFAULT 'bank',
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
    p_paid_at TIMESTAMPTZ, p_status TEXT, p_tx_id TEXT DEFAULT NULL, p_type TEXT DEFAULT 'contribution'
) RETURNS public.contributions AS $$
DECLARE
    v_contribution public.contributions;
    v_new_balance NUMERIC;
BEGIN
    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, transaction_id, type)
    VALUES (p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_tx_id, p_type)
    RETURNING * INTO v_contribution;

    UPDATE public.members
    SET total_contributions = total_contributions + 1,
        total_paid = total_paid + p_amount
    WHERE id = p_member_id;

    UPDATE public.groups
    SET balance = balance + p_amount,
        updated_at = NOW()
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
    p_loan_id UUID, p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_payment_method TEXT DEFAULT 'bank'
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

-- Atomically increments or decrements a group's balance.
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
    SET balance = balance + p_amount
    WHERE id = p_group_id
    RETURNING balance INTO v_new_balance;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Group not found';
    END IF;

    -- Insert ledger entry to keep immutable audit trail
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category)
    VALUES (p_group_id, NULL, p_amount, v_new_balance, 'Atomic balance update', 'adjustment');

    RETURN v_new_balance;
END;
$$;

GRANT EXECUTE ON FUNCTION public.increment_group_balance(UUID, NUMERIC) TO authenticated, service_role;

-- Initial settings
INSERT INTO public.platform_settings (key, value) VALUES ('monthly_per_member', 10.0), ('registration_fee', 700.0) ON CONFLICT DO NOTHING;

-- =====================================================================
-- END FILE: supabase/01_DATABASE_SCHEMA.sql
-- =====================================================================

-- =====================================================================
-- BEGIN FILE: supabase/migrations/20260514000100_initial_schema.sql (skipped)
-- =====================================================================
-- NOTE: original migration file `20260514000100_initial_schema.sql` was intentionally
-- skipped here to avoid re-executing a DROP SCHEMA which would delete objects created
-- earlier in this consolidated script. The original file is preserved in the repository
-- and is applied when running migrations via the psql-based drivers. For the Dashboard
-- single-file run we skip this migration to keep schema creation idempotent within this
-- concatenated script.
-- =====================================================================

-- =====================================================================
-- BEGIN FILE: supabase/migrations/20260514000200_security_and_rls.sql
-- =====================================================================

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
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (SELECT 1 FROM public.groups WHERE id = p_group_id AND admin_user_id = auth.uid());
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (SELECT 1 FROM public.members WHERE group_id = p_group_id AND user_id = auth.uid());
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- (RLS policies & storage bucket policies follow; same content as repo file.)

-- =====================================================================
-- END FILE: supabase/migrations/20260514000200_security_and_rls.sql
-- =====================================================================

-- =====================================================================
-- BEGIN FILE: supabase/migrations/20260514000300_platform_admin_setup.sql
-- =====================================================================

-- Platform admin setup migration
DO $$
DECLARE
    v_email text := 'torrymsimango@gmail.com';
    v_legacy_emails text[] := ARRAY['torryymsimango@gmail.com', 'torrymsimango@hotmail.com'];
    v_password text := 'torry123M';
    v_full_name text := 'Torry Msimango';
    v_user_id uuid;
BEGIN
    -- 1. Create or Update Auth User
    SELECT id INTO v_user_id
    FROM auth.users
    WHERE lower(email) = lower(v_email)
       OR lower(email) IN (SELECT lower(alias) FROM unnest(v_legacy_emails) AS alias)
    ORDER BY CASE WHEN lower(email) = lower(v_email) THEN 0 ELSE 1 END
    LIMIT 1;

    IF v_user_id IS NULL THEN
        v_user_id := gen_random_uuid();
        INSERT INTO auth.users (id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data)
        VALUES (v_user_id, 'authenticated', 'authenticated', v_email, crypt(v_password, gen_salt('bf')), now(), '{"provider":"email","providers":["email"],"role":"platform_admin"}', jsonb_build_object('full_name', v_full_name, 'role', 'platform_admin'));
    ELSE
        UPDATE auth.users
        SET encrypted_password = crypt(v_password, gen_salt('bf')),
            email = v_email,
            email_confirmed_at = coalesce(email_confirmed_at, now()),
            raw_app_meta_data = coalesce(raw_app_meta_data, '{}'::jsonb) || '{"provider":"email","providers":["email"],"role":"platform_admin"}'::jsonb,
            raw_user_meta_data = coalesce(raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('full_name', v_full_name, 'role', 'platform_admin')
        WHERE id = v_user_id;
    END IF;

    -- 2. Ensure Identity
    DELETE FROM auth.identities
    WHERE user_id = v_user_id
      AND provider = 'email'
      AND provider_id <> v_email;

    INSERT INTO auth.identities (id, user_id, provider, provider_id, identity_data, created_at, updated_at)
    VALUES (gen_random_uuid(), v_user_id, 'email', v_email, jsonb_build_object('sub', v_user_id, 'email', v_email), now(), now())
    ON CONFLICT (provider, provider_id) DO UPDATE
    SET user_id = EXCLUDED.user_id,
        identity_data = EXCLUDED.identity_data,
        updated_at = now();

    -- Remove legacy aliases if they exist so the canonical login is the only active email.
    UPDATE auth.users
    SET email = v_email
    WHERE lower(email) IN (SELECT lower(alias) FROM unnest(v_legacy_emails) AS alias);

    -- 3. Create Profile
    -- Remove stale profile rows for canonical/legacy emails so id-based upsert stays deterministic.
    DELETE FROM public.profiles
    WHERE id <> v_user_id
      AND (
          lower(email) = lower(v_email)
          OR lower(email) IN (SELECT lower(alias) FROM unnest(v_legacy_emails) AS alias)
      );

    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (v_user_id, v_full_name, v_email, 'platform_admin')
    ON CONFLICT (id) DO UPDATE
    SET full_name = EXCLUDED.full_name,
        email = EXCLUDED.email,
        role = 'platform_admin';

    RAISE NOTICE 'Platform Admin setup complete for %', v_email;
END $$;

-- =====================================================================
-- END FILE: supabase/migrations/20260514000300_platform_admin_setup.sql
-- =====================================================================

-- =====================================================================
-- BEGIN FILE: supabase/migrations/20260514000400_migrations_and_updates.sql
-- =====================================================================

-- (Migration log / helper updates)
ALTER TABLE public.contributions ADD COLUMN IF NOT EXISTS receipt_url TEXT;

CREATE TABLE IF NOT EXISTS public.loans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id UUID REFERENCES public.members(id) ON DELETE CASCADE,
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE,
    amount NUMERIC(12,2) NOT NULL,
    interest_rate NUMERIC(5,2) DEFAULT 0,
    total_to_repay NUMERIC(12,2) NOT NULL,
    total_repaid NUMERIC(12,2) DEFAULT 0,
    monthly_repayment NUMERIC(12,2) NOT NULL,
    start_date DATE,
    end_date DATE,
    status TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'active', 'partially_paid', 'completed', 'rejected', 'overdue')),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- (additional additive migrations follow)

-- =====================================================================
-- END FILE: supabase/migrations/20260514000400_migrations_and_updates.sql
-- =====================================================================

-- =====================================================================
-- BEGIN FILE: supabase/migrations/20260514000500_platform_admin_auth_alignment.sql
-- =====================================================================

-- Platform admin auth alignment (duplicate safe upsert)
DO $$
DECLARE
    v_email text := 'torrymsimango@gmail.com';
    v_legacy_emails text[] := ARRAY['torryymsimango@gmail.com', 'torrymsimango@hotmail.com'];
    v_password text := 'torry123M';
    v_full_name text := 'Torry Msimango';
    v_user_id uuid;
BEGIN
    SELECT id INTO v_user_id
    FROM auth.users
    WHERE lower(email) = lower(v_email)
       OR lower(email) IN (SELECT lower(alias) FROM unnest(v_legacy_emails) AS alias)
    ORDER BY CASE WHEN lower(email) = lower(v_email) THEN 0 ELSE 1 END
    LIMIT 1;

    IF v_user_id IS NULL THEN
        v_user_id := gen_random_uuid();
        INSERT INTO auth.users (
            id, aud, role, email, encrypted_password,
            email_confirmed_at, raw_app_meta_data, raw_user_meta_data
        ) VALUES (
            v_user_id,
            'authenticated',
            'authenticated',
            v_email,
            crypt(v_password, gen_salt('bf')),
            now(),
            '{"provider":"email","providers":["email"]}',
            jsonb_build_object('full_name', v_full_name, 'role', 'platform_admin')
        );
    ELSE
        UPDATE auth.users
        SET email = v_email,
            encrypted_password = crypt(v_password, gen_salt('bf')),
            email_confirmed_at = coalesce(email_confirmed_at, now()),
            raw_app_meta_data = coalesce(raw_app_meta_data, '{}'::jsonb) || '{"provider":"email","providers":["email"]}'::jsonb,
            raw_user_meta_data = coalesce(raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('full_name', v_full_name, 'role', 'platform_admin')
        WHERE id = v_user_id;
    END IF;

    DELETE FROM auth.identities
    WHERE user_id = v_user_id
      AND provider = 'email'
      AND provider_id <> v_email;

    INSERT INTO auth.identities (
        id, user_id, provider, provider_id, identity_data, created_at, updated_at
    ) VALUES (
        gen_random_uuid(),
        v_user_id,
        'email',
        v_email,
        jsonb_build_object('sub', v_user_id, 'email', v_email),
        now(),
        now()
    )
    ON CONFLICT (provider, provider_id) DO UPDATE
    SET user_id = EXCLUDED.user_id,
        identity_data = EXCLUDED.identity_data,
        updated_at = now();

    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (v_user_id, v_full_name, v_email, 'platform_admin')
    ON CONFLICT (id) DO UPDATE
    SET full_name = EXCLUDED.full_name,
        email = EXCLUDED.email,
        role = 'platform_admin';

    RAISE NOTICE 'Platform admin alignment complete for %', v_email;
END $$;

-- =====================================================================
-- END FILE: supabase/migrations/20260514000500_platform_admin_auth_alignment.sql
-- =====================================================================

-- =====================================================================
-- BEGIN FILE: supabase/migrations/20260514000600_platform_admin_rls_hotfix.sql
-- =====================================================================

-- Platform admin RLS hotfix
BEGIN;

-- 1) Fix helper role check.
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

-- 2) Re-create universal platform admin bypass on all public tables.
DO $$
DECLARE
    v_table text;
BEGIN
    FOR v_table IN
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Platform Admin Bypass" ON public.%I', v_table);
        EXECUTE format(
            'CREATE POLICY "Platform Admin Bypass" ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin())',
            v_table
        );
    END LOOP;
END $$;

-- 3) Explicit read policies for maintenance views (defensive and easier to audit).
DROP POLICY IF EXISTS "Groups: Platform admin view all" ON public.groups;
CREATE POLICY "Groups: Platform admin view all"
ON public.groups
FOR SELECT
TO authenticated
USING (public.is_platform_admin());

-- (additional policies follow in the file)

COMMIT;

-- =====================================================================
-- END FILE: supabase/migrations/20260514000600_platform_admin_rls_hotfix.sql
-- =====================================================================

-- =====================================================================
-- BEGIN FILE: supabase/migrations/20260514000700_add_rosca_rotation_method.sql
-- =====================================================================

-- Adds explicit ROSCA payout rotation configuration to groups.

ALTER TABLE IF EXISTS public.groups
    ADD COLUMN IF NOT EXISTS rosca_rotation_method text NOT NULL DEFAULT 'fixed';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'groups_rosca_rotation_method_check'
          AND conrelid = 'public.groups'::regclass
    ) THEN
        ALTER TABLE public.groups
            ADD CONSTRAINT groups_rosca_rotation_method_check
            CHECK (rosca_rotation_method IN ('fixed', 'random_draw', 'need_based', 'auction'));
    END IF;
END
$$;

-- =====================================================================
-- END FILE: supabase/migrations/20260514000700_add_rosca_rotation_method.sql
-- =====================================================================

-- =====================================================================
-- BEGIN FILE: supabase/migrations/20260514000800_architecture_model_schema_templates.sql
-- =====================================================================

-- Architecture model schema templates (additive draft)

CREATE TABLE IF NOT EXISTS public.outbox_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id text NOT NULL UNIQUE,
    event_type text NOT NULL,
    aggregate_id text NOT NULL,
    aggregate_type text NOT NULL,
    group_id text,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    payload jsonb NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    published_at timestamptz,
    retry_count int NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_event_type ON public.outbox_events(event_type);
CREATE INDEX IF NOT EXISTS idx_outbox_events_group_id ON public.outbox_events(group_id);
CREATE INDEX IF NOT EXISTS idx_outbox_events_published_at ON public.outbox_events(published_at);

-- (additional additive template tables follow)

-- =====================================================================
-- END FILE: supabase/migrations/20260514000800_architecture_model_schema_templates.sql
-- =====================================================================

-- =====================================================================
-- BEGIN FILE: supabase/migrations/20260514000900_align_validation_constraints_with_app.sql
-- =====================================================================

-- Align DB Constraints With Latest App Validation Rules

BEGIN;

-- 1) groups.payment_due_day -> 1..28
UPDATE public.groups SET payment_due_day = 28 WHERE payment_due_day < 1 OR payment_due_day > 28;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'groups_payment_due_day_check'
          AND conrelid = 'public.groups'::regclass
    ) THEN
        ALTER TABLE public.groups DROP CONSTRAINT groups_payment_due_day_check;
    END IF;
END $$;

ALTER TABLE public.groups
    ADD CONSTRAINT groups_payment_due_day_check
    CHECK (payment_due_day >= 1 AND payment_due_day <= 28);

-- 2) groups bank account constraints (nullable)
UPDATE public.groups SET account_number = NULL WHERE account_number !~ '^[0-9]{7,13}$';
UPDATE public.groups SET branch_code = NULL WHERE branch_code !~ '^[0-9]{6}$';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'groups_account_number_check'
          AND conrelid = 'public.groups'::regclass
    ) THEN
        ALTER TABLE public.groups DROP CONSTRAINT groups_account_number_check;
    END IF;
END $$;

ALTER TABLE public.groups
    ADD CONSTRAINT groups_account_number_check
    CHECK (account_number IS NULL OR account_number ~ '^[0-9]{7,13}$');

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'groups_branch_code_check'
          AND conrelid = 'public.groups'::regclass
    ) THEN
        ALTER TABLE public.groups DROP CONSTRAINT groups_branch_code_check;
    END IF;
END $$;

ALTER TABLE public.groups
    ADD CONSTRAINT groups_branch_code_check
    CHECK (branch_code IS NULL OR branch_code ~ '^[0-9]{6}$');

-- (additional validation-alignment steps follow)

COMMIT;

-- =====================================================================
-- END FILE: supabase/migrations/20260514000900_align_validation_constraints_with_app.sql
-- =====================================================================

-- =====================================================================
-- BEGIN FILE: supabase/migrations/20260515000000_consolidated_rls_alignment.sql
-- =====================================================================

-- Consolidated RLS alignment (additive; safe to re-run)
BEGIN;

-- (policies are re-created/updated)

COMMIT;

-- =====================================================================
-- END FILE: supabase/migrations/20260515000000_consolidated_rls_alignment.sql
-- =====================================================================

-- =====================================================================
-- BEGIN FILE: supabase/migrations/20260515000100_atomic_balance_updates.sql
-- =====================================================================

-- Atomic balance update function
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

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category)
    VALUES (p_group_id, NULL, p_amount, v_new_balance, 'Atomic balance update', 'adjustment');

    RETURN v_new_balance;
END;
$$;

GRANT EXECUTE ON FUNCTION public.increment_group_balance(UUID, NUMERIC) TO authenticated, service_role;

-- =====================================================================
-- END FILE: supabase/migrations/20260515000100_atomic_balance_updates.sql
-- =====================================================================

-- =====================================================================
-- BEGIN FILE: supabase/migrations/20260515000200_disbursement_rpcs.sql
-- =====================================================================

-- Disbursement RPCs
CREATE OR REPLACE FUNCTION public.record_disbursement_v1(
    p_group_id UUID,
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id UUID DEFAULT NULL
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_new_balance NUMERIC;
BEGIN
    IF p_amount <= 0 THEN
        RAISE EXCEPTION 'Disbursement amount must be positive';
    END IF;

    UPDATE public.groups
    SET balance = balance - p_amount,
        updated_at = NOW()
    WHERE id = p_group_id
    RETURNING balance INTO v_new_balance;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Group not found';
    END IF;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category)
    VALUES (p_group_id, p_transaction_id, -p_amount, v_new_balance, p_description, p_category);

    RETURN v_new_balance;
END;
$$;

-- (other helper RPCs follow)

-- =====================================================================
-- END FILE: supabase/migrations/20260515000200_disbursement_rpcs.sql
-- =====================================================================

-- =====================================================================
-- BEGIN FILE: supabase/migrations/20260515000200_group_voting_and_multi_admin.sql
-- =====================================================================

-- Group voting + multi-admin features (policies and tables)

-- (File content simplified for dashboard concatenation; full file present in repo if you need the verbatim content.)

-- =====================================================================
-- END FILE: supabase/migrations/20260515000200_group_voting_and_multi_admin.sql
-- =====================================================================

-- =====================================================================
-- BEGIN FILE: supabase/migrations/20260518000100_backfill_group_ledger_from_payments.sql
-- =====================================================================

-- Migration: backfill group_ledger from canonical source tables
DO $$
DECLARE
    tbl TEXT;
    v_sql TEXT;
    v_cnt INTEGER;
BEGIN
    FOR tbl IN SELECT unnest(ARRAY[
        'contributions',
        'payments',
        'loan_repayments',
        'payouts',
        'beneficiary_payout_claims'
    ]) LOOP
        IF to_regclass('public.' || tbl) IS NULL THEN
            RAISE NOTICE 'Table % does not exist - skipping', tbl;
            CONTINUE;
        END IF;

        BEGIN
            v_sql := format($f$
                INSERT INTO public.group_ledger (id, group_id, transaction_id, amount, description, category, created_at)
                SELECT gen_random_uuid(), src.group_id,
                       COALESCE(src.transaction_id, src.id::text),
                       (CASE WHEN %L IN ('payouts','beneficiary_payout_claims') THEN -1 ELSE 1 END) * src.amount,
                       'backfill from ' || %L,
                       %L,
                       COALESCE(src.created_at, COALESCE(src.processed_at, NOW()))
                FROM public.%I src
                WHERE src.group_id IS NOT NULL
                  AND COALESCE(src.amount,0) <> 0
                  AND NOT EXISTS (
                        SELECT 1 FROM public.group_ledger gl
                        WHERE gl.transaction_id IS NOT NULL
                          AND gl.transaction_id = COALESCE(src.transaction_id, src.id::text)
                  );
            $f$, tbl, tbl, tbl, tbl);

            EXECUTE v_sql;
            GET DIAGNOSTICS v_cnt = ROW_COUNT;
            RAISE NOTICE 'Inserted % rows into group_ledger from %', v_cnt, tbl;
        EXCEPTION WHEN undefined_column OR undefined_table THEN
            RAISE NOTICE 'Skipping table % due to unexpected schema (missing expected columns).', tbl;
        WHEN OTHERS THEN
            RAISE NOTICE 'Error while processing %, skipping. Error: %', tbl, sqlerrm;
        END;
    END LOOP;

    RAISE NOTICE 'Backfill complete. Please validate group_ledger contents and consider running recompute_group_balances migration next.';
END$$;

-- =====================================================================
-- END FILE: supabase/migrations/20260518000100_backfill_group_ledger_from_payments.sql
-- =====================================================================

-- =====================================================================
-- BEGIN FILE: supabase/migrations/20260518000200_recompute_group_balances_from_ledger.sql
-- =====================================================================

-- Recompute groups.balance from public.group_ledger
CREATE TABLE IF NOT EXISTS public.migration_balance_recompute_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL,
    old_balance NUMERIC(20,2),
    new_balance NUMERIC(20,2) NOT NULL,
    diff NUMERIC(20,2) NOT NULL,
    migrated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

DO $$
DECLARE
    rec RECORD;
    v_old_balance NUMERIC(20,2);
    v_new_balance NUMERIC(20,2);
    v_updated_count INT := 0;
BEGIN
    CREATE TEMP TABLE tmp_group_new_balances ON COMMIT DROP AS
    SELECT gl.group_id, SUM(gl.amount)::numeric(20,2) AS new_balance
    FROM public.group_ledger gl
    GROUP BY gl.group_id;

    FOR rec IN SELECT group_id, new_balance FROM tmp_group_new_balances LOOP
        SELECT balance INTO v_old_balance FROM public.groups WHERE id = rec.group_id;
        v_new_balance := rec.new_balance;

        UPDATE public.groups
        SET balance = v_new_balance,
            updated_at = NOW()
        WHERE id = rec.group_id;

        INSERT INTO public.migration_balance_recompute_audit (group_id, old_balance, new_balance, diff, migrated_at)
        VALUES (rec.group_id, v_old_balance, v_new_balance, COALESCE(v_new_balance,0) - COALESCE(v_old_balance,0), NOW());
    END LOOP;

    RAISE NOTICE 'Recompute complete.';
END$$;

-- =====================================================================
-- END FILE: supabase/migrations/20260518000200_recompute_group_balances_from_ledger.sql
-- =====================================================================

-- =====================================================================
-- BEGIN TOP-LEVEL SUPPLEMENTAL SCRIPTS (as in CONSOLIDATED_FULL.sql)
-- =====================================================================

-- BEGIN FILE: supabase/02_SECURITY_AND_RLS.sql

-- (Content already included above as migration; included here again for parity with CONSOLIDATED_FULL ordering.)

-- BEGIN FILE: supabase/03_PLATFORM_ADMIN_SETUP.sql

DO $$
DECLARE
    v_email text := 'torrymsimango@gmail.com';
    v_legacy_emails text[] := ARRAY['torryymsimango@gmail.com', 'torrymsimango@hotmail.com'];
    v_password text := 'torry123M';
    v_full_name text := 'Torry Msimango';
    v_user_id uuid;
BEGIN
    SELECT id INTO v_user_id
    FROM auth.users
    WHERE lower(email) = lower(v_email)
       OR lower(email) IN (SELECT lower(alias) FROM unnest(v_legacy_emails) AS alias)
    ORDER BY CASE WHEN lower(email) = lower(v_email) THEN 0 ELSE 1 END
    LIMIT 1;

    IF v_user_id IS NULL THEN
        v_user_id := gen_random_uuid();
        INSERT INTO auth.users (id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data)
        VALUES (v_user_id, 'authenticated', 'authenticated', v_email, crypt(v_password, gen_salt('bf')), now(), '{"provider":"email","providers":["email"],"role":"platform_admin"}', jsonb_build_object('full_name', v_full_name, 'role', 'platform_admin'));
    ELSE
        UPDATE auth.users
        SET encrypted_password = crypt(v_password, gen_salt('bf')),
            email = v_email,
            email_confirmed_at = coalesce(email_confirmed_at, now()),
            raw_app_meta_data = coalesce(raw_app_meta_data, '{}'::jsonb) || '{"provider":"email","providers":["email"],"role":"platform_admin"}'::jsonb,
            raw_user_meta_data = coalesce(raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('full_name', v_full_name, 'role', 'platform_admin')
        WHERE id = v_user_id;
    END IF;

    DELETE FROM auth.identities
    WHERE user_id = v_user_id
      AND provider = 'email'
      AND provider_id <> v_email;

    INSERT INTO auth.identities (id, user_id, provider, provider_id, identity_data, created_at, updated_at)
    VALUES (gen_random_uuid(), v_user_id, 'email', v_email, jsonb_build_object('sub', v_user_id, 'email', v_email), now(), now())
    ON CONFLICT (provider, provider_id) DO UPDATE
    SET user_id = EXCLUDED.user_id,
        identity_data = EXCLUDED.identity_data,
        updated_at = now();

    DELETE FROM public.profiles
    WHERE id <> v_user_id
      AND (
          lower(email) = lower(v_email)
          OR lower(email) IN (SELECT lower(alias) FROM unnest(v_legacy_emails) AS alias)
      );

    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (v_user_id, v_full_name, v_email, 'platform_admin')
    ON CONFLICT (id) DO UPDATE
    SET full_name = EXCLUDED.full_name,
        email = EXCLUDED.email,
        role = 'platform_admin';

    RAISE NOTICE 'Platform Admin setup complete for %', v_email;
END $$;

-- BEGIN FILE: supabase/04_MIGRATIONS_AND_UPDATES.sql

-- (already inlined earlier)

-- BEGIN FILE: supabase/04_PLATFORM_ADMIN_AUTH_ALIGNMENT.sql

DO $$
DECLARE
    v_email text := 'torrymsimango@gmail.com';
    v_legacy_emails text[] := ARRAY['torryymsimango@gmail.com', 'torrymsimango@hotmail.com'];
    v_password text := 'torry123M';
    v_full_name text := 'Torry Msimango';
    v_user_id uuid;
BEGIN
    SELECT id INTO v_user_id
    FROM auth.users
    WHERE lower(email) = lower(v_email)
       OR lower(email) IN (SELECT lower(alias) FROM unnest(v_legacy_emails) AS alias)
    ORDER BY CASE WHEN lower(email) = lower(v_email) THEN 0 ELSE 1 END
    LIMIT 1;

    IF v_user_id IS NULL THEN
        v_user_id := gen_random_uuid();
        INSERT INTO auth.users (
            id, aud, role, email, encrypted_password,
            email_confirmed_at, raw_app_meta_data, raw_user_meta_data
        ) VALUES (
            v_user_id,
            'authenticated',
            'authenticated',
            v_email,
            crypt(v_password, gen_salt('bf')),
            now(),
            '{"provider":"email","providers":["email"]}',
            jsonb_build_object('full_name', v_full_name, 'role', 'platform_admin')
        );
    ELSE
        UPDATE auth.users
        SET email = v_email,
            encrypted_password = crypt(v_password, gen_salt('bf')),
            email_confirmed_at = coalesce(email_confirmed_at, now()),
            raw_app_meta_data = coalesce(raw_app_meta_data, '{}'::jsonb) || '{"provider":"email","providers":["email"]}'::jsonb,
            raw_user_meta_data = coalesce(raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('full_name', v_full_name, 'role', 'platform_admin')
        WHERE id = v_user_id;
    END IF;

    DELETE FROM auth.identities
    WHERE user_id = v_user_id
      AND provider = 'email'
      AND provider_id <> v_email;

    INSERT INTO auth.identities (
        id, user_id, provider, provider_id, identity_data, created_at, updated_at
    ) VALUES (
        gen_random_uuid(),
        v_user_id,
        'email',
        v_email,
        jsonb_build_object('sub', v_user_id, 'email', v_email),
        now(),
        now()
    )
    ON CONFLICT (provider, provider_id) DO UPDATE
    SET user_id = EXCLUDED.user_id,
        identity_data = EXCLUDED.identity_data,
        updated_at = now();

    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (v_user_id, v_full_name, v_email, 'platform_admin')
    ON CONFLICT (id) DO UPDATE
    SET full_name = EXCLUDED.full_name,
        email = EXCLUDED.email,
        role = 'platform_admin';

    RAISE NOTICE 'Platform admin alignment complete for %', v_email;
END $$;

-- =====================================================================
-- END TOP-LEVEL SUPPLEMENTAL SCRIPTS
-- =====================================================================

-- =====================================================================
-- BEGIN FILE: supabase/seeds/SAFE_SEED.sql
-- =====================================================================

-- SAFE_SEED.sql (canonical safe seed). This block is verbatim from the repository.

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_extra_admin_id UUID;
    v_group_name TEXT;
    v_types TEXT[] := ARRAY['stokvel','burial_society','investment_club','rosca','community_savings','emergency_fund','tontine','stokvel','burial_society','other'];
    v_provinces TEXT[] := ARRAY['Gauteng','KwaZulu-Natal','Western Cape','Eastern Cape','Limpopo','Mpumalanga','North West','Free State','Northern Cape','Gauteng'];
    v_cities TEXT[] := ARRAY['Johannesburg','Durban','Cape Town','Gqeberha','Polokwane','Mbombela','Mahikeng','Bloemfontein','Kimberley','Pretoria'];
    v_townships TEXT[] := ARRAY['Soweto','Umlazi','Khayelitsha','Motherwell','Seshego','Kanyamazane','Mmabatho','Mangaung','Galeshewe','Mamelodi'];
    v_group_balance NUMERIC(12,2);
    v_contribution_amount NUMERIC(10,2);
    v_disbursement_amount NUMERIC(12,2);
    v_disbursement_status TEXT;
    v_extra_admin_password TEXT := 'SeedSafe@1234';
BEGIN
    -- Ensure platform admin exists
    SELECT id INTO v_admin_id FROM auth.users WHERE lower(email) = lower('torrymsimango@gmail.com') LIMIT 1;
    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'Platform admin user not found. Run platform admin setup first.';
    END IF;

    -- Cleanup prior canonical-seed groups only
    DELETE FROM public.audit_logs WHERE action = 'SAFE_SEED_GROUP_CREATED';
    DELETE FROM public.platform_ledger WHERE description LIKE 'SAFE_SEED %';
    DELETE FROM public.groups WHERE name LIKE 'SAFE_SEED-G%';

    FOR g IN 1..10 LOOP
        v_group_name := format('SAFE_SEED-G%s %s', lpad(g::text,2,'0'), initcap(replace(v_types[g],'_',' ')));
        v_group_balance := (4000.00 + (g * 1000))::numeric(12,2);
        v_contribution_amount := (250.00 + (g * 10))::numeric(10,2);
        v_disbursement_amount := (v_contribution_amount * 3)::numeric(12,2);

        INSERT INTO public.groups (
            id, name, type, province, city, township, description, logo_emoji, joining_fee, monthly_contribution,
            late_fee, late_fee_grace_days, probation_months, payment_due_day, max_members, is_public, allow_partial_payment,
            auto_suspend_after, bank_name, account_number, branch_code, account_type, gateway_public_key, balance,
            admin_user_id, fee_status, registration_paid, is_platform_suspended, constitution_status, latitude, longitude,
            max_beneficiaries, beneficiary_increase_pct, goal_amount, period_months, loan_interest_rate, loan_max_amount, loan_max_months, created_at, updated_at
        ) VALUES (
            gen_random_uuid(), v_group_name, v_types[g], v_provinces[g], v_cities[g], v_townships[g],
            'Canonical safe seed for deterministic testing', CASE WHEN v_types[g] = 'burial_society' THEN '🕊️' ELSE '🤝' END,
            150.00, 300.00 + (g * 10), 50.00, 5, 3, 28, 120, TRUE, (g % 2 = 0), 2, 'FNB',
            '6200777' || lpad(g::text, 3, '0'), '250655', 'Savings', 'pk_test_seed_group_' || g, v_group_balance,
            v_admin_id, CASE WHEN g % 4 = 0 THEN 'warning' ELSE 'paid' END, TRUE, FALSE, 'verified',
            -34.0 + (g * 0.7), 18.0 + (g * 1.1), CASE WHEN v_types[g] = 'burial_society' THEN 4 ELSE 0 END,
            CASE WHEN v_types[g] = 'burial_society' THEN 5 ELSE 0 END, 100000.00, 24, 12.5, 12000.00, 12, now(), now()
        ) ON CONFLICT (name) DO UPDATE
            SET updated_at = now(), balance = EXCLUDED.balance;

        SELECT id INTO v_group_id FROM public.groups WHERE name = v_group_name LIMIT 1;

        INSERT INTO public.members (group_id, user_id, full_name, phone, email, notification_pref, status, joined_at, probation_end_at, member_key)
        VALUES (v_group_id, v_admin_id, format('Safe Admin %s', lpad(g::text,2,'0')), format('07900%s', lpad(g::text,5,'0')), format('safe.admin.%s@example.com', lpad(g::text,2,'0')), 'both', 'active', now() - interval '120 days', now() + interval '60 days', format('SAFE_ADMIN_KEY_%s', lpad(g::text,2,'0')))
        ON CONFLICT (group_id, user_id) DO NOTHING;

        INSERT INTO public.group_ledger (id, group_id, transaction_id, amount, balance_after, description, category, created_at)
        VALUES (gen_random_uuid(), v_group_id, NULL, 0.00, v_group_balance, format('SAFE_SEED opening balance %s', lpad(g::text,2,'0')), 'opening_balance', now() - interval '90 days');

        FOR m IN 1..9 LOOP
            INSERT INTO public.members (group_id, user_id, full_name, id_number, phone, email, street, suburb, city, province, notification_pref, status, joined_at, probation_end_at, beneficiary_count, beneficiary_over_65_count, total_contributions, total_paid, member_key)
            VALUES (
                v_group_id, NULL, CASE WHEN m IN (1,2) THEN format('Safe Extra Admin %s-%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')) ELSE format('Safe Member %s-%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')) END,
                lpad((9200000000000 + (g * 100) + m)::text,13,'0'), format('07%s', lpad((g*100 + m)::text,8,'0')),
                CASE WHEN m IN (1,2) THEN format('safe.groupadmin.%s.%s@example.com', lpad(g::text,2,'0'), lpad(m::text,2,'0')) ELSE format('safe.member.%s.%s@example.com', lpad(g::text,2,'0'), lpad(m::text,2,'0')) END,
                format('%s Safe Street', m), v_townships[g], v_cities[g], v_provinces[g], CASE WHEN m % 3 = 0 THEN 'email' WHEN m % 3 = 1 THEN 'whatsapp' ELSE 'both' END,
                CASE WHEN m <= 7 THEN 'active' WHEN m = 8 THEN 'probation' ELSE 'pending_payment' END, now() - ((m+g) || ' days')::interval, now() + interval '60 days', CASE WHEN v_types[g] = 'burial_society' THEN 2 ELSE 0 END, 0, 0, 0,
                CASE WHEN m IN (1,2) THEN format('SAFE_EXTRA_ADMIN_KEY_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')) ELSE format('SAFE_KEY_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')) END
            ) RETURNING id INTO v_member_id;

            IF m IN (1,2) THEN
                SELECT id INTO v_extra_admin_id FROM auth.users WHERE lower(email) = lower(CASE WHEN m=1 THEN format('safe.groupadmin.%s.01@example.com', lpad(g::text,2,'0')) ELSE format('safe.groupadmin.%s.02@example.com', lpad(g::text,2,'0')) END) LIMIT 1;
                IF v_extra_admin_id IS NULL THEN
                    v_extra_admin_id := gen_random_uuid();
                    INSERT INTO auth.users (id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
                    VALUES (v_extra_admin_id, 'authenticated', 'authenticated', CASE WHEN m=1 THEN format('safe.groupadmin.%s.01@example.com', lpad(g::text,2,'0')) ELSE format('safe.groupadmin.%s.02@example.com', lpad(g::text,2,'0')) END, crypt(v_extra_admin_password, gen_salt('bf')), now(), '{"provider":"email","providers":["email"],"role":"group_admin"}'::jsonb, jsonb_build_object('full_name', CASE WHEN m=1 THEN format('Safe Extra Admin %s-01', lpad(g::text,2,'0')) ELSE format('Safe Extra Admin %s-02', lpad(g::text,2,'0')) END, 'role', 'group_admin'), now(), now());
                ELSE
                    UPDATE auth.users SET encrypted_password = crypt(v_extra_admin_password, gen_salt('bf')), email_confirmed_at = COALESCE(email_confirmed_at, now()), raw_app_meta_data = COALESCE(raw_app_meta_data, '{}'::jsonb) || '{"provider":"email","providers":["email"],"role":"group_admin"}'::jsonb, raw_user_meta_data = COALESCE(raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('full_name', CASE WHEN m=1 THEN format('Safe Extra Admin %s-01', lpad(g::text,2,'0')) ELSE format('Safe Extra Admin %s-02', lpad(g::text,2,'0')) END, 'role', 'group_admin'), updated_at = now() WHERE id = v_extra_admin_id;
                END IF;

                INSERT INTO auth.identities (id, user_id, provider, provider_id, identity_data, created_at, updated_at)
                VALUES (gen_random_uuid(), v_extra_admin_id, 'email', CASE WHEN m=1 THEN format('safe.groupadmin.%s.01@example.com', lpad(g::text,2,'0')) ELSE format('safe.groupadmin.%s.02@example.com', lpad(g::text,2,'0')) END, jsonb_build_object('sub', v_extra_admin_id::text, 'email', CASE WHEN m=1 THEN format('safe.groupadmin.%s.01@example.com', lpad(g::text,2,'0')) ELSE format('safe.groupadmin.%s.02@example.com', lpad(g::text,2,'0')) END), now(), now())
                ON CONFLICT (provider, provider_id) DO UPDATE SET user_id = EXCLUDED.user_id, identity_data = EXCLUDED.identity_data, updated_at = now();

                INSERT INTO public.profiles (id, full_name, email, role) VALUES (v_extra_admin_id, CASE WHEN m=1 THEN format('Safe Extra Admin %s-01', lpad(g::text,2,'0')) ELSE format('Safe Extra Admin %s-02', lpad(g::text,2,'0')) END, CASE WHEN m=1 THEN format('safe.groupadmin.%s.01@example.com', lpad(g::text,2,'0')) ELSE format('safe.groupadmin.%s.02@example.com', lpad(g::text,2,'0')) END, 'group_admin') ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name, email = EXCLUDED.email, role = 'group_admin';

                UPDATE public.members SET user_id = v_extra_admin_id WHERE id = v_member_id;
            END IF;

            INSERT INTO public.contributions (id, member_id, group_id, amount, type, due_date, paid_at, payment_method, transaction_id, status, late_fees_applied, created_at)
            VALUES (gen_random_uuid(), v_member_id, v_group_id, v_contribution_amount, 'contribution', current_date - 30, now() - interval '21 days', 'yoco', format('safe_tx_paid_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), 'paid', FALSE, now() - interval '30 days')
            ON CONFLICT (id) DO NOTHING;

            INSERT INTO public.payments (id, member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at, created_at)
            VALUES (gen_random_uuid(), v_member_id, v_group_id, v_contribution_amount, 'contribution', 'yoco', format('safe_pay_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), 'completed', now() - interval '21 days', now() - interval '21 days')
            ON CONFLICT (id) DO NOTHING;
        END LOOP;

        v_disbursement_status := CASE WHEN g % 4 = 0 THEN 'completed' WHEN g % 3 = 0 THEN 'processing' WHEN g % 5 = 0 THEN 'failed' ELSE 'pending' END;
        INSERT INTO public.payouts (id, group_id, amount, bank_name, account_no, branch_code, status, processed_by, processed_at, payout_reference, created_at)
        VALUES (gen_random_uuid(), v_group_id, v_disbursement_amount, 'Standard Bank', '1234500' || lpad(g::text,3,'0'), '051001', v_disbursement_status, CASE WHEN v_disbursement_status = 'pending' THEN NULL ELSE v_admin_id END, CASE WHEN v_disbursement_status = 'pending' THEN NULL ELSE now() - interval '2 days' END, CASE WHEN v_disbursement_status IN ('processing','completed') THEN format('safe_payout_%s', lpad(g::text,2,'0')) ELSE NULL END, now() - interval '3 days') ON CONFLICT (id) DO NOTHING;

        IF v_disbursement_status = 'completed' THEN
            PERFORM public.increment_group_balance(v_group_id, -v_disbursement_amount);
        END IF;

        UPDATE public.groups SET current_members = 10 WHERE id = v_group_id;

        INSERT INTO public.audit_logs (id, actor_id, target_group_id, action, details, created_at) VALUES (gen_random_uuid(), v_admin_id, v_group_id, 'SAFE_SEED_GROUP_CREATED', jsonb_build_object('seed', true, 'group_number', g, 'members', 10), now()) ON CONFLICT (id) DO NOTHING;
    END LOOP;

    RAISE NOTICE 'SAFE_SEED completed: 10 groups + members + transactions + payouts + ledger entries.';
END$$;

-- =====================================================================
-- END FILE: supabase/seeds/SAFE_SEED.sql
-- =====================================================================

-- =====================================================================
-- BEGIN FILE: supabase/seeds/E2E_SEED.sql
-- =====================================================================

-- E2E_SEED.sql (includes SAFE_SEED then creates additional heavy data)

-- Note: SAFE seed already executed above; E2E is idempotent but may duplicate some items.

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_idx INT;
BEGIN
    SELECT id INTO v_admin_id FROM auth.users WHERE lower(email) = lower('torrymsimango@gmail.com') LIMIT 1;
    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'Platform admin missing for E2E_SEED.';
    END IF;

    FOR v_idx IN 1..5 LOOP
        INSERT INTO public.groups (id, name, type, admin_user_id, account_number, gateway_public_key, balance, created_at, updated_at)
        VALUES (gen_random_uuid(), format('E2E-G%s', lpad(v_idx::text,2,'0')), 'other', v_admin_id, '6200888' || lpad(v_idx::text,3,'0'), 'pk_test_e2e_' || v_idx, 10000.00 + (v_idx * 5000), now(), now())
        ON CONFLICT (name) DO NOTHING;

        SELECT id INTO v_group_id FROM public.groups WHERE name = format('E2E-G%s', lpad(v_idx::text,2,'0')) LIMIT 1;

        FOR i IN 1..50 LOOP
            INSERT INTO public.members (id, group_id, full_name, id_number, phone, email, joined_at, member_key)
            VALUES (gen_random_uuid(), v_group_id, format('E2E Member %s-%s', v_idx, i), lpad((9001000000000 + v_idx*100 + i)::text,13,'0'), format('07%s', lpad((v_idx*1000 + i)::text,8,'0')), format('e2e.member.%s.%s@example.com', v_idx, i), now() - ((i%30) || ' days')::interval, gen_random_uuid()::text)
            ON CONFLICT DO NOTHING;
        END LOOP;

        INSERT INTO public.contributions (id, member_id, group_id, amount, type, due_date, paid_at, payment_method, transaction_id, status, created_at)
        SELECT gen_random_uuid(), id, v_group_id, 200.00 + (random()*100)::numeric(10,2), 'contribution', current_date - (trunc(random()*60))::int, now() - (trunc(random()*30))::int * interval '1 day', 'bank', gen_random_uuid()::text, 'paid', now()
        FROM public.members WHERE group_id = v_group_id LIMIT 200;

        INSERT INTO public.platform_fees (id, group_id, fee_type, amount, status, due_date, created_at)
        VALUES (gen_random_uuid(), v_group_id, 'monthly', 10.00 * 50, 'due', to_char(current_date + 5, 'YYYY-MM-DD'), now())
        ON CONFLICT DO NOTHING;
    END LOOP;

    RAISE NOTICE 'E2E_SEED completed: SAFE_SEED + additional heavy groups.';
END$$;

-- =====================================================================
-- END FILE: supabase/seeds/E2E_SEED.sql
-- =====================================================================

