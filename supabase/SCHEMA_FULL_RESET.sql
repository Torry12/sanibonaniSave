-- SanibonaniSave: Full Database Reset and Schema Build Script
-- This script drops all relevant tables, recreates the schema, adds constraints, and prepares for seeding.

-- 1. Drop tables (reverse dependency order)
DROP TABLE IF EXISTS public.audit_logs CASCADE;
DROP TABLE IF EXISTS public.group_ledger CASCADE;
DROP TABLE IF EXISTS public.payments CASCADE;
DROP TABLE IF EXISTS public.contributions CASCADE;
DROP TABLE IF EXISTS public.members CASCADE;
DROP TABLE IF EXISTS public.groups CASCADE;
DROP TABLE IF EXISTS public.profiles CASCADE;
DROP TABLE IF EXISTS auth.users CASCADE;

-- 2. Enable required extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 3. Create tables

-- 3.1 auth.users
CREATE TABLE auth.users (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    encrypted_password TEXT NOT NULL,
    email_confirmed_at TIMESTAMP,
    raw_app_meta_data JSONB,
    raw_user_meta_data JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 3.2 public.profiles
CREATE TABLE public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL,
    email TEXT NOT NULL,
    role TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 3.3 public.groups
CREATE TABLE public.groups (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    province TEXT,
    city TEXT,
    township TEXT,
    description TEXT,
    logo_emoji TEXT,
    joining_fee NUMERIC(10,2),
    monthly_contribution NUMERIC(10,2),
    late_fee NUMERIC(10,2),
    late_fee_grace_days INT,
    probation_months INT,
    payment_due_day INT,
    max_members INT,
    is_public BOOLEAN,
    allow_partial_payment BOOLEAN,
    auto_suspend_after INT,
    bank_name TEXT,
    account_number TEXT,
    branch_code TEXT,
    account_type TEXT,
    gateway_public_key TEXT,
    balance NUMERIC(12,2) DEFAULT 0,
    admin_user_id UUID REFERENCES auth.users(id),
    fee_status TEXT,
    registration_paid BOOLEAN,
    is_platform_suspended BOOLEAN,
    constitution_status TEXT,
    latitude NUMERIC(9,6),
    longitude NUMERIC(9,6),
    max_beneficiaries INT,
    beneficiary_increase_pct NUMERIC(5,2),
    goal_amount NUMERIC(12,2),
    period_months INT,
    loan_interest_rate NUMERIC(5,2),
    loan_max_amount NUMERIC(12,2),
    loan_max_months INT,
    current_members INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 3.4 public.members
CREATE TABLE public.members (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL,
    phone TEXT,
    email TEXT,
    notification_pref TEXT,
    status TEXT,
    joined_at TIMESTAMP,
    probation_end_at TIMESTAMP,
    beneficiary_count INT DEFAULT 0,
    total_contributions INT DEFAULT 0,
    total_paid NUMERIC(12,2) DEFAULT 0,
    member_key TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 3.5 public.contributions
CREATE TABLE public.contributions (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES public.members(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    amount NUMERIC(10,2) NOT NULL,
    type TEXT NOT NULL,
    due_date DATE,
    paid_at TIMESTAMP,
    payment_method TEXT,
    transaction_id TEXT,
    status TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 3.6 public.payments
CREATE TABLE public.payments (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES public.members(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    amount NUMERIC(10,2) NOT NULL,
    payment_type TEXT NOT NULL,
    payment_method TEXT,
    transaction_id TEXT,
    status TEXT,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 3.7 public.group_ledger
CREATE TABLE public.group_ledger (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    amount NUMERIC(12,2) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 3.8 public.audit_logs
CREATE TABLE public.audit_logs (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES auth.users(id),
    target_member_id UUID REFERENCES public.members(id),
    target_group_id UUID REFERENCES public.groups(id),
    action TEXT NOT NULL,
    details JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 4. Add unique constraints (idempotent, for ON CONFLICT)

-- Unique group name
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'groups'
          AND constraint_type = 'UNIQUE'
          AND constraint_name = 'groups_name_unique'
    ) THEN
        EXECUTE 'ALTER TABLE public.groups ADD CONSTRAINT groups_name_unique UNIQUE (name)';
    END IF;
END
$$;

-- Unique member per group
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'members'
          AND constraint_type = 'UNIQUE'
          AND constraint_name = 'members_group_id_user_id_unique'
    ) THEN
        EXECUTE 'ALTER TABLE public.members ADD CONSTRAINT members_group_id_user_id_unique UNIQUE (group_id, user_id)';
    END IF;
END
$$;

-- 5. (Optional) Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_members_group_id ON public.members(group_id);
CREATE INDEX IF NOT EXISTS idx_contributions_group_id ON public.contributions(group_id);
CREATE INDEX IF NOT EXISTS idx_payments_group_id ON public.payments(group_id);

-- 6. (Optional) Add functions/triggers here if needed
-- (e.g., public.increment_group_balance)

-- 7. (Optional) Run seed scripts after this completes
-- \i supabase/seeds/SEED_15_GROUPS_150_MEMBERS.sql

