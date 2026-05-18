-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — COMPREHENSIVE DASHBOARD SEED
-- Version: 1.0 (May 2026)
--
-- PREREQUISITE: Run 36_DASHBOARD_FULL_REBUILD.sql (Version 9.0) first.
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_group_index INT;
    v_member_index INT;
    v_group_type TEXT;
    v_group_name TEXT;
    v_group_city TEXT;
    v_group_province TEXT;
    v_member_ids UUID[];

    v_group_types TEXT[] := ARRAY['burial_society','stokvel','rosca','investment_club','emergency_fund','community_savings','tontine','other'];
    v_provinces TEXT[] := ARRAY['Gauteng','Western Cape','KwaZulu-Natal','Eastern Cape','Free State','Limpopo','Mpumalanga','North West'];
    v_cities TEXT[] := ARRAY['Johannesburg','Cape Town','Durban','Gqeberha','Bloemfontein','Polokwane','Mbombela','Mahikeng'];
BEGIN
    -- 1. GET ADMIN ID
    SELECT id INTO v_admin_id FROM auth.users WHERE lower(email) = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_admin_id IS NULL THEN RAISE EXCEPTION 'Platform admin account not found. Run Rebuild script first.'; END IF;

    -- 2. CLEANUP PREVIOUS SEED DATA (Idempotency)
    DELETE FROM public.group_ledger WHERE description LIKE 'SEED%';
    DELETE FROM public.contributions WHERE transaction_id LIKE 'tx-seed%';
    DELETE FROM public.members WHERE member_key LIKE 'MK-SEED%';
    DELETE FROM public.groups WHERE name LIKE 'SEED-%';

    -- 3. LOOP TO CREATE 8 GROUPS
    FOR v_group_index IN 1..8 LOOP
        v_group_type := v_group_types[v_group_index];
        v_group_province := v_provinces[v_group_index];
        v_group_city := v_cities[v_group_index];
        v_group_name := format('SEED-%s %s', v_group_index, replace(initcap(replace(v_group_type, '_', ' ')), '  ', ' '));

        INSERT INTO public.groups (
            name, type, province, city, township, description, joining_fee, monthly_contribution,
            balance, admin_user_id, fee_status, registration_paid, is_public,
            loan_interest_rate, loan_max_amount, period_months, rosca_rotation_method
        ) VALUES (
            v_group_name, v_group_type, v_group_province, v_group_city, 'Seed Township',
            'Comprehensively seeded group for E2E testing.',
            150.00, 250.00, 5000.00, v_admin_id, 'paid', true, true,
            12.00, 5000.00, 12, 'fixed'
        ) RETURNING id INTO v_group_id;

        -- Initial Ledger Entry
        INSERT INTO public.group_ledger (group_id, amount, balance_after, description, category)
        VALUES (v_group_id, 5000.00, 5000.00, 'SEED: Initial Group Capital', 'system');

        v_member_ids := ARRAY[]::UUID[];

        -- 4. LOOP TO CREATE 8 MEMBERS PER GROUP
        FOR v_member_index IN 1..8 LOOP
            INSERT INTO public.members (
                group_id, user_id, full_name, email, phone, status, member_key,
                joined_at, total_contributions, total_paid
            ) VALUES (
                v_group_id,
                CASE WHEN v_member_index = 1 THEN v_admin_id ELSE NULL END, -- First member is the Admin
                format('Seed Member %s-%s', v_group_index, v_member_index),
                format('seed.m%s.g%s@example.com', v_member_index, v_group_index),
                '07' || lpad((v_group_index * 10 + v_member_index)::text, 8, '0'),
                CASE WHEN v_member_index > 6 THEN 'suspended' WHEN v_member_index > 4 THEN 'probation' ELSE 'active' END,
                format('MK-SEED-%s-%s', v_group_index, v_member_index),
                NOW() - interval '6 months',
                3, 750.00
            ) RETURNING id INTO v_member_id;

            v_member_ids := array_append(v_member_ids, v_member_id);

            -- 5. SEED CONTRIBUTIONS (3 paid, 1 due per member)
            -- Paid 1
            PERFORM public.record_contribution_v1(
                v_member_id,
                v_group_id,
                250.00,
                (CURRENT_DATE - interval '60 days')::DATE,
                (NOW() - interval '58 days')::TIMESTAMPTZ,
                'paid',
                format('tx-seed-%s-%s-1', v_group_index, v_member_index)
            );
            -- Paid 2
            PERFORM public.record_contribution_v1(
                v_member_id,
                v_group_id,
                250.00,
                (CURRENT_DATE - interval '30 days')::DATE,
                (NOW() - interval '29 days')::TIMESTAMPTZ,
                'paid',
                format('tx-seed-%s-%s-2', v_group_index, v_member_index)
            );
            -- Due
            INSERT INTO public.contributions (member_id, group_id, amount, due_date, status, payment_method)
            VALUES (v_member_id, v_group_id, 250.00, CURRENT_DATE + interval '5 days', 'due', 'bank');
        END LOOP;

        -- 6. SEED GROUP-SPECIFIC TASKS

        -- Pending Payout (Disbursement Request)
        INSERT INTO public.payouts (group_id, amount, bank_name, account_no, branch_code, status, processed_by)
        VALUES (v_group_id, 1200.00, 'FNB', '62000000001', '250655', 'group_approved', v_admin_id);

        -- Pending Loan
        INSERT INTO public.loans (member_id, group_id, amount, interest_rate, total_to_repay, total_repaid, monthly_repayment, start_date, status, purpose)
        VALUES (v_member_ids[3], v_group_id, 2000.00, 10.00, 2200.00, 0, 550.00, CURRENT_DATE, 'pending', 'Business Stock');

        -- Escalated Burial Claim (if Burial Society)
        IF v_group_type = 'burial_society' THEN
            INSERT INTO public.beneficiary_payout_claims (
                group_id, member_id, beneficiary_id, beneficiary_name,
                cause_of_death, date_of_death, claim_amount, bank_name, account_no, status
            ) VALUES (
                v_group_id, v_member_ids[4], gen_random_uuid(), 'Legacy Relative',
                'Natural Causes', CURRENT_DATE - interval '10 days', 15000.00, 'Standard Bank', '10123456789', 'escalated'
            );
        END IF;

    END LOOP;

    -- 7. SYSTEM REVENUE SEED
    INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category)
    VALUES (gen_random_uuid(), 700.00, 700.00, 'SEED: Initial Registration Fee', 'registration');

END $$;
