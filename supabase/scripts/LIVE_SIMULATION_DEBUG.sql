-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — LIVE BACKEND SIMULATION & INTEGRITY DEBUG
-- Version: 1.2 (Bulk Groups - August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_loan_id UUID;
    v_idx INT;
    v_g_idx INT;
    v_month INT;
    v_contrib_amt NUMERIC := 500.00;
    v_group_name TEXT;
    v_lat FLOAT8;
    v_lon FLOAT8;
BEGIN
    -- 1. Get Platform Admin
    SELECT id INTO v_admin_id FROM auth.users WHERE email = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'Platform admin not found. Run admin setup first.';
    END IF;

    RAISE NOTICE '🚀 Starting SQL-based Live Simulation for 10 Groups';

    FOR v_g_idx IN 1..10 LOOP
        v_group_name := 'SIM-STRESS-GROUP-' || v_g_idx || '-' || floor(random() * 100);
        v_lat := -26.2041 + (random() - 0.5) * 0.5; -- Spread around Joburg
        v_lon := 28.0473 + (random() - 0.5) * 0.5;

        -- 2. Create Group
        INSERT INTO public.groups (
            name, type, province, city, township, description, joining_fee, monthly_contribution,
            balance, admin_user_id, fee_status, registration_paid, created_at,
            latitude, longitude
        ) VALUES (
            v_group_name, 'stokvel', 'Gauteng', 'Johannesburg', 'Sim-Zone',
            'SIMULATION_STRESS: Group ' || v_g_idx, 150.00, v_contrib_amt,
            0.00, v_admin_id, 'paid', TRUE, now() - interval '90 days',
            v_lat, v_lon
        ) RETURNING id INTO v_group_id;

        -- Record Platform Revenue (Registration Fee)
        PERFORM public.record_platform_revenue_v1(700.00, 'Platform Registration: ' || v_group_name, 'registration', 'SIM-REG-' || v_group_id, v_group_id);

        -- 3. Add 5 Members per Group
        FOR v_idx IN 1..5 LOOP
            INSERT INTO public.members (
                group_id, full_name, email, phone, status, joined_at, member_key
            ) VALUES (
                v_group_id,
                'Sim Member ' || v_g_idx || '-' || v_idx,
                'sim.' || v_g_idx || '.' || v_idx || '@stress.example.com',
                '072' || lpad(v_g_idx::text, 3, '0') || lpad(v_idx::text, 4, '0'),
                'active',
                now() - interval '90 days',
                'STRESS-KEY-' || v_group_id || '-' || v_idx
            ) RETURNING id INTO v_member_id;

            -- 4. Simulate Financial History
            PERFORM public.record_contribution_v2(
                v_member_id, v_group_id, v_contrib_amt,
                (now() - interval '60 days')::date,
                now() - interval '58 days',
                'paid',
                gen_random_uuid()::text,
                'contribution',
                'Initial Contribution'
            );

            -- Record Platform Revenue (Member Fee)
            PERFORM public.record_platform_revenue_v1(10.00, 'Monthly Member Fee (Sim)', 'member_fee', 'SIM-FEE-1-' || v_member_id, v_group_id);

            -- Occasional default (20% rate)
            IF random() > 0.2 THEN
                PERFORM public.record_contribution_v2(
                    v_member_id, v_group_id, v_contrib_amt,
                    (now() - interval '30 days')::date,
                    now() - interval '28 days',
                    'paid',
                    gen_random_uuid()::text,
                    'contribution',
                    'Month 2 Contribution'
                );

                -- Record Platform Revenue (Member Fee)
                PERFORM public.record_platform_revenue_v1(10.00, 'Monthly Member Fee (Sim)', 'member_fee', 'SIM-FEE-2-' || v_member_id, v_group_id);
            END IF;
        END LOOP;

        -- 5. Run Authoritative Recalculation
        PERFORM public.recalculate_all_group_financials_v3(v_group_id);

        RAISE NOTICE '✅ Created Group %: %', v_g_idx, v_group_name;
    END LOOP;

    RAISE NOTICE '🚀 SIMULATION COMPLETE. 10 groups with history established.';
END $$;
