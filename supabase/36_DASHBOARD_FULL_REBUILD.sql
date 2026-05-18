-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — DASHBOARD-SAFE MASTER SETUP (TOTAL ALIGNMENT)
-- Version: 14.0 (Final Fix for Android App)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. CLEAN RESET
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;

-- 2. PERMISSIONS
GRANT ALL ON SCHEMA public TO postgres, public, anon, authenticated, service_role;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 3. CORE TABLES (EXACT MATCH FOR ANDROID APP CODE)

CREATE TABLE public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT, email TEXT, role TEXT DEFAULT 'member',
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.groups (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                   TEXT NOT NULL,
    type                   TEXT NOT NULL DEFAULT 'other',
    province               TEXT, city TEXT, township TEXT, description TEXT,
    logo_emoji             TEXT DEFAULT '🤝',
    joining_fee            NUMERIC(12,2) DEFAULT 0,
    monthly_contribution   NUMERIC(12,2) DEFAULT 0,
    late_fee               NUMERIC(12,2) DEFAULT 0,
    late_fee_grace_days    INTEGER DEFAULT 5,
    probation_months       INTEGER DEFAULT 3,
    payment_due_day        INTEGER DEFAULT 28,
    max_members            INTEGER DEFAULT 50,
    current_members        INTEGER DEFAULT 0,
    is_public              BOOLEAN DEFAULT TRUE,
    allow_partial_payment  BOOLEAN DEFAULT FALSE,
    auto_suspend_after     INTEGER DEFAULT 2,
    bank_name              TEXT, account_number TEXT, branch_code TEXT, account_type TEXT DEFAULT 'Savings',
    gateway_public_key        TEXT, -- REQUIRED BY APP
    balance                NUMERIC(12,2) DEFAULT 0,
    admin_user_id          UUID REFERENCES auth.users(id) NOT NULL,
    fee_status             TEXT DEFAULT 'paid',
    registration_paid      BOOLEAN DEFAULT TRUE, -- REQUIRED FOR VISIBILITY
    is_platform_suspended  BOOLEAN DEFAULT FALSE,
    constitution_url       TEXT, constitution_status TEXT DEFAULT 'pending',
    latitude FLOAT8, longitude FLOAT8, geohash TEXT,
    max_beneficiaries INTEGER DEFAULT 0, beneficiary_increase_pct NUMERIC(10,2) DEFAULT 0,
    goal_amount NUMERIC(12,2) DEFAULT 0, period_months INTEGER DEFAULT 12,
    rosca_rotation_method TEXT NOT NULL DEFAULT 'fixed',
    loan_interest_rate NUMERIC(10,2) DEFAULT 0, loan_max_amount NUMERIC(12,2) DEFAULT 0, loan_max_months INTEGER DEFAULT 12,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    user_id UUID REFERENCES auth.users(id),
    full_name TEXT NOT NULL, email TEXT, phone TEXT,
    status TEXT DEFAULT 'active', joined_at TIMESTAMPTZ DEFAULT NOW(),
    member_key TEXT UNIQUE, total_contributions INTEGER DEFAULT 0, total_paid NUMERIC(12,2) DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.contributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount NUMERIC(12,2) NOT NULL, type TEXT DEFAULT 'contribution', due_date DATE NOT NULL,
    paid_at TIMESTAMPTZ, status TEXT DEFAULT 'paid', transaction_id TEXT,
    transaction_id TEXT, -- REQUIRED BY APP
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id UUID REFERENCES public.members(id) ON DELETE CASCADE,
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount NUMERIC(12,2) NOT NULL, payment_type TEXT NOT NULL, status TEXT DEFAULT 'completed',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.loans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount NUMERIC(12,2) NOT NULL, status TEXT DEFAULT 'pending', created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount NUMERIC(12,2) NOT NULL, status TEXT DEFAULT 'pending', created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.beneficiary_payout_claims (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    member_id UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    beneficiary_name TEXT NOT NULL, status TEXT DEFAULT 'submitted', claim_amount NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.group_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    amount NUMERIC(12,2) NOT NULL, balance_after NUMERIC(12,2) NOT NULL, description TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.platform_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    amount NUMERIC(12,2) NOT NULL, balance_after NUMERIC(12,2) NOT NULL, description TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.platform_settings ( key TEXT PRIMARY KEY, value NUMERIC NOT NULL );
CREATE TABLE public.notifications ( id UUID PRIMARY KEY DEFAULT gen_random_uuid(), group_id UUID, member_id UUID, message TEXT, created_at TIMESTAMPTZ DEFAULT NOW() );
CREATE TABLE public.beneficiaries ( group_id UUID, member_id UUID, id UUID DEFAULT gen_random_uuid(), full_name TEXT, PRIMARY KEY (group_id, member_id, id) );
CREATE TABLE public.policies ( id UUID PRIMARY KEY DEFAULT gen_random_uuid(), group_id UUID, name TEXT, required_amount NUMERIC(12,2) );
CREATE TABLE public.member_documents ( id UUID PRIMARY KEY DEFAULT gen_random_uuid(), member_id UUID, group_id UUID, label TEXT, document_url TEXT );
CREATE TABLE public.group_actuarial_metrics ( id UUID PRIMARY KEY DEFAULT gen_random_uuid(), group_id UUID, reserve_adequacy_pct NUMERIC(10,2) );
CREATE TABLE public.platform_fees ( id UUID PRIMARY KEY DEFAULT gen_random_uuid(), group_id UUID, amount NUMERIC(12,2), status TEXT );

-- 4. ADMIN USER INJECTION
DO $$
DECLARE v_uid UUID;
BEGIN
    SELECT id INTO v_uid FROM auth.users WHERE lower(email) = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_uid IS NULL THEN
        v_uid := gen_random_uuid();
        INSERT INTO auth.users (id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data)
        VALUES (v_uid, 'authenticated', 'authenticated', 'torrymsimango@gmail.com', crypt('torry123M', gen_salt('bf')), now(), '{"role":"platform_admin"}', '{"role":"platform_admin","full_name":"Torry Admin"}');
    END IF;
    INSERT INTO public.profiles (id, full_name, email, role) VALUES (v_uid, 'Torry Admin', 'torrymsimango@gmail.com', 'platform_admin') ON CONFLICT (id) DO UPDATE SET role = 'platform_admin';
END $$;

-- 5. SEED DATA (PERFECTLY LINKED TO ADMIN)
DO $$
DECLARE v_aid UUID; v_g1 UUID; v_m1 UUID;
BEGIN
    SELECT id INTO v_aid FROM auth.users WHERE lower(email) = 'torrymsimango@gmail.com' LIMIT 1;

    INSERT INTO public.groups (name, type, province, city, admin_user_id, balance, logo_emoji)
    VALUES ('DASH Stokvel', 'stokvel', 'Gauteng', 'Joburg', v_aid, 15000, '💰') RETURNING id INTO v_g1;

    INSERT INTO public.members (group_id, user_id, full_name, email, member_key)
    VALUES (v_g1, v_aid, 'Torry Admin', 'torrymsimango@gmail.com', 'MK-SEED-A1') RETURNING id INTO v_m1;

    INSERT INTO public.contributions (member_id, group_id, amount, due_date, status, transaction_id)
    VALUES (v_m1, v_g1, 250, CURRENT_DATE, 'paid', 'tx-seed-101');

    INSERT INTO public.group_ledger (group_id, amount, balance_after, description)
    VALUES (v_g1, 15000, 15000, 'Initial Balance');
END $$;

INSERT INTO public.platform_settings (key, value) VALUES ('monthly_per_member', 12.0), ('registration_fee', 700.0) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

-- 6. SECURITY (ENABLE EVERYTHING FOR DEBUGGING)
DO $$
DECLARE v_t text;
BEGIN
    FOR v_t IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', v_t);
        EXECUTE format('DROP POLICY IF EXISTS "Allow All" ON public.%I', v_t);
        EXECUTE format('CREATE POLICY "Allow All" ON public.%I FOR ALL TO authenticated, anon USING (true) WITH CHECK (true)', v_t);
    END LOOP;
END $$;

-- 7. RELOAD SCHEMA
NOTIFY pgrst, 'reload schema';

-- 8. VERIFICATION
SELECT 'SUCCESS' as status, (SELECT COUNT(*) FROM public.groups) as groups;
