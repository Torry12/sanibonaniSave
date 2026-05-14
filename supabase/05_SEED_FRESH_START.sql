-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — PRIMARY SEED SCRIPT
-- Version: 5.0 (Consolidated May 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. CLEANUP
TRUNCATE TABLE public.audit_logs CASCADE;
TRUNCATE TABLE public.loan_repayments CASCADE;
TRUNCATE TABLE public.loans CASCADE;
TRUNCATE TABLE public.payouts CASCADE;
TRUNCATE TABLE public.contributions CASCADE;
TRUNCATE TABLE public.members CASCADE;
TRUNCATE TABLE public.groups CASCADE;
TRUNCATE TABLE public.profiles CASCADE;

-- 2. RE-RUN ADMIN SETUP
-- (Logic from 03_PLATFORM_ADMIN_SETUP.sql should be run first or included)

-- 3. SEED GROUPS
DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
BEGIN
    -- Dynamically fetch the admin ID to ensure referential integrity
    SELECT id INTO v_admin_id FROM auth.users WHERE email = 'torrymsimango@gmail.com' LIMIT 1;

    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'Platform Admin user not found. Please run 03_PLATFORM_ADMIN_SETUP.sql first.';
    END IF;
    -- Standard Stokvels
    FOR i IN 1..3 LOOP
        INSERT INTO public.groups (id, name, type, province, city, admin_user_id, fee_status, registration_paid, balance, monthly_contribution)
        VALUES (gen_random_uuid(), 'Stokvel ' || i, 'stokvel', 'Gauteng', 'Johannesburg', v_admin_id, 'paid', true, 5000 * i, 500.0);
    END LOOP;

    -- Burial Society
    INSERT INTO public.groups (id, name, type, province, city, admin_user_id, fee_status, registration_paid, balance, monthly_contribution, beneficiary_increase_pct)
    VALUES (gen_random_uuid(), 'Unity Burial Society', 'burial_society', 'KwaZulu-Natal', 'Durban', v_admin_id, 'paid', true, 25000, 250.0, 10.0);

    -- ROSCA
    INSERT INTO public.groups (id, name, type, province, city, admin_user_id, fee_status, registration_paid, balance, monthly_contribution)
    VALUES (gen_random_uuid(), 'Market ROSCA', 'rosca', 'Western Cape', 'Cape Town', v_admin_id, 'paid', true, 0, 1000.0);

    -- Investment Club
    INSERT INTO public.groups (id, name, type, province, city, admin_user_id, fee_status, registration_paid, balance, monthly_contribution)
    VALUES (gen_random_uuid(), 'Future Wealth Club', 'investment_club', 'Gauteng', 'Pretoria', v_admin_id, 'paid', true, 150000, 2000.0);
pecific
    -- Group admin must always exist as a member in each seeded group.
    INSERT INTO public.members (
        id,
        group_id,
        user_id,
        full_name,
        email,
        phone,
        status,
        joined_at,
        member_key
    )
    SELECT
        gen_random_uuid(),
        g.id,
        v_admin_id,
        'Seed Group Admin - ' || g.name,
        format('fresh.admin.%s@seed.local', replace(lower(g.name), ' ', '_')),
        '0733300000',
        'active',
        now() - interval '120 days',
        format('FRESH_ADMIN_KEY_%s', replace(lower(g.name), ' ', '_'))
    FROM public.groups g
    WHERE g.admin_user_id = v_admin_id
    ON CONFLICT (group_id, user_id) DO NOTHING;

    UPDATE public.groups g
    SET current_members = COALESCE(m.member_count, 0)
    FROM (
        SELECT group_id, COUNT(*)::int AS member_count
        FROM public.members
        GROUP BY group_id
    ) m
    WHERE g.id = m.group_id;
END $$;
