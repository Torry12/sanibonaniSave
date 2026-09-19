-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — EXTENSIVE SEED DATA (100 Groups, 2000 Members)
-- Version: 5.0 (Professional Grade, Multi-Year History, Complex Scenarios)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Create Storage Buckets (Safe execution)
INSERT INTO storage.buckets (id, name, public)
VALUES
    ('avatars', 'avatars', true),
    ('constitutions', 'constitutions', true),
    ('documents', 'documents', false)
ON CONFLICT (id) DO NOTHING;

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_loan_id UUID;
    v_group_idx INT;
    v_member_idx INT;
    v_month_idx INT;
    v_history_months INT := 12; -- Up to 1 year of history
    v_payout_amt NUMERIC(10,2);
    v_current_pot_receiver_idx INT;

    -- Config Arrays
    v_group_types TEXT[] := ARRAY['burial_society', 'stokvel', 'rosca', 'investment_club', 'emergency_fund'];
    v_group_prefixes TEXT[] := ARRAY['Siyakhula', 'Unity', 'Round Robin', 'Zenith', 'SOS', 'Luthando', 'Rising Stars', 'Speedy Payout', 'Blue Chip', 'Community', 'Trust', 'Legacy', 'Pioneer', 'Horizon', 'Summit'];
    v_provinces TEXT[] := ARRAY['Gauteng', 'Western Cape', 'KwaZulu-Natal', 'Eastern Cape', 'Free State', 'Limpopo', 'Mpumalanga', 'North West', 'Northern Cape'];
    v_cities TEXT[] := ARRAY['Johannesburg', 'Cape Town', 'Durban', 'Gqeberha', 'Bloemfontein', 'Polokwane', 'Mbombela', 'Mahikeng', 'Kimberley', 'Pretoria', 'Soweto', 'Tembisa', 'Umlazi', 'Khayelitsha'];

    -- Expanded Identity Data
    v_first_names TEXT[] := ARRAY['Thabo', 'Sipho', 'Lerato', 'Naledi', 'Johan', 'Annelie', 'Fatima', 'Yusuf', 'Siyabonga', 'Nomvula', 'Kagiso', 'Boipelo', 'Zanele', 'Mandla', 'Lindiwe', 'Bongani', 'Tshepo', 'Refilwe', 'Kopano', 'Palesa', 'Jabulani', 'Mbali', 'Themba', 'Ayanda', 'Simphiwe', 'Zandile', 'Sfiso', 'Nonhlanhla'];
    v_surnames TEXT[] := ARRAY['Mokoena', 'Dlamini', 'Nkosi', 'Van der Merwe', 'Botha', 'Patel', 'Naidoo', 'Ndlovu', 'Khumalo', 'Sithole', 'Mahlangu', 'Modise', 'Zuma', 'Molefe', 'Gumede', 'Nyathi', 'Fourie', 'Radebe', 'Mthembu', 'Ngcobo', 'Mdlalose', 'Xaba', 'Cele', 'Buthelezi', 'Smit', 'Govender'];

    v_group_type TEXT;
    v_group_name TEXT;
    v_province TEXT;
    v_city TEXT;
    v_monthly_contrib NUMERIC(10,2);
    v_joining_fee NUMERIC(10,2);
    v_first_name TEXT;
    v_surname TEXT;
    v_full_name TEXT;
    v_email TEXT;
    v_member_status TEXT;

    v_now TIMESTAMPTZ := NOW();
    v_join_date TIMESTAMPTZ;
