-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — COMPLETE DATABASE RESET WITH MOCK DATA
-- Version: 2.2 (Updated April 19, 2026)
-- ─────────────────────────────────────────────────────────────────────────────
-- CHANGELOG v2.2:
-- - Synced with schema v2.2 (payment_method/policy_id/status fields)
-- - Added large-scale mock dataset (~10x) + richer scenario coverage
-- CHANGELOG v2.1:
-- - Added township field to groups for detailed location
-- - Added realistic geolocation coordinates for Johannesburg, Durban, Cape Town
-- - Updated contribution totals and member payment tracking
-- - Added registration_contribution type support
-- - Added platform_fees table creation if not exists
-- ─────────────────────────────────────────────────────────────────────────────
-- Run this script in Supabase SQL Editor to completely reset and populate the database.
-- WARNING: This will DELETE ALL existing data!

-- 1. FIRST: Run the main schema.sql file, then run this script

-- ─────────────────────────────────────────────────────────────────────────────
-- 1b. SCHEMA SAFETY MIGRATIONS (Ensure all columns and tables exist)
-- ─────────────────────────────────────────────────────────────────────────────
-- These statements ensure the schema is up-to-date before inserting data

-- Groups table columns
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS latitude FLOAT8;
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS longitude FLOAT8;
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS geohash TEXT;
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS constitution_url TEXT;
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS constitution_status TEXT DEFAULT 'pending';
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS township TEXT;
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS is_platform_suspended BOOLEAN DEFAULT FALSE;

-- Members table columns
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS street TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS suburb TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS city TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS province TEXT;

-- Beneficiaries table columns
ALTER TABLE public.beneficiaries ADD COLUMN IF NOT EXISTS document_url TEXT;
ALTER TABLE public.beneficiaries ADD COLUMN IF NOT EXISTS document_status TEXT DEFAULT 'pending';

-- Contributions table columns
ALTER TABLE public.contributions ADD COLUMN IF NOT EXISTS type TEXT DEFAULT 'contribution';
ALTER TABLE public.contributions ADD COLUMN IF NOT EXISTS policy_id UUID;
ALTER TABLE public.contributions ADD COLUMN IF NOT EXISTS payment_method TEXT DEFAULT 'yoco';
ALTER TABLE public.contributions ADD COLUMN IF NOT EXISTS receipt_url TEXT;
ALTER TABLE public.contributions ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'due';
ALTER TABLE public.contributions ADD COLUMN IF NOT EXISTS late_fees_applied BOOLEAN DEFAULT FALSE;

