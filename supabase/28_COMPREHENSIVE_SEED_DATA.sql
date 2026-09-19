-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — COMPREHENSIVE SEED DATA
-- Version: 8.0 (Ledger-First, Multi-Persona, High Fidelity)
-- ─────────────────────────────────────────────────────────────────────────────
-- This script generates a high-quality test dataset following the immutable ledger
-- principle. Every financial movement is recorded via atomic RPCs or ledger inserts.

DO $$
DECLARE
    -- Personas
    v_platform_admin_id UUID;
    v_group_admin_id UUID;
    v_member_id UUID;
    v_group_id UUID;
    v_loan_id UUID;

    -- Counters
    v_group_idx INT;
    v_member_idx INT;
    v_month_idx INT;
    v_history_months INT := 6;

    -- Constants / Config Arrays
    v_group_types TEXT[] := ARRAY['burial_society', 'stokvel', 'rosca', 'investment_club', 'emergency_fund'];
    v_provinces TEXT[] := ARRAY['Gauteng', 'Western Cape', 'KwaZulu-Natal', 'Eastern Cape', 'Free State', 'Limpopo', 'Mpumalanga', 'North West', 'Northern Cape'];
    v_cities TEXT[] := ARRAY['Soweto', 'Khayelitsha', 'Umlazi', 'Mamelodi', 'Alexandra', 'Katlehong', 'Soshanguve', 'Sebokeng', 'Mthatha', 'Tembisa'];

    -- Identity Data
    v_first_names TEXT[] := ARRAY['Thabo', 'Sipho', 'Lerato', 'Naledi', 'Johan', 'Annelie', 'Fatima', 'Yusuf', 'Siyabonga', 'Nomvula', 'Kagiso', 'Boipelo', 'Zanele', 'Mandla', 'Lindiwe', 'Bongani', 'Tshepo', 'Refilwe', 'Kopano', 'Palesa'];
    v_surnames TEXT[] := ARRAY['Mokoena', 'Dlamini', 'Nkosi', 'Van der Merwe', 'Botha', 'Patel', 'Naidoo', 'Ndlovu', 'Khumalo', 'Sithole', 'Mahlangu', 'Modise', 'Zuma', 'Molefe', 'Gumede', 'Nyathi', 'Fourie', 'Radebe', 'Mthembu', 'Ngcobo'];

    -- Variables
    v_group_type TEXT;
    v_group_name TEXT;
    v_province TEXT;
    v_city TEXT;
    v_monthly_contrib NUMERIC(12,2);
    v_joining_fee NUMERIC(12,2);
    v_full_name TEXT;
    v_email TEXT;
    v_join_date TIMESTAMPTZ;
    v_now TIMESTAMPTZ := NOW();
    v_reg_fee NUMERIC;
    v_monthly_fee NUMERIC;
    v_rosca_cycle INT;
    v_temp_json JSONB;
    v_new_balance NUMERIC;

