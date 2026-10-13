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
    -- Seed policy
    INSERT INTO public.policies (id, group_id, name, description, required_amount, status, created_at, updated_at)
    VALUES (gen_random_uuid(), v_group_id_burial, 'SEED01_POLICY_BURIAL', 'Seeded policy for burial group', 10000.00, 'active', now() - interval '60 days', now() - interval '30 days')
    ON CONFLICT (name) DO NOTHING;
    -- Seed poll
    DO $$
    DECLARE v_poll_id UUID := gen_random_uuid();
    BEGIN
    INSERT INTO public.group_polls (id, group_id, created_by_member_id, title, description, status, starts_at, ends_at, created_at, updated_at)
    VALUES (v_poll_id, v_group_id_burial, NULL, 'SEED01_POLL_BURIAL', 'Seeded poll for burial group', 'open', now() - interval '10 days', now() + interval '10 days', now() - interval '10 days', now())
    ON CONFLICT (id) DO NOTHING;
    INSERT INTO public.group_poll_options (id, poll_id, label, position, created_at)
    VALUES (gen_random_uuid(), v_poll_id, 'Option A', 1, now()), (gen_random_uuid(), v_poll_id, 'Option B', 2, now())
    ON CONFLICT (id) DO NOTHING;
    END $$;

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

        -- Add beneficiary
        INSERT INTO public.beneficiaries (id, group_id, member_id, full_name, id_number, relationship, date_of_birth, is_over_65, document_status, created_at, updated_at)
        VALUES (gen_random_uuid(), v_group_id_burial, v_user_id, format('SEED01_BENEFICIARY_BURIAL_%s', i), lpad((9900000000000 + i)::text,13,'0'), 'child', '2010-01-01', FALSE, 'verified', now() - interval '10 days', now())
        ON CONFLICT (id) DO NOTHING;

        -- Add member document
        INSERT INTO public.member_documents (id, member_id, group_id, label, document_url, document_type, status, created_at, updated_at)
        VALUES (gen_random_uuid(), v_user_id, v_group_id_burial, format('SEED01_DOC_BURIAL_%s', i), 'https://example.com/doc.pdf', 'id_card', 'verified', now() - interval '10 days', now())
        ON CONFLICT (id) DO NOTHING;

        -- Add notification
        INSERT INTO public.notifications (id, group_id, member_id, message, channel, trigger_event, created_at)
        VALUES (gen_random_uuid(), v_group_id_burial, v_user_id, format('SEED01 notification for burial member %s', i), 'both', 'seed', now() - interval '5 days')
        ON CONFLICT (id) DO NOTHING;
    END LOOP;

    -- --- 2. NEW STOKVEL (PENDING ACTIVATION) ---
    INSERT INTO public.groups (id, name, type, province, city, joining_fee, monthly_contribution, balance, admin_user_id, fee_status, registration_paid)
    VALUES (v_group_id_stokvel, 'Future Wealth Stokvel', 'stokvel', 'Western Cape', 'Cape Town', 0.00, 500.00, 0.00, v_admin_id, 'pending_activation', FALSE)
    ON CONFLICT (id) DO NOTHING;
    -- Seed policy
    INSERT INTO public.policies (id, group_id, name, description, required_amount, status, created_at, updated_at)
    VALUES (gen_random_uuid(), v_group_id_stokvel, 'SEED01_POLICY_STOKVEL', 'Seeded policy for stokvel group', 20000.00, 'active', now() - interval '60 days', now() - interval '30 days')
    ON CONFLICT (name) DO NOTHING;

    -- --- 3. ROSCA GROUP (ACTIVE ROTATION) ---
    INSERT INTO public.groups (id, name, type, province, city, monthly_contribution, balance, admin_user_id, registration_paid, rosca_rotation_method)
    VALUES (v_group_id_rosca, 'Quick Cash ROSCA', 'rosca', 'KwaZulu-Natal', 'Durban', 1000.00, 5000.00, v_admin_id, TRUE, 'fixed')
    ON CONFLICT (id) DO NOTHING;
    -- Seed policy
    INSERT INTO public.policies (id, group_id, name, description, required_amount, status, created_at, updated_at)
    VALUES (gen_random_uuid(), v_group_id_rosca, 'SEED01_POLICY_ROSCA', 'Seeded policy for rosca group', 15000.00, 'active', now() - interval '60 days', now() - interval '30 days')
    ON CONFLICT (name) DO NOTHING;

    -- --- 4. LOAN SCENARIO (OVERDUE) ---
    -- (Reuse burial group for a member with a loan)
    SELECT id INTO v_member_id FROM public.members WHERE group_id = v_group_id_burial LIMIT 1;
    INSERT INTO public.loans (id, member_id, group_id, amount, interest_rate, total_to_repay, monthly_repayment, status, next_payment_date, created_at, updated_at, purpose)
    VALUES (gen_random_uuid(), v_member_id, v_group_id_burial, 5000.00, 10.00, 5500.00, 500.00, 'overdue', current_date - 5, now() - interval '40 days', now(), 'SEED01_LOAN_BURIAL')
    ON CONFLICT DO NOTHING;
    -- Add repayment
    INSERT INTO public.loan_repayments (id, loan_id, member_id, group_id, amount, paid_at, payment_method, transaction_id, created_at)
    VALUES (gen_random_uuid(), (SELECT id FROM public.loans WHERE member_id = v_member_id AND group_id = v_group_id_burial LIMIT 1), v_member_id, v_group_id_burial, 500.00, now() - interval '10 days', 'bank', 'seed01_loanrepay_burial', now() - interval '10 days')
    ON CONFLICT (id) DO NOTHING;
    -- Add platform fees for each group
    INSERT INTO public.platform_fees (id, group_id, fee_type, amount, status, due_date, paid_at, transaction_id, created_at, updated_at)
    VALUES (gen_random_uuid(), v_group_id_burial, 'registration', 700.00, 'paid', to_char(now() - interval '100 days', 'YYYY-MM-DD'), now() - interval '99 days', 'SEED01_FEE_BURIAL', now() - interval '100 days', now() - interval '99 days')
    ON CONFLICT (id) DO NOTHING;
    INSERT INTO public.platform_fees (id, group_id, fee_type, amount, status, due_date, paid_at, transaction_id, created_at, updated_at)
    VALUES (gen_random_uuid(), v_group_id_stokvel, 'registration', 700.00, 'due', to_char(now() - interval '100 days', 'YYYY-MM-DD'), NULL, 'SEED01_FEE_STOKVEL', now() - interval '100 days', now() - interval '99 days')
    ON CONFLICT (id) DO NOTHING;
    INSERT INTO public.platform_fees (id, group_id, fee_type, amount, status, due_date, paid_at, transaction_id, created_at, updated_at)
    VALUES (gen_random_uuid(), v_group_id_rosca, 'registration', 700.00, 'paid', to_char(now() - interval '100 days', 'YYYY-MM-DD'), now() - interval '99 days', 'SEED01_FEE_ROSCA', now() - interval '100 days', now() - interval '99 days')
    ON CONFLICT (id) DO NOTHING;

    RAISE NOTICE 'Core test groups seeded.';
END $$;
