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
    balance                NUMERIC(12,2) NOT NULL DEFAULT 0,
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
    face_photo_status TEXT NOT NULL DEFAULT 'pending' CHECK (face_photo_status IN ('pending', 'verified', 'rejected')),
    beneficiary_id_url TEXT,
    beneficiary_id_status TEXT NOT NULL DEFAULT 'pending' CHECK (beneficiary_id_status IN ('pending', 'verified', 'rejected')),
    death_certificate_url TEXT,
    death_certificate_status TEXT NOT NULL DEFAULT 'pending' CHECK (death_certificate_status IN ('pending', 'verified', 'rejected')),
    member_id_url      TEXT,
    member_id_status   TEXT NOT NULL DEFAULT 'pending' CHECK (member_id_status IN ('pending', 'verified', 'rejected')),
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

-- 8.1 ROTATION IDEMPOTENCY LEDGER
-- One row per member per rotation period. process_rosca_payout_v3 claims this key before it
-- offsets any loan, so a re-run of a period that already executed cannot move money twice -
-- including a period whose net pot was zero, which leaves no payouts row to dedupe against.
CREATE TABLE IF NOT EXISTS public.rosca_rotation_executions (
    group_id     UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    member_id    UUID NOT NULL REFERENCES public.members(id) ON DELETE CASCADE,
    period       TEXT NOT NULL CHECK (period ~ '^[0-9]{4}-[0-9]{2}$'),
    payout_id    UUID REFERENCES public.payouts(id) ON DELETE SET NULL,
    total_pot    NUMERIC(12,2) NOT NULL CHECK (total_pot >= 0),
    loan_offsets NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (loan_offsets >= 0),
    net_amount   NUMERIC(12,2) NOT NULL CHECK (net_amount >= 0),
    executed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (group_id, member_id, period)
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

-- 20.1 FINANCIAL HOT-PATH INDEXES
-- Every one of these backs an idempotency guard or a per-write aggregate that runs inside a
-- money path, so a sequential scan here is paid on every payment, contribution and repayment.
CREATE INDEX IF NOT EXISTS idx_group_ledger_tx ON public.group_ledger(group_id, transaction_id);
CREATE INDEX IF NOT EXISTS idx_contributions_group_tx ON public.contributions(group_id, transaction_id);
CREATE INDEX IF NOT EXISTS idx_payments_group_tx ON public.payments(group_id, transaction_id);
CREATE INDEX IF NOT EXISTS idx_platform_ledger_tx ON public.platform_ledger(transaction_id);
CREATE INDEX IF NOT EXISTS idx_loan_repayments_loan_id ON public.loan_repayments(loan_id);
CREATE INDEX IF NOT EXISTS idx_reconciliations_group_created ON public.reconciliations(group_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_loans_group_id ON public.loans(group_id);
CREATE INDEX IF NOT EXISTS idx_members_id_number ON public.members(id_number);

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


