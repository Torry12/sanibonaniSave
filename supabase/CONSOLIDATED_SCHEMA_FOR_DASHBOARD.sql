-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — GROUND ZERO MASTER MIGRATION (Consolidated)
-- Generated: 2026-10-17
-- Version: 2.2 (Regenerated from the authoritative master scripts)
--
-- Applied in this order, mirroring supabase/00_REBUILD_ALL.sql and
-- supabase/online/00_REBUILD_ONLINE.sql:
--   01_DATABASE_SCHEMA.sql
--   02_FUNCTIONS_AND_TRIGGERS.sql
--   18_DEPLOY_PROCESS_COMPLEX_PAYMENT_V4.sql
--   03_SECURITY_AND_RLS.sql
--   04_PLATFORM_ADMIN_SETUP.sql
--   05_PERFORMANCE_VIEWS.sql
--   30_STORAGE_RLS.sql

--
-- Deliberately excluded: 07_RESET_DATABASE.sql (destructive) and every SEED_*.sql
-- (dev fixtures are not part of the schema baseline). Delta migrations under
-- supabase/migrations/ that change routine behaviour are folded into the masters
-- (43_platform_fee_payment_idempotency.sql -> pay_platform_fee_v2 above).
-- ─────────────────────────────────────────────────────────────────────────────

-- ─────────────────────────────────────────────────────────────────────────────
-- INLINED MODULE: 01_DATABASE_SCHEMA.sql
-- ─────────────────────────────────────────────────────────────────────────────
-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — SUPABASE MASTER SCHEMA (Consolidated)
-- Version: 10.0 (High-Fidelity Architecture - August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. EXTENSIONS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- 2. PROFILES (Synced with Auth)
CREATE TABLE IF NOT EXISTS public.profiles (
    id          UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name   TEXT NOT NULL CHECK (char_length(full_name) >= 3),
    email       TEXT NOT NULL CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    phone       TEXT,
    role        TEXT NOT NULL DEFAULT 'member' CHECK (role IN ('platform_admin', 'group_admin', 'member')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2.1 FINANCIAL PROVIDERS (Bank Neutral Architecture)
CREATE TABLE IF NOT EXISTS public.financial_providers (
    id                TEXT PRIMARY KEY,
    name              TEXT NOT NULL,
    type              TEXT NOT NULL CHECK (type IN ('bank', 'payment_gateway', 'insurer', 'lender', 'investment')),
    adapter_type      TEXT NOT NULL,
    logo_url          TEXT,
    supported_ops     TEXT[] DEFAULT '{}',
    is_active         BOOLEAN DEFAULT TRUE,
    config            JSONB DEFAULT '{}',
    metadata          JSONB DEFAULT '{}',
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 3. GROUPS
CREATE TABLE IF NOT EXISTS public.groups (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                   TEXT NOT NULL CHECK (char_length(name) >= 3),
    type                   TEXT NOT NULL DEFAULT 'other' CHECK (type IN ('burial_society', 'stokvel', 'rosca', 'investment_club', 'emergency_fund', 'community_savings', 'hybrid_savings_group', 'tontine', 'other')),
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
    account_number         TEXT,
    branch_code            TEXT,
    account_type           TEXT DEFAULT 'Savings',
    gateway_public_key     TEXT,
    balance                NUMERIC(12,2) DEFAULT 0, -- Hardened: constraint checked in triggers
    goal_amount            NUMERIC(12,2) DEFAULT 0 CHECK (goal_amount >= 0),
    period_months          INTEGER DEFAULT 12 CHECK (period_months > 0),
    burial_claim_amount    NUMERIC(10,2) DEFAULT 10000.00 CHECK (burial_claim_amount >= 0),
    min_members_for_claims INTEGER DEFAULT 10 CHECK (min_members_for_claims >= 0),
    min_reserve_percentage NUMERIC(5,2) DEFAULT 20.00 CHECK (min_reserve_percentage >= 0),
    admin_user_id          UUID REFERENCES auth.users(id) NOT NULL,
    fee_status             TEXT DEFAULT 'due' CHECK (fee_status IN ('paid', 'due', 'warning', 'suspended', 'pending_activation', 'overdue')),
    registration_paid      BOOLEAN DEFAULT FALSE,
    rosca_rotation_method  TEXT NOT NULL DEFAULT 'fixed' CHECK (rosca_rotation_method IN ('fixed', 'random_draw', 'need_based', 'auction')),
    investment_payout_date DATE,
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
    financial_provider_id  TEXT REFERENCES public.financial_providers(id),
    financial_provider_metadata JSONB DEFAULT '{}'::jsonb,
    created_at             TIMESTAMPTZ DEFAULT NOW(),
    updated_at             TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(name)
);

-- 4. MEMBERS
CREATE TABLE IF NOT EXISTS public.members (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id             UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    user_id              UUID REFERENCES auth.users(id),
    member_key           TEXT,
    full_name            TEXT NOT NULL CHECK (char_length(full_name) >= 2),
    id_number            TEXT CHECK (id_number IS NULL OR id_number ~ '^[0-9]{13}$'),
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
    contract_url         TEXT,
    contract_status      TEXT DEFAULT 'pending' CHECK (contract_status IN ('pending', 'verified', 'rejected')),
    bank_name            TEXT,
    account_no           TEXT,
    branch_code          TEXT,
    beneficiary_count         INTEGER DEFAULT 0 CHECK (beneficiary_count >= 0),
    beneficiary_over_65_count INTEGER DEFAULT 0 CHECK (beneficiary_over_65_count >= 0),
    monthly_contribution_override NUMERIC(10,2) CHECK (monthly_contribution_override >= 0),
    total_contributions  INTEGER DEFAULT 0 CHECK (total_contributions >= 0),
    total_paid           NUMERIC(12,2) DEFAULT 0, -- Hardened: Allowed to be negative for settlements
    fcm_token            TEXT,
    rosca_position       INTEGER,
    created_at           TIMESTAMPTZ DEFAULT NOW(),
    updated_at           TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(group_id, user_id),
    UNIQUE(group_id, member_key)
);

-- 4.1 MEMBER DOCUMENTS
CREATE TABLE IF NOT EXISTS public.member_documents (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id         UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    document_type     TEXT NOT NULL,
    document_url      TEXT NOT NULL,
    status            TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'verified', 'rejected')),
    notes             TEXT,
    verified_at       TIMESTAMPTZ,
    verified_by       UUID REFERENCES auth.users(id),
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 5. CONTRIBUTIONS & PAYMENTS
CREATE TABLE IF NOT EXISTS public.contributions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id              UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id               UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount                 NUMERIC(10,2) NOT NULL,
    type                   TEXT DEFAULT 'contribution' CHECK (type IN ('contribution', 'joining_fee', 'registration_contribution', 'late_fee', 'member_fee', 'member_fee_ledger', 'loan_settlement_extraction')),
    due_date               DATE NOT NULL,
    paid_at                TIMESTAMPTZ,
    payment_method         TEXT DEFAULT 'bank' CHECK (payment_method IN ('yoco', 'stitch', 'payfast', 'bank', 'cash', 'wallet', 'other')),
    transaction_id         TEXT,
    policy_id              TEXT,
    status                 TEXT DEFAULT 'due' CHECK (status IN ('paid', 'due', 'overdue', 'partial', 'missed')),
    late_fees_applied      BOOLEAN DEFAULT FALSE,
    late_fee_amount        NUMERIC(10,2) DEFAULT 0 CHECK (late_fee_amount >= 0),
    principal_amount       NUMERIC(10,2) DEFAULT 0 CHECK (principal_amount >= 0),
    created_at             TIMESTAMPTZ DEFAULT NOW(),
    updated_at             TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(group_id, transaction_id)
);

CREATE TABLE IF NOT EXISTS public.payments (
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

-- 6. LOANS & REPAYMENTS
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
    contract_url      TEXT,
    surety_amount     NUMERIC(12,2),
    status            TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'active', 'partially_paid', 'completed', 'rejected', 'overdue', 'cancelled', 'defaulted')),
    purpose           TEXT,
    amortization_type TEXT DEFAULT 'flat' CHECK (amortization_type IN ('flat', 'reducing_balance', 'bullet')),
    reviewed_by       UUID REFERENCES auth.users(id),
    reviewed_at       TIMESTAMPTZ,
    admin_notes       TEXT,
    rejection_reason  TEXT,
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
    payment_method    TEXT DEFAULT 'bank',
    transaction_id    TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(group_id, transaction_id)
);

-- 7. BENEFICIARIES & CLAIMS
CREATE TABLE IF NOT EXISTS public.beneficiaries (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id             UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    member_id            UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    full_name            TEXT NOT NULL CHECK (char_length(full_name) >= 2),
    id_number            TEXT CHECK (id_number IS NULL OR id_number ~ '^[0-9]{13}$'),
    relationship         TEXT,
    date_of_birth        DATE,
    is_over_65           BOOLEAN DEFAULT FALSE,
    document_url         TEXT,
    face_photo_url       TEXT,
    document_status      TEXT DEFAULT 'pending' CHECK (document_status IN ('pending', 'verified', 'rejected')),
    created_at           TIMESTAMPTZ DEFAULT NOW(),
    updated_at           TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(member_id, id)
);

CREATE TABLE IF NOT EXISTS public.beneficiary_payout_claims (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    member_id         UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    beneficiary_id    UUID NOT NULL,
    beneficiary_name  TEXT NOT NULL,
    face_photo_url    TEXT,
    beneficiary_id_url TEXT,
    death_certificate_url TEXT,
    member_id_url      TEXT,
    cause_of_death    TEXT,
    date_of_death     DATE,
    claim_amount      NUMERIC(12,2) NOT NULL CHECK (claim_amount > 0),
    bank_name         TEXT,
    account_no        TEXT,
    branch_code       TEXT,
    account_holder    TEXT,
    notes             TEXT,
    status            TEXT DEFAULT 'submitted' CHECK (status IN ('submitted', 'under_review', 'escalated', 'approved', 'rejected', 'paid', 'cancelled')),
    reviewed_by       UUID REFERENCES auth.users(id),
    reviewed_at       TIMESTAMPTZ,
    admin_notes       TEXT,
    rejection_reason  TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 8. PAYOUTS
CREATE TABLE IF NOT EXISTS public.payouts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    member_id         UUID REFERENCES public.members(id) ON DELETE CASCADE,
    amount            NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    bank_name         TEXT NOT NULL,
    account_no        TEXT NOT NULL,
    branch_code       TEXT NOT NULL,
    status            TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'cancelled', 'group_approved', 'rejected')),
    payout_type       TEXT DEFAULT 'custom',
    description       TEXT,
    processed_by      UUID REFERENCES auth.users(id),
    processed_at      TIMESTAMPTZ,
    payout_reference  TEXT,
    supporting_document_url    TEXT,
    supporting_document_status TEXT DEFAULT 'pending' CHECK (supporting_document_status IN ('pending', 'verified', 'rejected')),
    metadata          JSONB DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 9. LEDGERS
DROP TABLE IF EXISTS public.group_ledger CASCADE;
CREATE TABLE public.group_ledger (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    member_id         UUID REFERENCES public.members(id) ON DELETE SET NULL,
    transaction_id    TEXT,
    amount            NUMERIC(12,2) NOT NULL,
    balance_after     NUMERIC(12,2) NOT NULL,
    description       TEXT NOT NULL,
    category          TEXT NOT NULL,
    parent_entry_id   UUID REFERENCES public.group_ledger(id), -- 🛡️ Traceability: Link related entries
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(group_id, transaction_id) -- Prevent double-ledgering
);

CREATE TABLE IF NOT EXISTS public.ledger_checkpoints (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    ledger_entry_id   UUID REFERENCES public.group_ledger(id) NOT NULL,
    balance           NUMERIC(12,2) NOT NULL,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(group_id, ledger_entry_id)
);

