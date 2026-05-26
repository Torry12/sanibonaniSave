-- SEED_01_CORE_TEST_GROUPS.sql
-- Systematic seed for core testing objectives:
-- 1. Active Burial Society with full membership and payouts.
-- 2. New Stokvel pending registration payment.
-- 3. ROSCA group with active rotation.
-- 4. Group with overdue loans.

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id_burial UUID := '11111111-1111-1111-1111-111111111111';
    v_group_id_stokvel UUID := '22222222-2222-2222-2222-222222222222';
    v_group_id_rosca UUID := '33333333-3333-3333-3333-333333333333';
    v_member_id UUID;
    v_user_id UUID;
BEGIN
    SELECT id INTO v_admin_id FROM public.profiles WHERE role = 'platform_admin' LIMIT 1;

    -- --- 1. BURIAL SOCIETY (ACTIVE & HEALTHY) ---
    INSERT INTO public.groups (id, name, type, province, city, township, joining_fee, monthly_contribution, balance, admin_user_id, fee_status, registration_paid)
    VALUES (v_group_id_burial, 'Unity Burial Society', 'burial_society', 'Gauteng', 'Soweto', 'Orlando', 150.00, 200.00, 25000.00, v_admin_id, 'paid', TRUE)
    ON CONFLICT (id) DO NOTHING;

    -- Add members to Burial Society
    FOR i IN 1..5 LOOP
        v_user_id := gen_random_uuid();
        -- Create auth user (simplified for speed in test)
        INSERT INTO auth.users (id, email, encrypted_password, role, aud)
        VALUES (v_user_id, format('burial.member%s@test.com', i), crypt('Test@123', gen_salt('bf')), 'authenticated', 'authenticated');

        INSERT INTO public.profiles (id, full_name, email, role)
        VALUES (v_user_id, format('Burial Member %s', i), format('burial.member%s@test.com', i), 'member');

        INSERT INTO public.members (id, group_id, user_id, full_name, status, joined_at)
        VALUES (gen_random_uuid(), v_group_id_burial, v_user_id, format('Burial Member %s', i), 'active', now() - interval '6 months');
    END LOOP;

    -- --- 2. NEW STOKVEL (PENDING ACTIVATION) ---
    INSERT INTO public.groups (id, name, type, province, city, joining_fee, monthly_contribution, balance, admin_user_id, fee_status, registration_paid)
    VALUES (v_group_id_stokvel, 'Future Wealth Stokvel', 'stokvel', 'Western Cape', 'Cape Town', 0.00, 500.00, 0.00, v_admin_id, 'pending_activation', FALSE)
    ON CONFLICT (id) DO NOTHING;

    -- --- 3. ROSCA GROUP (ACTIVE ROTATION) ---
    INSERT INTO public.groups (id, name, type, province, city, monthly_contribution, balance, admin_user_id, registration_paid, rosca_rotation_method)
    VALUES (v_group_id_rosca, 'Quick Cash ROSCA', 'rosca', 'KwaZulu-Natal', 'Durban', 1000.00, 5000.00, v_admin_id, TRUE, 'fixed')
    ON CONFLICT (id) DO NOTHING;

    -- --- 4. LOAN SCENARIO (OVERDUE) ---
    -- (Reuse burial group for a member with a loan)
    SELECT id INTO v_member_id FROM public.members WHERE group_id = v_group_id_burial LIMIT 1;
    INSERT INTO public.loans (member_id, group_id, amount, interest_rate, total_to_repay, monthly_repayment, status, next_payment_date)
    VALUES (v_member_id, v_group_id_burial, 5000.00, 10.00, 5500.00, 500.00, 'overdue', current_date - 5)
    ON CONFLICT DO NOTHING;

    RAISE NOTICE 'Core test groups seeded.';
END $$;