BEGIN
    -- 1. Identify Platform Admin (Fallback to first user if specific email not found)
    SELECT id INTO v_admin_id FROM auth.users WHERE email = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_admin_id IS NULL THEN
        SELECT id INTO v_admin_id FROM auth.users LIMIT 1;
    END IF;

    IF v_admin_id IS NULL THEN
        RAISE NOTICE 'No users found in auth.users. Please create a user first.';
        RETURN;
    END IF;

    -- 2. loop to create 100 Groups
    FOR v_group_idx IN 1..100 LOOP
        v_group_type := v_group_types[((v_group_idx - 1) % 5) + 1];
        v_group_name := v_group_prefixes[floor(random() * array_length(v_group_prefixes, 1) + 1)::int] || ' ' || initcap(replace(v_group_type, '_', ' ')) || ' ' || v_group_idx;
        v_province := v_provinces[floor(random() * array_length(v_provinces, 1) + 1)::int];
        v_city := v_cities[floor(random() * array_length(v_cities, 1) + 1)::int];

        v_monthly_contrib := CASE
            WHEN v_group_type = 'burial_society' THEN 200.00 + (random() * 150)
            WHEN v_group_type = 'stokvel' THEN 300.00 + (random() * 500)
            WHEN v_group_type = 'rosca' THEN 500.00 + (random() * 1000)
            WHEN v_group_type = 'investment_club' THEN 1000.00 + (random() * 5000)
            ELSE 150.00 + (random() * 200)
        END;

        v_joining_fee := 100.00 + (random() * 900);

        INSERT INTO public.groups (
            name, type, province, city, township, description, joining_fee, monthly_contribution,
            balance, admin_user_id, fee_status, registration_paid, created_at, updated_at,
            max_beneficiaries, beneficiary_increase_pct, probation_months, latitude, longitude, constitution_url, rosca_rotation_method, payment_due_day,
            loan_interest_rate, loan_max_amount, loan_max_months
        ) VALUES (
            v_group_name, v_group_type, v_province, v_city, 'Region ' || (v_group_idx % 20),
            'Professional ' || replace(v_group_type, '_', ' ') || ' dedicated to financial resilience and communal growth in ' || v_city || '.',
            v_joining_fee, v_monthly_contrib,
            0.00, v_admin_id,
            CASE WHEN random() > 0.9 THEN 'warning' WHEN random() > 0.95 THEN 'overdue' ELSE 'paid' END,
            TRUE, v_now - interval '365 days', v_now,
            CASE WHEN v_group_type = 'burial_society' THEN 6 ELSE 0 END,
            CASE WHEN v_group_type = 'burial_society' THEN 12.50 ELSE 0 END,
            CASE WHEN v_group_type = 'burial_society' THEN 6 ELSE 3 END,
            -26.0 + (random() * 6), 27.0 + (random() * 6),
            'constitutions/mock_constitution_' || v_group_idx || '.pdf',
            CASE WHEN random() > 0.7 THEN 'random_draw' ELSE 'fixed' END,
            28,
            CASE WHEN v_group_type IN ('stokvel', 'burial_society', 'emergency_fund') THEN 5.0 + random() * 10.0 ELSE NULL END,
            CASE WHEN v_group_type IN ('stokvel', 'burial_society', 'emergency_fund') THEN 5000.00 ELSE NULL END,
            3
        ) RETURNING id INTO v_group_id;

        -- 3. Create 20 Members per Group (Total 2000)
        FOR v_member_idx IN 1..20 LOOP
            v_first_name := v_first_names[floor(random() * array_length(v_first_names, 1) + 1)::int];
            v_surname := v_surnames[floor(random() * array_length(v_surnames, 1) + 1)::int];
            v_full_name := v_first_name || ' ' || v_surname;
            v_email := lower(v_first_name) || '.' || lower(v_surname) || '.' || (v_group_idx * 100 + v_member_idx)::text || '@example.com';

            v_join_date := v_now - (360 - (v_member_idx * 5) || ' days')::interval;

            INSERT INTO public.members (
                group_id, full_name, email, phone, status, joined_at, member_key,
                total_contributions, total_paid, beneficiary_count, profile_photo_url, rosca_position, id_number, probation_end_at,
                bank_name, account_no, branch_code
            ) VALUES (
                v_group_id, v_full_name, v_email,
                format('0%s%s%s', 60 + (v_group_idx % 20), lpad(v_group_idx::text, 3, '0'), lpad(v_member_idx::text, 4, '0')),
                CASE
                    WHEN v_member_idx = 20 AND random() > 0.5 THEN 'suspended'
                    WHEN v_member_idx = 19 AND random() > 0.5 THEN 'probation'
                    ELSE 'active'
                END,
                v_join_date,
                format('SEED-%s-%s', v_group_id, v_member_id),
                0, 0.00, CASE WHEN v_group_type = 'burial_society' THEN floor(random() * 6)::int ELSE 0 END,
                'avatars/mock_avatar_' || (floor(random() * 10) + 1)::int || '.jpg',
                CASE WHEN v_group_type = 'rosca' THEN v_member_idx ELSE NULL END,
                '850101' || floor(1000 + random() * 8999)::text || '08' || floor(1+random()*9)::text,
                v_join_date + interval '6 months',
                ARRAY['FNB', 'Standard Bank', 'Absa', 'Nedbank', 'Capitec', 'TymeBank'][floor(random() * 6 + 1)::int],
                floor(100000000 + random() * 900000000)::text,
                '250655'
            ) RETURNING id INTO v_member_id;

            -- 4. Multi-Month History
            -- Joining Fee
            PERFORM public.increment_group_balance(v_group_id, v_joining_fee, 'Joining Fee: ' || v_full_name, 'joining_fee', 'seed-join-' || v_member_id);

            -- Contributions (12 month history)
            FOR v_month_idx IN 1..v_history_months LOOP
                -- Skip if member joined later
                IF (v_now - (v_month_idx * 30 || ' days')::interval) < v_join_date THEN
                    CONTINUE;
                END IF;

                -- 90% payment rate
                IF random() < 0.90 THEN
                    PERFORM public.record_contribution_v1(
                        v_member_id, v_group_id, v_monthly_contrib,
                        (v_now - (v_month_idx * 30 || ' days')::interval)::date,
                        v_now - (v_month_idx * 30 - floor(random() * 3)::int || ' days')::interval,
                        'paid',
                        format('seed-tx-%s-%s', v_member_id, v_month_idx),
                        'contribution'
                    );

                    -- Occasional extra platform fee logic is already inside record_contribution_v1 (tiered)
                END IF;
            END LOOP;

            -- 5. Active Loans (15% of members in lending groups)
            IF v_group_type NOT IN ('rosca', 'investment_club') AND random() < 0.15 AND v_member_idx < 18 THEN
                v_loan_id := gen_random_uuid();
                INSERT INTO public.loans (
                    id, member_id, group_id, amount, interest_rate, total_to_repay, total_repaid,
                    monthly_repayment, start_date, end_date, status, purpose, created_at, amortization_type
                ) VALUES (
                    v_loan_id, v_member_id, v_group_id, 2000.00, 10.0, 2200.00, 0.00,
                    733.33, (v_now - interval '30 days')::date, (v_now + interval '60 days')::date,
                    'active', 'Household upgrades', v_now - interval '30 days',
                    CASE WHEN random() > 0.5 THEN 'reducing' ELSE 'flat' END
                );

                -- Initial repayment
                PERFORM public.record_loan_repayment_v1(v_loan_id, v_member_id, v_group_id, 733.33, 'bank');
            END IF;

        END LOOP;

        -- 6. Periodic Group Events
        IF v_group_type = 'rosca' THEN
            -- Pay out first 3 members of the rotation
            FOR i IN 1..3 LOOP
                v_payout_amt := 20 * v_monthly_contrib;
                INSERT INTO public.payouts (
                    group_id, amount, bank_name, account_no, branch_code, status,
                    processed_by, processed_at, payout_reference, created_at
                ) VALUES (
                    v_group_id, v_payout_amt, 'Standard Bank', '987654321', '051001', 'COMPLETED',
                    v_admin_id::text, v_now - (i * 30 || ' days')::interval, 'ROSCACYCLE-' || i, v_now - (i * 30 || ' days')::interval
                );
                PERFORM public.increment_group_balance(v_group_id, -v_payout_amt, 'ROSCA Rotation Payout', 'payout', 'seed-rosca-' || v_group_id || '-' || i);
            END LOOP;
        END IF;

        -- 7. Analytics & Scoring
        INSERT INTO public.group_health_scores (
            group_id, overall_score, zone, components_json, recommendations_json, generated_at
        ) VALUES (
            v_group_id, 50 + random() * 45,
            CASE WHEN random() > 0.8 THEN 'RED' WHEN random() > 0.6 THEN 'YELLOW' ELSE 'GREEN' END,
            '{"Liquidity": 80, "Compliance": 95, "Growth": 70}',
            '["Maintain consistent contributions", "Consider reducing loan exposure"]',
            v_now
        );

    END LOOP;

    RAISE NOTICE 'Extensive seed completed: 100 groups, 2000 members, and 1 year of financial history generated.';
END $$;