-- Create platform_fees table if it doesn't exist
CREATE TABLE IF NOT EXISTS public.platform_fees (
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

-- Create platform_settings table if it doesn't exist
CREATE TABLE IF NOT EXISTS public.platform_settings (
    key TEXT PRIMARY KEY,
    value NUMERIC NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Seed platform settings if empty
INSERT INTO public.platform_settings (key, value)
VALUES
    ('monthly_per_member', 10.0),
    ('registration_fee', 700.0)
ON CONFLICT (key) DO NOTHING;

-- Create index for platform_fees if not exists
CREATE INDEX IF NOT EXISTS idx_platform_fees_group_id ON public.platform_fees(group_id);
CREATE INDEX IF NOT EXISTS idx_platform_fees_status ON public.platform_fees(status);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. CLEAR EXISTING DATA (RESPECTING CASCADE)
-- ─────────────────────────────────────────────────────────────────────────────
-- Use DELETE instead of TRUNCATE for tables that might not exist
DO $$
BEGIN
    -- Try to truncate each table, ignore errors if table doesn't exist
    BEGIN TRUNCATE TABLE public.member_documents CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE public.group_actuarial_metrics CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE public.payouts CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE public.notifications CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE public.contributions CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE public.payments CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE public.beneficiaries CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE public.policies CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE public.members CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE public.platform_fees CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE public.groups CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE public.profiles CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. RESET SEQUENCES
-- ─────────────────────────────────────────────────────────────────────────────
-- Not applicable for UUID primary keys, but keeps things clean

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. CLEAR AUTH USERS (Required for fresh platform admin)
-- ─────────────────────────────────────────────────────────────────────────────
DELETE FROM auth.users;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. CREATE PLATFORM ADMIN
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO auth.users (
    id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, confirmation_token
) VALUES (
    '1b8aca84-c136-4c1b-b024-902584ae80d8',
    'authenticated',
    'authenticated',
    'torryymsimango@gmail.com',
    extensions.crypt('torry123M', extensions.gen_salt('bf')),
    NOW(),
    '{"provider": "email", "providers": ["email"]}',
    '{"full_name": "Torry Msimango", "role": "platform_admin"}',
    NOW(),
    NOW(),
    ''
);

-- Create profile for platform admin (should be auto-triggered, but ensure it exists)
INSERT INTO public.profiles (id, full_name, email, role)
VALUES (
    '1b8aca84-c136-4c1b-b024-902584ae80d8',
    'Torry Msimango',
    'torryymsimango@gmail.com',
    'platform_admin'
) ON CONFLICT (id) DO UPDATE SET role = 'platform_admin';

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. CREATE TEST USERS
-- ─────────────────────────────────────────────────────────────────────────────

-- Test Group Admin 1
INSERT INTO auth.users (
    id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, confirmation_token
) VALUES (
    'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1',
    'authenticated',
    'authenticated',
    'admin1@test.com',
    extensions.crypt('password123', extensions.gen_salt('bf')),
    NOW(),
    '{"provider": "email", "providers": ["email"]}',
    '{"full_name": "John Admin", "role": "group_admin"}',
    NOW(),
    NOW(),
    ''
);

INSERT INTO public.profiles (id, full_name, email, role)
VALUES ('a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'John Admin', 'admin1@test.com', 'group_admin')
ON CONFLICT (id) DO UPDATE SET role = 'group_admin';

-- Test Group Admin 2
INSERT INTO auth.users (
    id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, confirmation_token
) VALUES (
    'a2a2a2a2-a2a2-a2a2-a2a2-a2a2a2a2a2a2',
    'authenticated',
    'authenticated',
    'admin2@test.com',
    extensions.crypt('password123', extensions.gen_salt('bf')),
    NOW(),
    '{"provider": "email", "providers": ["email"]}',
    '{"full_name": "Jane Admin", "role": "group_admin"}',
    NOW(),
    NOW(),
    ''
);

INSERT INTO public.profiles (id, full_name, email, role)
VALUES ('a2a2a2a2-a2a2-a2a2-a2a2-a2a2a2a2a2a2', 'Jane Admin', 'admin2@test.com', 'group_admin')
ON CONFLICT (id) DO UPDATE SET role = 'group_admin';

-- Test Members
INSERT INTO auth.users (
    id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, confirmation_token
) VALUES
('b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1', 'authenticated', 'authenticated', 'member1@test.com', extensions.crypt('password123', extensions.gen_salt('bf')), NOW(), '{"provider": "email", "providers": ["email"]}', '{"full_name": "Sipho Nkosi", "role": "member"}', NOW(), NOW(), ''),
('b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2', 'authenticated', 'authenticated', 'member2@test.com', extensions.crypt('password123', extensions.gen_salt('bf')), NOW(), '{"provider": "email", "providers": ["email"]}', '{"full_name": "Thandi Dlamini", "role": "member"}', NOW(), NOW(), ''),
('b3b3b3b3-b3b3-b3b3-b3b3-b3b3b3b3b3b3', 'authenticated', 'authenticated', 'member3@test.com', extensions.crypt('password123', extensions.gen_salt('bf')), NOW(), '{"provider": "email", "providers": ["email"]}', '{"full_name": "Bongani Moyo", "role": "member"}', NOW(), NOW(), ''),
('b4b4b4b4-b4b4-b4b4-b4b4-b4b4b4b4b4b4', 'authenticated', 'authenticated', 'member4@test.com', extensions.crypt('password123', extensions.gen_salt('bf')), NOW(), '{"provider": "email", "providers": ["email"]}', '{"full_name": "Nomvula Zulu", "role": "member"}', NOW(), NOW(), '');

INSERT INTO public.profiles (id, full_name, email, role) VALUES
('b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1', 'Sipho Nkosi', 'member1@test.com', 'member'),
('b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2', 'Thandi Dlamini', 'member2@test.com', 'member'),
('b3b3b3b3-b3b3-b3b3-b3b3-b3b3b3b3b3b3', 'Bongani Moyo', 'member3@test.com', 'member'),
('b4b4b4b4-b4b4-b4b4-b4b4-b4b4b4b4b4b4', 'Nomvula Zulu', 'member4@test.com', 'member')
ON CONFLICT (id) DO UPDATE SET role = 'member';

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. CREATE MOCK GROUPS
-- ─────────────────────────────────────────────────────────────────────────────

-- Group 1: Active Burial Society
INSERT INTO public.groups (
    id, name, type, province, city, township, description, logo_emoji,
    joining_fee, monthly_contribution, late_fee, late_fee_grace_days,
    probation_months, payment_due_day, max_members, current_members,
    is_public, allow_partial_payment, auto_suspend_after,
    bank_name, account_number, branch_code, account_type,
    balance, admin_user_id, fee_status, registration_paid,
    max_beneficiaries, beneficiary_increase_pct, goal_amount, period_months,
    latitude, longitude, geohash
) VALUES (
    'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1',
    'Ubuntu Burial Society',
    'burial_society',
    'Gauteng',
    'Johannesburg',
    'Soweto',
    'A community burial society supporting families in Soweto and surrounding areas.',
    '🕊️',
    250.00, 150.00, 50.00, 5,
    3, 28, 100, 0,
    TRUE, TRUE, 2,
    'FNB', '62123456789', '250655', 'Savings',
    5250.00, 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'paid', TRUE,
    5, 10.00, 100000.00, 12,
    -26.2485, 27.8540, 'ke7fxj6n9'
);

-- Group 2: Stokvel
INSERT INTO public.groups (
    id, name, type, province, city, township, description, logo_emoji,
    joining_fee, monthly_contribution, late_fee, late_fee_grace_days,
    probation_months, payment_due_day, max_members, current_members,
    is_public, allow_partial_payment, auto_suspend_after,
    bank_name, account_number, branch_code, account_type,
    balance, admin_user_id, fee_status, registration_paid,
    goal_amount, period_months,
    latitude, longitude, geohash
) VALUES (
    'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2',
    'Imali Yethu Stokvel',
    'stokvel',
    'KwaZulu-Natal',
    'Durban',
    'Umhlanga',
    'Monthly savings club for year-end payouts.',
    '💰',
    100.00, 500.00, 25.00, 7,
    1, 15, 12, 0,
    TRUE, FALSE, 3,
    'Standard Bank', '123456789', '051001', 'Cheque',
    18000.00, 'a2a2a2a2-a2a2-a2a2-a2a2-a2a2a2a2a2a2', 'paid', TRUE,
    72000.00, 12,
    -29.8587, 31.0218, 'kd3qzm1c4'
);

-- Group 3: Investment Club (Public)
INSERT INTO public.groups (
    id, name, type, province, city, township, description, logo_emoji,
    joining_fee, monthly_contribution, late_fee, late_fee_grace_days,
    probation_months, payment_due_day, max_members, current_members,
    is_public, allow_partial_payment, auto_suspend_after,
    bank_name, account_number, branch_code, account_type,
    balance, admin_user_id, fee_status, registration_paid,
    goal_amount, period_months,
    latitude, longitude, geohash
) VALUES (
    'c3c3c3c3-c3c3-c3c3-c3c3-c3c3c3c3c3c3',
    'Wealth Builders Club',
    'investment_club',
    'Western Cape',
    'Cape Town',
    'Bellville',
    'Investing together for a better future.',
    '📈',
    500.00, 1000.00, 100.00, 3,
    6, 1, 20, 0,
    TRUE, TRUE, 1,
    'Nedbank', '1234567890123', '198765', 'Business',
    75000.00, 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'paid', TRUE,
    500000.00, 24,
    -33.9249, 18.4241, 'k3vn8gxf2'
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. CREATE MOCK MEMBERS (Including Admin as first member)
-- ─────────────────────────────────────────────────────────────────────────────

-- Group 1 Members
INSERT INTO public.members (
    id, group_id, user_id, full_name, id_number, phone, email,
    street, suburb, city, province, notification_pref, status,
    joined_at, probation_end_at, beneficiary_count, beneficiary_over_65_count
) VALUES
-- Admin as member
('d1000001-d100-d100-d100-d10000000001', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'John Admin', '8501015009087', '0821234567', 'admin1@test.com', '123 Main St', 'Soweto', 'Johannesburg', 'Gauteng', 'both', 'active', NOW() - INTERVAL '6 months', NOW() - INTERVAL '3 months', 2, 1),
-- Regular members
('d1000002-d100-d100-d100-d10000000002', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1', 'Sipho Nkosi', '9001015800085', '0831112233', 'member1@test.com', '45 Freedom Ave', 'Diepkloof', 'Johannesburg', 'Gauteng', 'whatsapp', 'active', NOW() - INTERVAL '4 months', NOW() - INTERVAL '1 month', 3, 0),
('d1000003-d100-d100-d100-d10000000003', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2', 'Thandi Dlamini', '8506150023087', '0842223344', 'member2@test.com', '78 Unity Rd', 'Orlando', 'Johannesburg', 'Gauteng', 'email', 'probation', NOW() - INTERVAL '1 month', NOW() + INTERVAL '2 months', 1, 1);

-- Group 2 Members
INSERT INTO public.members (
    id, group_id, user_id, full_name, id_number, phone, email,
    street, suburb, city, province, notification_pref, status,
    joined_at, probation_end_at
) VALUES
('d2000001-d200-d200-d200-d20000000001', 'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 'a2a2a2a2-a2a2-a2a2-a2a2-a2a2a2a2a2a2', 'Jane Admin', '8803015800089', '0711234567', 'admin2@test.com', '10 Beach Rd', 'Umhlanga', 'Durban', 'KwaZulu-Natal', 'both', 'active', NOW() - INTERVAL '8 months', NOW() - INTERVAL '7 months'),
('d2000002-d200-d200-d200-d20000000002', 'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 'b3b3b3b3-b3b3-b3b3-b3b3-b3b3b3b3b3b3', 'Bongani Moyo', '9205015800082', '0723334455', 'member3@test.com', '22 Durban St', 'Phoenix', 'Durban', 'KwaZulu-Natal', 'whatsapp', 'active', NOW() - INTERVAL '5 months', NOW() - INTERVAL '4 months'),
('d2000003-d200-d200-d200-d20000000003', 'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 'b4b4b4b4-b4b4-b4b4-b4b4-b4b4b4b4b4b4', 'Nomvula Zulu', '9510150123088', '0734445566', 'member4@test.com', '5 Zulu Lane', 'Pinetown', 'Durban', 'KwaZulu-Natal', 'both', 'active', NOW() - INTERVAL '3 months', NOW());

-- Update group member counts manually since trigger might not fire
UPDATE public.groups SET current_members = 3 WHERE id = 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1';
UPDATE public.groups SET current_members = 3 WHERE id = 'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2';

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. CREATE MOCK BENEFICIARIES
-- ─────────────────────────────────────────────────────────────────────────────


INSERT INTO public.beneficiaries (group_id, member_id, id, full_name, relationship, date_of_birth, is_over_65, document_url, document_status)
VALUES
-- Admin's beneficiaries
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'd1000001-d100-d100-d100-d10000000001', gen_random_uuid(), 'Mary Admin', 'Spouse', '1987-03-15', FALSE, NULL, 'pending'),
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'd1000001-d100-d100-d100-d10000000001', gen_random_uuid(), 'James Admin Sr', 'Father', '1955-08-20', TRUE, NULL, 'pending'),
-- Sipho's beneficiaries
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'd1000002-d100-d100-d100-d10000000002', gen_random_uuid(), 'Grace Nkosi', 'Spouse', '1992-05-10', FALSE, 'https://example.com/docs/grace_id.pdf', 'verified'),
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'd1000002-d100-d100-d100-d10000000002', gen_random_uuid(), 'Junior Nkosi', 'Child', '2015-01-20', FALSE, NULL, 'pending'),
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'd1000002-d100-d100-d100-d10000000002', gen_random_uuid(), 'Baby Nkosi', 'Child', '2020-06-15', FALSE, NULL, 'pending'),
-- Thandi's beneficiaries
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'd1000003-d100-d100-d100-d10000000003', gen_random_uuid(), 'Gogo Dlamini', 'Mother', '1950-12-01', TRUE, NULL, 'pending');

-- ─────────────────────────────────────────────────────────────────────────────
-- 10. CREATE MOCK CONTRIBUTIONS
-- ─────────────────────────────────────────────────────────────────────────────

-- John Admin's contributions (6 months worth)
INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, status, yoco_transaction_id)
SELECT
    'd1000001-d100-d100-d100-d10000000001',
    'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1',
    150.00,
    'contribution',
    (NOW() - (i || ' months')::interval)::date,
    NOW() - ((i - 1) || ' months')::interval,
    'yoco',
    'paid',
    'yoco_test_' || gen_random_uuid()::text
FROM generate_series(1, 6) AS i;

-- Sipho Nkosi's contributions (4 months, on probation rate)
INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, status, yoco_transaction_id)
SELECT
    'd1000002-d100-d100-d100-d10000000002',
    'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1',
    150.00,
    'contribution',
    (NOW() - (i || ' months')::interval)::date,
    NOW() - ((i - 1) || ' months')::interval,
    'yoco',
    'paid',
    'yoco_test_' || gen_random_uuid()::text
FROM generate_series(1, 4) AS i;

-- Thandi Dlamini (1 month paid, current month overdue with late fee)
INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, status, yoco_transaction_id)
VALUES
('d1000003-d100-d100-d100-d10000000003', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 165.00, 'contribution', (NOW() - INTERVAL '1 month')::date, NOW() - INTERVAL '3 weeks', 'yoco', 'paid', 'yoco_test_' || gen_random_uuid()::text);

