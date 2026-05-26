-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — 01. TABLES AND INDEXES
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

-- 1. PROFILES
CREATE TABLE public.profiles (
    id          UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name   TEXT NOT NULL CHECK (char_length(full_name) >= 3),
    email       TEXT NOT NULL CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    role        TEXT DEFAULT 'member' CHECK (role IN ('platform_admin', 'group_admin', 'member')),
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- 2. GROUPS
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
    balance                NUMERIC(15,2) DEFAULT 0 CHECK (balance >= 0),
    admin_user_id          UUID NOT NULL REFERENCES auth.users(id),
    fee_status             TEXT DEFAULT 'due' CHECK (fee_status IN ('paid', 'due', 'warning', 'suspended', 'pending_activation')),
    registration_paid      BOOLEAN DEFAULT FALSE,
    is_platform_suspended  BOOLEAN DEFAULT FALSE,
    constitution_url       TEXT,
    constitution_status    TEXT DEFAULT 'pending' CHECK (constitution_status IN ('pending', 'verified', 'rejected')),
    latitude               FLOAT8 CHECK (latitude >= -90 AND latitude <= 90),
    longitude              FLOAT8 CHECK (longitude >= -180 AND longitude <= 180),
    geohash                TEXT,
    max_beneficiaries      INTEGER DEFAULT 0 CHECK (max_beneficiaries >= 0),
    beneficiary_increase_pct NUMERIC(5,2) DEFAULT 0 CHECK (beneficiary_increase_pct >= 0),
    goal_amount            NUMERIC(15,2) DEFAULT 0 CHECK (goal_amount >= 0),
    period_months          INTEGER DEFAULT 12 CHECK (period_months > 0),
    rosca_rotation_method  TEXT NOT NULL DEFAULT 'fixed' CHECK (rosca_rotation_method IN ('fixed', 'random_draw', 'need_based', 'auction')),
    loan_interest_rate     NUMERIC(5,2) DEFAULT 0 CHECK (loan_interest_rate >= 0),
    loan_max_amount        NUMERIC(15,2) DEFAULT 0 CHECK (loan_max_amount >= 0),
    loan_max_months        INTEGER DEFAULT 12 CHECK (loan_max_months > 0),
    created_at             TIMESTAMPTZ DEFAULT NOW(),
    updated_at             TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT groups_name_unique UNIQUE (name)
);

-- 3. MEMBERS
CREATE TABLE public.members (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id             UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    user_id              UUID REFERENCES auth.users(id),
    full_name            TEXT NOT NULL CHECK (char_length(full_name) >= 2),
    id_number            TEXT CHECK (id_number ~ '^[0-9]{13}$' OR id_number IS NULL),
    phone                TEXT CHECK (phone ~ '^[0-9]{10,15}$'),
    email                TEXT CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$' OR email IS NULL),
    notification_pref    TEXT DEFAULT 'both' CHECK (notification_pref IN ('whatsapp', 'email', 'both')),
    status               TEXT DEFAULT 'probation' CHECK (status IN ('active', 'probation', 'suspended', 'pending_payment')),
    joined_at            TIMESTAMPTZ DEFAULT NOW(),
    probation_end_at     TIMESTAMPTZ,
    beneficiary_count         INTEGER DEFAULT 0 CHECK (beneficiary_count >= 0),
    beneficiary_over_65_count INTEGER DEFAULT 0 CHECK (beneficiary_over_65_count >= 0),
    monthly_contribution_override NUMERIC(12,2) CHECK (monthly_contribution_override >= 0),
    total_contributions  INTEGER DEFAULT 0 CHECK (total_contributions >= 0),
    total_paid           NUMERIC(15,2) DEFAULT 0 CHECK (total_paid >= 0),
    fcm_token            TEXT,
    member_key           TEXT,
    created_at           TIMESTAMPTZ DEFAULT NOW(),
    updated_at           TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(group_id, user_id)
);

-- 4. POLICIES (Internal Group Rules)
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

-- 5. CONTRIBUTIONS
CREATE TABLE public.contributions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id              UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id               UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    policy_id              UUID REFERENCES public.policies(id) ON DELETE SET NULL,
    amount                 NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    type                   TEXT DEFAULT 'contribution' CHECK (type IN ('contribution', 'joining_fee', 'registration_contribution', 'late_fee')),
    due_date               DATE NOT NULL CHECK (due_date >= NOW()),
    paid_at                TIMESTAMPTZ,
    payment_method         TEXT DEFAULT 'bank',
    transaction_id         TEXT,
    receipt_url            TEXT,
    status                 TEXT DEFAULT 'due' CHECK (status IN ('paid', 'due', 'overdue', 'partial')),
    late_fees_applied      BOOLEAN DEFAULT FALSE,
    created_at             TIMESTAMPTZ DEFAULT NOW(),
    updated_at             TIMESTAMPTZ DEFAULT NOW()
);

