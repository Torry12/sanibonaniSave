-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — LARGE SEED DATA (50 Groups, 1000 Members)
-- Version: 3.0 (June 4, 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_loan_id UUID;
    v_group_idx INT;
    v_member_idx INT;
    v_month_idx INT;
    v_payout_amt NUMERIC(10,2);

    -- Config Arrays (Cyclic access)
    v_group_types TEXT[] := ARRAY['burial_society', 'stokvel', 'rosca', 'investment_club', 'emergency_fund'];
    v_group_prefixes TEXT[] := ARRAY['Siyakhula', 'Unity', 'Round Robin', 'Zenith', 'SOS', 'Luthando', 'Rising Stars', 'Speedy Payout', 'Blue Chip', 'Community'];
    v_provinces TEXT[] := ARRAY['Gauteng', 'Western Cape', 'KwaZulu-Natal', 'Eastern Cape', 'Free State', 'Limpopo', 'Mpumalanga', 'North West', 'Northern Cape'];
    v_cities TEXT[] := ARRAY['Johannesburg', 'Cape Town', 'Durban', 'Gqeberha', 'Bloemfontein', 'Polokwane', 'Mbombela', 'Mahikeng', 'Kimberley', 'Pretoria'];

    v_group_type TEXT;
    v_group_name TEXT;
    v_province TEXT;
    v_city TEXT;
    v_monthly_contrib NUMERIC(10,2);
    v_joining_fee NUMERIC(10,2);

    v_now TIMESTAMPTZ := NOW();
BEGIN
    -- 1. Get Platform Admin
    SELECT id INTO v_admin_id FROM auth.users WHERE email = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'Platform admin torrymsimango@gmail.com not found. Run admin setup first.';
    END IF;

    -- 2. Cleanup existing seed data (idempotency)
    DELETE FROM public.audit_logs WHERE details->>'seed' = 'true';
    DELETE FROM public.member_behavior_track WHERE id::text LIKE 'seed-%';
    DELETE FROM public.group_health_scores WHERE id::text LIKE 'seed-%';
    DELETE FROM public.payouts WHERE payout_reference LIKE 'seed-payout-%';
    DELETE FROM public.loan_repayments WHERE id::text LIKE 'seed-%';
    DELETE FROM public.loans WHERE id::text LIKE 'seed-%';
    DELETE FROM public.contributions WHERE transaction_id LIKE 'seed-tx-%';
    DELETE FROM public.payments WHERE transaction_id LIKE 'seed-%';
    DELETE FROM public.members WHERE member_key LIKE 'SEED-%';
    DELETE FROM public.groups WHERE description LIKE 'Seed data for%';

    -- 3. Loop to create 50 Groups
    FOR v_group_idx IN 1..50 LOOP
        v_group_type := v_group_types[((v_group_idx - 1) % 5) + 1];
        v_group_name := v_group_prefixes[((v_group_idx - 1) % 10) + 1] || ' ' || initcap(replace(v_group_type, '_', ' ')) || ' ' || v_group_idx;
        v_province := v_provinces[((v_group_idx - 1) % 9) + 1];
        v_city := v_cities[((v_group_idx - 1) % 10) + 1];

        v_monthly_contrib := CASE
            WHEN v_group_type = 'burial_society' THEN 250.00 + (random() * 100)
            WHEN v_group_type = 'stokvel' THEN 400.00 + (random() * 200)
            WHEN v_group_type = 'rosca' THEN 800.00 + (random() * 400)
            WHEN v_group_type = 'investment_club' THEN 1500.00 + (random() * 1000)
            ELSE 200.00 + (random() * 100)
        END;

        v_joining_fee := 100.00 + (random() * 400);

        INSERT INTO public.groups (
            name, type, province, city, township, description, joining_fee, monthly_contribution,
            balance, admin_user_id, fee_status, registration_paid, created_at, updated_at,
            max_beneficiaries, beneficiary_increase_pct, probation_months
        ) VALUES (
            v_group_name, v_group_type, v_province, v_city, 'Township ' || v_group_idx,
            'Seed data for ' || v_group_name, v_joining_fee, v_monthly_contrib,
            0.00, v_admin_id,
            CASE WHEN random() > 0.85 THEN 'warning' ELSE 'paid' END,
            TRUE, v_now - interval '180 days', v_now,
            CASE WHEN v_group_type = 'burial_society' THEN 5 ELSE 0 END,
            CASE WHEN v_group_type = 'burial_society' THEN 10.00 ELSE 0 END,
            3
        ) RETURNING id INTO v_group_id;

        -- 4. Create 20 Members per Group (Total 1000)
        FOR v_member_idx IN 1..20 LOOP
            INSERT INTO public.members (
                group_id, full_name, email, phone, status, joined_at, member_key,
                total_contributions, total_paid, beneficiary_count
            ) VALUES (
                v_group_id,
                format('Member %s-%s', v_group_idx, v_member_idx),
                format('member.%s.%s@example.com', v_group_idx, v_member_idx),
                format('0%s%s%s', 70 + (v_group_idx % 9), lpad(v_group_idx::text, 3, '0'), lpad(v_member_idx::text, 4, '0')),
                CASE
                    WHEN v_member_idx = 20 THEN 'suspended'
                    WHEN v_member_idx = 19 THEN 'probation'
                    WHEN v_member_idx = 18 THEN 'pending_payment'
                    ELSE 'active'
                END,
                v_now - (150 - (v_member_idx * 2) || ' days')::interval,
                format('SEED-%s-%s', v_group_idx, v_member_idx),
                0, 0.00, CASE WHEN v_group_type = 'burial_society' THEN floor(random() * 5)::int ELSE 0 END
            ) RETURNING id INTO v_member_id;

            -- 5. Create 3 to 6 months of Contributions for each active member
            IF v_member_idx <= 17 THEN
                FOR v_month_idx IN 1..(3 + floor(random() * 4)::int) LOOP
                    PERFORM public.record_contribution_v1(
                        v_member_id, v_group_id, v_monthly_contrib,
                        (v_now - ((180 - (v_month_idx * 30)) || ' days')::interval)::date,
                        v_now - ((180 - (v_month_idx * 30) - floor(random() * 5)::int) || ' days')::interval,
                        'paid',
                        format('seed-tx-%s-%s-%s', v_group_idx, v_member_idx, v_month_idx),
                        'contribution'
                    );
                END LOOP;
            END IF;

            -- 6. Complex Financial Scenarios

            -- Loans for every 4th member
            IF v_member_idx % 4 = 0 AND v_member_idx <= 16 THEN
                v_loan_id := gen_random_uuid();
                INSERT INTO public.loans (
                    id, member_id, group_id, amount, interest_rate, total_to_repay, total_repaid,
                    monthly_repayment, start_date, end_date, status, purpose, created_at
                ) VALUES (
                    v_loan_id, v_member_id, v_group_id, 3000.00, 5.0, 3150.00, 0.00,
                    1050.00, (v_now - interval '60 days')::date, (v_now + interval '30 days')::date,
                    CASE WHEN v_member_idx % 8 = 0 THEN 'overdue' ELSE 'active' END,
                    'Personal expenses', v_now - interval '60 days'
                );

                -- Record some repayments for non-overdue loans
                IF v_member_idx % 8 != 0 THEN
                    PERFORM public.record_loan_repayment_v1(v_loan_id, v_member_id, v_group_id, 1050.00, 'bank');
                    -- Maybe a second repayment?
                    IF random() > 0.5 THEN
                        PERFORM public.record_loan_repayment_v1(v_loan_id, v_member_id, v_group_id, 1050.00, 'bank');
                    END IF;
                END IF;
            END IF;

            -- Burial Claims for 1st member in every 5th Burial Society
            IF v_group_type = 'burial_society' AND v_group_idx % 5 = 0 AND v_member_idx = 1 THEN
                INSERT INTO public.beneficiary_payout_claims (
                    group_id, member_id, beneficiary_id, beneficiary_name, claim_amount, status,
                    cause_of_death, date_of_death, bank_name, account_no, branch_code, created_at
                ) VALUES (
                    v_group_id, v_member_id, gen_random_uuid(), 'Legacy Beneficiary', 15000.00, 'paid',
                    'Natural Causes', (v_now - interval '45 days')::date, 'Standard Bank', '123456789', '051001', v_now - interval '45 days'
                );
                -- Process payout side-effect via ledger (manual since it's a seed)
                PERFORM public.increment_group_balance(v_group_id, -15000.00, 'Burial Claim Payout: Legacy Beneficiary', 'burial_claim', 'seed-claim-' || v_group_idx);
            END IF;

            -- Behavior tracks
            INSERT INTO public.member_behavior_track (
                id, member_id, group_id, behavior_score, fraud_score, fraud_risk_level, member_status
            ) VALUES (
                gen_random_uuid(), v_member_id, v_group_id,
                70 + random() * 30, random() * 15,
                CASE WHEN random() > 0.9 THEN 'medium' ELSE 'low' END,
                CASE WHEN random() > 0.8 THEN 'good' ELSE 'excellent' END
            );
        END LOOP;

        -- 7. Random Payouts (Historical Fund Movement)
        IF random() > 0.4 THEN
            FOR i IN 1..floor(random() * 2 + 1)::int LOOP
                v_payout_amt := 2000.00 + (random() * 3000);
                INSERT INTO public.payouts (
                    group_id, amount, bank_name, account_no, branch_code, status,
                    processed_by, processed_at, payout_reference, created_at
                ) VALUES (
                    v_group_id, v_payout_amt, 'Standard Bank', '123456789', '051001', 'completed',
                    v_admin_id, v_now - (30 * i || ' days')::interval, 'seed-payout-' || v_group_idx || '-' || i, v_now - (30 * i + 2 || ' days')::interval
                );
                -- Ensure balance reflects the payout in the ledger
                PERFORM public.increment_group_balance(v_group_id, -v_payout_amt, 'Group Payout Withdrawal', 'payout', 'seed-payout-tx-' || v_group_idx || '-' || i);
            END LOOP;
        END IF;

        -- 8. Group Health Score
        INSERT INTO public.group_health_scores (
            id, group_id, overall_score, zone, components_json, recommendations_json, generated_at
        ) VALUES (
            gen_random_uuid(), v_group_id, 60 + random() * 35,
            CASE WHEN random() > 0.7 THEN 'YELLOW' ELSE 'GREEN' END,
            '{"Solvency": 85, "Retention": 90, "Compliance": 75}',
            '["Maintain current reserve levels", "Improve meeting attendance records"]',
            v_now
        );

        -- Audit Log
        INSERT INTO public.audit_logs (actor_id, target_group_id, action, details)
        VALUES (v_admin_id, v_group_id, 'SEED_DATA_GENERATED', '{"seed": true, "members": 20}');

        -- 9. Platform Revenue (Simulation)
        PERFORM public.record_platform_revenue_v1(v_joining_fee, 'Seed Registration Fee - ' || v_group_name, 'registration_fee', 'seed-reg-' || v_group_idx);

        -- 10. Actuarial Metrics
        INSERT INTO public.group_actuarial_metrics (
            group_id, composite_risk_score, reserve_adequacy_pct, solvency_ratio, payment_rate_pct,
            loss_ratio_pct, contribution_sufficiency_pct, funding_ratio_pct
        ) VALUES (
            v_group_id, 20 + (random() * 40), 70.0 + random() * 40, 1.0 + random() * 1.0,
            80.0 + random() * 20, 10.0 + random() * 30, 90.0 + random() * 20, 100.0 + random() * 30
        ) ON CONFLICT (group_id) DO UPDATE SET composite_risk_score = EXCLUDED.composite_risk_score;

        -- 11. Data Repair (Sync counters and stats)
        PERFORM public.repair_group_data_v1(v_group_id);

    END LOOP;

    RAISE NOTICE 'Seed successful: 50 groups and 1000 members with rich history and complex scenarios created.';
END $$;