-- Current month overdue contribution + separate late fee record
INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, status, yoco_transaction_id, late_fees_applied)
VALUES
('d1000003-d100-d100-d100-d10000000003', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 150.00, 'contribution', (DATE_TRUNC('month', NOW()) + INTERVAL '27 days')::date, NULL, 'yoco', 'overdue', NULL, TRUE),
('d1000003-d100-d100-d100-d10000000003', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 50.00,  'late_fee',      (DATE_TRUNC('month', NOW()) + INTERVAL '27 days')::date, NULL, 'yoco', 'due', NULL, TRUE);

-- Group 2 Contributions
INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, status, yoco_transaction_id)
SELECT
    'd2000001-d200-d200-d200-d20000000001',
    'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2',
    500.00,
    'contribution',
    (NOW() - (i || ' months')::interval)::date,
    NOW() - ((i - 1) || ' months')::interval,
    'bank',
    'paid',
    'yoco_test_' || gen_random_uuid()::text
FROM generate_series(1, 8) AS i;

INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, status, yoco_transaction_id)
SELECT
    'd2000002-d200-d200-d200-d20000000002',
    'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2',
    500.00,
    'contribution',
    (NOW() - (i || ' months')::interval)::date,
    NOW() - ((i - 1) || ' months')::interval,
    'yoco',
    'paid',
    'yoco_test_' || gen_random_uuid()::text
