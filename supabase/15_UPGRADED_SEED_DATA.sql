-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — UPGRADED SEED DATA (High Fidelity Financial History)
-- Version: 5.0 (Ledger Centric, Multi-Persona, June 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    -- PERSONAS
    v_platform_admin_email TEXT := 'torrymsimango@gmail.com';
    v_admin_id UUID;

    -- MOCK DATA ARRAYS
    v_group_types TEXT[] := ARRAY['burial_society', 'stokvel', 'rosca', 'investment_club', 'emergency_fund'];
    v_group_names TEXT[] := ARRAY['Phambili', 'Zethembe', 'Ikhaya', 'Sakhisizwe', 'Unity', 'Ubuntu', 'Siyakhula', 'Vuka', 'Luthando', 'Zonke'];
    v_cities TEXT[] := ARRAY['Soweto', 'Tembisa', 'Khayelitsha', 'Umlazi', 'Mamelodi', 'Alexandra', 'Katlehong', 'Soshanguve', 'Sebokeng', 'Mthatha'];
    v_first_names TEXT[] := ARRAY['Thabo', 'Sipho', 'Lerato', 'Naledi', 'Johan', 'Annelie', 'Fatima', 'Yusuf', 'Siyabonga', 'Nomvula', 'Kagiso', 'Boipelo', 'Zanele', 'Mandla', 'Lindiwe', 'Bongani', 'Tshepo', 'Refilwe', 'Kopano', 'Palesa'];
    v_surnames TEXT[] := ARRAY['Mokoena', 'Dlamini', 'Nkosi', 'Van der Merwe', 'Botha', 'Patel', 'Naidoo', 'Ndlovu', 'Khumalo', 'Sithole', 'Mahlangu', 'Modise', 'Zuma', 'Molefe', 'Gumede', 'Nyathi', 'Fourie', 'Radebe', 'Mthembu', 'Ngcobo'];

    -- LOOP VARIABLES
    v_group_idx INT;
    v_member_idx INT;
    v_month_idx INT;

    v_group_id UUID;
    v_member_id UUID;
    v_user_id UUID; -- Mock auth user
    v_loan_id UUID;

    v_type TEXT;
    v_name TEXT;
    v_city TEXT;
    v_contrib NUMERIC(10,2);
    v_join_fee NUMERIC(10,2);
    v_full_name TEXT;
    v_email TEXT;
    v_status TEXT;

    v_now TIMESTAMPTZ := NOW();
    v_start_date TIMESTAMPTZ := NOW() - interval '6 months';
    v_curr_date TIMESTAMPTZ;

    v_group_balance NUMERIC(12,2);
    v_platform_balance NUMERIC(12,2);

BEGIN
    -- 1. IDENTIFY PLATFORM ADMIN
    SELECT id INTO v_admin_id FROM auth.users WHERE lower(email) = lower(v_platform_admin_email) LIMIT 1;
    IF v_admin_id IS NULL THEN
        RAISE NOTICE 'Platform admin % not found. Using first available user or creating placeholder.', v_platform_admin_email;
        SELECT id INTO v_admin_id FROM auth.users LIMIT 1;
    END IF;

    -- 2. CLEANUP PREVIOUS SEED DATA
    RAISE NOTICE 'Cleaning up previous seed data...';
    DELETE FROM public.audit_logs WHERE details->>'seed' = 'true';
    DELETE FROM public.fraud_detection_events WHERE details_json->>'seed' = 'true';
    DELETE FROM public.member_behavior_track WHERE id::text LIKE 'seed-%';
    DELETE FROM public.loan_repayments WHERE id::text LIKE 'seed-%';
    DELETE FROM public.loans WHERE id::text LIKE 'seed-%';
    DELETE FROM public.contributions WHERE transaction_id LIKE 'seed-%';
    DELETE FROM public.payments WHERE transaction_id LIKE 'seed-%';
    DELETE FROM public.beneficiary_payout_claims WHERE notes LIKE 'SEED-%';
    DELETE FROM public.beneficiaries WHERE full_name LIKE 'SEED-%';
    DELETE FROM public.members WHERE member_key LIKE 'SEED-%';
    DELETE FROM public.groups WHERE description LIKE 'SEED-%';

    -- 3. RESET LEDGERS (Optional: Only if starting fresh simulation)
    -- DELETE FROM public.group_ledger WHERE transaction_id LIKE 'seed-%';
    -- DELETE FROM public.platform_ledger WHERE transaction_id LIKE 'seed-%';

    -- 4. GENERATE 10 GROUPS
    FOR v_group_idx IN 1..10 LOOP
        v_type := v_group_types[((v_group_idx - 1) % 5) + 1];
        v_name := v_group_names[v_group_idx] || ' ' || initcap(replace(v_type, '_', ' '));
        v_city := v_cities[v_group_idx];

        v_contrib := CASE
            WHEN v_type = 'burial_society' THEN 150.00
            WHEN v_type = 'stokvel' THEN 500.00
            WHEN v_type = 'rosca' THEN 1000.00
            WHEN v_type = 'investment_club' THEN 2000.00
            ELSE 300.00
        END;
        v_join_fee := 200.00;

        INSERT INTO public.groups (
            name, type, city, province, description, joining_fee, monthly_contribution,
            balance, admin_user_id, fee_status, registration_paid, created_at, updated_at
        ) VALUES (
            v_name, v_type, v_city, 'Gauteng', 'SEED-High fidelity ' || v_type || ' for testing.',
            v_join_fee, v_contrib, 0.00, v_admin_id, 'paid', TRUE, v_start_date, v_start_date
        ) RETURNING id INTO v_group_id;

        -- 5. INITIAL PLATFORM REVENUE (Registration)
        PERFORM public.record_platform_revenue_v1(700.00, 'Registration Fee: ' || v_name, 'registration', 'seed-reg-' || v_group_id);

        -- 6. GENERATE 15 MEMBERS PER GROUP
        FOR v_member_idx IN 1..15 LOOP
            v_full_name := v_first_names[((v_member_idx + v_group_idx) % 20) + 1] || ' ' || v_surnames[((v_member_idx * v_group_idx) % 20) + 1];
            v_email := lower(replace(v_full_name, ' ', '.')) || '.' || v_group_idx || '.' || v_member_idx || '@seed.com';

            v_status := CASE
                WHEN v_member_idx <= 10 THEN 'active'
                WHEN v_member_idx = 11 THEN 'probation'
                WHEN v_member_idx = 12 THEN 'suspended'
                WHEN v_member_idx = 13 THEN 'pending_payment'
                ELSE 'active'
            END;

            INSERT INTO public.members (
                group_id, full_name, email, phone, status, joined_at, member_key,
                total_contributions, total_paid, id_number
            ) VALUES (
                v_group_id, v_full_name, v_email, '07' || lpad((v_group_idx * 100 + v_member_idx)::text, 8, '0'),
                v_status, v_start_date + interval '1 day', 'SEED-' || v_group_id || '-' || v_member_idx,
                0, 0.00, '900101' || lpad((v_group_idx * 100 + v_member_idx)::text, 7, '0')
            ) RETURNING id INTO v_member_id;

            -- 7. SIMULATE 6 MONTHS OF FINANCIAL ACTIVITY
            FOR v_month_idx IN 1..6 LOOP
                v_curr_date := v_start_date + (v_month_idx * interval '1 month');
                IF v_curr_date > v_now THEN EXIT; END IF;

                -- Skip activity for suspended or pending if appropriate
                IF v_status = 'suspended' AND v_month_idx > 3 THEN CONTINUE; END IF;
                IF v_status = 'pending_payment' AND v_month_idx > 1 THEN CONTINUE; END IF;

                -- A. CONTRIBUTION PAYMENT
                -- Varied outcomes: 80% on time, 10% late, 5% partial, 5% miss
                DECLARE
                    v_rand FLOAT := random();
                    v_paid_amt NUMERIC(10,2) := v_contrib;
                    v_payment_status TEXT := 'paid';
                    v_payment_date TIMESTAMPTZ := v_curr_date + (random() * interval '5 days');
                BEGIN
                    IF v_rand > 0.95 THEN -- MISS
                        INSERT INTO public.contributions (member_id, group_id, amount, due_date, status, type, transaction_id)
                        VALUES (v_member_id, v_group_id, v_contrib, v_curr_date::date, 'overdue', 'contribution', 'seed-miss-' || v_member_id || '-' || v_month_idx);
                    ELSIF v_rand > 0.90 THEN -- PARTIAL
                        v_paid_amt := v_contrib * 0.5;
                        PERFORM public.record_contribution_v1(v_member_id, v_group_id, v_paid_amt, v_curr_date::date, v_payment_date, 'partial', 'seed-tx-' || v_member_id || '-' || v_month_idx, 'contribution');
                    ELSE -- PAID (On time or Late)
                        IF v_rand < 0.1 THEN -- Late
                            v_payment_date := v_curr_date + interval '10 days';
                        END IF;
                        PERFORM public.record_contribution_v1(v_member_id, v_group_id, v_paid_amt, v_curr_date::date, v_payment_date, 'paid', 'seed-tx-' || v_member_id || '-' || v_month_idx, 'contribution');

                        -- If late, apply and pay late fee
                        IF v_rand < 0.1 THEN
                            PERFORM public.record_contribution_v1(v_member_id, v_group_id, 50.00, v_curr_date::date, v_payment_date, 'paid', 'seed-late-' || v_member_id || '-' || v_month_idx, 'late_fee');
                        END IF;
                    END IF;
                END;

                -- B. PLATFORM MONTHLY FEE (Simulate payment from group balance)
                IF v_member_idx = 1 THEN -- Once per group per month
                    PERFORM public.record_platform_revenue_v1(150.00, 'Monthly Platform Fee - ' || v_name || ' (Month ' || v_month_idx || ')', 'monthly_fee', 'seed-pfee-' || v_group_id || '-' || v_month_idx);
                END IF;
            END LOOP;

            -- 8. LOAN SCENARIOS
            IF v_member_idx = 5 AND v_type IN ('burial_society', 'stokvel', 'emergency_fund') THEN
                -- ACTIVE LOAN
                INSERT INTO public.loans (
                    member_id, group_id, amount, interest_rate, total_to_repay, total_repaid, monthly_repayment,
                    start_date, status, purpose, created_at
                ) VALUES (
                    v_member_id, v_group_id, 5000.00, 10.0, 5500.00, 2200.00, 1100.00,
                    (v_now - interval '2 months')::date, 'active', 'Education', v_now - interval '2 months'
                ) RETURNING id INTO v_loan_id;

                -- Ledger entries for loan disbursement (simulated outflow)
                PERFORM public.increment_group_balance(v_group_id, -5000.00, 'Loan Disbursement: ' || v_full_name, 'loan_disbursement', 'seed-loan-out-' || v_loan_id);

                -- Repayments
                PERFORM public.record_loan_repayment_v1(v_loan_id, v_member_id, v_group_id, 1100.00, 'bank');
                PERFORM public.record_loan_repayment_v1(v_loan_id, v_member_id, v_group_id, 1100.00, 'bank');
            END IF;

            -- 9. BEHAVIOR TRACKING SEEDING
            INSERT INTO public.member_behavior_track (
                member_id, group_id, behavior_score, member_status, total_contributions, on_time_contributions,
                total_amount_contributed, months_in_group, joined_at, last_activity_at
            ) VALUES (
                v_member_id, v_group_id,
                CASE WHEN v_status = 'active' THEN 85 + random() * 15 ELSE 50 + random() * 30 END,
                CASE WHEN v_status = 'active' THEN 'EXCELLENT' ELSE 'FAIR' END,
                6, 5, v_contrib * 5.5, 6, v_start_date, v_now
            );

        END LOOP; -- Members

        -- 10. CLAIMS SCENARIO
        IF v_type = 'burial_society' AND v_group_idx = 1 THEN
            DECLARE
                v_benef_id UUID := gen_random_uuid();
            BEGIN
                INSERT INTO public.beneficiaries (group_id, member_id, full_name, relationship, created_at)
                VALUES (v_group_id, (SELECT id FROM public.members WHERE group_id = v_group_id LIMIT 1), 'SEED-Relative', 'Sibling', v_start_date);

                INSERT INTO public.beneficiary_payout_claims (
                    group_id, member_id, beneficiary_id, beneficiary_name, claim_amount, status, cause_of_death, date_of_death, notes, created_at
                ) VALUES (
                    v_group_id, (SELECT id FROM public.members WHERE group_id = v_group_id LIMIT 1), v_benef_id, 'SEED-Relative', 10000.00, 'paid', 'Natural', v_now - interval '10 days', 'SEED-High fidelity claim', v_now - interval '12 days'
                );

                -- Deduct from group balance
                PERFORM public.increment_group_balance(v_group_id, -10000.00, 'Claim Payout: SEED-Relative', 'claim', 'seed-claim-' || v_group_id);
            END;
        END IF;

        -- 11. REPAIR COUNTERS
        PERFORM public.repair_group_data_v1(v_group_id);

    END LOOP; -- Groups

    -- 12. AUDIT SUMMARY
    INSERT INTO public.audit_logs (actor_id, action, details)
    VALUES (v_admin_id, 'UPGRADED_SEED_GENERATED', jsonb_build_object('seed', true, 'groups', 10, 'members', 150, 'fidelity', 'high'));

    RAISE NOTICE 'Upgraded Seed Data Generated Successfully.';
END $$;
