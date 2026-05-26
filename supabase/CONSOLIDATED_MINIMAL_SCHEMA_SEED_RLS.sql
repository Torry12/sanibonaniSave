-- SanibonaniSave: Consolidated Minimal Schema, Seed, and RLS Script
-- This script creates the schema, ensures at least one visible group, and enables dev RLS policies.

-- 1. Enable required extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 2. Create tables (minimal, app-aligned)
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY,
    full_name TEXT NOT NULL,
    email TEXT NOT NULL,
    role TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.groups (
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
    admin_user_id UUID,
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

CREATE TABLE IF NOT EXISTS public.members (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    user_id UUID NOT NULL,
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

-- 3. Add unique constraints
ALTER TABLE public.groups ADD CONSTRAINT IF NOT EXISTS groups_name_unique UNIQUE (name);
ALTER TABLE public.members ADD CONSTRAINT IF NOT EXISTS members_group_id_user_id_unique UNIQUE (group_id, user_id);

-- 4. Ensure at least one group is visible in the app
UPDATE public.groups
SET is_public = true,
    registration_paid = true
WHERE id = (
    SELECT id FROM public.groups
    ORDER BY created_at ASC
    LIMIT 1
);

INSERT INTO public.groups (
  id, name, type, province, city, township, description, logo_emoji,
  joining_fee, monthly_contribution, late_fee, late_fee_grace_days,
  probation_months, payment_due_day, max_members, is_public, allow_partial_payment,
  auto_suspend_after, bank_name, account_number, branch_code, account_type,
  gateway_public_key, balance, admin_user_id, fee_status, registration_paid,
  is_platform_suspended, constitution_status, latitude, longitude,
  max_beneficiaries, beneficiary_increase_pct, goal_amount, period_months,
  loan_interest_rate, loan_max_amount, loan_max_months, current_members,
  created_at, updated_at
)
SELECT
  gen_random_uuid(), 'Test Group', 'stokvel', 'Gauteng', 'Johannesburg', 'Soweto', 'A test group', '💰',
  100.0, 200.0, 10.0, 5,
  3, 1, 50, true, true,
  2, 'FNB', '1234567890', '250655', 'Savings',
  null, 1000.0, 'admin-user-uuid', 'active', true,
  false, 'complete', -26.2041, 28.0473,
  5, 10.0, 5000.0, 12,
  5.0, 2000.0, 6, 10,
  now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM public.groups WHERE is_public = true AND registration_paid = true
);

-- 5. Enable RLS and allow all for dev
ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
CREATE POLICY IF NOT EXISTS "Allow all for dev" ON public.groups FOR ALL USING (true);

ALTER TABLE public.members ENABLE ROW LEVEL SECURITY;
CREATE POLICY IF NOT EXISTS "Allow all for dev" ON public.members FOR ALL USING (true);

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
CREATE POLICY IF NOT EXISTS "Allow all for dev" ON public.profiles FOR ALL USING (true);