BEGIN
    -- 1. IDENTIFY KEY USERS
    SELECT id INTO v_platform_admin_id FROM auth.users WHERE email = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_platform_admin_id IS NULL THEN
        RAISE NOTICE 'Admin torrymsimango@gmail.com not found. Using first user.';
        SELECT id INTO v_platform_admin_id FROM auth.users LIMIT 1;
    END IF;

    -- Create a dedicated Group Admin persona if not exists
    SELECT id INTO v_group_admin_id FROM auth.users WHERE email = 'groupadmin@seed.com' LIMIT 1;
    IF v_group_admin_id IS NULL THEN
        v_group_admin_id := gen_random_uuid();
        INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, raw_user_meta_data, created_at, updated_at)
        VALUES (v_group_admin_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'groupadmin@seed.com', extensions.crypt('SeedAdmin123!', extensions.gen_salt('bf')), now(), '{"full_name": "Seed Group Admin", "role": "group_admin"}', now(), now());

        INSERT INTO public.profiles (id, full_name, email, role)
        VALUES (v_group_admin_id, 'Seed Group Admin', 'groupadmin@seed.com', 'group_admin');
    END IF;

    -- 2. CLEANUP EXISTING SEED DATA
    RAISE NOTICE 'Cleaning up existing seed data...';
    DELETE FROM public.audit_logs WHERE details->>'seed' = 'true';
    DELETE FROM public.member_behavior_track WHERE id::text LIKE 'seed-%';
    DELETE FROM public.group_health_scores WHERE id::text LIKE 'seed-%';
    DELETE FROM public.payouts WHERE payout_reference LIKE 'SEED-%';
    DELETE FROM public.loan_repayments WHERE transaction_id LIKE 'SEED-%';
    DELETE FROM public.loans WHERE purpose LIKE 'SEED-%';
    DELETE FROM public.contributions WHERE transaction_id LIKE 'SEED-%';
    DELETE FROM public.payments WHERE transaction_id LIKE 'SEED-%';
    DELETE FROM public.group_ledger WHERE transaction_id LIKE 'SEED-%' OR description LIKE 'SEED-%';
    DELETE FROM public.platform_ledger WHERE transaction_id LIKE 'SEED-%' OR description LIKE 'SEED-%';
    DELETE FROM public.members WHERE member_key LIKE 'SEED-%';
    DELETE FROM public.groups WHERE description LIKE 'SEED-%';

    -- 3. INITIALIZE PLATFORM FEE CONFIG
    RAISE NOTICE 'Initializing Platform Fee Configuration...';
    INSERT INTO public.platform_fee_config (group_type, rosca_cycle, registration_fee, monthly_fee)
    VALUES
        ('burial_society', 0, 750.00, 15.00),
        ('stokvel', 0, 500.00, 10.00),
        ('rosca', 6, 400.00, 8.00),
        ('rosca', 12, 600.00, 12.00),
        ('investment_club', 0, 1500.00, 25.00),
        ('emergency_fund', 0, 300.00, 5.00)
    ON CONFLICT (group_type, rosca_cycle) DO UPDATE
    SET registration_fee = EXCLUDED.registration_fee, monthly_fee = EXCLUDED.monthly_fee;

    -- 4. CREATE STORAGE BUCKETS
    INSERT INTO storage.buckets (id, name, public)
    VALUES ('avatars', 'avatars', true), ('constitutions', 'constitutions', true), ('documents', 'documents', false)
    ON CONFLICT (id) DO NOTHING;

    -- 5. MAIN GROUP GENERATION LOOP (25 Groups)
    FOR v_group_idx IN 1..25 LOOP
        v_group_type := v_group_types[((v_group_idx - 1) % 5) + 1];
        v_rosca_cycle := CASE WHEN v_group_type = 'rosca' THEN (ARRAY[6, 12])[floor(random() * 2 + 1)::int] ELSE 0 END;
        v_group_name := 'SEED-' || initcap(replace(v_group_type, '_', ' ')) || ' ' || v_group_idx;
        v_province := v_provinces[floor(random() * 9 + 1)::int];
        v_city := v_cities[((v_group_idx - 1) % 10) + 1];

        SELECT registration_fee, monthly_fee INTO v_reg_fee, v_monthly_fee
        FROM public.platform_fee_config WHERE group_type = v_group_type AND rosca_cycle = v_rosca_cycle;

        v_monthly_contrib := CASE
            WHEN v_group_type = 'burial_society' THEN 200.00
            WHEN v_group_type = 'stokvel' THEN 500.00
            WHEN v_group_type = 'rosca' THEN 1000.00
            WHEN v_group_type = 'investment_club' THEN 2500.00
            ELSE 350.00
        END;
        v_joining_fee := 250.00;

        INSERT INTO public.groups (
            name, type, province, city, township, description, joining_fee, monthly_contribution,
            balance, admin_user_id, fee_status, registration_paid, created_at, updated_at,
            max_beneficiaries, probation_months, period_months, max_members
        ) VALUES (
            v_group_name, v_group_type, v_province, v_city, 'Township ' || v_group_idx,
            'SEED-Comprehensive ' || replace(v_group_type, '_', ' ') || ' for robust architectural testing.',
            v_joining_fee, v_monthly_contrib, 0.00, v_group_admin_id, 'paid', TRUE, v_now - interval '200 days', v_now,
            CASE WHEN v_group_type = 'burial_society' THEN 5 ELSE 0 END,
            3,
            CASE WHEN v_group_type = 'rosca' THEN v_rosca_cycle ELSE 12 END,
            CASE WHEN v_group_type = 'rosca' THEN v_rosca_cycle ELSE 50 END
        ) RETURNING id INTO v_group_id;

        -- Group Level: Pay Platform Registration Fee
        PERFORM public.record_platform_revenue_v1(v_reg_fee, 'SEED-Registration Fee: ' || v_group_name, 'registration_fee', 'SEED-REG-' || v_group_id);

        -- 6. MEMBER GENERATION LOOP (15 Members per Group)
        FOR v_member_idx IN 1..15 LOOP
            v_first_name := v_first_names[floor(random() * 20 + 1)::int];
            v_surname := v_surnames[floor(random() * 20 + 1)::int];
            v_full_name := v_first_name || ' ' || v_surname;
            v_email := lower(v_first_name) || '.' || lower(v_surname) || '.' || (v_group_idx * 100 + v_member_idx)::text || '@seed.com';
            v_join_date := v_now - (180 - (v_member_idx * 2) || ' days')::interval;

            INSERT INTO public.members (
                group_id, full_name, email, phone, status, joined_at, member_key, id_number, probation_end_at
            ) VALUES (
                v_group_id, v_full_name, v_email, '07' || lpad((v_group_idx * 100 + v_member_idx)::text, 8, '0'),
                CASE WHEN v_member_idx = 15 THEN 'probation' ELSE 'active' END,
                v_join_date, format('SEED-%s-%s', v_group_id, v_member_idx),
                '800101' || floor(1000 + random() * 8999)::text || '08' || (v_member_idx % 9),
                v_join_date + interval '3 months'
            ) RETURNING id INTO v_member_id;

            -- Member Level: Pay Joining Fee (updates ledger)
            PERFORM public.record_contribution_v1(v_member_id, v_group_id, v_joining_fee, v_join_date::date, v_join_date, 'paid', 'SEED-JOIN-' || v_member_id, 'joining_fee');

            -- 7. SIMULATE FINANCIAL HISTORY (Up to 6 Months)
            FOR v_month_idx IN 1..v_history_months LOOP
                v_join_date := v_now - (v_month_idx * 30 || ' days')::interval;
                IF v_join_date < (v_now - interval '180 days') THEN CONTINUE; END IF;

                -- a) Monthly Group Contribution
                PERFORM public.record_contribution_v1(
                    v_member_id, v_group_id, v_monthly_contrib, v_join_date::date, v_join_date + interval '1 day',
                    'paid', 'SEED-TX-' || v_member_id || '-' || v_month_idx, 'contribution'
                );

                -- b) Platform Member Fee (Monthly)
                PERFORM public.record_contribution_v1(
                    v_member_id, v_group_id, v_monthly_fee, v_join_date::date, v_join_date + interval '1 day',
                    'paid', 'SEED-FEE-' || v_member_id || '-' || v_month_idx, 'member_fee'
                );

                -- c) Occasional Late Fee
                IF random() > 0.9 THEN
                    PERFORM public.record_contribution_v1(
                        v_member_id, v_group_id, 50.00, v_join_date::date, v_join_date + interval '10 days',
                        'paid', 'SEED-LATE-' || v_member_id || '-' || v_month_idx, 'late_fee'
                    );
                END IF;
            END LOOP;

            -- 8. LOANS & REPAYMENTS (Every 5th member in specific groups)
            IF v_member_idx % 5 = 0 AND v_group_type IN ('stokvel', 'emergency_fund') THEN
                INSERT INTO public.loans (
                    member_id, group_id, amount, interest_rate, total_to_repay, total_repaid, monthly_repayment,
                    start_date, status, purpose, created_at
                ) VALUES (
                    v_member_id, v_group_id, 2000.00, 10.0, 2200.00, 0.00, 550.00,
                    (v_now - interval '60 days')::date, 'active', 'SEED-Tuition Fees', v_now - interval '60 days'
                ) RETURNING id INTO v_loan_id;

                -- Record Disbursement In Ledger
                PERFORM public.increment_group_balance(v_group_id, -2000.00, 'SEED-Loan Disbursement: ' || v_full_name, 'loan_disbursement', 'SEED-LOAN-OUT-' || v_loan_id, v_member_id);

                -- Record 2 repayments
                PERFORM public.record_loan_repayment_v1(v_loan_id, v_member_id, v_group_id, 550.00, 'bank');
                PERFORM public.record_loan_repayment_v1(v_loan_id, v_member_id, v_group_id, 550.00, 'bank');
            END IF;

            -- 9. BEHAVIOR SCORES
            INSERT INTO public.member_behavior_track (
                member_id, group_id, behavior_score, fraud_score, fraud_risk_level, member_status,
                total_contributions, on_time_contributions, total_amount_contributed, months_in_group
            ) VALUES (
                v_member_id, v_group_id, 80 + random() * 20, random() * 5, 'LOW', 'EXCELLENT',
                v_history_months, v_history_months - 1, (v_monthly_contrib * v_history_months) + v_joining_fee, v_history_months
            );
        END LOOP; -- Members

        -- 10. CLAIMS & PAYOUTS (Simulate for representative groups)
        IF v_group_type = 'burial_society' AND v_group_idx % 5 = 1 THEN
            INSERT INTO public.beneficiary_payout_claims (
                group_id, member_id, beneficiary_id, beneficiary_name, claim_amount, status,
                cause_of_death, date_of_death, bank_name, account_no, branch_code, account_holder, notes, created_at
            ) VALUES (
                v_group_id, (SELECT id FROM public.members WHERE group_id = v_group_id LIMIT 1), gen_random_uuid(), 'Legacy Beneficiary', 15000.00, 'paid',
                'Natural Causes', (v_now - interval '30 days')::date, 'Standard Bank', '123456789', '051001', 'Beneficiary Holder', 'SEED-High fidelity claim', v_now - interval '32 days'
            );
            -- Reflect payout in ledger
            PERFORM public.increment_group_balance(v_group_id, -15000.00, 'SEED-Burial Payout: Legacy Beneficiary', 'payout', 'SEED-CLAIM-OUT-' || v_group_idx, NULL);
        END IF;

        -- 11. FINAL GROUP REPAIR
        PERFORM public.repair_group_data_v1(v_group_id);

    END LOOP; -- Groups

    RAISE NOTICE 'Comprehensive Seed Data Generation Complete.';
END $$;