FROM generate_series(1, 5) AS i;

-- Joining fee contributions (paid) for the two non-admin members in Group 1
INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, status, yoco_transaction_id)
VALUES
('d1000002-d100-d100-d100-d10000000002', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 250.00, 'joining_fee', (NOW() - INTERVAL '4 months')::date, NOW() - INTERVAL '4 months', 'yoco', 'paid', 'yoco_join_' || gen_random_uuid()::text),
('d1000003-d100-d100-d100-d10000000003', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 250.00, 'joining_fee', (NOW() - INTERVAL '1 month')::date, NOW() - INTERVAL '1 month', 'yoco', 'paid', 'yoco_join_' || gen_random_uuid()::text);

-- Registration contributions (platform registration fee credit) for group admins
INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, status, yoco_transaction_id)
VALUES
('d1000001-d100-d100-d100-d10000000001', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 700.00, 'registration_contribution', (NOW() - INTERVAL '6 months')::date, NOW() - INTERVAL '6 months', 'yoco', 'paid', 'yoco_reg_' || gen_random_uuid()::text),
('d2000001-d200-d200-d200-d20000000001', 'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 700.00, 'registration_contribution', (NOW() - INTERVAL '8 months')::date, NOW() - INTERVAL '8 months', 'yoco', 'paid', 'yoco_reg_' || gen_random_uuid()::text);

-- ─────────────────────────────────────────────────────────────────────────────
-- 10b. SCALE UP MOCK DATA (~10x) + RICH SCENARIOS
-- ─────────────────────────────────────────────────────────────────────────────

-- Additional groups (27) with mixed types, fee states, public/private, and some coordinates.
-- NOTE: We reuse the existing group admins as the admin_user_id so we don't need to create 27 extra auth users.
WITH params AS (
    SELECT
        'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1'::uuid AS admin1,
        'a2a2a2a2-a2a2-a2a2-a2a2-a2a2a2a2a2a2'::uuid AS admin2
), gen AS (
    SELECT
        i,
        CASE (i % 7)
            WHEN 0 THEN 'burial_society'
            WHEN 1 THEN 'stokvel'
            WHEN 2 THEN 'rosca'
            WHEN 3 THEN 'investment_club'
            WHEN 4 THEN 'emergency_fund'
            WHEN 5 THEN 'community_savings'
            ELSE 'other'
        END AS group_type,
        CASE WHEN (i % 2) = 0 THEN (SELECT admin1 FROM params) ELSE (SELECT admin2 FROM params) END AS admin_user_id,
        CASE WHEN (i % 4) IN (0, 1) THEN TRUE ELSE FALSE END AS is_public,
        CASE WHEN (i % 5) = 0 THEN TRUE ELSE FALSE END AS allow_partial_payment,
        CASE
            WHEN (i % 13) = 0 THEN 'suspended'
            WHEN (i % 9) = 0 THEN 'warning'
            WHEN (i % 7) = 0 THEN 'due'
            ELSE 'paid'
        END AS fee_status,
        CASE WHEN (i % 3) = 0 THEN 'Gauteng' WHEN (i % 3) = 1 THEN 'KwaZulu-Natal' ELSE 'Western Cape' END AS province,
        CASE WHEN (i % 3) = 0 THEN 'Johannesburg' WHEN (i % 3) = 1 THEN 'Durban' ELSE 'Cape Town' END AS city,
        CASE WHEN (i % 3) = 0 THEN 'Alexandra' WHEN (i % 3) = 1 THEN 'Umlazi' ELSE 'Khayelitsha' END AS township
    FROM generate_series(1, 27) AS i
)
INSERT INTO public.groups (
    name, type, province, city, township, description, logo_emoji,
    joining_fee, monthly_contribution, late_fee, late_fee_grace_days,
    probation_months, payment_due_day, max_members,
    is_public, allow_partial_payment, auto_suspend_after,
    bank_name, account_number, branch_code, account_type,
    balance, admin_user_id, fee_status, registration_paid, is_platform_suspended,
    constitution_url, constitution_status,
    latitude, longitude, geohash,
    max_beneficiaries, beneficiary_increase_pct, goal_amount, period_months
)
SELECT
    'Mock Group ' || i,
    group_type,
    province,
    city,
    township,
    'Seeded scenario group #' || i || ' (' || group_type || ')',
    CASE group_type
        WHEN 'burial_society' THEN '🕊️'
        WHEN 'stokvel' THEN '💰'
        WHEN 'rosca' THEN '🔁'
        WHEN 'investment_club' THEN '📈'
        WHEN 'emergency_fund' THEN '🧯'
        WHEN 'community_savings' THEN '🏘️'
        ELSE '🤝'
    END,
    CASE WHEN group_type IN ('burial_society','investment_club') THEN 250 ELSE 100 END::numeric,
    CASE WHEN group_type = 'investment_club' THEN 1200 WHEN group_type = 'stokvel' THEN 600 ELSE 200 END::numeric,
    CASE WHEN group_type IN ('burial_society','investment_club') THEN 75 ELSE 25 END::numeric,
    5,
    CASE WHEN group_type = 'investment_club' THEN 6 ELSE 3 END,
    CASE WHEN (i % 3) = 0 THEN 28 WHEN (i % 3) = 1 THEN 15 ELSE 1 END,
    CASE WHEN group_type = 'stokvel' THEN 12 ELSE 50 END,
    is_public,
    allow_partial_payment,
    2,
    CASE WHEN (i % 2) = 0 THEN 'FNB' ELSE 'Standard Bank' END,
    CASE WHEN (i % 2) = 0 THEN '62123456789' ELSE '123456789' END,
    CASE WHEN (i % 2) = 0 THEN '250655' ELSE '051001' END,
    'Savings',
    (1000 + (i * 250))::numeric,
    admin_user_id,
    fee_status,
    (fee_status = 'paid') AS registration_paid,
    (fee_status = 'suspended') AS is_platform_suspended,
    CASE WHEN (i % 6) = 0 THEN 'https://example.com/constitutions/group_' || i || '.pdf' ELSE NULL END,
    CASE WHEN (i % 12) = 0 THEN 'rejected' WHEN (i % 6) = 0 THEN 'verified' ELSE 'pending' END,
    CASE WHEN (i % 2) = 0 THEN NULL
         WHEN province = 'Gauteng' THEN -26.2041
         WHEN province = 'KwaZulu-Natal' THEN -29.8587
         ELSE -33.9249 END,
    CASE WHEN (i % 2) = 0 THEN NULL
         WHEN province = 'Gauteng' THEN 28.0473
         WHEN province = 'KwaZulu-Natal' THEN 31.0218
         ELSE 18.4241 END,
    SUBSTRING(md5('mock-group-' || i::text) FROM 1 FOR 9),
    CASE WHEN group_type = 'burial_society' THEN 5 ELSE 0 END,
    CASE WHEN group_type = 'burial_society' THEN 10 ELSE 0 END::numeric,
    CASE WHEN group_type = 'investment_club' THEN 500000 WHEN group_type = 'stokvel' THEN 72000 ELSE 50000 END::numeric,
    CASE WHEN group_type = 'investment_club' THEN 24 ELSE 12 END
FROM gen;

-- Policies for burial society groups (used by some contributions)
INSERT INTO public.policies (group_id, name, description, required_amount, status)
SELECT
    g.id,
    'Policy A - Basic Cover',
    'Basic cover policy for ' || g.name,
    5000,
    'active'
FROM public.groups g
WHERE g.type = 'burial_society'
UNION ALL
SELECT
    g.id,
    'Policy B - Extended Cover',
    'Extended cover policy for ' || g.name,
    15000,
    CASE WHEN (abs(hashtext(g.id::text)) % 3) = 0 THEN 'partial' ELSE 'inactive' END
FROM public.groups g
WHERE g.type = 'burial_society';

-- Bulk members for all groups (adds 6–14 members each) with mixed statuses.
-- user_id is NULL for synthetic members to avoid creating many auth users.
INSERT INTO public.members (
    group_id, user_id, full_name, id_number, phone, email,
    street, suburb, city, province, notification_pref, status,
    joined_at, probation_end_at
)
SELECT
    g.id AS group_id,
    NULL::uuid AS user_id,
    ('Mock Member ' || SUBSTRING(g.name FROM 1 FOR 24) || ' #' || m)::text AS full_name,
    LPAD(((abs(hashtext(g.id::text))::bigint + (m * 7919)) % 10000000000000)::text, 13, '0') AS id_number,
    ('07' || LPAD(((abs(hashtext(g.id::text)) + (m * 97)) % 100000000)::text, 8, '0')) AS phone,
    NULL::text AS email,
    (m || ' Seed Street')::text AS street,
    g.township,
    g.city,
    g.province,
    CASE (m % 3) WHEN 0 THEN 'whatsapp' WHEN 1 THEN 'email' ELSE 'both' END,
    CASE
        WHEN (m % 11) = 0 THEN 'pending_payment'
        WHEN (m % 9) = 0 THEN 'suspended'
        WHEN (m % 4) = 0 THEN 'probation'
        ELSE 'active'
    END,
    NOW() - ((m + (abs(hashtext(g.id::text)) % 180)) || ' days')::interval,
    CASE
        WHEN (m % 4) = 0 THEN NOW() + ((g.probation_months * 30) || ' days')::interval
        ELSE NOW() - INTERVAL '1 day'
    END
FROM public.groups g
JOIN LATERAL generate_series(1,
    CASE
        WHEN g.type IN ('burial_society','investment_club') THEN 14
        WHEN g.type IN ('stokvel','rosca') THEN 10
        ELSE 6
    END
) AS m ON TRUE;

-- Multi-group memberships for real test accounts (to exercise the group switcher)
-- Assign member1@test.com + member2@test.com into several groups.
WITH target_groups AS (
    SELECT id
    FROM public.groups
    WHERE id NOT IN (
        'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1',
        'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2'
    )
    ORDER BY created_at
    LIMIT 6
)
INSERT INTO public.members (group_id, user_id, full_name, id_number, phone, email, notification_pref, status, joined_at, probation_end_at)
SELECT
    tg.id,
    'b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1'::uuid,
    'Sipho Nkosi',
    '9001015800085',
    '0831112233',
    'member1@test.com',
    'whatsapp',
    'active',
    NOW() - INTERVAL '2 months',
    NOW() - INTERVAL '1 month'
FROM target_groups tg
ON CONFLICT (group_id, user_id) DO NOTHING;

WITH target_groups AS (
    SELECT id
    FROM public.groups
    WHERE id NOT IN (
        'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1',
        'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2'
    )
    ORDER BY created_at
    OFFSET 3
    LIMIT 6
)
INSERT INTO public.members (group_id, user_id, full_name, id_number, phone, email, notification_pref, status, joined_at, probation_end_at)
SELECT
    tg.id,
    'b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2'::uuid,
    'Thandi Dlamini',
    '8506150023087',
    '0842223344',
    'member2@test.com',
    'email',
    'probation',
    NOW() - INTERVAL '1 month',
    NOW() + INTERVAL '2 months'
FROM target_groups tg
ON CONFLICT (group_id, user_id) DO NOTHING;

-- Beneficiaries for burial society members (0–3 each), mixed document statuses.
INSERT INTO public.beneficiaries (
    group_id, member_id, id, full_name, relationship, date_of_birth, is_over_65,
    document_url, document_status
)
SELECT
    m.group_id,
    m.id AS member_id,
    gen_random_uuid(),
    'Beneficiary ' || b || ' of ' || split_part(m.full_name, ' ', 1),
    CASE (b % 4) WHEN 0 THEN 'Spouse' WHEN 1 THEN 'Child' WHEN 2 THEN 'Parent' ELSE 'Sibling' END,
    (NOW() - ((18 + (b * 7) + (abs(hashtext(m.id::text)) % 50)) || ' years')::interval)::date,
    ((b % 5) = 0) AS is_over_65,
    CASE WHEN (b % 3) = 0 THEN 'https://example.com/docs/beneficiaries/' || m.id::text || '_' || b || '.pdf' ELSE NULL END,
    CASE WHEN (b % 7) = 0 THEN 'rejected' WHEN (b % 3) = 0 THEN 'verified' ELSE 'pending' END
FROM public.members m
JOIN public.groups g ON g.id = m.group_id
JOIN LATERAL generate_series(1, CASE WHEN (abs(hashtext(m.id::text)) % 4) = 0 THEN 0 ELSE 3 END) AS b ON TRUE
WHERE g.type = 'burial_society';

-- Member documents (2 per member) with ON CONFLICT protection for the base seeded members.
INSERT INTO public.member_documents (member_id, group_id, label, document_url, document_type, status)
SELECT
    m.id,
    m.group_id,
    doc.label,
    'https://example.com/docs/members/' || m.id::text || '/' || doc.path,
    doc.doc_type,
    CASE WHEN (abs(hashtext(m.id::text)) % 11) = 0 THEN 'rejected'
         WHEN (abs(hashtext(m.id::text)) % 4) = 0 THEN 'verified'
         ELSE 'pending' END AS status
FROM public.members m
JOIN LATERAL (
    VALUES
        ('ID Document', 'id.pdf', 'pdf'),
        ('Proof of Address', 'poa.pdf', 'pdf')
) AS doc(label, path, doc_type) ON TRUE
ON CONFLICT (member_id, label) DO NOTHING;

-- Monthly contribution schedule (6 months) for every member, with mixed paid/due/overdue/partial.
-- Some burial society contributions will reference an active policy.
WITH member_base AS (
    SELECT
        m.id AS member_id,
        m.group_id,
        m.status AS member_status,
        g.monthly_contribution,
        g.joining_fee,
        g.late_fee,
        g.type AS group_type,
        (SELECT p.id FROM public.policies p WHERE p.group_id = m.group_id AND p.status = 'active' ORDER BY created_at LIMIT 1) AS active_policy_id
    FROM public.members m
    JOIN public.groups g ON g.id = m.group_id
)
INSERT INTO public.contributions (
    member_id, group_id, policy_id, amount, type, due_date, paid_at,
    payment_method, yoco_transaction_id, status, late_fees_applied
)
SELECT
    mb.member_id,
    mb.group_id,
    CASE WHEN mb.group_type = 'burial_society' AND (k % 3) = 0 THEN mb.active_policy_id ELSE NULL END,
    CASE
        WHEN status_calc = 'partial' THEN (mb.monthly_contribution * 0.5)
        WHEN status_calc = 'late_fee' THEN mb.late_fee
        ELSE mb.monthly_contribution
    END::numeric,
    CASE WHEN status_calc = 'late_fee' THEN 'late_fee' ELSE 'contribution' END,
    (DATE_TRUNC('month', NOW()) - ((k - 1) || ' months')::interval + INTERVAL '14 days')::date,
    CASE
        WHEN status_calc IN ('paid','partial') THEN NOW() - ((k - 1) || ' months')::interval + INTERVAL '16 days'
        ELSE NULL
    END,
    CASE WHEN (abs(hashtext(mb.member_id::text)) % 5) = 0 THEN 'bank' ELSE 'yoco' END,
    CASE WHEN status_calc IN ('paid','partial') THEN 'tx_' || gen_random_uuid()::text ELSE NULL END,
    CASE
        WHEN status_calc = 'late_fee' THEN 'due'
        ELSE status_calc
    END,
    (status_calc IN ('overdue','late_fee'))
FROM member_base mb
JOIN LATERAL generate_series(1, 6) AS k ON TRUE
JOIN LATERAL (
    SELECT
        CASE
            WHEN k = 1 AND (abs(hashtext(mb.member_id::text)) % 10) IN (0,1) THEN 'overdue'
            WHEN k = 1 AND (abs(hashtext(mb.member_id::text)) % 10) IN (2,3) THEN 'partial'
            WHEN k <= 4 THEN 'paid'
            ELSE 'due'
        END AS status_calc
) sc ON TRUE
UNION ALL
-- Add explicit late fee rows for overdue scenarios (current month only)
SELECT
    mb.member_id,
    mb.group_id,
    NULL,
    mb.late_fee::numeric,
    'late_fee',
    (DATE_TRUNC('month', NOW()) + INTERVAL '14 days')::date,
    NULL,
    'yoco',
    NULL,
    'due',
    TRUE
FROM member_base mb
WHERE (abs(hashtext(mb.member_id::text)) % 10) IN (0,1);

-- Joining fee contribution record per member (paid for active/probation, due for pending_payment)
INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, status, yoco_transaction_id)
SELECT
    m.id,
    m.group_id,
    g.joining_fee,
    'joining_fee',
    m.joined_at::date,
    CASE WHEN m.status IN ('active','probation') THEN m.joined_at ELSE NULL END,
    'yoco',
    CASE WHEN m.status IN ('active','probation') THEN 'paid' ELSE 'due' END,
    CASE WHEN m.status IN ('active','probation') THEN 'join_' || gen_random_uuid()::text ELSE NULL END
