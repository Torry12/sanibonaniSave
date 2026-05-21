-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — DASHBOARD-SAFE SEED DATA
-- Version: 1.0 (May 2026)
--
-- INSTRUCTIONS:
-- 1. Run 36_DASHBOARD_FULL_REBUILD.sql FIRST.
-- 2. Copy ALL text in this file.
-- 3. Paste into Supabase Dashboard -> SQL Editor -> New Query.
-- 4. Click RUN.
--
-- This script seeds broad, realistic data for E2E testing:
-- - 8 groups of different types (Burial, Stokvel, etc.)
-- - 8 members per group with various statuses and documents.
-- - Mock contributions, payments, and loan history.
-- - Burial claims and escalated platform admin tasks.
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_loan_id UUID;
    v_group_index INT;
    v_member_index INT;
    v_group_type TEXT;
    v_group_name TEXT;
    v_group_city TEXT;
    v_group_province TEXT;
    v_member_status TEXT;
    v_fee_status TEXT;
    v_member_ids UUID[];

    -- Constraints compatibility mapping
    v_status_group_approved TEXT := 'group_approved';
    v_status_processing TEXT := 'processing';
    v_status_failed TEXT := 'failed';
    v_platform_fee_overdue TEXT := 'overdue';

    v_group_types TEXT[] := ARRAY['burial_society','stokvel','rosca','investment_club','emergency_fund','community_savings','tontine','other'];
    v_provinces TEXT[] := ARRAY['Gauteng','Western Cape','KwaZulu-Natal','Eastern Cape','Free State','Limpopo','Mpumalanga','North West'];
    v_cities TEXT[] := ARRAY['Johannesburg','Cape Town','Durban','Gqeberha','Bloemfontein','Polokwane','Mbombela','Mahikeng'];
BEGIN
    -- 1. Identify Platform Admin
    SELECT id INTO v_admin_id FROM auth.users WHERE lower(email) = lower('torrymsimango@gmail.com') LIMIT 1;
    IF v_admin_id IS NULL THEN RAISE EXCEPTION 'Platform admin user not found. Run Rebuild script first.'; END IF;

    -- 2. Setup Platform Settings
    INSERT INTO public.platform_settings (key, value) VALUES ('monthly_per_member', 12.0) ON CONFLICT (key) DO UPDATE SET value = 12.0;
    INSERT INTO public.platform_settings (key, value) VALUES ('registration_fee', 700.0) ON CONFLICT (key) DO UPDATE SET value = 700.0;

    -- 3. Seed Groups Loop
    FOR v_group_index IN 1..array_length(v_group_types, 1) LOOP
        v_group_type := v_group_types[v_group_index];
        v_group_province := v_provinces[v_group_index];
        v_group_city := v_cities[v_group_index];
        v_group_name := format('E2E-SEED-%s %s', v_group_index, replace(initcap(replace(v_group_type, '_', ' ')), '  ', ' '));
        v_fee_status := CASE (v_group_index % 5) WHEN 1 THEN 'paid' WHEN 2 THEN 'due' WHEN 3 THEN 'warning' WHEN 4 THEN 'suspended' ELSE 'pending_activation' END;

        INSERT INTO public.groups (id, name, type, province, city, township, description, logo_emoji, joining_fee, monthly_contribution, late_fee, payment_due_day, max_members, is_public, balance, admin_user_id, fee_status, registration_paid, is_platform_suspended, max_beneficiaries, beneficiary_increase_pct, loan_interest_rate, loan_max_amount, loan_max_months)
        VALUES (gen_random_uuid(), v_group_name, v_group_type, v_group_province, v_group_city, 'Seed Township ' || v_group_index, 'E2E seeded group.', '🤝', 100 + (v_group_index * 10), 150 + (v_group_index * 50), 25, 25, 80, TRUE, 20000 + (v_group_index * 2500), v_admin_id, v_fee_status, (v_group_index % 2 = 0), (v_group_index = 8), CASE WHEN v_group_type = 'burial_society' THEN 8 ELSE 0 END, CASE WHEN v_group_type = 'burial_society' THEN 20 ELSE 0 END, 10, 5000, 12)
        RETURNING id INTO v_group_id;

        v_member_ids := ARRAY[]::UUID[];

        -- Seed 8 members per group
        FOR v_member_index IN 1..8 LOOP
            v_member_status := CASE (v_member_index % 4) WHEN 1 THEN 'active' WHEN 2 THEN 'probation' WHEN 3 THEN 'pending_payment' ELSE 'suspended' END;

            INSERT INTO public.members (group_id, user_id, full_name, id_number, phone, email, status, joined_at, probation_end_at, total_contributions, total_paid, member_key)
            VALUES (v_group_id, CASE WHEN v_member_index = 1 THEN v_admin_id ELSE NULL END, format('E2E Member %s-%s', v_group_index, v_member_index), lpad((9001010000000 + (v_group_index * 1000) + v_member_index)::TEXT, 13, '0'), '07' || lpad((v_group_index * 100 + v_member_index)::TEXT, 8, '0'), format('e2e.member.%s.%s@example.com', v_group_index, v_member_index), v_member_status, NOW() - make_interval(months => v_member_index), CASE WHEN v_member_status = 'probation' THEN NOW() + INTERVAL '30 days' ELSE NOW() - INTERVAL '10 days' END, 3 + v_member_index, 1200 + (v_member_index * 250), format('MK-E2E-%s-%s', v_group_index, v_member_index))
            RETURNING id INTO v_member_id;
            v_member_ids := array_append(v_member_ids, v_member_id);

            -- Contribution
            INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, transaction_id, status)
            VALUES (v_member_id, v_group_id, 200, 'contribution', CURRENT_DATE - INTERVAL '35 days', NOW() - INTERVAL '34 days', 'bank', format('tx-e2e-%s-%s', v_group_index, v_member_index), 'paid');
        END LOOP;

        -- Burial claims for burial_society
        IF v_group_type = 'burial_society' THEN
            INSERT INTO public.beneficiary_payout_claims (group_id, member_id, beneficiary_id, beneficiary_name, cause_of_death, date_of_death, claim_amount, bank_name, account_no, branch_code, account_holder, status)
            VALUES (v_group_id, v_member_ids[1], gen_random_uuid(), 'E2E Claim Beneficiary', 'Natural causes', CURRENT_DATE - INTERVAL '14 days', 25000, 'FNB', '62000000111', '250655', 'E2E Claimant', 'escalated');
        END IF;

        -- Loan Request
        INSERT INTO public.loans (member_id, group_id, amount, interest_rate, total_to_repay, total_repaid, monthly_repayment, start_date, status, purpose)
        VALUES (v_member_ids[2], v_group_id, 3000, 10, 3300, 0, 550, CURRENT_DATE, 'pending', 'Business inventory');

        -- Payout Request
        INSERT INTO public.payouts (group_id, amount, bank_name, account_no, branch_code, status, processed_by, processed_at)
        VALUES (v_group_id, 5000, 'FNB', '62000090011', '250655', 'group_approved', v_admin_id, NOW() - INTERVAL '2 days');
    END LOOP;

    -- Platform Ledger income
    INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category)
    VALUES (gen_random_uuid(), 700, 700, 'E2E seed registration fee income', 'registration');

    RAISE NOTICE 'E2E seed completed successfully.';
END $$;
