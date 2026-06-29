-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — LARGE SEED DATA (10 Groups, 100 Members)
-- Version: 1.0 (June 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_loan_id UUID;
    v_group_idx INT;
    v_member_idx INT;

    -- Config Arrays
    v_group_types TEXT[] := ARRAY['burial_society', 'stokvel', 'rosca', 'investment_club', 'emergency_fund', 'burial_society', 'stokvel', 'rosca', 'investment_club', 'emergency_fund'];
    v_group_names TEXT[] := ARRAY[
        'Siyakhula Burial Society', 'Unity Stokvel', 'Round Robin ROSCA', 'Zenith Investment Club', 'SOS Emergency Fund',
        'Luthando Family Trust', 'Rising Stars Stokvel', 'Speedy Payout ROSCA', 'Blue Chip Investors', 'Community Safety Net'
    ];
    v_provinces TEXT[] := ARRAY['Gauteng', 'Western Cape', 'KwaZulu-Natal', 'Eastern Cape', 'Free State', 'Limpopo', 'Mpumalanga', 'North West', 'Northern Cape', 'Gauteng'];
    v_cities TEXT[] := ARRAY['Johannesburg', 'Cape Town', 'Durban', 'Gqeberha', 'Bloemfontein', 'Polokwane', 'Mbombela', 'Mahikeng', 'Kimberley', 'Pretoria'];

    v_group_type TEXT;
    v_group_name TEXT;
    v_province TEXT;
    v_city TEXT;
    v_monthly_contrib NUMERIC(10,2);
    v_joining_fee NUMERIC(10,2) := 500.00;

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
    DELETE FROM public.loan_repayments WHERE id::text LIKE 'seed-%';
    DELETE FROM public.loans WHERE id::text LIKE 'seed-%';
    DELETE FROM public.contributions WHERE transaction_id LIKE 'seed-%';
    DELETE FROM public.payments WHERE transaction_id LIKE 'seed-%';
    DELETE FROM public.members WHERE member_key LIKE 'SEED-%';
    DELETE FROM public.groups WHERE name = ANY(v_group_names);

    -- 3. Loop to create 10 Groups
    FOR v_group_idx IN 1..10 LOOP
        v_group_type := v_group_types[v_group_idx];
        v_group_name := v_group_names[v_group_idx];
        v_province := v_provinces[v_group_idx];
        v_city := v_cities[v_group_idx];
        v_monthly_contrib := CASE
            WHEN v_group_type = 'burial_society' THEN 350.00
            WHEN v_group_type = 'stokvel' THEN 500.00
            WHEN v_group_type = 'rosca' THEN 1000.00
            WHEN v_group_type = 'investment_club' THEN 2000.00
            ELSE 250.00
        END;

        INSERT INTO public.groups (
            name, type, province, city, township, description, joining_fee, monthly_contribution,
            balance, admin_user_id, fee_status, registration_paid, created_at, updated_at
        ) VALUES (
            v_group_name, v_group_type, v_province, v_city, 'Township ' || v_group_idx,
            'Seed data for ' || v_group_name, v_joining_fee, v_monthly_contrib,
            0.00, v_admin_id, 'paid', TRUE, v_now - interval '90 days', v_now
        ) RETURNING id INTO v_group_id;

        -- 4. Create 10 Members per Group (Total 100)
        FOR v_member_idx IN 1..10 LOOP
            INSERT INTO public.members (
                group_id, full_name, email, phone, status, joined_at, member_key,
                total_contributions, total_paid, beneficiary_count
            ) VALUES (
                v_group_id,
                format('Member %s %s', v_group_idx, v_member_idx),
                format('member.%s.%s@example.com', v_group_idx, v_member_idx),
                format('071%s%s', lpad(v_group_idx::text, 3, '0'), lpad(v_member_idx::text, 4, '0')),
                CASE WHEN v_member_idx = 10 THEN 'suspended' WHEN v_member_idx = 9 THEN 'probation' ELSE 'active' END,
                v_now - (90 - (v_member_idx * 2) || ' days')::interval,
                format('SEED-%s-%s', v_group_idx, v_member_idx),
                0, 0.00, CASE WHEN v_group_type = 'burial_society' THEN 3 ELSE 0 END
            ) RETURNING id INTO v_member_id;

            -- 5. Create 3 months of Contributions for each member
            FOR i IN 1..3 LOOP
                PERFORM public.record_contribution_v1(
                    v_member_id, v_group_id, v_monthly_contrib,
                    (v_now - (120 - (i * 30)) || ' days')::date,
                    v_now - (120 - (i * 30) - 2 || ' days')::interval,
                    'paid',
                    format('seed-tx-%s-%s-%s', v_group_idx, v_member_idx, i),
                    'contribution'
                );
            END LOOP;

            -- 6. Add some specialized data

            -- Loans for every 3rd member
            IF v_member_idx % 3 = 0 THEN
                INSERT INTO public.loans (
                    id, member_id, group_id, amount, interest_rate, total_to_repay, total_repaid,
                    monthly_repayment, start_date, end_date, status, purpose, created_at
                ) VALUES (
                    gen_random_uuid(), v_member_id, v_group_id, 2000.00, 5.0, 2100.00, 700.00,
                    700.00, (v_now - interval '60 days')::date, (v_now + interval '30 days')::date,
                    'active', 'Personal expenses', v_now - interval '60 days'
                ) RETURNING id INTO v_loan_id;

                -- Record a repayment
                PERFORM public.record_loan_repayment_v1(
                    v_loan_id, v_member_id, v_group_id, 700.00, 'bank'
                );
            END IF;

            -- Behavior tracks
            INSERT INTO public.member_behavior_track (
                id, member_id, group_id, behavior_score, fraud_score, fraud_risk_level, member_status
            ) VALUES (
                gen_random_uuid(), v_member_id, v_group_id,
                80 + random() * 20, random() * 10, 'LOW', 'excellent'
            );
        END LOOP;

        -- 7. Group Health Score
        INSERT INTO public.group_health_scores (
            id, group_id, overall_score, zone, components_json, recommendations_json, generated_at
        ) VALUES (
            gen_random_uuid(), v_group_id, 85, 'GREEN',
            '{"Solvency": 90, "Retention": 95, "Compliance": 80}',
            '["Maintain current reserve levels", "Continue regular audits"]',
            v_now
        );

        -- Audit Log
        INSERT INTO public.audit_logs (actor_id, target_group_id, action, details)
        VALUES (v_admin_id, v_group_id, 'SEED_DATA_GENERATED', '{"seed": true, "members": 10}');

    END LOOP;

    RAISE NOTICE 'Seed successful: 10 groups and 100 members with history created.';
END $$;