FROM public.members m
JOIN public.groups g ON g.id = m.group_id
WHERE g.joining_fee > 0;

-- Payments derived from joining_fee contributions (keeps payments table populated for UI testing)
INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at)
SELECT
    c.member_id,
    c.group_id,
    c.amount,
    'joining_fee',
    'yoco',
    c.yoco_transaction_id,
    CASE WHEN c.status = 'paid' THEN 'completed' ELSE 'pending' END,
    c.paid_at
FROM public.contributions c
WHERE c.type = 'joining_fee';

-- Additional mixed payments (contribution/late_fee/custom) to test payment history UI.
INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at)
SELECT
    m.id,
    m.group_id,
    (100 + (abs(hashtext(m.id::text)) % 900))::numeric,
    CASE (abs(hashtext(m.id::text)) % 6)
        WHEN 0 THEN 'contribution'
        WHEN 1 THEN 'late_fee'
        WHEN 2 THEN 'platform_fee'
        WHEN 3 THEN 'custom'
        WHEN 4 THEN 'registration'
        ELSE 'claim'
    END,
    CASE (abs(hashtext(m.id::text)) % 4)
        WHEN 0 THEN 'yoco'
        WHEN 1 THEN 'bank'
        WHEN 2 THEN 'cash'
        ELSE 'other'
    END,
    'pay_' || gen_random_uuid()::text,
    CASE (abs(hashtext(m.id::text)) % 5)
        WHEN 0 THEN 'failed'
        WHEN 1 THEN 'refunded'
        WHEN 2 THEN 'processing'
        ELSE 'completed'
    END,
    NOW() - ((abs(hashtext(m.id::text)) % 120) || ' days')::interval
