-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — LARGE SCALE SEED (20 Groups, 200 Members)
-- Version: 4.0 (Updated April 29, 2026)
-- ─────────────────────────────────────────────────────────────────────────────
-- This script performs a full database reset and populates it with:
-- 1. 20 diverse groups across South African provinces.
-- 2. 200 members with realistic scenarios (overdue, probation, suspended).
-- 3. Historical contributions, active loans, and pending payouts.
-- 4. Syncs the platform admin: torrymsimango@gmail.com
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. CLEANUP EVERYTHING
DELETE FROM auth.users;
TRUNCATE TABLE public.profiles CASCADE;
TRUNCATE TABLE public.groups CASCADE;
TRUNCATE TABLE public.members CASCADE;
TRUNCATE TABLE public.contributions CASCADE;
TRUNCATE TABLE public.loans CASCADE;
TRUNCATE TABLE public.loan_repayments CASCADE;
TRUNCATE TABLE public.payouts CASCADE;
TRUNCATE TABLE public.notifications CASCADE;
TRUNCATE TABLE public.member_documents CASCADE;
TRUNCATE TABLE public.platform_fees CASCADE;
TRUNCATE TABLE public.audit_logs CASCADE;

-- 2. SEED PLATFORM ADMIN (Deterministic ID)
INSERT INTO auth.users (id, aud, role, email, encrypted_password, email_confirmed_at, raw_user_meta_data) VALUES
('1b8aca84-c136-4c1b-b024-902584ae80d8', 'authenticated', 'authenticated', 'torrymsimango@gmail.com', extensions.crypt('torry123M', extensions.gen_salt('bf')), NOW(), '{"full_name": "Torry Msimango", "role": "platform_admin"}');

-- Profile will be created via trigger

-- 3. SEED GROUP ADMINS (20 Admins)
DO $$
DECLARE
    i INT;
    admin_id UUID;
BEGIN
    FOR i IN 1..20 LOOP
        admin_id := gen_random_uuid();
        INSERT INTO auth.users (id, aud, role, email, encrypted_password, email_confirmed_at, raw_user_meta_data)
        VALUES (
            admin_id, 'authenticated', 'authenticated',
            'admin' || i || '@sanibonani.test',
            extensions.crypt('password123', extensions.gen_salt('bf')),
            NOW(),
            jsonb_build_object('full_name', 'Admin ' || i, 'role', 'group_admin')
        );
    END LOOP;
END $$;

-- 4. SEED GROUPS (20 Groups)
DO $$
DECLARE
    admin_rec RECORD;
    i INT := 1;
    g_types TEXT[] := ARRAY['burial_society', 'stokvel', 'rosca', 'investment_club', 'emergency_fund', 'community_savings'];
    provinces TEXT[] := ARRAY['Gauteng', 'KwaZulu-Natal', 'Western Cape', 'Eastern Cape', 'Limpopo', 'Mpumalanga'];
    cities TEXT[] := ARRAY['Johannesburg', 'Durban', 'Cape Town', 'Gqeberha', 'Polokwane', 'Mbombela'];
    g_id UUID;
BEGIN
    FOR admin_rec IN (SELECT id FROM auth.users WHERE raw_user_meta_data->>'role' = 'group_admin' ORDER BY created_at) LOOP
        g_id := gen_random_uuid();
        INSERT INTO public.groups (
            id, name, type, province, city, description, logo_emoji,
            joining_fee, monthly_contribution, late_fee, admin_user_id,
            fee_status, registration_paid, balance,
            latitude, longitude
        ) VALUES (
            g_id,
            'Group ' || i || ' (' || g_types[(i % array_length(g_types, 1)) + 1] || ')',
            g_types[(i % array_length(g_types, 1)) + 1],
            provinces[(i % array_length(provinces, 1)) + 1],
            cities[(i % array_length(cities, 1)) + 1],
            'This is a seeded group for testing high-volume scenarios.',
            ARRAY['🕊️', '💰', '📉', '🤝', '🧯', '🏘️'][(i % 6) + 1],
            (100 + (i * 10))::NUMERIC,
            (200 + (i * 50))::NUMERIC,
            50.00,
            admin_rec.id,
            CASE WHEN i % 5 = 0 THEN 'due' ELSE 'paid' END,
            CASE WHEN i % 5 = 0 THEN FALSE ELSE TRUE END,
            (i * 1000)::NUMERIC,
            -26.0 + (i * 0.1), 28.0 + (i * 0.1)
        );
        i := i + 1;
    END LOOP;
