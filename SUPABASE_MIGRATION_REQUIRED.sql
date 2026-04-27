-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — Comprehensive Schema Migration & Fix Script
-- Version: 2.0 (Updated April 2026)
-- ─────────────────────────────────────────────────────────────────────────────
-- Run this in the Supabase SQL Editor (https://supabase.com/dashboard/project/_/sql)

-- 0. Ensure Extensions exist
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Update GROUPS table
ALTER TABLE public.groups
ADD COLUMN IF NOT EXISTS max_beneficiaries INTEGER DEFAULT 10,
ADD COLUMN IF NOT EXISTS beneficiary_increase_pct DOUBLE PRECISION DEFAULT 10.0,
ADD COLUMN IF NOT EXISTS goal_amount DOUBLE PRECISION DEFAULT 0.0,
ADD COLUMN IF NOT EXISTS period_months INTEGER DEFAULT 12,
ADD COLUMN IF NOT EXISTS constitution_url TEXT,
ADD COLUMN IF NOT EXISTS constitution_status TEXT DEFAULT 'pending',
ADD COLUMN IF NOT EXISTS latitude FLOAT8,
ADD COLUMN IF NOT EXISTS longitude FLOAT8,
ADD COLUMN IF NOT EXISTS geohash TEXT;

-- 2. Update MEMBERS table
ALTER TABLE public.members
ADD COLUMN IF NOT EXISTS document_1_url TEXT,
ADD COLUMN IF NOT EXISTS document_1_type TEXT,
ADD COLUMN IF NOT EXISTS document_1_status TEXT DEFAULT 'pending',
ADD COLUMN IF NOT EXISTS document_2_url TEXT,
ADD COLUMN IF NOT EXISTS document_2_type TEXT,
ADD COLUMN IF NOT EXISTS document_2_status TEXT DEFAULT 'pending',
ADD COLUMN IF NOT EXISTS document_3_url TEXT,
ADD COLUMN IF NOT EXISTS document_3_type TEXT,
ADD COLUMN IF NOT EXISTS document_3_status TEXT DEFAULT 'pending',
ADD COLUMN IF NOT EXISTS document_4_url TEXT,
ADD COLUMN IF NOT EXISTS document_4_type TEXT,
ADD COLUMN IF NOT EXISTS document_4_status TEXT DEFAULT 'pending',
ADD COLUMN IF NOT EXISTS document_5_url TEXT,
ADD COLUMN IF NOT EXISTS document_5_type TEXT,
ADD COLUMN IF NOT EXISTS document_5_status TEXT DEFAULT 'pending',
ADD COLUMN IF NOT EXISTS fcm_token TEXT,
ADD COLUMN IF NOT EXISTS total_contributions DOUBLE PRECISION DEFAULT 0.0,
ADD COLUMN IF NOT EXISTS beneficiary_count INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS beneficiary_over_65_count INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS monthly_contribution_override DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS street TEXT,
ADD COLUMN IF NOT EXISTS suburb TEXT,
ADD COLUMN IF NOT EXISTS city TEXT,
ADD COLUMN IF NOT EXISTS province TEXT;

-- 2b. Update BENEFICIARIES table (add document columns)
ALTER TABLE public.beneficiaries
ADD COLUMN IF NOT EXISTS document_url TEXT,
ADD COLUMN IF NOT EXISTS document_status TEXT DEFAULT 'pending';

-- 3. Ensure CONTRIBUTIONS table exists
CREATE TABLE IF NOT EXISTS public.contributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    type TEXT DEFAULT 'contribution',
    due_date DATE NOT NULL,
    paid_at TIMESTAMPTZ,
    status TEXT DEFAULT 'pending',
    processed_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- If it already existed, ensure missing columns are added
ALTER TABLE public.contributions
ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'pending',
ADD COLUMN IF NOT EXISTS type TEXT DEFAULT 'contribution',
ADD COLUMN IF NOT EXISTS processed_at TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS notes TEXT;

-- 4. Ensure PAYMENTS table exists
CREATE TABLE IF NOT EXISTS public.payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id UUID REFERENCES public.members(id) ON DELETE CASCADE,
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    payment_type TEXT NOT NULL,
    payment_method TEXT NOT NULL,
    transaction_id TEXT,
    status TEXT DEFAULT 'pending',
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- If it already existed, ensure missing columns are added
ALTER TABLE public.payments
ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'pending',
ADD COLUMN IF NOT EXISTS processed_at TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();

-- 5. Ensure PAYOUTS table exists
CREATE TABLE IF NOT EXISTS public.payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    bank_name TEXT NOT NULL,
    account_no TEXT NOT NULL,
    branch_code TEXT NOT NULL,
    status TEXT DEFAULT 'pending',
    processed_by UUID REFERENCES auth.users(id),
    processed_at TIMESTAMPTZ,
    yoco_payout_id TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. Enable RLS (Security)
ALTER TABLE public.contributions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payouts ENABLE ROW LEVEL SECURITY;

-- 7. Basic RLS Policies (Allows authenticated users to read/write)
-- Note: You may want to refine these for production based on group membership.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'contributions' AND policyname = 'Allow authenticated access') THEN
        CREATE POLICY "Allow authenticated access" ON public.contributions FOR ALL TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'payments' AND policyname = 'Allow authenticated access') THEN
        CREATE POLICY "Allow authenticated access" ON public.payments FOR ALL TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'payouts' AND policyname = 'Allow authenticated access') THEN
        CREATE POLICY "Allow authenticated access" ON public.payouts FOR ALL TO authenticated USING (true);
    END IF;
END $$;