FROM public.members m
WHERE (abs(hashtext(m.id::text)) % 4) = 0;

-- Notifications (group-wide + member-specific), enriched volume.
INSERT INTO public.notifications (group_id, member_id, message, channel, trigger_event)
SELECT
    g.id,
    NULL,
    'System notice for ' || g.name || ': next contributions due on day ' || g.payment_due_day,
    'both',
    'payment_due'
FROM public.groups g;

INSERT INTO public.notifications (group_id, member_id, message, channel, trigger_event)
SELECT
    m.group_id,
    m.id,
    'Hi ' || split_part(m.full_name, ' ', 1) || ', please review your account status: ' || m.status,
    CASE (abs(hashtext(m.id::text)) % 3) WHEN 0 THEN 'whatsapp' WHEN 1 THEN 'email' ELSE 'both' END,
    'custom'
FROM public.members m
WHERE (abs(hashtext(m.id::text)) % 5) = 0;

-- Payout requests across all statuses.
INSERT INTO public.payouts (group_id, amount, bank_name, account_no, branch_code, status, created_at, processed_at, processed_by)
SELECT
    g.id,
    (1000 + (abs(hashtext(g.id::text)) % 20000))::numeric,
    'FNB',
    '62123456789',
    '250655',
    CASE (abs(hashtext(g.id::text)) % 5)
        WHEN 0 THEN 'pending'
        WHEN 1 THEN 'processing'
        WHEN 2 THEN 'completed'
        WHEN 3 THEN 'failed'
        ELSE 'cancelled'
    END,
    NOW() - ((abs(hashtext(g.id::text)) % 90) || ' days')::interval,
    CASE WHEN (abs(hashtext(g.id::text)) % 5) IN (2,3) THEN NOW() - ((abs(hashtext(g.id::text)) % 60) || ' days')::interval ELSE NULL END,
    CASE WHEN (abs(hashtext(g.id::text)) % 5) IN (2,3) THEN '1b8aca84-c136-4c1b-b024-902584ae80d8'::uuid ELSE NULL END