-- 6. PAYMENTS (Financial Proof)
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

-- 7. LOANS
CREATE TABLE public.loans (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id         UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount            NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    interest_rate     NUMERIC(5,2) DEFAULT 0 CHECK (interest_rate >= 0),
    total_to_repay    NUMERIC(15,2) NOT NULL CHECK (total_to_repay >= amount),
    total_repaid      NUMERIC(15,2) DEFAULT 0 CHECK (total_repaid >= 0),
    monthly_repayment NUMERIC(15,2) NOT NULL CHECK (monthly_repayment > 0),
    start_date        DATE,
    end_date          DATE,
    next_payment_date DATE,
    status            TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'active', 'partially_paid', 'completed', 'rejected', 'overdue', 'cancelled')),
    purpose           TEXT,
    contract_url      TEXT,
    surety_amount     NUMERIC(15,2) DEFAULT 0,
    reviewed_by       UUID REFERENCES auth.users(id),
    reviewed_at       TIMESTAMPTZ,
    admin_notes       TEXT,
    rejection_reason  TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 8. LOAN REPAYMENTS
CREATE TABLE public.loan_repayments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id           UUID REFERENCES public.loans(id) ON DELETE CASCADE NOT NULL,
    member_id         UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount            NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    paid_at           TIMESTAMPTZ DEFAULT NOW(),
    payment_method    TEXT DEFAULT 'bank',
    transaction_id    TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 9. BENEFICIARIES
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
    updated_at           TIMESTAMPTZ DEFAULT NOW()
);

-- 10. PAYOUTS
CREATE TABLE public.payouts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount            NUMERIC(15,2) NOT NULL CHECK (amount > 0),
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

-- 11. LEDGERS
CREATE TABLE public.group_ledger (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    transaction_id    UUID,
    amount            NUMERIC(15,2) NOT NULL,
    balance_after     NUMERIC(15,2) NOT NULL,
    description       TEXT NOT NULL,
    category          TEXT NOT NULL,
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.platform_ledger (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id    UUID,
    amount            NUMERIC(15,2) NOT NULL,
    balance_after     NUMERIC(15,2) NOT NULL,
    description       TEXT NOT NULL,
    category          TEXT NOT NULL,
    created_at        TIMESTAMPTZ DEFAULT NOW()
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

-- 14. GROUP ACTUARIAL METRICS
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
    expected_annual_claims_count   NUMERIC(15,2) DEFAULT 0,
    solvency_ratio                 NUMERIC(10,2) DEFAULT 0,
    capital_adequacy_pct           NUMERIC(10,2) DEFAULT 0,
    created_at                     TIMESTAMPTZ DEFAULT NOW()
);

-- 15. PLATFORM SETTINGS
CREATE TABLE public.platform_settings (
    key   TEXT PRIMARY KEY,
    value NUMERIC NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 16. AUDIT LOGS
CREATE TABLE public.audit_logs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id          UUID REFERENCES auth.users(id),
    target_member_id  UUID REFERENCES public.members(id),
    target_group_id   UUID REFERENCES public.groups(id),
    action            TEXT NOT NULL,
    details           JSONB,
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 17. ERROR LOGS
CREATE TABLE public.transaction_error_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    error_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    operation TEXT NOT NULL,
    reference_id UUID,
    error_message TEXT NOT NULL,
    context JSONB
);

-- 18. INDEXES (CORE)
CREATE INDEX idx_groups_geohash ON public.groups(geohash);
CREATE INDEX idx_groups_is_public ON public.groups(is_public);
CREATE INDEX idx_members_group_id ON public.members(group_id);
CREATE INDEX idx_members_user_id ON public.members(user_id);
CREATE INDEX idx_contributions_member_id ON public.contributions(member_id);
CREATE INDEX idx_payments_transaction_id ON public.payments(transaction_id);
CREATE INDEX idx_audit_logs_group_id ON public.audit_logs(target_group_id);

-- Accelerated search for members and groups
CREATE INDEX idx_members_full_name_trgm ON public.members USING gin (full_name gin_trgm_ops);
CREATE INDEX idx_groups_name_trgm ON public.groups USING gin (name gin_trgm_ops);

COMMIT;
