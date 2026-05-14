-- Architecture model schema templates (additive draft)
-- Date: 2026-05-13
-- Purpose: provide safe SQL scaffolding for future service/API/workflow extraction.
-- Note: this file is intentionally additive and uses IF NOT EXISTS.

-- 1) Outbox events (event-driven architecture)
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

-- 2) Event deduplication markers (idempotent consumers)
CREATE TABLE IF NOT EXISTS public.event_dedup_markers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    consumer_name text NOT NULL,
    event_id text NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (consumer_name, event_id)
);

-- 3) Group policy versioning (governance engine)
CREATE TABLE IF NOT EXISTS public.group_policy_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id text NOT NULL,
    policy_version int NOT NULL,
    policy_json jsonb NOT NULL,
    activated_by text,
    activated_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (group_id, policy_version)
);

CREATE INDEX IF NOT EXISTS idx_group_policy_versions_group_id ON public.group_policy_versions(group_id);

-- 4) Risk alerts (risk framework)
CREATE TABLE IF NOT EXISTS public.risk_alerts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id text NOT NULL,
    risk_type text NOT NULL,
    severity text NOT NULL,
    signal text NOT NULL,
    score numeric(10,2),
    status text NOT NULL DEFAULT 'OPEN',
    details jsonb NOT NULL DEFAULT '{}'::jsonb,
    raised_at timestamptz NOT NULL DEFAULT now(),
    resolved_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_risk_alerts_group_id ON public.risk_alerts(group_id);
CREATE INDEX IF NOT EXISTS idx_risk_alerts_status ON public.risk_alerts(status);

-- 5) Social credit score ledger (social credit system)
CREATE TABLE IF NOT EXISTS public.social_credit_scores (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id text NOT NULL,
    member_id text NOT NULL,
    score int NOT NULL,
    band text NOT NULL,
    reason text,
    factors jsonb NOT NULL DEFAULT '{}'::jsonb,
    calculated_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_social_credit_scores_member ON public.social_credit_scores(group_id, member_id, calculated_at DESC);

-- 6) Education savings targets (education savings groups)
CREATE TABLE IF NOT EXISTS public.education_targets (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id text NOT NULL,
    target_name text NOT NULL,
    target_amount numeric(14,2) NOT NULL,
    target_date date NOT NULL,
    beneficiary_name text,
    notes text,
    status text NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_education_targets_group_id ON public.education_targets(group_id);

-- 7) Grocery order windows (grocery groups)
CREATE TABLE IF NOT EXISTS public.grocery_order_windows (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id text NOT NULL,
    opens_at timestamptz NOT NULL,
    closes_at timestamptz NOT NULL,
    supplier_name text,
    status text NOT NULL DEFAULT 'OPEN',
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_grocery_order_windows_group_id ON public.grocery_order_windows(group_id);

-- 8) Hybrid model policy bindings (hybrid groups)
CREATE TABLE IF NOT EXISTS public.hybrid_policy_bindings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id text NOT NULL,
    model_type text NOT NULL,
    weight numeric(8,4) NOT NULL DEFAULT 1.0000,
    policy_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_hybrid_policy_bindings_group_id ON public.hybrid_policy_bindings(group_id);