FROM public.groups g
WHERE (abs(hashtext(g.id::text)) % 3) = 0;

-- Platform fees: registration + monthly timeline with mixed due/warning/suspended.
INSERT INTO public.platform_fees (group_id, fee_type, amount, status, due_date, paid_at, transaction_id)
SELECT
    g.id,
    'registration',
    700.00,
    CASE
        WHEN g.fee_status IN ('suspended','warning','due') THEN g.fee_status
        ELSE 'paid'
    END,
    (g.created_at::date + INTERVAL '7 days')::text,
    CASE WHEN g.fee_status = 'paid' THEN g.created_at + INTERVAL '1 day' ELSE NULL END,
    CASE WHEN g.fee_status = 'paid' THEN 'regfee_' || gen_random_uuid()::text ELSE NULL END
FROM public.groups g;

INSERT INTO public.platform_fees (group_id, fee_type, amount, status, due_date, paid_at, transaction_id)
SELECT
    g.id,
    'monthly',
    250.00,
    CASE
        WHEN (abs(hashtext(g.id::text)) % 12) = 0 THEN 'suspended'
        WHEN (abs(hashtext(g.id::text)) % 8) = 0 THEN 'warning'
        WHEN (abs(hashtext(g.id::text)) % 5) = 0 THEN 'due'
        ELSE 'paid'
    END,
    (DATE_TRUNC('month', NOW()) - ((m - 1) || ' months')::interval)::date::text,
    CASE WHEN (abs(hashtext(g.id::text)) % 5) <> 0 THEN NOW() - ((m - 1) || ' months')::interval ELSE NULL END,
    CASE WHEN (abs(hashtext(g.id::text)) % 5) <> 0 THEN 'monthly_' || gen_random_uuid()::text ELSE NULL END
FROM public.groups g
JOIN LATERAL generate_series(1, 3) AS m ON TRUE;

-- Ensure group fee flags align with the registration fee status
UPDATE public.groups g
SET
    registration_paid = (pf.status = 'paid'),
    fee_status = pf.status,
    is_platform_suspended = (pf.status = 'suspended')
FROM public.platform_fees pf
WHERE pf.group_id = g.id
  AND pf.fee_type = 'registration';

-- Actuarial metrics snapshot per group (one row each)
INSERT INTO public.group_actuarial_metrics (
    group_id, pure_premium, gross_premium, reserve_adequacy_pct,
    solvency_margin_pct, loss_ratio_pct, contribution_sufficiency_pct,
    break_even_members, payment_rate_pct, composite_risk_score
)
SELECT
    g.id,
    (50 + (abs(hashtext(g.id::text)) % 500))::numeric,
    (75 + (abs(hashtext(g.id::text)) % 650))::numeric,
    (60 + (abs(hashtext(g.id::text)) % 60))::numeric,
    (80 + (abs(hashtext(g.id::text)) % 100))::numeric,
    (30 + (abs(hashtext(g.id::text)) % 70))::numeric,
    (70 + (abs(hashtext(g.id::text)) % 40))::numeric,
    (3 + (abs(hashtext(g.id::text)) % 25)),
    (60 + (abs(hashtext(g.id::text)) % 40))::numeric,
    (5 + (abs(hashtext(g.id::text)) % 90))::int
FROM public.groups g
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 11. CREATE MOCK PAYMENTS
-- ─────────────────────────────────────────────────────────────────────────────