DROP TABLE IF EXISTS public.platform_ledger CASCADE;
CREATE TABLE public.platform_ledger (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE SET NULL,
    transaction_id    TEXT,
    amount            NUMERIC(12,2) NOT NULL,
    balance_after     NUMERIC(12,2) NOT NULL,
    description       TEXT NOT NULL,
    category          TEXT NOT NULL,
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 9.1 LEDGER CHECKPOINTS
CREATE INDEX IF NOT EXISTS idx_ledger_checkpoints_group_id ON public.ledger_checkpoints(group_id);

-- 10. BANK INTEGRATION
CREATE TABLE IF NOT EXISTS public.bank_transactions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    bank_reference    TEXT NOT NULL,
    amount            NUMERIC(12,2) NOT NULL,
    transaction_date  DATE NOT NULL,
    description       TEXT,
    status            TEXT NOT NULL DEFAULT 'unreconciled' CHECK (status IN ('unreconciled', 'matched', 'ignored', 'manual_fix')),
    reconciled_with   UUID, -- References group_ledger.id
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 10.1 BANK CONNECTIONS
CREATE TABLE IF NOT EXISTS public.bank_connections (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id               UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    provider_id            TEXT REFERENCES public.financial_providers(id) NOT NULL,
    status                 TEXT NOT NULL CHECK (status IN ('active', 'expired', 'disconnected', 'pending')),
    account_name           TEXT,
    account_number_hidden  TEXT,
    linked_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at             TIMESTAMPTZ,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(group_id)
);

-- 11. GOVERNANCE (Voting)
CREATE TABLE IF NOT EXISTS public.group_polls (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id              UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    created_by_member_id  UUID REFERENCES public.members(id) ON DELETE SET NULL,
    title                 TEXT NOT NULL CHECK (char_length(title) >= 3),
    description           TEXT,
    type                  TEXT DEFAULT 'general',
    status                TEXT DEFAULT 'open' CHECK (status IN ('draft', 'open', 'closed', 'cancelled')),
    effect_status         TEXT DEFAULT 'none',
    effect_data           TEXT,
    allow_multiple_choice BOOLEAN DEFAULT FALSE,
    starts_at             TIMESTAMPTZ,
    ends_at               TIMESTAMPTZ,
    created_at            TIMESTAMPTZ DEFAULT NOW(),
    updated_at            TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.group_poll_options (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id     UUID REFERENCES public.group_polls(id) ON DELETE CASCADE NOT NULL,
    label       TEXT NOT NULL,
    position    INTEGER DEFAULT 1,
    vote_count  INTEGER DEFAULT 0,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.group_poll_votes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id     UUID REFERENCES public.group_polls(id) ON DELETE CASCADE NOT NULL,
    option_id   UUID REFERENCES public.group_poll_options(id) ON DELETE CASCADE NOT NULL,
    member_id   UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id    UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(poll_id, member_id)
);

-- 12. SETTLEMENTS & RECONCILIATIONS
CREATE TABLE IF NOT EXISTS public.settlements (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id    TEXT NOT NULL,
    provider_reference TEXT,
    status            TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'completed', 'failed')),
    settled_at        TIMESTAMPTZ,
    fee_deducted      NUMERIC(10,2) DEFAULT 0,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.reconciliations (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    period_start      TIMESTAMPTZ NOT NULL,
    period_end        TIMESTAMPTZ NOT NULL,
    status            TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'balanced', 'discrepancy_found', 'resolved')),
    ledger_balance    NUMERIC(12,2) NOT NULL DEFAULT 0,
    actual_balance    NUMERIC(12,2) NOT NULL DEFAULT 0,
    discrepancy       NUMERIC(12,2) NOT NULL DEFAULT 0,
    notes             TEXT,
    performed_by      UUID REFERENCES auth.users(id),
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 13. ANALYTICS & MONITORING
CREATE TABLE IF NOT EXISTS public.group_actuarial_metrics (
    group_id                       UUID PRIMARY KEY REFERENCES public.groups(id) ON DELETE CASCADE,
    composite_risk_score           INTEGER DEFAULT 0,
    reserve_adequacy_pct           NUMERIC(10,2) DEFAULT 0,
    solvency_ratio                 NUMERIC(12,2) DEFAULT 0,
    payment_rate_pct               NUMERIC(10,2) DEFAULT 0,
    loss_ratio_pct                 NUMERIC(10,2) DEFAULT 0,
    contribution_sufficiency_pct   NUMERIC(10,2) DEFAULT 0,
    funding_ratio_pct              NUMERIC(10,2) DEFAULT 0,
    pure_premium                   NUMERIC(12,2) DEFAULT 0,
    gross_premium                  NUMERIC(12,2) DEFAULT 0,
    solvency_margin_pct            NUMERIC(10,2) DEFAULT 0,
    capital_adequacy_pct           NUMERIC(10,2) DEFAULT 0,
    expected_annual_claims         NUMERIC(12,2) DEFAULT 0,
    expected_annual_claims_count   NUMERIC(10,2) DEFAULT 0,
    break_even_members             INTEGER DEFAULT 0,
    actuarial_present_value        NUMERIC(12,2) DEFAULT 0,
    insolvency_months              INTEGER DEFAULT 0,
    updated_at                     TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.group_health_scores (
    group_id          UUID PRIMARY KEY REFERENCES public.groups(id) ON DELETE CASCADE,
    overall_score     INTEGER NOT NULL,
    zone              TEXT NOT NULL CHECK (zone IN ('GREEN', 'YELLOW', 'RED')),
    components        JSONB NOT NULL DEFAULT '{}'::jsonb,
    recommendations   JSONB NOT NULL DEFAULT '[]'::jsonb,
    generated_at      TIMESTAMPTZ DEFAULT NOW(),
    expires_at        TIMESTAMPTZ,
    updated_at        BIGINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS public.member_behavior_track (
    member_id                   UUID PRIMARY KEY REFERENCES public.members(id) ON DELETE CASCADE,
    group_id                    UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    member_id_number            TEXT,
    total_contributions         INTEGER DEFAULT 0,
    on_time_contributions       INTEGER DEFAULT 0,
    late_contributions          INTEGER DEFAULT 0,
    overdue_count               INTEGER DEFAULT 0,
    missed_contributions        INTEGER DEFAULT 0,
    payment_consistency_score   NUMERIC(5,2) DEFAULT 0,
    average_days_late           NUMERIC(5,2) DEFAULT 0,
    current_payment_streak      INTEGER DEFAULT 0,
    longest_payment_streak      INTEGER DEFAULT 0,
    has_broken_streak_recently  BOOLEAN DEFAULT FALSE,
    total_amount_contributed    NUMERIC(12,2) DEFAULT 0,
    total_late_fees_paid        NUMERIC(12,2) DEFAULT 0,
    pending_late_fees           NUMERIC(12,2) DEFAULT 0,
    total_outstanding_amount    NUMERIC(12,2) DEFAULT 0,
    total_loans_requested       INTEGER DEFAULT 0,
    total_loans_approved        INTEGER DEFAULT 0,
    total_loans_completed       INTEGER DEFAULT 0,
    active_loans                INTEGER DEFAULT 0,
    overdue_loans               INTEGER DEFAULT 0,
    loan_default_count          INTEGER DEFAULT 0,
    loan_completion_rate        NUMERIC(5,2) DEFAULT 0,
    duplicate_transaction_count INTEGER DEFAULT 0,
    suspicious_activity_count   INTEGER DEFAULT 0,
    unusual_payment_patterns    BOOLEAN DEFAULT FALSE,
    multiple_accounts_detected  BOOLEAN DEFAULT FALSE,
    velocity_check_failed       BOOLEAN DEFAULT FALSE,
    rapid_disbursement_attempts INTEGER DEFAULT 0,
    behavior_score              NUMERIC(5,2) NOT NULL DEFAULT 100.0,
    fraud_score                 NUMERIC(5,2) DEFAULT 0,
    fraud_risk_level            TEXT NOT NULL DEFAULT 'low',
    member_status               TEXT NOT NULL DEFAULT 'fair',
    is_flagged_for_review       BOOLEAN DEFAULT FALSE,
    is_suspended                BOOLEAN DEFAULT FALSE,
    suspension_reason           TEXT,
    review_notes                TEXT,
    months_in_group             INTEGER DEFAULT 0,
    joined_at                   TIMESTAMPTZ,
    last_activity_at            TIMESTAMPTZ,
    last_contribution_at        TIMESTAMPTZ,
    admin_notes                 TEXT,
    last_reviewed_at            TIMESTAMPTZ,
    reviewed_by                 UUID REFERENCES auth.users(id),
    created_at                  TIMESTAMPTZ DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.fraud_detection_events (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id         UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    event_type        TEXT NOT NULL,
    severity          TEXT NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    details           JSONB NOT NULL DEFAULT '{}'::jsonb,
    action_taken      TEXT,
    resolved          BOOLEAN DEFAULT FALSE,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.behavior_analytics_summary (
    group_id                 UUID PRIMARY KEY REFERENCES public.groups(id) ON DELETE CASCADE,
    total_members_tracked    INTEGER DEFAULT 0,
    excellent_members        INTEGER DEFAULT 0,
    good_members             INTEGER DEFAULT 0,
    fair_members             INTEGER DEFAULT 0,
    poor_members             INTEGER DEFAULT 0,
    suspended_members        INTEGER DEFAULT 0,
    high_fraud_risk_count    INTEGER DEFAULT 0,
    flagged_members_count    INTEGER DEFAULT 0,
    average_behavior_score   NUMERIC(5,2) DEFAULT 0,
    average_fraud_score      NUMERIC(5,2) DEFAULT 0,
    on_time_payment_rate     NUMERIC(5,2) DEFAULT 0,
    loan_default_rate        NUMERIC(5,2) DEFAULT 0,
    calculated_at            TIMESTAMPTZ DEFAULT NOW()
);

-- 13.1 PLATFORM AUDIT REPORTS
CREATE TABLE IF NOT EXISTS public.platform_audit_reports (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    generated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    liquidity         NUMERIC(15,2) NOT NULL DEFAULT 0,
    revenue           NUMERIC(15,2) NOT NULL DEFAULT 0,
    risk              JSONB NOT NULL DEFAULT '{}'::jsonb,
    groups            JSONB NOT NULL DEFAULT '{}'::jsonb,
    top_level_stats   JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata          JSONB DEFAULT '{}'::jsonb
);

-- 13.2 GENERIC AUDIT REPORTS (App Cache)
CREATE TABLE IF NOT EXISTS public.audit_reports (
    id                TEXT PRIMARY KEY, -- format: [type]_[id], e.g. member_abc, group_123, platform
    report_type       TEXT NOT NULL CHECK (report_type IN ('member', 'group', 'platform')),
    target_id         UUID,
    content_json      TEXT NOT NULL,
    generated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 14. SYSTEM CONFIGURATION
CREATE TABLE IF NOT EXISTS public.platform_settings (
    key        TEXT PRIMARY KEY,
    value      NUMERIC,
    value_text TEXT,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.platform_fees (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    fee_type          TEXT NOT NULL CHECK (fee_type IN ('registration', 'monthly')),
    amount_due        NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (amount_due >= 0),
    amount_paid       NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0),
    status            TEXT DEFAULT 'due' CHECK (status IN ('paid', 'due', 'warning', 'suspended')),
    due_date          DATE,
    paid_at           TIMESTAMPTZ,
    month_reference   DATE,
    transaction_id    TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(group_id, month_reference)
);

CREATE TABLE IF NOT EXISTS public.platform_fee_config (
    group_type       TEXT NOT NULL,
    rosca_cycle      INTEGER DEFAULT 0,
    registration_fee NUMERIC(10,2) NOT NULL DEFAULT 0,
    monthly_fee      NUMERIC(10,2) NOT NULL DEFAULT 0,
    shortfall_recovery_fee NUMERIC(10,2) NOT NULL DEFAULT 5.0,
    updated_at       TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (group_type, rosca_cycle)
);

CREATE TABLE IF NOT EXISTS public.pending_operations (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type           TEXT NOT NULL,
    payload        TEXT NOT NULL,
    group_id       UUID REFERENCES public.groups(id) ON DELETE SET NULL,
    created_at     BIGINT NOT NULL, -- Epoch ms to match Room
    retry_count    INTEGER DEFAULT 0,
    last_error     TEXT,
    server_synced  BOOLEAN DEFAULT FALSE
);

-- 15. COMMUNICATION & AUDIT
CREATE TABLE IF NOT EXISTS public.notifications (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id       UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    member_id      UUID REFERENCES public.members(id) ON DELETE CASCADE,
    message        TEXT NOT NULL,
    channel        TEXT DEFAULT 'both' CHECK (channel IN ('whatsapp', 'email', 'both')),
    trigger_event  TEXT DEFAULT 'custom',
    created_at     TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.audit_logs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id          TEXT NOT NULL,
    target_member_id  UUID,
    target_group_id   UUID,
    action            TEXT NOT NULL,
    related_id        UUID,
    details           JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 16. DEBUG & LOGGING
CREATE TABLE IF NOT EXISTS public.debug_log (
    id          SERIAL PRIMARY KEY,
    tag         TEXT,
    message     TEXT,
    details     JSONB,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.simulation_results (
    id             SERIAL PRIMARY KEY,
    test_name      TEXT,
    success_count  INT,
    total_tests    INT,
    final_balance  NUMERIC,
    member_paid    NUMERIC,
    created_at     TIMESTAMPTZ DEFAULT NOW()
);

-- 17. INDEXES
CREATE INDEX IF NOT EXISTS idx_groups_admin_user_id ON public.groups(admin_user_id);
CREATE INDEX IF NOT EXISTS idx_members_group_id ON public.members(group_id);
CREATE INDEX IF NOT EXISTS idx_members_user_id ON public.members(user_id);
CREATE INDEX IF NOT EXISTS idx_contributions_member_id ON public.contributions(member_id);
CREATE INDEX IF NOT EXISTS idx_loans_member_id ON public.loans(member_id);
CREATE INDEX IF NOT EXISTS idx_member_behavior_track_group_id ON public.member_behavior_track(group_id);
CREATE INDEX IF NOT EXISTS idx_fraud_events_member_id ON public.fraud_detection_events(member_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_group_id ON public.audit_logs(target_group_id);
CREATE INDEX IF NOT EXISTS idx_bank_tx_group_status ON public.bank_transactions(group_id, status);

-- 18. DEFAULT SETTINGS & PROVISIONING
INSERT INTO public.platform_settings (key, value) VALUES
    ('monthly_per_member', 10.0),
    ('registration_fee', 700.0),
    ('platform_bank_name_code', 1.0),
    ('platform_account_number', 62889900112.0),
    ('platform_branch_code', 250655.0)
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

-- 19. REALTIME PUBLICATION (Consolidated)
-- This ensures that the mobile app receives reactive updates for all core tables.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        CREATE PUBLICATION supabase_realtime;
    END IF;
END $$;

DO $$
DECLARE
    v_table text;
    v_tables text[] := ARRAY[
        'groups', 'members', 'contributions', 'loans', 'loan_repayments',
        'payouts', 'beneficiary_payout_claims', 'notifications',
        'group_ledger', 'bank_transactions', 'platform_fee_config',
        'group_polls', 'group_poll_options', 'group_poll_votes'
    ];
BEGIN
    FOREACH v_table IN ARRAY v_tables LOOP
        IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = v_table) THEN
            BEGIN
                EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', v_table);
            EXCEPTION WHEN others THEN
                NULL; -- Already exists or skip error
            END;
        END IF;
    END LOOP;
END $$;

-- 20. ANALYTICS INDEXES
CREATE INDEX IF NOT EXISTS idx_group_ledger_composite ON public.group_ledger(group_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON public.audit_logs(actor_id);
CREATE INDEX IF NOT EXISTS idx_payouts_composite ON public.payouts(group_id, status);
CREATE INDEX IF NOT EXISTS idx_members_name_trgm ON public.members USING gin (full_name gin_trgm_ops);

-- 21. DEFAULT PROVISIONING
INSERT INTO public.financial_providers (id, name, type, adapter_type, supported_ops)
VALUES
    ('sim_bank', 'Sanibonani Virtual Bank', 'bank', 'sim_bank', '{get_balance, get_transactions, make_payment}'),
    ('yoco', 'YoCo Card Payments', 'payment_gateway', 'yoco', '{initiate_checkout}'),
    ('stitch', 'Stitch Instant EFT', 'payment_gateway', 'stitch', '{initiate_checkout}'),
    ('nedbank', 'Nedbank (Direct)', 'bank', 'nedbank', '{get_balance, make_payment}'),
    ('fnb', 'First National Bank', 'bank', 'fnb', '{get_balance, get_transactions}'),
    ('platform_main', 'SanibonaniSave Revenue Account', 'bank', 'internal', '{make_payment, get_balance}')
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- INLINED MODULE: 02_FUNCTIONS_AND_TRIGGERS.sql
-- ─────────────────────────────────────────────────────────────────────────────
-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — SUPABASE MASTER FUNCTIONS & TRIGGERS
-- Version: 11.0 (Gold Standard Integrated - August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. SECURITY & AUTH HELPERS
CREATE OR REPLACE FUNCTION public.is_aal2()
RETURNS BOOLEAN AS $$
BEGIN
  RETURN (auth.jwt() ->> 'aal') = 'aal2';
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION public.is_platform_admin()
RETURNS BOOLEAN AS $$
BEGIN
  IF (auth.jwt() -> 'app_metadata' ->> 'role') = 'platform_admin' THEN RETURN true; END IF;
  RETURN EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'platform_admin');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION public.is_group_admin(p_group_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (SELECT 1 FROM public.groups WHERE id = p_group_id AND admin_user_id = auth.uid());
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (SELECT 1 FROM public.members WHERE group_id = p_group_id AND user_id = auth.uid());
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION public.can_access_member_storage(p_path text)
RETURNS BOOLEAN AS $$
DECLARE
    v_m_id text;
BEGIN
    v_m_id := (storage.foldername(p_path))[2];
    IF v_m_id IS NULL THEN RETURN false; END IF;
    IF public.is_platform_admin() THEN RETURN true; END IF;
    RETURN EXISTS (
        SELECT 1 FROM public.members m
        JOIN public.groups g ON m.group_id = g.id
        WHERE m.id::text = v_m_id AND (m.user_id = auth.uid() OR g.admin_user_id = auth.uid())
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE;

-- 2. UTILITY FUNCTIONS
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 3. ATOMIC ACCOUNTING CORE
DROP FUNCTION IF EXISTS public.increment_group_balance(UUID, NUMERIC, TEXT, TEXT, TEXT, UUID);
CREATE OR REPLACE FUNCTION public.increment_group_balance(
    p_group_id UUID,
    p_amount NUMERIC,
    p_description TEXT DEFAULT 'Atomic balance update',
    p_category TEXT DEFAULT 'adjustment',
    p_transaction_id TEXT DEFAULT NULL,
    p_member_id UUID DEFAULT NULL
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_new_balance NUMERIC;
BEGIN
    UPDATE public.groups SET balance = balance + p_amount, updated_at = NOW()
    WHERE id = p_group_id RETURNING balance INTO v_new_balance;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND'; END IF;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id)
    VALUES (p_group_id, p_transaction_id, p_amount, v_new_balance, p_description, p_category, p_member_id);

    RETURN v_new_balance;
END;
$$;

CREATE OR REPLACE FUNCTION public.record_platform_revenue_v2(
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id TEXT DEFAULT NULL,
    p_group_id UUID DEFAULT NULL
) RETURNS JSONB AS $$
DECLARE
    v_last_balance NUMERIC;
    v_new_balance NUMERIC;
BEGIN
    SELECT COALESCE(SUM(amount), 0) INTO v_last_balance FROM public.platform_ledger;
    v_new_balance := v_last_balance + p_amount;

    INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category, group_id)
    VALUES (p_transaction_id, p_amount, v_new_balance, p_description, p_category, p_group_id);

    RETURN jsonb_build_object('amount', p_amount, 'balance_after', v_new_balance);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 4. CONTRIBUTION SYSTEM (V2)
CREATE OR REPLACE FUNCTION public.internal_post_contribution(
    p_member_id UUID,
    p_group_id UUID,
    p_amount NUMERIC,
    p_due_date DATE,
    p_paid_at TIMESTAMPTZ,
    p_status TEXT,
    p_tx_id TEXT,
    p_type TEXT,
    p_description TEXT DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_contrib RECORD;
    v_new_bal NUMERIC;
    v_m_name TEXT;
    v_l_desc TEXT;
    v_l_cat TEXT;
BEGIN
    SELECT full_name INTO v_m_name FROM public.members WHERE id = p_member_id AND group_id = p_group_id;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] Member not found in group'; END IF;

    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, transaction_id, type)
    VALUES (p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_tx_id, p_type)
    RETURNING * INTO v_contrib;

    v_l_desc := COALESCE(p_description, initcap(replace(p_type, '_', ' ')) || ' from ' || v_m_name);
    v_l_cat := CASE WHEN p_type IN ('member_fee', 'member_fee_ledger') THEN 'platform_fee' ELSE p_type END;

    IF p_status IN ('paid', 'partial') THEN
        IF p_type NOT IN ('member_fee', 'member_fee_ledger', 'registration_contribution') THEN
            v_new_bal := public.increment_group_balance(p_group_id, p_amount, v_l_desc, v_l_cat, v_contrib.id::text, p_member_id);
        ELSE
            SELECT balance INTO v_new_bal FROM public.groups WHERE id = p_group_id;
            PERFORM public.record_platform_revenue_v2(p_amount, v_l_desc, v_l_cat, v_contrib.id::text, p_group_id);
        END IF;
    ELSE
        SELECT balance INTO v_new_bal FROM public.groups WHERE id = p_group_id;
    END IF;

    RETURN jsonb_build_object('contribution', to_jsonb(v_contrib), 'new_balance', v_new_bal);
END;
$$;

CREATE OR REPLACE FUNCTION public.record_contribution_v2(
    p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_due_date DATE,
    p_paid_at TIMESTAMPTZ, p_status TEXT, p_tx_id TEXT DEFAULT NULL,
    p_type TEXT DEFAULT 'contribution', p_description TEXT DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_contrib RECORD;
    v_new_bal NUMERIC;
BEGIN
    SELECT balance INTO v_new_bal FROM public.groups WHERE id = p_group_id FOR UPDATE;
    IF p_tx_id IS NOT NULL THEN
        SELECT * INTO v_contrib FROM public.contributions WHERE group_id = p_group_id AND transaction_id = p_tx_id;
        IF FOUND THEN
            SELECT balance INTO v_new_bal FROM public.groups WHERE id = p_group_id;
            RETURN jsonb_build_object('contribution', to_jsonb(v_contrib), 'new_balance', v_new_bal, 'idempotent', true);
        END IF;
    END IF;

    RETURN public.internal_post_contribution(p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_tx_id, p_type, p_description);
END;
$$;

-- 5. LOANS
CREATE OR REPLACE FUNCTION public.record_loan_repayment_v1(
    p_loan_id UUID, p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_payment_method TEXT DEFAULT 'bank'
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_repay RECORD;
    v_m_name TEXT;
    v_new_bal NUMERIC;
BEGIN
    SELECT balance INTO v_new_bal FROM public.groups WHERE id = p_group_id FOR UPDATE;
    SELECT full_name INTO v_m_name FROM public.members WHERE id = p_member_id;
    INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, payment_method)
    VALUES (p_loan_id, p_member_id, p_group_id, p_amount, p_payment_method)
    RETURNING * INTO v_repay;
    SELECT public.increment_group_balance(p_group_id, p_amount, 'Loan repayment from ' || v_m_name, 'loan_repayment', v_repay.id::text, p_member_id) INTO v_new_bal;
    RETURN jsonb_build_object('repayment', to_jsonb(v_repay), 'new_balance', v_new_bal);
END;
$$;

-- 6. AUDIT
DROP FUNCTION IF EXISTS public.recompute_ledger_running_balances_v2(UUID);
CREATE OR REPLACE FUNCTION public.recompute_ledger_running_balances_v2(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_entry RECORD;
    v_bal NUMERIC := 0;
    v_count INT := 0;
BEGIN
    FOR v_entry IN (SELECT id, amount FROM public.group_ledger WHERE group_id = p_group_id ORDER BY created_at ASC, id ASC) LOOP
        v_bal := v_bal + v_entry.amount;
        v_count := v_count + 1;
        UPDATE public.group_ledger SET balance_after = v_bal WHERE id = v_entry.id;
    END LOOP;
    UPDATE public.groups SET balance = v_bal WHERE id = p_group_id;
    RETURN jsonb_build_object('final_balance', v_bal, 'count', v_count);
END;
$$;

-- 6.1 AUDIT AND FIX (LEGACY WRAPPER FOR V3 COMPATIBILITY)
CREATE OR REPLACE FUNCTION public.audit_and_fix_group_balance_v1(p_group_id UUID, p_fix BOOLEAN DEFAULT FALSE)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_ledger_sum NUMERIC;
    v_current_bal NUMERIC;
    v_diff NUMERIC;
BEGIN
    SELECT COALESCE(SUM(amount), 0) INTO v_ledger_sum FROM public.group_ledger WHERE group_id = p_group_id;
    SELECT balance INTO v_current_bal FROM public.groups WHERE id = p_group_id;
    v_diff := v_current_bal - v_ledger_sum;

    IF p_fix AND ABS(v_diff) > 0.01 THEN
        UPDATE public.groups SET balance = v_ledger_sum, updated_at = NOW() WHERE id = p_group_id;
        v_current_bal := v_ledger_sum;
        v_diff := 0;
    END IF;

    RETURN jsonb_build_object(
        'group_id', p_group_id,
        'ledger_sum', v_ledger_sum,
        'current_balance', v_current_bal,
        'discrepancy', v_diff,
        'fixed', p_fix
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.recalculate_all_group_financials_v3(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_stats JSONB;
    v_recon_id UUID;
    v_report JSONB;
BEGIN
    v_recon_id := gen_random_uuid();
    SELECT public.recompute_ledger_running_balances_v2(p_group_id) INTO v_stats;

    -- Insert a record into reconciliations for forensic tracking
    INSERT INTO public.reconciliations (
        id, group_id, period_start, period_end, status,
        ledger_balance, actual_balance, discrepancy, notes, created_at
    ) VALUES (
        v_recon_id, p_group_id, NOW() - INTERVAL '365 days', NOW(), 'balanced',
        (v_stats->>'final_balance')::NUMERIC, (v_stats->>'final_balance')::NUMERIC, 0,
        'Deep ledger reconstruction performed via system repair.', NOW()
    );

    v_report := jsonb_build_object(
        'reconstruction_id', v_recon_id::text,
        'group_id', p_group_id::text,
        'entries_reconstructed', (v_stats->>'count')::int,
        'final_balance', (v_stats->>'final_balance')::text,
        'reconciliation_report', jsonb_build_object(
            'id', v_recon_id::text,
            'group_id', p_group_id::text,
            'period_start', (EXTRACT(EPOCH FROM (NOW() - INTERVAL '365 days')) * 1000)::bigint,
            'period_end', (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint,
            'status', 'balanced',
            'ledger_balance', (v_stats->>'final_balance')::text,
            'actual_balance', (v_stats->>'final_balance')::text,
            'discrepancy', '0.00',
            'notes', 'Deep ledger reconstruction performed via system repair.',
            'created_at', NOW()::text
        ),
        'timestamp', NOW()::text
    );

    RETURN v_report;
END;
$$;

-- 7. AUDIT REPORTS
DROP FUNCTION IF EXISTS public.generate_platform_audit_report_v1();
CREATE OR REPLACE FUNCTION public.generate_platform_audit_report_v1()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_liquidity NUMERIC;
    v_revenue NUMERIC;
    v_high_risk INT;
    v_critical_risk INT;
    v_total_groups INT;
    v_suspended_groups INT;
    v_total_members INT;
    v_report JSONB;
BEGIN
    SELECT COALESCE(SUM(balance), 0) INTO v_liquidity FROM public.groups;
    SELECT COALESCE(SUM(amount), 0) INTO v_revenue FROM public.platform_ledger;
    SELECT COUNT(*) INTO v_high_risk FROM public.member_behavior_track WHERE behavior_score <= 40;
    SELECT COUNT(*) INTO v_critical_risk FROM public.member_behavior_track WHERE behavior_score <= 20;
    SELECT COUNT(*) INTO v_total_groups FROM public.groups;
    SELECT COUNT(*) INTO v_suspended_groups FROM public.groups WHERE is_platform_suspended = true;
    SELECT COUNT(*) INTO v_total_members FROM public.members;

    v_report := jsonb_build_object(
        'generated_at', NOW()::text,
        'liquidity', v_liquidity::text,
        'revenue', v_revenue::text,
        'risk', jsonb_build_object(
            'high_risk_members', v_high_risk,
            'critical_risk_members', v_critical_risk
        ),
        'groups', jsonb_build_object(
            'total_groups', v_total_groups,
            'suspended_groups', v_suspended_groups
        ),
        'top_level_stats', jsonb_build_object(
            'total_groups', v_total_groups,
            'total_members', v_total_members,
            'total_liquidity', v_liquidity::text,
            'total_revenue', v_revenue::text
        )
    );

    INSERT INTO public.platform_audit_reports (generated_at, liquidity, revenue, risk, groups, top_level_stats)
    VALUES (NOW(), v_liquidity, v_revenue, v_report->'risk', v_report->'groups', v_report->'top_level_stats');

    -- Also cache in the generic audit_reports table for standard UI observation
    INSERT INTO public.audit_reports (id, report_type, content_json, generated_at, updated_at)
    VALUES ('platform', 'platform', v_report::text, NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET
        content_json = EXCLUDED.content_json,
        generated_at = EXCLUDED.generated_at,
        updated_at = EXCLUDED.updated_at;

    RETURN v_report;
END;
$$;

CREATE OR REPLACE FUNCTION public.generate_group_audit_report_v1(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_group RECORD;
    v_total INT;
    v_active INT;
    v_probation INT;
    v_suspended INT;
    v_inflow NUMERIC;
    v_outflow NUMERIC;
    v_last_check TIMESTAMPTZ;
    v_recon_status TEXT;
    v_recon_diff NUMERIC;
    v_risk INT;
    v_solvency NUMERIC;
    v_reserve NUMERIC;
    v_report JSONB;
BEGIN
    SELECT * INTO v_group FROM public.groups WHERE id = p_group_id;
    IF NOT FOUND THEN RETURN NULL; END IF;

    -- Membership
    SELECT COUNT(*) INTO v_total FROM public.members WHERE group_id = p_group_id;
    SELECT COUNT(*) INTO v_active FROM public.members WHERE group_id = p_group_id AND status = 'active';
    SELECT COUNT(*) INTO v_probation FROM public.members WHERE group_id = p_group_id AND status = 'probation';
    SELECT COUNT(*) INTO v_suspended FROM public.members WHERE group_id = p_group_id AND status = 'suspended';

    -- Finances
    SELECT COALESCE(SUM(amount), 0) INTO v_inflow FROM public.group_ledger WHERE group_id = p_group_id AND amount > 0;
    SELECT COALESCE(SUM(ABS(amount)), 0) INTO v_outflow FROM public.group_ledger WHERE group_id = p_group_id AND amount < 0;

    -- Reconciliation
    SELECT created_at, status, discrepancy INTO v_last_check, v_recon_status, v_recon_diff
    FROM public.reconciliations WHERE group_id = p_group_id ORDER BY created_at DESC LIMIT 1;

    -- Actuarial
    SELECT composite_risk_score, solvency_ratio, reserve_adequacy_pct INTO v_risk, v_solvency, v_reserve
    FROM public.group_actuarial_metrics WHERE group_id = p_group_id;

    v_report := jsonb_build_object(
        'group_id', p_group_id::text,
        'name', v_group.name,
        'type', v_group.type,
        'balance', v_group.balance::text,
        'membership', jsonb_build_object(
            'total', v_total,
            'active', v_active,
            'probation', v_probation,
            'suspended', v_suspended
        ),
        'finances', jsonb_build_object(
            'total_inflow', v_inflow::text,
            'total_outflow', v_outflow::text
        ),
        'reconciliation', jsonb_build_object(
            'last_check', v_last_check::text,
            'status', COALESCE(v_recon_status, 'none'),
            'discrepancy', COALESCE(v_recon_diff, 0)::text
        ),
        'actuarial', jsonb_build_object(
            'risk_score', COALESCE(v_risk, 0),
            'solvency_ratio', COALESCE(v_solvency, 0),
            'reserve_adequacy', COALESCE(v_reserve, 0)
        )
    );

    INSERT INTO public.audit_reports (id, report_type, target_id, content_json, generated_at, updated_at)
    VALUES ('group_' || p_group_id::text, 'group', p_group_id, v_report::text, NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET content_json = EXCLUDED.content_json, updated_at = NOW();

    RETURN v_report;
END;
$$;

CREATE OR REPLACE FUNCTION public.generate_member_audit_report_v1(p_member_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_member RECORD;
    v_behavior RECORD;
    v_total_count INT;
    v_total_paid NUMERIC;
    v_total_outstanding NUMERIC;
    v_last_payment TIMESTAMPTZ;
    v_loan_count INT;
    v_total_borrowed NUMERIC;
    v_total_repaid NUMERIC;
    v_recent_logs JSONB;
    v_report JSONB;
BEGIN
    SELECT * INTO v_member FROM public.members WHERE id = p_member_id;
    IF NOT FOUND THEN RETURN NULL; END IF;

    SELECT * INTO v_behavior FROM public.member_behavior_track WHERE member_id = p_member_id;

    -- Contributions
    SELECT COUNT(*), SUM(CASE WHEN status = 'paid' THEN amount ELSE 0 END), SUM(CASE WHEN status != 'paid' THEN amount ELSE 0 END), MAX(paid_at)
    INTO v_total_count, v_total_paid, v_total_outstanding, v_last_payment
    FROM public.contributions WHERE member_id = p_member_id;

    -- Loans
    SELECT COUNT(*), SUM(amount), SUM(total_repaid)
    INTO v_loan_count, v_total_borrowed, v_total_repaid
    FROM public.loans WHERE member_id = p_member_id AND status NOT IN ('rejected', 'cancelled');

    -- Logs
    SELECT jsonb_agg(row_to_json(l)) INTO v_recent_logs FROM (
        SELECT id, action, created_at::text FROM public.audit_logs
        WHERE target_member_id = p_member_id ORDER BY created_at DESC LIMIT 5
    ) l;

    v_report := jsonb_build_object(
        'member_id', p_member_id::text,
        'full_name', v_member.full_name,
        'status', v_member.status,
        'joined_at', v_member.joined_at::text,
        'contributions', jsonb_build_object(
            'total_count', v_total_count,
            'total_paid', COALESCE(v_total_paid, 0)::text,
            'total_outstanding', COALESCE(v_total_outstanding, 0)::text,
            'last_payment', v_last_payment::text
        ),
        'loans', jsonb_build_object(
            'count', v_loan_count,
            'total_borrowed', COALESCE(v_total_borrowed, 0)::text,
            'total_repaid', COALESCE(v_total_repaid, 0)::text,
            'outstanding', (COALESCE(v_total_borrowed, 0) - COALESCE(v_total_repaid, 0))::text
        ),
        'behavior', jsonb_build_object(
            'score', COALESCE(v_behavior.behavior_score, 100),
            'risk_level', COALESCE(v_behavior.fraud_risk_level, 'low'),
            'consistency', COALESCE(v_behavior.payment_consistency_score, 0)
        ),
        'recent_audit_logs', COALESCE(v_recent_logs, '[]'::jsonb)
    );

    INSERT INTO public.audit_reports (id, report_type, target_id, content_json, generated_at, updated_at)
    VALUES ('member_' || p_member_id::text, 'member', p_member_id, v_report::text, NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET content_json = EXCLUDED.content_json, updated_at = NOW();

    RETURN v_report;
END;
$$;

-- 8. TRIGGERS
CREATE OR REPLACE FUNCTION public.fn_sync_group_member_count()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE public.groups SET current_members = (SELECT count(*) FROM public.members WHERE group_id = COALESCE(NEW.group_id, OLD.group_id) AND status != 'suspended')
    WHERE id = COALESCE(NEW.group_id, OLD.group_id);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_sync_group_member_count ON public.members;
CREATE TRIGGER tr_sync_group_member_count AFTER INSERT OR UPDATE OR DELETE ON public.members FOR EACH ROW EXECUTE FUNCTION public.fn_sync_group_member_count();

CREATE OR REPLACE FUNCTION public.fn_trigger_ensure_admin_is_member()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.members (group_id, user_id, full_name, status, joined_at)
    VALUES (NEW.id, NEW.admin_user_id, 'Group Admin', 'active', NOW())
    ON CONFLICT (group_id, user_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_ensure_admin_is_member ON public.groups;
CREATE TRIGGER tr_ensure_admin_is_member AFTER INSERT ON public.groups FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_ensure_admin_is_member();

-- 9. AUDIT LOGGING SYSTEM
CREATE OR REPLACE FUNCTION public.log_audit_event(
    p_action TEXT,
    p_actor_id TEXT DEFAULT 'system',
    p_target_member_id UUID DEFAULT NULL,
    p_target_group_id UUID DEFAULT NULL,
    p_details JSONB DEFAULT '{}'::jsonb,
    p_related_id UUID DEFAULT NULL
) RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_id UUID;
BEGIN
    INSERT INTO public.audit_logs (actor_id, target_member_id, target_group_id, action, details, related_id)
    VALUES (p_actor_id, p_target_member_id, p_target_group_id, p_action, p_details, p_related_id)
    RETURNING id INTO v_id;
    RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.fn_member_status_audit() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status IS DISTINCT FROM NEW.status THEN
        PERFORM public.log_audit_event('MEMBER_STATUS_CHANGED', 'system', NEW.id, NEW.group_id, jsonb_build_object('old_status', OLD.status, 'new_status', NEW.status), NEW.id);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS tr_member_status_audit ON public.members;
CREATE TRIGGER tr_member_status_audit AFTER UPDATE OF status ON public.members FOR EACH ROW EXECUTE FUNCTION public.fn_member_status_audit();

CREATE OR REPLACE FUNCTION public.fn_member_document_audit() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.document_1_status IS DISTINCT FROM NEW.document_1_status THEN
        PERFORM public.log_audit_event('DOCUMENT_VERIFIED', 'system', NEW.id, NEW.group_id, jsonb_build_object('index', 1, 'status', NEW.document_1_status), NEW.id);
    END IF;
    IF OLD.document_2_status IS DISTINCT FROM NEW.document_2_status THEN
        PERFORM public.log_audit_event('DOCUMENT_VERIFIED', 'system', NEW.id, NEW.group_id, jsonb_build_object('index', 2, 'status', NEW.document_2_status), NEW.id);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS tr_member_document_audit ON public.members;
CREATE TRIGGER tr_member_document_audit AFTER UPDATE OF document_1_status, document_2_status ON public.members FOR EACH ROW EXECUTE FUNCTION public.fn_member_document_audit();

CREATE OR REPLACE FUNCTION public.fn_trigger_reconciliation_audit() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status IS DISTINCT FROM NEW.status AND NEW.status = 'resolved' THEN
        PERFORM public.log_audit_event('RECONCILIATION_RESOLVED', 'admin', NULL, NEW.group_id, jsonb_build_object('recon_id', NEW.id, 'notes', NEW.notes), NEW.id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS tr_reconciliation_audit ON public.reconciliations;
CREATE TRIGGER tr_reconciliation_audit AFTER UPDATE ON public.reconciliations FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_reconciliation_audit();

CREATE OR REPLACE FUNCTION public.reconcile_bank_transaction_v1(
    p_bank_tx_id UUID,
    p_ledger_id UUID
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_group_id UUID;
BEGIN
    SELECT group_id INTO v_group_id
    FROM public.bank_transactions
    WHERE id = p_bank_tx_id;

    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'Bank transaction % not found', p_bank_tx_id;
    END IF;

    UPDATE public.bank_transactions
    SET status = 'reconciled',
        reconciled_with = p_ledger_id
    WHERE id = p_bank_tx_id;

    PERFORM public.log_audit_event(
        'BANK_TX_RECONCILED',
        'admin',
        NULL,
        v_group_id,
        jsonb_build_object('bank_tx_id', p_bank_tx_id, 'ledger_id', p_ledger_id),
        p_bank_tx_id
    );
END;
$$;

-- 10. PLATFORM FEE & BILLING SYSTEM
CREATE OR REPLACE FUNCTION public.pay_platform_fee_v2(
    p_group_id UUID,
    p_amount NUMERIC,
    p_fee_type TEXT, -- 'monthly', 'registration'
    p_tx_id TEXT DEFAULT NULL,
    p_from_group_fund BOOLEAN DEFAULT TRUE
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_current_bal NUMERIC;
    v_allocated NUMERIC := 0;
    v_fee RECORD;
    v_group_name TEXT;
BEGIN
    -- Row lock first: serialises concurrent submissions of the same tx_id.
    SELECT name, balance INTO v_group_name, v_current_bal FROM public.groups WHERE id = p_group_id FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND: %', p_group_id;
    END IF;

    -- ── Idempotency guard ────────────────────────────────────────────────
    -- A NULL tx_id cannot be deduplicated, so fall through to normal processing;
    -- callers are expected to always supply a deterministic reference.
    IF p_tx_id IS NOT NULL AND (
        EXISTS (SELECT 1 FROM public.group_ledger   WHERE group_id = p_group_id AND transaction_id = p_tx_id)
        OR EXISTS (SELECT 1 FROM public.platform_ledger WHERE transaction_id = p_tx_id)
        OR EXISTS (SELECT 1 FROM public.platform_fees  WHERE group_id = p_group_id AND transaction_id = p_tx_id)
    ) THEN
        RETURN jsonb_build_object(
            'paid', p_amount,
            'remaining_allocated', p_amount,
            'transaction_id', p_tx_id,
            'status', 'idempotent'
        );
    END IF;

    IF p_from_group_fund THEN
        IF v_current_bal < p_amount THEN
            RAISE EXCEPTION '[FIN_002] INSUFFICIENT_FUNDS: Group balance is %', v_current_bal;
        END IF;

        -- Deduct from group
        PERFORM public.increment_group_balance(
            p_group_id,
            -p_amount,
            'Platform ' || p_fee_type || ' fee payment',
            'platform_fee',
            p_tx_id
        );
    END IF;

    -- Record platform revenue
    PERFORM public.record_platform_revenue_v2(
        p_amount,
        'Fee payment from ' || v_group_name,
        p_fee_type || '_fee',
        p_tx_id,
        p_group_id
    );

    -- Waterfall allocation for 'monthly' fees
    IF p_fee_type = 'monthly' THEN
        FOR v_fee IN (
            SELECT id, amount_due, amount_paid
            FROM public.platform_fees
            WHERE group_id = p_group_id AND status != 'paid' AND fee_type = 'monthly'
            ORDER BY due_date ASC
        ) LOOP
            IF p_amount - v_allocated <= 0 THEN EXIT; END IF;

            DECLARE
                v_to_pay NUMERIC := LEAST(v_fee.amount_due - v_fee.amount_paid, p_amount - v_allocated);
            BEGIN
                UPDATE public.platform_fees
                SET amount_paid = amount_paid + v_to_pay,
                    status = CASE WHEN (amount_paid + v_to_pay) >= amount_due THEN 'paid' ELSE status END,
                    paid_at = NOW(),
                    transaction_id = COALESCE(transaction_id, p_tx_id)
                WHERE id = v_fee.id;

                v_allocated := v_allocated + v_to_pay;
            END;
        END LOOP;
    ELSIF p_fee_type = 'registration' THEN
        UPDATE public.groups SET registration_paid = TRUE WHERE id = p_group_id;
    END IF;

    -- Update group standing
    IF NOT EXISTS (SELECT 1 FROM public.platform_fees WHERE group_id = p_group_id AND status != 'paid') THEN
        UPDATE public.groups SET fee_status = 'paid', is_platform_suspended = FALSE WHERE id = p_group_id;
    END IF;

    RETURN jsonb_build_object(
        'paid', p_amount,
        'remaining_allocated', p_amount - v_allocated,
        'transaction_id', p_tx_id,
        'status', 'processed'
    );
END;
$$;

/**
 * Automates the monthly billing for all active groups.
 * Should be run on the 1st of every month via pg_cron or external trigger.
 */
CREATE OR REPLACE FUNCTION public.automate_monthly_billing_v1()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_group RECORD;
    v_fee_amount NUMERIC;
    v_count INT := 0;
    v_monthly_fee NUMERIC;
BEGIN
    -- Get default monthly fee from platform_settings
    SELECT (value)::NUMERIC INTO v_monthly_fee FROM public.platform_settings WHERE key = 'monthly_per_member';

    FOR v_group IN (
        SELECT id, current_members FROM public.groups
        WHERE registration_paid = TRUE AND is_platform_suspended = FALSE
    ) LOOP
        v_fee_amount := v_group.current_members * COALESCE(v_monthly_fee, 10.0);

        IF v_fee_amount > 0 THEN
            INSERT INTO public.platform_fees (group_id, fee_type, amount_due, due_date, month_reference, status)
            VALUES (
                v_group.id,
                'monthly',
                v_fee_amount,
                (CURRENT_DATE + INTERVAL '1 month' - INTERVAL '1 day')::DATE, -- End of month
                date_trunc('month', CURRENT_DATE)::DATE,
                'due'
            )
            ON CONFLICT DO NOTHING; -- Unique constraint on (group_id, month_reference) recommended

            UPDATE public.groups SET fee_status = 'due' WHERE id = v_group.id AND fee_status = 'paid';
            v_count := v_count + 1;
        END IF;
    END LOOP;

    RETURN jsonb_build_object('groups_billed', v_count, 'processed_at', NOW());
END;
$$;

-- 10.1 AGGREGATE & GOVERNANCE SYNC TRIGGERS
-- Back-ported from the GROUND_ZERO baseline so master-built tiers maintain derived
-- totals server-side. groups.current_members is owned by tr_sync_group_member_count
-- above, so the baseline's duplicate counter trigger was intentionally not ported.

CREATE OR REPLACE FUNCTION public.fn_sync_single_member_financials(p_member_id UUID)
RETURNS VOID AS $$
BEGIN
    UPDATE public.members SET
        total_paid = (
            SELECT COALESCE(SUM(amount), 0)
            FROM public.contributions
            WHERE member_id = p_member_id AND status = 'paid'
            AND type IN ('contribution', 'joining_fee', 'registration_contribution', 'loan_settlement_extraction')
        ),
        total_contributions = (
            SELECT COUNT(*)
            FROM public.contributions
            WHERE member_id = p_member_id AND status = 'paid'
            AND type IN ('contribution', 'registration_contribution')
        ),
        updated_at = NOW()
    WHERE id = p_member_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.fn_trigger_sync_member_financials() RETURNS TRIGGER AS $$
DECLARE
    v_m_id UUID;
BEGIN
    v_m_id := COALESCE(NEW.member_id, OLD.member_id);
    IF v_m_id IS NOT NULL THEN
        PERFORM public.fn_sync_single_member_financials(v_m_id);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS tr_sync_member_financials_contrib ON public.contributions;
CREATE TRIGGER tr_sync_member_financials_contrib AFTER INSERT OR UPDATE OR DELETE ON public.contributions FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_sync_member_financials();

CREATE OR REPLACE FUNCTION public.fn_sync_single_loan_totals(p_loan_id UUID)
RETURNS VOID AS $$
DECLARE
    v_rep NUMERIC;
    v_target NUMERIC;
BEGIN
    SELECT COALESCE(SUM(amount), 0) INTO v_rep FROM public.loan_repayments WHERE loan_id = p_loan_id;
    SELECT total_to_repay INTO v_target FROM public.loans WHERE id = p_loan_id;
    UPDATE public.loans SET total_repaid = v_rep, status = CASE WHEN v_rep >= v_target - 0.01 THEN 'completed' WHEN v_rep > 0 THEN 'partially_paid' ELSE status END, updated_at = NOW() WHERE id = p_loan_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.fn_trigger_sync_loan_repayment_total() RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT' OR TG_OP = 'UPDATE') THEN PERFORM public.fn_sync_single_loan_totals(NEW.loan_id); END IF;
    IF (TG_OP = 'DELETE' OR (TG_OP = 'UPDATE' AND OLD.loan_id IS DISTINCT FROM NEW.loan_id)) THEN PERFORM public.fn_sync_single_loan_totals(OLD.loan_id); END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS tr_sync_loan_repayment_total ON public.loan_repayments;
CREATE TRIGGER tr_sync_loan_repayment_total AFTER INSERT OR UPDATE OR DELETE ON public.loan_repayments FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_sync_loan_repayment_total();

CREATE OR REPLACE FUNCTION public.fn_member_behavior_init() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.member_behavior_track (member_id, group_id, behavior_score, fraud_risk_level)
    VALUES (NEW.id, NEW.group_id, 100.0, 'low')
    ON CONFLICT (member_id) DO NOTHING;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS tr_member_behavior_init ON public.members;
CREATE TRIGGER tr_member_behavior_init AFTER INSERT ON public.members FOR EACH ROW EXECUTE FUNCTION public.fn_member_behavior_init();

CREATE OR REPLACE FUNCTION public.fn_member_financial_sync_on_change() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        -- No sync needed for deleted member (cascaded) or just clear?
        -- Usually we keep it.
    ELSE
        IF pg_trigger_depth() < 2 THEN
            PERFORM public.fn_sync_single_member_financials(NEW.id);
        END IF;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS tr_member_financial_sync_member ON public.members;
CREATE TRIGGER tr_member_financial_sync_member AFTER INSERT OR UPDATE OF status ON public.members FOR EACH ROW EXECUTE FUNCTION public.fn_member_financial_sync_on_change();

CREATE OR REPLACE FUNCTION public.fn_trigger_update_behavior_metrics() RETURNS TRIGGER AS $$
DECLARE
    v_m_id UUID;
    v_g_id UUID;
    v_score NUMERIC(5,2) := 100.0;
    v_risk TEXT := 'low';
    v_late_count INT;
BEGIN
    v_m_id := COALESCE(NEW.member_id, OLD.member_id);
    v_g_id := COALESCE(NEW.group_id, OLD.group_id);

    -- 1. Simple heuristic: Deduct 10 points for each overdue contribution
    SELECT COUNT(*) INTO v_late_count FROM public.contributions WHERE member_id = v_m_id AND status = 'overdue';
    v_score := (v_score - (v_late_count * 10.0))::NUMERIC(5,2);

    IF v_score < 40 THEN v_risk := 'high';
    ELSIF v_score < 70 THEN v_risk := 'medium';
    END IF;

    UPDATE public.member_behavior_track
    SET behavior_score = GREATEST(0, v_score),
        fraud_risk_level = v_risk,
        updated_at = NOW()
    WHERE member_id = v_m_id;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS tr_update_behavior_metrics ON public.contributions;
CREATE TRIGGER tr_update_behavior_metrics AFTER INSERT OR UPDATE ON public.contributions FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_update_behavior_metrics();

DROP TRIGGER IF EXISTS tr_update_behavior_metrics_loans ON public.loan_repayments;
CREATE TRIGGER tr_update_behavior_metrics_loans AFTER INSERT OR UPDATE ON public.loan_repayments FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_update_behavior_metrics();

CREATE OR REPLACE FUNCTION public.fn_trigger_sync_poll_vote_count() RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN UPDATE public.group_poll_options SET vote_count = vote_count + 1 WHERE id = NEW.option_id;
    ELSIF (TG_OP = 'DELETE') THEN UPDATE public.group_poll_options SET vote_count = vote_count - 1 WHERE id = OLD.option_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS tr_sync_poll_vote_count ON public.group_poll_votes;
CREATE TRIGGER tr_sync_poll_vote_count AFTER INSERT OR DELETE ON public.group_poll_votes FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_sync_poll_vote_count();

CREATE OR REPLACE FUNCTION public.fn_trigger_sync_poll_status() RETURNS TRIGGER AS $$
BEGIN
    -- This would normally be handled by a cron job, but we can hook into updates
    IF NEW.ends_at <= NOW() AND NEW.status = 'open' THEN
        NEW.status := 'closed';
        PERFORM public.log_audit_event('POLL_AUTO_CLOSED', 'system_gov', NULL, NEW.group_id, jsonb_build_object('poll_id', NEW.id), NEW.id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_sync_poll_status ON public.group_polls;
CREATE TRIGGER tr_sync_poll_status BEFORE UPDATE ON public.group_polls FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_sync_poll_status();

CREATE OR REPLACE FUNCTION public.fn_trigger_voting_audit() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF TG_TABLE_NAME = 'group_polls' THEN
            PERFORM public.log_audit_event('POLL_CREATED', NEW.created_by_member_id::text, NULL, NEW.group_id, jsonb_build_object('poll_id', NEW.id, 'title', NEW.title), NEW.id);
        ELSIF TG_TABLE_NAME = 'group_poll_votes' THEN
            PERFORM public.log_audit_event('VOTE_CAST', NEW.member_id::text, NEW.member_id, NEW.group_id, jsonb_build_object('poll_id', NEW.poll_id), NEW.poll_id);
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS tr_poll_audit ON public.group_polls;
CREATE TRIGGER tr_poll_audit AFTER INSERT ON public.group_polls FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_voting_audit();

DROP TRIGGER IF EXISTS tr_vote_audit ON public.group_poll_votes;
CREATE TRIGGER tr_vote_audit AFTER INSERT ON public.group_poll_votes FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_voting_audit();

-- 10.2 SIGNUP BOOTSTRAP
-- Defined for baseline parity. No trigger is attached to auth.users here, matching the
-- baseline: Supabase provisions that attachment out of band per project.
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
DECLARE
    v_full_name TEXT;
BEGIN
  v_full_name := COALESCE(NEW.raw_user_meta_data ->> 'full_name', 'New User');
  IF char_length(v_full_name) < 3 THEN v_full_name := v_full_name || ' (User)'; END IF;
  INSERT INTO public.profiles (id, full_name, email, role)
  VALUES (NEW.id, v_full_name, NEW.email, COALESCE(NEW.raw_user_meta_data ->> 'role', 'member'));
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

-- 10.3 DISBURSEMENT, RECONCILIATION & SIMULATION RPCs
-- Restored from the GROUND_ZERO baseline: these objects existed only in the
-- consolidated dashboard script and in no master, so a masters-only rebuild
-- silently dropped them. process_rosca_payout_v2 and simulate_bulk_activity_v2
-- are required by the ROSCA cycle and the Payment Sandbox (rule 6).

-- 10.3.1 DISBURSEMENT SECURITY

CREATE OR REPLACE FUNCTION public.assert_mfa_for_disbursement_v1(p_amount NUMERIC)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    IF ABS(p_amount) >= 10000.00 THEN
        IF NOT public.is_aal2() THEN
            RAISE EXCEPTION '[SEC_001] MFA_REQUIRED' USING
                DETAIL = 'High-value disbursements (>= R10,000) require Multi-Factor Authentication (AAL2).',
                HINT = 'mfa_challenge_required';
        END IF;
    END IF;
END;
$$;


CREATE OR REPLACE FUNCTION public.record_platform_revenue_v1(
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id TEXT DEFAULT NULL,
    p_group_id UUID DEFAULT NULL
) RETURNS VOID AS $$
DECLARE
    v_last_balance NUMERIC;
BEGIN
    SELECT balance_after INTO v_last_balance FROM public.platform_ledger ORDER BY created_at DESC LIMIT 1;
    INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category, group_id)
    VALUES (p_transaction_id, p_amount, COALESCE(v_last_balance, 0) + p_amount, p_description, p_category, p_group_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;


CREATE OR REPLACE FUNCTION public.record_loan_settlement_extraction_v1(
    p_member_id UUID,
    p_group_id UUID,
    p_amount NUMERIC,
    p_loan_id UUID
) RETURNS VOID AS $$
DECLARE
    v_m_name TEXT;
BEGIN
    SELECT full_name INTO v_m_name FROM public.members WHERE id = p_member_id;

    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, type, transaction_id)
    VALUES (p_member_id, p_group_id, -p_amount, CURRENT_DATE, NOW(), 'paid', 'loan_settlement_extraction', 'ext-' || p_loan_id::text);

    PERFORM public.record_loan_repayment_v1(p_loan_id, p_member_id, p_group_id, p_amount, 'wallet');

    INSERT INTO public.group_ledger (group_id, member_id, amount, balance_after, description, category, transaction_id)
    VALUES (p_group_id, p_member_id, 0, (SELECT balance FROM public.groups WHERE id = p_group_id), 'Loan settlement extraction for ' || v_m_name, 'loan_settlement', 'settle-' || p_loan_id::text);

    PERFORM public.log_audit_event('LOAN_FORCED_SETTLEMENT', 'system', p_member_id, p_group_id, jsonb_build_object('loan_id', p_loan_id, 'amount', p_amount), p_loan_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;


CREATE OR REPLACE FUNCTION public.approve_and_disburse_loan_v2(
    p_loan_id UUID,
    p_admin_id UUID,
    p_payment_method TEXT DEFAULT 'bank'
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_loan RECORD;
    v_m_name TEXT;
    v_group RECORD;
BEGIN
    SELECT * INTO v_loan FROM public.loans WHERE id = p_loan_id;
    IF v_loan.id IS NULL THEN RAISE EXCEPTION '[SYS_001] LOAN_NOT_FOUND'; END IF;
    IF v_loan.status NOT IN ('pending', 'approved') THEN RAISE EXCEPTION '[FIN_002] INVALID_STATUS: Loan cannot be disbursed from status %', v_loan.status; END IF;

    PERFORM public.assert_mfa_for_disbursement_v1(v_loan.amount);

    SELECT * INTO v_group FROM public.groups WHERE id = v_loan.group_id;
    IF v_group.balance < v_loan.amount THEN RAISE EXCEPTION '[FIN_001] INSUFFICIENT_LIQUIDITY'; END IF;

    SELECT full_name INTO v_m_name FROM public.members WHERE id = v_loan.member_id;

    UPDATE public.loans SET status = 'active', reviewed_by = p_admin_id, reviewed_at = NOW(), start_date = NOW(), updated_at = NOW() WHERE id = p_loan_id;
    PERFORM public.increment_group_balance(v_loan.group_id, -v_loan.amount, 'Loan disbursement to ' || v_m_name || ' (' || p_payment_method || ')', 'loan_disbursement', p_loan_id::text, v_loan.member_id);

    PERFORM public.log_audit_event('LOAN_DISBURSED', p_admin_id::text, v_loan.member_id, v_loan.group_id, jsonb_build_object('loan_id', p_loan_id, 'amount', v_loan.amount), p_loan_id);
END;
$$;



-- 10.3.2 DISBURSEMENTS

CREATE OR REPLACE FUNCTION public.complete_payout_v2(
    p_payout_id UUID,
    p_admin_id UUID DEFAULT NULL,
    p_payout_reference TEXT DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_payout RECORD;
    v_group RECORD;
    v_eff_admin_id UUID := COALESCE(p_admin_id, auth.uid());
BEGIN
    PERFORM id FROM public.groups WHERE id = (SELECT group_id FROM public.payouts WHERE id = p_payout_id) FOR UPDATE;

    SELECT * INTO v_payout FROM public.payouts WHERE id = p_payout_id;
    IF v_payout.id IS NULL THEN RAISE EXCEPTION '[SYS_001] PAYOUT_NOT_FOUND'; END IF;
    IF v_payout.status NOT IN ('group_approved', 'processing') THEN RAISE EXCEPTION '[FIN_002] BAD_STATUS: Payout in % cannot be completed', v_payout.status; END IF;

    PERFORM public.assert_mfa_for_disbursement_v1(v_payout.amount);

    SELECT * INTO v_group FROM public.groups WHERE id = v_payout.group_id;
    IF v_group.balance < v_payout.amount THEN RAISE EXCEPTION '[FIN_001] NO_CASH: Group balance insufficient for payout.'; END IF;

    UPDATE public.payouts SET status = 'completed', processed_by = v_eff_admin_id, processed_at = NOW(), payout_reference = COALESCE(p_payout_reference, payout_reference), updated_at = NOW() WHERE id = p_payout_id;
    PERFORM public.increment_group_balance(v_payout.group_id, -v_payout.amount, 'Disbursement Ref: ' || COALESCE(p_payout_reference, p_payout_id::text), 'payout', p_payout_id::text, v_payout.member_id);

    PERFORM public.log_audit_event('PAYOUT_COMPLETED', COALESCE(v_eff_admin_id::text, 'system_admin'), v_payout.member_id, v_payout.group_id, jsonb_build_object('payout_id', p_payout_id, 'amount', v_payout.amount), p_payout_id);
END;
$$;


CREATE OR REPLACE FUNCTION public.process_rosca_payout_v2(
    p_group_id UUID,
    p_member_id UUID,
    p_total_pot NUMERIC,
    p_loan_deductions JSONB,
    p_payout_amount NUMERIC,
    p_payout_data JSONB
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_loan RECORD;
    v_p_id UUID;
BEGIN
    PERFORM id FROM public.groups WHERE id = p_group_id FOR UPDATE;

    FOR v_loan IN (SELECT * FROM jsonb_to_recordset(p_loan_deductions) AS x(loan_id uuid, amount numeric)) LOOP
        IF v_loan.amount > 0 THEN
            PERFORM public.increment_group_balance(p_group_id, -v_loan.amount, 'ROSCA Offset', 'rosca_payout_offset', 'off-' || v_loan.loan_id::text, p_member_id);
            PERFORM public.record_loan_repayment_v1(v_loan.loan_id, p_member_id, p_group_id, v_loan.amount, 'wallet');
        END IF;
    END LOOP;

    IF p_payout_amount > 0 THEN
        INSERT INTO public.payouts (group_id, member_id, amount, bank_name, account_no, branch_code, status, payout_type)
        VALUES (p_group_id, p_member_id, p_payout_amount, p_payout_data->>'bank_name', p_payout_data->>'account_no', p_payout_data->>'branch_code', 'pending', 'rosca') RETURNING id INTO v_p_id;
    END IF;

    PERFORM public.log_audit_event('ROSCA_PAYOUT_INITIATED', 'system_rosca', p_member_id, p_group_id, jsonb_build_object('pot', p_total_pot, 'net_payout', p_payout_amount), v_p_id);

    RETURN jsonb_build_object('success', true, 'payout_id', v_p_id);
END;
$$;


CREATE OR REPLACE FUNCTION public.perform_group_reconciliation_v1(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_ledger_bal NUMERIC;
    v_actual_bal NUMERIC;
    v_diff NUMERIC;
    v_record RECORD;
BEGIN
    SELECT COALESCE(SUM(amount), 0) INTO v_ledger_bal FROM public.group_ledger WHERE group_id = p_group_id;
    SELECT balance INTO v_actual_bal FROM public.groups WHERE id = p_group_id;
    v_diff := v_actual_bal - v_ledger_bal;
    INSERT INTO public.reconciliations (group_id, period_start, period_end, status, ledger_balance, actual_balance, discrepancy, notes)
    VALUES (p_group_id, NOW() - interval '30 days', NOW(), CASE WHEN ABS(v_diff) < 0.01 THEN 'balanced' ELSE 'discrepancy_found' END, v_ledger_bal, v_actual_bal, v_diff, 'Auto-recon triggered')
    RETURNING * INTO v_record;
    RETURN to_jsonb(v_record);
END;
$$;


CREATE OR REPLACE FUNCTION public.consolidate_group_ledger_v1(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_total INT := 0;
BEGIN
    -- 1. Contributions
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT c.group_id, CAST(c.id AS TEXT), c.amount, 0, initcap(replace(c.type, '_', ' ')) || ' from ' || m.full_name, c.type, c.member_id, c.paid_at
    FROM public.contributions c JOIN public.members m ON c.member_id = m.id
    WHERE c.group_id = p_group_id AND c.status = 'paid'
    ON CONFLICT (group_id, transaction_id) DO NOTHING;
    v_total := v_total + (SELECT count(*) FROM public.group_ledger WHERE group_id = p_group_id AND transaction_id LIKE '%-%' AND created_at > NOW() - interval '5 minutes');

    -- 2. Repayments
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT lr.group_id, CAST(lr.id AS TEXT), lr.amount, 0, 'Loan repayment from ' || m.full_name, 'loan_repayment', lr.member_id, lr.paid_at
    FROM public.loan_repayments lr JOIN public.members m ON lr.member_id = m.id
    WHERE lr.group_id = p_group_id
    ON CONFLICT (group_id, transaction_id) DO NOTHING;

    -- 3. Disbursements (Payouts)
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT p.group_id, CAST(p.id AS TEXT), -p.amount, 0, 'Disbursement Ref: ' || COALESCE(p.payout_reference, p.id::text), 'payout', p.member_id, p.processed_at
    FROM public.payouts p WHERE p.group_id = p_group_id AND p.status = 'completed'
    ON CONFLICT (group_id, transaction_id) DO NOTHING;

    -- 4. Loan Disbursements
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT loan.group_id, CAST(loan.id AS TEXT), -loan.amount, 0, 'Loan disbursement to ' || m.full_name, 'loan_disbursement', loan.member_id, COALESCE(loan.reviewed_at, loan.created_at)
    FROM public.loans loan JOIN public.members m ON loan.member_id = m.id
    WHERE loan.group_id = p_group_id AND loan.status NOT IN ('pending', 'rejected', 'cancelled')
    ON CONFLICT (group_id, transaction_id) DO NOTHING;

    -- 5. Burial Claims
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT bc.group_id, CAST(bc.id AS TEXT), -bc.claim_amount, 0, 'Burial claim: ' || bc.beneficiary_name, 'claim', bc.member_id, bc.reviewed_at
    FROM public.beneficiary_payout_claims bc WHERE bc.group_id = p_group_id AND bc.status = 'paid'
    ON CONFLICT (group_id, transaction_id) DO NOTHING;

    RETURN jsonb_build_object('entries_reconstructed', v_total);
END;
$$;


-- 10.3.3 LEDGER CHECKPOINTS

CREATE OR REPLACE FUNCTION public.create_ledger_checkpoint_v1(p_group_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_latest_entry RECORD;
    v_checkpoint_id UUID;
BEGIN
    -- 1. Find the absolute latest ledger entry for this group
    SELECT * INTO v_latest_entry
    FROM public.group_ledger
    WHERE group_id = p_group_id
    ORDER BY created_at DESC, id DESC
    LIMIT 1;

    IF NOT FOUND THEN
        RAISE EXCEPTION '[SYS_001] NO_LEDGER_ENTRIES: Cannot create checkpoint for an empty ledger.';
    END IF;

    -- 2. Create the checkpoint
    INSERT INTO public.ledger_checkpoints (group_id, ledger_entry_id, balance)
    VALUES (p_group_id, v_latest_entry.id, v_latest_entry.balance_after)
    ON CONFLICT (group_id, ledger_entry_id) DO UPDATE SET balance = EXCLUDED.balance
    RETURNING id INTO v_checkpoint_id;

    PERFORM public.log_audit_event('CHECKPOINT_CREATED', 'system', NULL, p_group_id, jsonb_build_object('ledger_entry_id', v_latest_entry.id, 'balance', v_latest_entry.balance_after), v_checkpoint_id);

    RETURN v_checkpoint_id;
END;
$$;


-- 10.3.4 ADVANCED SEARCH

CREATE OR REPLACE FUNCTION public.search_members_v1(p_query TEXT)
RETURNS SETOF JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    RETURN QUERY
    SELECT jsonb_build_object(
        'member_id', m.id,
        'member_name', m.full_name,
        'group_id', m.group_id,
        'total_loan_requests', COALESCE(bt.total_loans_requested, 0),
        'overdue_loans', COALESCE(bt.overdue_loans, 0),
        'outstanding_amount', COALESCE(bt.total_outstanding_amount, 0),
        'completion_ratio', COALESCE(bt.loan_completion_rate, 0) / 100.0,
        'risk_band', COALESCE(bt.fraud_risk_level, 'low')
    )
    FROM public.members m
    LEFT JOIN public.member_behavior_track bt ON m.id = bt.member_id
    WHERE m.full_name ILIKE '%' || p_query || '%'
       OR m.id_number ILIKE '%' || p_query || '%'
    LIMIT 20;
END;
$$;


-- 10.3.5 SIMULATION TOOLS

CREATE OR REPLACE FUNCTION public.simulate_bulk_activity_v2(
    p_group_id UUID,
    p_months INT DEFAULT 6,
    p_member_count INT DEFAULT 10
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_member_id UUID;
    v_month_idx INT;
    v_member_idx INT;
    v_contrib_amt NUMERIC;
    v_due_date DATE;
    v_res JSONB;
BEGIN
    SELECT monthly_contribution INTO v_contrib_amt FROM public.groups WHERE id = p_group_id;
    FOR v_member_idx IN 1..p_member_count LOOP
        INSERT INTO public.members (group_id, full_name, email, status, joined_at, member_key)
        VALUES (p_group_id, 'Sim Member ' || v_member_idx, 'sim.' || v_member_idx || '.' || p_group_id || '@example.com', 'active', now() - (p_months || ' months')::interval, 'SIM-KEY-' || p_group_id || '-' || v_member_idx)
        ON CONFLICT (group_id, member_key) DO NOTHING;
    END LOOP;
    FOR v_month_idx IN 1..p_months LOOP
        v_due_date := (now() - ((p_months - v_month_idx) || ' months')::interval)::date;
        FOR v_member_id IN SELECT id FROM public.members WHERE group_id = p_group_id LOOP
            IF random() < 0.85 THEN
                PERFORM public.record_contribution_v2(v_member_id, p_group_id, CASE WHEN random() < 0.9 THEN v_contrib_amt ELSE v_contrib_amt / 2 END, v_due_date, v_due_date + interval '12 hours', 'paid', 'sim-bulk-' || v_member_id || '-' || v_month_idx, 'contribution');
            END IF;
        END LOOP;
    END LOOP;
    SELECT public.recalculate_all_group_financials_v3(p_group_id) INTO v_res;
    RETURN jsonb_build_object('status', 'success', 'months_simulated', p_months);
END;
$$;

-- 11. CACHE & GRANTS
NOTIFY pgrst, 'reload schema';
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO authenticated, service_role, anon;

-- ─────────────────────────────────────────────────────────────────────────────
-- INLINED MODULE: 18_DEPLOY_PROCESS_COMPLEX_PAYMENT_V4.sql
-- ─────────────────────────────────────────────────────────────────────────────
-- ─────────────
-- FIX & DEPLOY process_complex_payment_v4 FUNCTION
-- ─────────────

CREATE OR REPLACE FUNCTION public.process_complex_payment_v4(
    p_group_id UUID,
    p_member_id UUID,
    p_total_amount NUMERIC,
    p_tx_id TEXT,
    p_allocations JSONB,
    p_method TEXT DEFAULT 'bank',
    p_payment_type TEXT DEFAULT 'contribution',
    p_destination TEXT DEFAULT 'group'
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_bal NUMERIC;
    v_all RECORD;
    v_total_sum NUMERIC := 0;
BEGIN
    SELECT balance INTO v_bal FROM public.groups WHERE id = p_group_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND';
    END IF;

    IF jsonb_typeof(p_allocations) <> 'array' OR jsonb_array_length(p_allocations) = 0 THEN
        RAISE EXCEPTION '[FIN_003] At least one payment allocation is required';
    END IF;

    IF EXISTS (SELECT 1 FROM public.payments WHERE group_id = p_group_id AND transaction_id = p_tx_id AND status = 'completed') OR
       EXISTS (SELECT 1 FROM public.group_ledger WHERE group_id = p_group_id AND transaction_id = p_tx_id) THEN
        SELECT balance INTO v_bal FROM public.groups WHERE id = p_group_id;
        RETURN jsonb_build_object('success', true, 'new_balance', v_bal, 'transaction_id', p_tx_id, 'status', 'idempotent');
    END IF;

    SELECT SUM((val->>'amount')::NUMERIC) INTO v_total_sum FROM jsonb_array_elements(p_allocations) AS val;
    IF ABS(v_total_sum - p_total_amount) > 0.01 THEN
        RAISE EXCEPTION '[FIN_003] Allocation total % does not match requested amount %', v_total_sum, p_total_amount;
    END IF;

    INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at)
    VALUES (p_member_id, p_group_id, p_total_amount, p_payment_type, p_method, p_tx_id, 'completed', NOW());

    FOR v_all IN (SELECT * FROM jsonb_to_recordset(p_allocations) AS x(type text, amount numeric, loan_id uuid, due_date date, description text)) LOOP
        IF v_all.amount IS NULL OR v_all.amount <= 0 THEN
            RAISE EXCEPTION '[FIN_003] Allocation amounts must be positive';
        END IF;

        IF v_all.type = 'registration_contribution' OR v_all.type = 'joining_fee' THEN
            PERFORM public.internal_post_contribution(p_member_id, p_group_id, v_all.amount, COALESCE(v_all.due_date, CURRENT_DATE), NOW(), 'paid', p_tx_id || ':' || v_all.type, v_all.type, v_all.description);
        ELSIF v_all.type = 'contribution' THEN
            PERFORM public.internal_post_contribution(p_member_id, p_group_id, v_all.amount, COALESCE(v_all.due_date, CURRENT_DATE), NOW(), 'paid', p_tx_id || ':contribution', 'contribution', v_all.description);
        ELSIF v_all.type = 'loan_repayment' AND v_all.loan_id IS NOT NULL THEN
            PERFORM public.record_loan_repayment_v1(v_all.loan_id, p_member_id, p_group_id, v_all.amount, p_method);
        ELSIF v_all.type = 'late_fee' THEN
            PERFORM public.internal_post_contribution(p_member_id, p_group_id, v_all.amount, COALESCE(v_all.due_date, CURRENT_DATE), NOW(), 'paid', p_tx_id || ':late_fee', 'late_fee', COALESCE(v_all.description, 'Late payment fee'));
        ELSIF v_all.type = 'member_fee' OR v_all.type = 'member_fee_ledger' OR v_all.type = 'platform_fee' THEN
            PERFORM public.internal_post_contribution(p_member_id, p_group_id, v_all.amount, COALESCE(v_all.due_date, CURRENT_DATE), NOW(), 'paid', p_tx_id || ':' || v_all.type, v_all.type, COALESCE(v_all.description, 'Monthly Member Fee'));
        ELSE
            RAISE EXCEPTION '[FIN_003] Unsupported payment allocation type: %', v_all.type;
        END IF;
    END LOOP;

    PERFORM public.recalculate_all_group_financials_v3(p_group_id);

    SELECT balance INTO v_bal FROM public.groups WHERE id = p_group_id;
    RETURN jsonb_build_object('success', true, 'new_balance', v_bal, 'transaction_id', p_tx_id);
END;
$$;

GRANT EXECUTE ON FUNCTION public.process_complex_payment_v4(UUID, UUID, NUMERIC, TEXT, JSONB, TEXT, TEXT, TEXT) TO anon, authenticated, service_role;
COMMENT ON FUNCTION public.process_complex_payment_v4(UUID, UUID, NUMERIC, TEXT, JSONB, TEXT, TEXT, TEXT) IS 'Atomically records an allocated group payment and its ledger entries.';
NOTIFY pgrst, 'reload schema';

-- ─────────────────────────────────────────────────────────────────────────────
-- INLINED MODULE: 03_SECURITY_AND_RLS.sql
-- ─────────────────────────────────────────────────────────────────────────────
-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — MASTER RLS & SECURITY (Consolidated)
-- Version: 8.0 (Tiered Hardened Security - August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. ENABLE RLS GLOBALLY
DO $$
DECLARE
    v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', v_table);
    END LOOP;
END $$;

-- 2. TABLE GRANTS
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO anon, authenticated, service_role;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated, service_role;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO anon, authenticated, service_role;

-- 3. TIER 1: PUBLIC DISCOVERY
DROP POLICY IF EXISTS "Groups: Discover public" ON public.groups;
CREATE POLICY "Groups: Discover public" ON public.groups FOR SELECT TO anon, authenticated USING (is_public = true);

DROP POLICY IF EXISTS "Platform: Public providers" ON public.financial_providers;
CREATE POLICY "Platform: Public providers" ON public.financial_providers FOR SELECT TO authenticated USING (is_active = true);

-- 4. TIER 2: MEMBER-PRIVATE (Self-Service)
-- Profiles
DROP POLICY IF EXISTS "Profiles: Owner manage" ON public.profiles;
CREATE POLICY "Profiles: Owner manage" ON public.profiles FOR ALL TO authenticated USING (auth.uid() = id);

-- Standardized Member Access for Private Data
DO $$
DECLARE v_t text;
BEGIN
    FOREACH v_t IN ARRAY ARRAY['contributions', 'payments', 'loans', 'loan_repayments', 'payouts', 'beneficiary_payout_claims', 'member_documents', 'notifications', 'group_poll_votes'] LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Member: View own" ON public.%I', v_t);
        EXECUTE format('CREATE POLICY "Member: View own" ON public.%I FOR SELECT TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()))', v_t);

        -- Allow members to manage their own votes
        IF v_t = 'group_poll_votes' THEN
            EXECUTE format('DROP POLICY IF EXISTS "Member: Manage own votes" ON public.%I', v_t);
            EXECUTE format('CREATE POLICY "Member: Manage own votes" ON public.%I FOR ALL TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()))', v_t);
        END IF;
    END LOOP;
END $$;

-- 5. TIER 3: GROUP-ADMIN (Authoritative)
-- Groups
DROP POLICY IF EXISTS "Groups: Admin manage" ON public.groups;
CREATE POLICY "Groups: Admin manage" ON public.groups FOR ALL TO authenticated USING (admin_user_id = auth.uid());

DROP POLICY IF EXISTS "Groups: Member view" ON public.groups;
CREATE POLICY "Groups: Member view" ON public.groups FOR SELECT TO authenticated USING (public.is_group_member(id));

-- Standardized Admin Access
DO $$
DECLARE v_t text;
BEGIN
    FOREACH v_t IN ARRAY ARRAY[
        'members', 'contributions', 'payments', 'loans', 'loan_repayments', 'payouts',
        'beneficiary_payout_claims', 'member_documents', 'notifications', 'group_polls',
        'group_poll_votes', 'group_actuarial_metrics',
        'group_health_scores', 'ledger_checkpoints', 'group_ledger'
    ] LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Group Admin: Manage group" ON public.%I', v_t);
        EXECUTE format('CREATE POLICY "Group Admin: Manage group" ON public.%I FOR ALL TO authenticated USING (public.is_group_admin(group_id))', v_t);
    END LOOP;
END $$;

DROP POLICY IF EXISTS "Group Admin: Manage group" ON public.group_poll_options;
CREATE POLICY "Group Admin: Manage group" ON public.group_poll_options FOR ALL TO authenticated USING (public.is_group_admin((SELECT group_id FROM public.group_polls WHERE id = poll_id)));

-- 6. TIER 4: HIGH-SECURITY (MFA MANDATORY)
-- Sensitive tables require AAL2 for any mutation.
DO $$
DECLARE v_t text;
BEGIN
    FOREACH v_t IN ARRAY ARRAY['audit_logs', 'reconciliations', 'platform_settings', 'platform_fee_config', 'member_behavior_track', 'fraud_detection_events', 'platform_audit_reports'] LOOP
        EXECUTE format('DROP POLICY IF EXISTS "MFA Mandatory: Sensitive mutation" ON public.%I', v_t);
        EXECUTE format('CREATE POLICY "MFA Mandatory: Sensitive mutation" ON public.%I FOR ALL TO authenticated USING (public.is_aal2()) WITH CHECK (public.is_aal2())', v_t);

        -- Admin can still SELECT without MFA (except audit_logs and fraud_events which are restricted)
        IF v_t NOT IN ('audit_logs', 'fraud_detection_events') THEN
            EXECUTE format('DROP POLICY IF EXISTS "Admin: View sensitive" ON public.%I', v_t);
            IF v_t IN ('platform_settings', 'platform_fee_config', 'platform_audit_reports') THEN
                EXECUTE format('CREATE POLICY "Admin: View sensitive" ON public.%I FOR SELECT TO authenticated USING (public.is_platform_admin())', v_t);
            ELSE
                EXECUTE format('CREATE POLICY "Admin: View sensitive" ON public.%I FOR SELECT TO authenticated USING (public.is_group_admin(group_id) OR public.is_platform_admin())', v_t);
            END IF;
        END IF;
    END LOOP;
END $$;

-- 7. TIER 5: PLATFORM-WIDE BYPASS
-- Hardened: Applies to all public tables.
DO $$
DECLARE v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Platform Admin Bypass" ON public.%I', v_table);

        -- Audit Logs and Fraud Events require MFA even for Platform Admin
        IF v_table IN ('audit_logs', 'fraud_detection_events', 'reconciliations', 'platform_settings', 'platform_fee_config') THEN
            EXECUTE format('CREATE POLICY "Platform Admin Bypass" ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin() AND public.is_aal2()) WITH CHECK (public.is_platform_admin() AND public.is_aal2())', v_table);
        ELSE
            EXECUTE format('CREATE POLICY "Platform Admin Bypass" ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin())', v_table);
        END IF;
    END LOOP;
END $$;

-- 8. SPECIAL CASES
-- Audit Logs: Allow users to see logs where they are the actor or the target
DROP POLICY IF EXISTS "Audit Logs: View personal" ON public.audit_logs;
CREATE POLICY "Audit Logs: View personal" ON public.audit_logs FOR SELECT TO authenticated
USING (actor_id = auth.uid()::text OR target_member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

-- Bank Transactions: Admin only
DROP POLICY IF EXISTS "Bank Transactions: Admin manage" ON public.bank_transactions;
CREATE POLICY "Bank Transactions: Admin manage" ON public.bank_transactions FOR ALL TO authenticated USING (public.is_group_admin(group_id));

-- ─────────────────────────────────────────────────────────────────────────────
-- INLINED MODULE: 04_PLATFORM_ADMIN_SETUP.sql
-- ─────────────────────────────────────────────────────────────────────────────
-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — PLATFORM ADMIN SETUP
-- Version: 2.1 (Organized Layout - June 2026)
-- ─────────────────────────────────────────────────────────────────────────────

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
        INSERT INTO auth.users (
            id, instance_id, aud, role, email, encrypted_password, email_confirmed_at,
            raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
            confirmation_token, recovery_token, email_change_token_new, email_change
        )
        VALUES (
            v_user_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', v_email, extensions.crypt(v_password, extensions.gen_salt('bf', 10)), now(),
            '{"provider":"email","providers":["email"],"role":"platform_admin"}', jsonb_build_object('full_name', v_full_name, 'role', 'platform_admin'),
            now(), now(), '', '', '', ''
        );
    ELSE
        UPDATE auth.users
        SET encrypted_password = extensions.crypt(v_password, extensions.gen_salt('bf')),
            email = v_email,
            email_confirmed_at = coalesce(email_confirmed_at, now()),
            raw_app_meta_data = coalesce(raw_app_meta_data, '{}'::jsonb) || '{"provider":"email","providers":["email"],"role":"platform_admin"}'::jsonb,
            raw_user_meta_data = coalesce(raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('full_name', v_full_name, 'role', 'platform_admin'),
            -- Defensive: Ensure no NULLs in columns that cause GoTrue Scan errors
            email_change_token_new = COALESCE(email_change_token_new, ''),
            confirmation_token = COALESCE(confirmation_token, ''),
            recovery_token = COALESCE(recovery_token, ''),
            email_change = COALESCE(email_change, '')
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

    -- Remove legacy aliases if they exist so they do not conflict
    DELETE FROM auth.users
    WHERE lower(email) IN (SELECT lower(alias) FROM unnest(v_legacy_emails) AS alias)
      AND id <> v_user_id;

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

-- ─────────────────────────────────────────────────────────────────────────────
-- INLINED MODULE: 05_PERFORMANCE_VIEWS.sql
-- ─────────────────────────────────────────────────────────────────────────────
-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — PERFORMANCE & ANALYTICS VIEWS
-- Version: 1.1 (Security Definer Update - July 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. PLATFORM SUMMARY STATS (Landing Screen Support)
-- Using a SECURITY DEFINER function to allow 'anon' users to see platform-wide
-- aggregate stats without granting them SELECT access to the full members/ledger tables.
CREATE OR REPLACE FUNCTION public.get_platform_summary_stats_v1()
RETURNS TABLE (
    total_groups INT,
    total_members INT,
    total_balance NUMERIC(15,2),
    total_provinces INT,
    platform_revenue NUMERIC(15,2),
    average_risk_score FLOAT
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    RETURN QUERY
    SELECT
        (SELECT COUNT(id) FROM public.groups)::INT,
        (SELECT COUNT(id) FROM public.members)::INT,
        (SELECT COALESCE(SUM(balance), 0) FROM public.groups)::NUMERIC(15,2),
        (SELECT COUNT(DISTINCT province) FROM public.groups WHERE province IS NOT NULL AND province != '')::INT,
        (SELECT COALESCE(SUM(amount), 0) FROM public.platform_ledger)::NUMERIC(15,2),
        (SELECT COALESCE(AVG(composite_risk_score), 0) FROM public.group_actuarial_metrics)::FLOAT;
END;
$$;

DROP VIEW IF EXISTS public.platform_summary_stats;
CREATE OR REPLACE VIEW public.platform_summary_stats AS
SELECT * FROM public.get_platform_summary_stats_v1();

GRANT SELECT ON public.platform_summary_stats TO authenticated, anon;

-- 2. COMBINED AUDIT TRAIL
/**
 * View: Combined Audit Trail.
 * Shows both group-level entries and relevant platform-level entries (like fees).
 */
CREATE OR REPLACE VIEW public.vw_consolidated_audit_trail AS
SELECT
    id, group_id, member_id, transaction_id,
    amount, balance_after, description, category,
    created_at, FALSE as is_platform
FROM public.group_ledger
UNION ALL
SELECT
    id, group_id, NULL as member_id, transaction_id,
    amount, balance_after, description, category,
    created_at, TRUE as is_platform
FROM public.platform_ledger
WHERE group_id IS NOT NULL;

-- 3. MEMBER STANDING
/**
 * View: Member Financial Standing.
 * Aggregates core principal and loan totals for quick reference.
 */
CREATE OR REPLACE VIEW public.vw_member_standing AS
SELECT
    m.id as member_id,
    m.group_id,
    m.full_name,
    m.status,
    m.total_paid as principal_paid,
    (SELECT COALESCE(SUM(l.amount), 0) FROM public.loans l WHERE l.member_id = m.id AND l.status IN ('active', 'partially_paid', 'overdue')) as active_loan_principal,
    (SELECT COALESCE(SUM(l2.total_repaid), 0) FROM public.loans l2 WHERE l2.member_id = m.id) as total_repaid
FROM public.members m;

GRANT SELECT ON public.vw_consolidated_audit_trail TO authenticated;
GRANT SELECT ON public.vw_member_standing TO authenticated;

-- 4. LEDGER INTEGRITY CHECK (Admin Diagnostic)
/**
 * View: Ledger Integrity Check.
 * Flags groups with financial drift or broken ledger chains.
 */
CREATE OR REPLACE VIEW public.vw_ledger_integrity_check AS
WITH group_audit AS (
    SELECT
        id as group_id,
        name as group_name,
        balance as table_balance,
        (SELECT COALESCE(SUM(amount), 0) FROM public.group_ledger WHERE group_id = public.groups.id) as ledger_sum
    FROM public.groups
)
SELECT
    group_id,
    group_name,
    table_balance,
    ledger_sum,
    (ledger_sum - table_balance) as discrepancy,
    CASE
        WHEN ABS(ledger_sum - table_balance) > 0.01 THEN 'DRIFT'
        ELSE 'CONSISTENT'
    END as status
FROM group_audit;

GRANT SELECT ON public.vw_ledger_integrity_check TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- INLINED MODULE: 30_STORAGE_RLS.sql
-- ─────────────────────────────────────────────────────────────────────────────
-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — STORAGE BUCKETS & RLS POLICIES (Master)
-- Version: 2.1 (Full Admin Visibility & Parity - August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. PROVISION BUCKETS
-- Ensuring all critical buckets exist for the platform.
INSERT INTO storage.buckets (id, name, public)
VALUES
    ('avatars', 'avatars', true),
    ('constitutions', 'constitutions', true),
    ('documents', 'documents', false),
    ('loan_contracts', 'loan_contracts', false),
    ('backups', 'backups', false)
ON CONFLICT (id) DO NOTHING;

-- 2. ENABLE RLS
-- We attempt to enable RLS; failure is ignored if already enabled or permission restricted.
-- ALTER TABLE storage.buckets ENABLE ROW LEVEL SECURITY;
-- ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;

-- 3. BUCKET POLICIES (Visibility)
-- Authenticated users need to see buckets to interact with them.
DROP POLICY IF EXISTS "Buckets: Authenticated view" ON storage.buckets;
CREATE POLICY "Buckets: Authenticated view" ON storage.buckets FOR SELECT TO authenticated USING (TRUE);

-- 4. OBJECT POLICIES (Standard Roles)

-- Avatars (Public Read, Authority Manage)
DROP POLICY IF EXISTS "Avatars: Public view" ON storage.objects;
CREATE POLICY "Avatars: Public view" ON storage.objects FOR SELECT USING (bucket_id = 'avatars');

DROP POLICY IF EXISTS "Avatars: Authority manage" ON storage.objects;
CREATE POLICY "Avatars: Authority manage" ON storage.objects FOR ALL TO authenticated USING (
    bucket_id = 'avatars' AND public.can_access_member_storage(name)
);

-- Documents (Private, Authority Manage)
DROP POLICY IF EXISTS "Documents: Authority manage" ON storage.objects;
CREATE POLICY "Documents: Authority manage" ON storage.objects FOR ALL TO authenticated USING (
    bucket_id = 'documents' AND public.can_access_member_storage(name)
);

-- Constitutions (Public Read, Admin Manage)
DROP POLICY IF EXISTS "Constitutions: Public view" ON storage.objects;
CREATE POLICY "Constitutions: Public view" ON storage.objects FOR SELECT USING (bucket_id = 'constitutions');

DROP POLICY IF EXISTS "Constitutions: Admin manage" ON storage.objects;
CREATE POLICY "Constitutions: Admin manage" ON storage.objects FOR ALL TO authenticated USING (
    bucket_id = 'constitutions' AND (
        public.is_group_admin((storage.foldername(name))[1]::uuid) OR
        public.is_platform_admin()
    )
);

-- Loan Contracts (Private, Admin/Member Manage)
DROP POLICY IF EXISTS "Loan Contracts: Member/Admin view" ON storage.objects;
CREATE POLICY "Loan Contracts: Member/Admin view" ON storage.objects FOR SELECT TO authenticated USING (
    bucket_id = 'loan_contracts' AND (
        public.is_group_admin((storage.foldername(name))[2]::uuid) OR
        public.is_group_member((storage.foldername(name))[2]::uuid) OR
        public.is_platform_admin()
    )
);

-- 5. UNIVERSAL PLATFORM ADMIN BYPASS (The Gold Standard)
-- Platform Admin can do anything in any bucket.
DROP POLICY IF EXISTS "Storage: Platform Admin Buckets" ON storage.buckets;
CREATE POLICY "Storage: Platform Admin Buckets" ON storage.buckets FOR ALL TO authenticated USING (public.is_platform_admin());

DROP POLICY IF EXISTS "Storage: Platform Admin All" ON storage.objects;
CREATE POLICY "Storage: Platform Admin All" ON storage.objects FOR ALL TO authenticated USING (public.is_platform_admin());

-- =====================================================================
-- Inlined module: supabase\online\01_ONLINE_SPECIFIC_SETUP.sql
-- =====================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — ONLINE SPECIFIC SETUP
-- Version: 1.1 (Production Overrides - Fixed Block - August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
BEGIN
    -- 1. PRODUCTION PLATFORM SETTINGS
    -- Ensure real-world values for production environment
    UPDATE public.platform_settings SET value = 10.0 WHERE key = 'monthly_per_member';
    UPDATE public.platform_settings SET value = 700.0 WHERE key = 'registration_fee';

    -- 2. DISABLE DEBUG LOGGING (Optional - reduces DB noise in cloud)
    -- TRUNCATE public.debug_log;

    -- 3. ENSURE IDEMPOTENCE MARKERS
    -- If you need specific production groups pre-provisioned, add them here.

    RAISE NOTICE 'Online production overrides applied successfully.';
END $$;