END $$;

-- 5. SEED MEMBERS (200 Members distributed across 20 groups)
DO $$
DECLARE
    g_rec RECORD;
    m_count INT := 0;
    m_id UUID;
    u_id UUID;
    statuses TEXT[] := ARRAY['active', 'probation', 'suspended', 'pending_payment'];
BEGIN
    FOR g_rec IN (SELECT id FROM public.groups) LOOP
        -- Add 10 members per group
        FOR j IN 1..10 LOOP
            m_count := m_count + 1;
            m_id := gen_random_uuid();

            -- Create a "Real" Auth user for the first 10 members only (for fast login testing)
            -- For others, user_id can be NULL or we reuse IDs to keep it fast.
            IF m_count <= 10 THEN
                u_id := gen_random_uuid();
                INSERT INTO auth.users (id, aud, role, email, encrypted_password, email_confirmed_at, raw_user_meta_data)
                VALUES (u_id, 'authenticated', 'authenticated', 'member' || m_count || '@sanibonani.test', extensions.crypt('password123', extensions.gen_salt('bf')), NOW(), jsonb_build_object('full_name', 'Test Member ' || m_count, 'role', 'member'));
            ELSE
                u_id := NULL;
            END IF;

            INSERT INTO public.members (
                id, group_id, user_id, full_name, id_number, phone, email, status,
                joined_at, total_contributions, total_paid
            ) VALUES (
                m_id,
                g_rec.id,
                u_id,
                'Member ' || m_count,
                '900101' || LPAD(m_count::text, 7, '0'),
                '072' || LPAD(m_count::text, 7, '0'),
                'member' || m_count || '@sanibonani.test',
                statuses[(m_count % 4) + 1],
                NOW() - (m_count || ' days')::INTERVAL,
                (m_count % 12),
                (m_count % 12) * 150.00
            );

            -- 6. ADD CONTRIBUTIONS (10 per member)
            FOR k IN 1..(m_count % 12) LOOP
                INSERT INTO public.contributions (
                    member_id, group_id, amount, due_date, paid_at, status
                ) VALUES (
                    m_id, g_rec.id, 150.00,
                    (NOW() - (k || ' months')::INTERVAL)::DATE,
                    NOW() - (k || ' months')::INTERVAL,
                    'paid'
                );
            END LOOP;

            -- 7. RANDOM SCENARIOS (Loans)
            IF m_count % 15 = 0 THEN
                INSERT INTO public.loans (
                    member_id, group_id, amount, total_to_repay, monthly_repayment, status, purpose
                ) VALUES (
                    m_id, g_rec.id, 5000, 5500, 500, 'active', 'Education'
                );
            END IF;

        END LOOP;
    END LOOP;
END $$;

-- 8. SEED PAYOUTS (Pending requests)
INSERT INTO public.payouts (group_id, amount, bank_name, account_no, branch_code, status, created_at)
SELECT id, 25000.00, 'FNB', '12345678', '250655', 'pending', NOW() - INTERVAL '1 day'
FROM public.groups LIMIT 5;

-- 9. SETTINGS
INSERT INTO public.platform_settings (key, value) VALUES ('monthly_per_member', 10.0), ('registration_fee', 700.0) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

-- 10. FINAL REFRESH
NOTIFY pgrst, 'reload schema';

SELECT 'Platform Admins' as entity, COUNT(*) FROM public.profiles WHERE role = 'platform_admin'
UNION ALL SELECT 'Groups', COUNT(*) FROM public.groups
UNION ALL SELECT 'Members', COUNT(*) FROM public.members
UNION ALL SELECT 'Loans', COUNT(*) FROM public.loans
UNION ALL SELECT 'Pending Payouts', COUNT(*) FROM public.payouts WHERE status = 'pending';