-- Registration payments for groups
INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at)
VALUES
('d1000001-d100-d100-d100-d10000000001', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 450.00, 'platform_fee', 'yoco', 'yoco_reg_c1', 'completed', NOW() - INTERVAL '6 months'),
('d2000001-d200-d200-d200-d20000000001', 'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 450.00, 'platform_fee', 'yoco', 'yoco_reg_c2', 'completed', NOW() - INTERVAL '8 months');

-- Joining fee payments
INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at)
VALUES
('d1000002-d100-d100-d100-d10000000002', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 250.00, 'joining_fee', 'yoco', 'yoco_join_d1m2', 'completed', NOW() - INTERVAL '4 months'),
('d1000003-d100-d100-d100-d10000000003', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 250.00, 'joining_fee', 'yoco', 'yoco_join_d1m3', 'completed', NOW() - INTERVAL '1 month'),
('d2000002-d200-d200-d200-d20000000002', 'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 100.00, 'joining_fee', 'yoco', 'yoco_join_d2m2', 'completed', NOW() - INTERVAL '5 months'),
('d2000003-d200-d200-d200-d20000000003', 'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 100.00, 'joining_fee', 'yoco', 'yoco_join_d2m3', 'completed', NOW() - INTERVAL '3 months');

-- ─────────────────────────────────────────────────────────────────────────────
-- 12. CREATE MOCK NOTIFICATIONS
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO public.notifications (group_id, member_id, message, channel, trigger_event)
VALUES
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', NULL, 'Welcome to Ubuntu Burial Society! Your group is now active.', 'both', 'custom'),
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'd1000002-d100-d100-d100-d10000000002', 'Welcome Sipho! Your joining fee has been received.', 'whatsapp', 'payment_confirmed'),
('c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', NULL, 'Reminder: Contributions are due on the 15th of each month.', 'both', 'payment_due');

-- ─────────────────────────────────────────────────────────────────────────────
-- 13. CREATE MOCK MEMBER DOCUMENTS
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO public.member_documents (member_id, group_id, label, document_url, document_type, status)
VALUES
('d1000002-d100-d100-d100-d10000000002', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'ID Document', 'https://example.com/docs/sipho_id.pdf', 'pdf', 'verified'),
('d1000002-d100-d100-d100-d10000000002', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'Proof of Address', 'https://example.com/docs/sipho_poa.pdf', 'pdf', 'pending'),
('d1000003-d100-d100-d100-d10000000003', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'ID Document', 'https://example.com/docs/thandi_id.pdf', 'pdf', 'pending')
ON CONFLICT (member_id, label) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 14. CREATE MOCK PAYOUTS
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO public.payouts (group_id, amount, bank_name, account_no, branch_code, status, created_at)
VALUES
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 2000.00, 'FNB', '62123456789', '250655', 'completed', NOW() - INTERVAL '2 months'),
('c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 5000.00, 'Standard Bank', '123456789', '051001', 'pending', NOW() - INTERVAL '1 week');

-- ─────────────────────────────────────────────────────────────────────────────
-- 14b. CREATE PLATFORM FEES (Registration fees)
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO public.platform_fees (group_id, fee_type, amount, status, due_date, paid_at)
VALUES
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'registration', 700.00, 'paid', (NOW() - INTERVAL '6 months')::text, NOW() - INTERVAL '6 months'),
('c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 'registration', 700.00, 'paid', (NOW() - INTERVAL '8 months')::text, NOW() - INTERVAL '8 months'),
('c3c3c3c3-c3c3-c3c3-c3c3-c3c3c3c3c3c3', 'registration', 700.00, 'paid', (NOW() - INTERVAL '3 months')::text, NOW() - INTERVAL '3 months');

-- ─────────────────────────────────────────────────────────────────────────────
-- 15. UPDATE MEMBER TOTALS
-- ─────────────────────────────────────────────────────────────────────────────

UPDATE public.members m
SET
    total_contributions = (SELECT COUNT(*) FROM public.contributions c WHERE c.member_id = m.id AND c.status = 'paid'),
    total_paid = (SELECT COALESCE(SUM(c.amount), 0) FROM public.contributions c WHERE c.member_id = m.id AND c.status = 'paid');

-- ─────────────────────────────────────────────────────────────────────────────
-- 16. REFRESH ACTUARIAL METRICS
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO public.group_actuarial_metrics (
    group_id, pure_premium, gross_premium, reserve_adequacy_pct,
    solvency_margin_pct, loss_ratio_pct, contribution_sufficiency_pct,
    break_even_members, payment_rate_pct, composite_risk_score
)
VALUES
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 120.00, 150.00, 85.0, 120.0, 65.0, 95.0, 8, 92.0, 25),
('c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 400.00, 500.00, 90.0, 150.0, 55.0, 100.0, 5, 100.0, 15);

-- ─────────────────────────────────────────────────────────────────────────────
-- 17. ENSURE STORAGE BUCKET EXISTS
-- ─────────────────────────────────────────────────────────────────────────────
-- Note: Storage buckets must be created via the Supabase Dashboard:
-- 1. Go to Storage > Create Bucket
-- 2. Name: "documents"
-- 3. Public: No (private bucket)
-- 4. Allowed MIME types: application/pdf, image/jpeg, image/png

-- ─────────────────────────────────────────────────────────────────────────────
-- 18. VERIFY DATA
-- ─────────────────────────────────────────────────────────────────────────────

SELECT 'Platform Admin' as entity, COUNT(*) as count FROM public.profiles WHERE role = 'platform_admin'
UNION ALL
SELECT 'Groups', COUNT(*) FROM public.groups
UNION ALL
SELECT 'Members', COUNT(*) FROM public.members
UNION ALL
SELECT 'Beneficiaries', COUNT(*) FROM public.beneficiaries
UNION ALL
SELECT 'Contributions', COUNT(*) FROM public.contributions
UNION ALL
SELECT 'Payments', COUNT(*) FROM public.payments
UNION ALL
SELECT 'Notifications', COUNT(*) FROM public.notifications
UNION ALL
SELECT 'Member Documents', COUNT(*) FROM public.member_documents
UNION ALL
SELECT 'Payouts', COUNT(*) FROM public.payouts
UNION ALL
SELECT 'Platform Fees', COUNT(*) FROM public.platform_fees;

-- 19. REFRESH PostgREST CACHE
NOTIFY pgrst, 'reload schema';

-- ─────────────────────────────────────────────────────────────────────────────
-- DONE! Your database is now reset with mock data.
--
-- TEST ACCOUNTS:
-- Platform Admin: torryymsimango@gmail.com / torry123M
-- Group Admin 1:  admin1@test.com / password123 (Ubuntu Burial Society)
-- Group Admin 2:  admin2@test.com / password123 (Imali Yethu Stokvel)
-- Member 1:       member1@test.com / password123
-- Member 2:       member2@test.com / password123
-- Member 3:       member3@test.com / password123
-- Member 4:       member4@test.com / password123
-- ─────────────────────────────────────────────────────────────────────────────

