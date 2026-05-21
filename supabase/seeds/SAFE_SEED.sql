-- SAFE_SEED.sql
-- Canonical consolidated safe seed (10 groups, 10 members each) — idempotent and validated
-- Ensures: each group has an admin who is a member; extra admins are members; account_number digits-only; gateway_public_key used; ledger entries for balance changes.

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_extra_admin_id UUID;
    v_group_name TEXT;
    v_types TEXT[] := ARRAY['stokvel','burial_society','investment_club','rosca','community_savings','emergency_fund','tontine','stokvel','burial_society','other'];
    v_provinces TEXT[] := ARRAY['Gauteng','KwaZulu-Natal','Western Cape','Eastern Cape','Limpopo','Mpumalanga','North West','Free State','Northern Cape','Gauteng'];
    v_cities TEXT[] := ARRAY['Johannesburg','Durban','Cape Town','Gqeberha','Polokwane','Mbombela','Mahikeng','Bloemfontein','Kimberley','Pretoria'];
    v_townships TEXT[] := ARRAY['Soweto','Umlazi','Khayelitsha','Motherwell','Seshego','Kanyamazane','Mmabatho','Mangaung','Galeshewe','Mamelodi'];
    v_group_balance NUMERIC(12,2);
    v_contribution_amount NUMERIC(10,2);
    v_disbursement_amount NUMERIC(12,2);
    v_disbursement_status TEXT;
    v_extra_admin_password TEXT := 'SeedSafe@1234';
BEGIN
    -- Ensure platform admin exists
    SELECT id INTO v_admin_id FROM auth.users WHERE lower(email) = lower('torrymsimango@gmail.com') LIMIT 1;
    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'Platform admin user not found. Run platform admin setup first.';
    END IF;

    -- Cleanup prior canonical-seed groups only
    DELETE FROM public.audit_logs WHERE action = 'SAFE_SEED_GROUP_CREATED';
    DELETE FROM public.platform_ledger WHERE description LIKE 'SAFE_SEED %';
    DELETE FROM public.groups WHERE name LIKE 'SAFE_SEED-G%';

    FOR g IN 1..10 LOOP
        v_group_name := format('SAFE_SEED-G%s %s', lpad(g::text,2,'0'), initcap(replace(v_types[g],'_',' ')));
        v_group_balance := (4000.00 + (g * 1000))::numeric(12,2);
        v_contribution_amount := (250.00 + (g * 10))::numeric(10,2);
        v_disbursement_amount := (v_contribution_amount * 3)::numeric(12,2);

        -- Insert group (idempotent by name)
        INSERT INTO public.groups (
            id, name, type, province, city, township, description, logo_emoji, joining_fee, monthly_contribution,
            late_fee, late_fee_grace_days, probation_months, payment_due_day, max_members, is_public, allow_partial_payment,
            auto_suspend_after, bank_name, account_number, branch_code, account_type, gateway_public_key, balance,
            admin_user_id, fee_status, registration_paid, is_platform_suspended, constitution_status, latitude, longitude,
            max_beneficiaries, beneficiary_increase_pct, goal_amount, period_months, loan_interest_rate, loan_max_amount, loan_max_months, created_at, updated_at
        ) VALUES (
            gen_random_uuid(), v_group_name, v_types[g], v_provinces[g], v_cities[g], v_townships[g],
            'Canonical safe seed for deterministic testing', CASE WHEN v_types[g] = 'burial_society' THEN '🕊️' ELSE '🤝' END,
            150.00, 300.00 + (g * 10), 50.00, 5, 3, 28, 120, TRUE, (g % 2 = 0), 2, 'FNB',
            -- ensure digits-only account number
            '6200777' || lpad(g::text, 3, '0'), '250655', 'Savings', 'pk_test_seed_group_' || g, v_group_balance,
            v_admin_id, CASE WHEN g % 4 = 0 THEN 'warning' ELSE 'paid' END, TRUE, FALSE, 'verified',
            -34.0 + (g * 0.7), 18.0 + (g * 1.1), CASE WHEN v_types[g] = 'burial_society' THEN 4 ELSE 0 END,
            CASE WHEN v_types[g] = 'burial_society' THEN 5 ELSE 0 END, 100000.00, 24, 12.5, 12000.00, 12, now(), now()
        ) ON CONFLICT (name) DO UPDATE
            SET updated_at = now(), balance = EXCLUDED.balance;

        -- Get group id
        SELECT id INTO v_group_id FROM public.groups WHERE name = v_group_name LIMIT 1;

        -- Ensure admin is a member
        INSERT INTO public.members (group_id, user_id, full_name, phone, email, notification_pref, status, joined_at, probation_end_at, member_key)
        VALUES (v_group_id, v_admin_id, format('Safe Admin %s', lpad(g::text,2,'0')), format('07900%s', lpad(g::text,5,'0')), format('safe.admin.%s@example.com', lpad(g::text,2,'0')), 'both', 'active', now() - interval '120 days', now() + interval '60 days', format('SAFE_ADMIN_KEY_%s', lpad(g::text,2,'0')))
        ON CONFLICT (group_id, user_id) DO NOTHING;

        -- Opening ledger entry
        INSERT INTO public.group_ledger (id, group_id, transaction_id, amount, balance_after, description, category, created_at)
        VALUES (gen_random_uuid(), v_group_id, NULL, 0.00, v_group_balance, format('SAFE_SEED opening balance %s', lpad(g::text,2,'0')), 'opening_balance', now() - interval '90 days');

        -- Create members (9 additional per group), with two extra admins and 7 regular members
        FOR m IN 1..9 LOOP
            INSERT INTO public.members (group_id, user_id, full_name, id_number, phone, email, street, suburb, city, province, notification_pref, status, joined_at, probation_end_at, beneficiary_count, beneficiary_over_65_count, total_contributions, total_paid, member_key)
            VALUES (
                v_group_id, NULL, CASE WHEN m IN (1,2) THEN format('Safe Extra Admin %s-%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')) ELSE format('Safe Member %s-%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')) END,
                lpad((9200000000000 + (g * 100) + m)::text,13,'0'), format('07%s', lpad((g*100 + m)::text,8,'0')),
                CASE WHEN m IN (1,2) THEN format('safe.groupadmin.%s.%s@example.com', lpad(g::text,2,'0'), lpad(m::text,2,'0')) ELSE format('safe.member.%s.%s@example.com', lpad(g::text,2,'0'), lpad(m::text,2,'0')) END,
                format('%s Safe Street', m), v_townships[g], v_cities[g], v_provinces[g], CASE WHEN m % 3 = 0 THEN 'email' WHEN m % 3 = 1 THEN 'whatsapp' ELSE 'both' END,
                CASE WHEN m <= 7 THEN 'active' WHEN m = 8 THEN 'probation' ELSE 'pending_payment' END, now() - ((m+g) || ' days')::interval, now() + interval '60 days', CASE WHEN v_types[g] = 'burial_society' THEN 2 ELSE 0 END, 0, 0, 0,
                CASE WHEN m IN (1,2) THEN format('SAFE_EXTRA_ADMIN_KEY_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')) ELSE format('SAFE_KEY_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')) END
            ) RETURNING id INTO v_member_id;

            -- For extra admins (m 1 and 2), create auth.users and link
            IF m IN (1,2) THEN
                SELECT id INTO v_extra_admin_id FROM auth.users WHERE lower(email) = lower(CASE WHEN m=1 THEN format('safe.groupadmin.%s.01@example.com', lpad(g::text,2,'0')) ELSE format('safe.groupadmin.%s.02@example.com', lpad(g::text,2,'0')) END) LIMIT 1;
                IF v_extra_admin_id IS NULL THEN
                    v_extra_admin_id := gen_random_uuid();
                    INSERT INTO auth.users (id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
                    VALUES (v_extra_admin_id, 'authenticated', 'authenticated', CASE WHEN m=1 THEN format('safe.groupadmin.%s.01@example.com', lpad(g::text,2,'0')) ELSE format('safe.groupadmin.%s.02@example.com', lpad(g::text,2,'0')) END, crypt(v_extra_admin_password, gen_salt('bf')), now(), '{"provider":"email","providers":["email"],"role":"group_admin"}'::jsonb, jsonb_build_object('full_name', CASE WHEN m=1 THEN format('Safe Extra Admin %s-01', lpad(g::text,2,'0')) ELSE format('Safe Extra Admin %s-02', lpad(g::text,2,'0')) END, 'role', 'group_admin'), now(), now());
                ELSE
                    UPDATE auth.users SET encrypted_password = crypt(v_extra_admin_password, gen_salt('bf')), email_confirmed_at = COALESCE(email_confirmed_at, now()), raw_app_meta_data = COALESCE(raw_app_meta_data, '{}'::jsonb) || '{"provider":"email","providers":["email"],"role":"group_admin"}'::jsonb, raw_user_meta_data = COALESCE(raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('full_name', CASE WHEN m=1 THEN format('Safe Extra Admin %s-01', lpad(g::text,2,'0')) ELSE format('Safe Extra Admin %s-02', lpad(g::text,2,'0')) END, 'role', 'group_admin'), updated_at = now() WHERE id = v_extra_admin_id;
                END IF;

                INSERT INTO auth.identities (id, user_id, provider, provider_id, identity_data, created_at, updated_at)
                VALUES (gen_random_uuid(), v_extra_admin_id, 'email', CASE WHEN m=1 THEN format('safe.groupadmin.%s.01@example.com', lpad(g::text,2,'0')) ELSE format('safe.groupadmin.%s.02@example.com', lpad(g::text,2,'0')) END, jsonb_build_object('sub', v_extra_admin_id::text, 'email', CASE WHEN m=1 THEN format('safe.groupadmin.%s.01@example.com', lpad(g::text,2,'0')) ELSE format('safe.groupadmin.%s.02@example.com', lpad(g::text,2,'0')) END), now(), now())
                ON CONFLICT (provider, provider_id) DO UPDATE SET user_id = EXCLUDED.user_id, identity_data = EXCLUDED.identity_data, updated_at = now();

                INSERT INTO public.profiles (id, full_name, email, role) VALUES (v_extra_admin_id, CASE WHEN m=1 THEN format('Safe Extra Admin %s-01', lpad(g::text,2,'0')) ELSE format('Safe Extra Admin %s-02', lpad(g::text,2,'0')) END, CASE WHEN m=1 THEN format('safe.groupadmin.%s.01@example.com', lpad(g::text,2,'0')) ELSE format('safe.groupadmin.%s.02@example.com', lpad(g::text,2,'0')) END, 'group_admin') ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name, email = EXCLUDED.email, role = 'group_admin';

                UPDATE public.members SET user_id = v_extra_admin_id WHERE id = v_member_id;
            END IF;

            -- Add contributions and payments for coverage
            INSERT INTO public.contributions (id, member_id, group_id, amount, type, due_date, paid_at, payment_method, transaction_id, status, late_fees_applied, created_at)
            VALUES (gen_random_uuid(), v_member_id, v_group_id, v_contribution_amount, 'contribution', current_date - 30, now() - interval '21 days', 'yoco', format('safe_tx_paid_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), 'paid', FALSE, now() - interval '30 days')
            ON CONFLICT (id) DO NOTHING;

            INSERT INTO public.payments (id, member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at, created_at)
            VALUES (gen_random_uuid(), v_member_id, v_group_id, v_contribution_amount, 'contribution', 'yoco', format('safe_pay_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), 'completed', now() - interval '21 days', now() - interval '21 days')
            ON CONFLICT (id) DO NOTHING;
        END LOOP;

        -- All payouts are now escalated to platform admin and must be processed from there (including ROSCA payouts)
        -- Payouts are created with status 'escalated', processed_by and processed_at are NULL
        INSERT INTO public.payouts (
            id, group_id, amount, bank_name, account_no, branch_code, status, processed_by, processed_at, payout_reference, created_at
        ) VALUES (
            gen_random_uuid(),
            v_group_id,
            v_disbursement_amount,
            'Standard Bank',
            '1234500' || lpad(g::text,3,'0'),
            '051001',
            'escalated',
            NULL,
            NULL,
            format('safe_payout_%s', lpad(g::text,2,'0')),
            now() - interval '3 days'
        ) ON CONFLICT (id) DO NOTHING;

        -- No balance update here; payout must be processed by platform admin

        -- Ensure current_members count
        UPDATE public.groups SET current_members = 10 WHERE id = v_group_id;

        -- Seed Actuarial Snapshot
        INSERT INTO public.group_actuarial_metrics (
            group_id, pure_premium, gross_premium, solvency_margin_pct, reserve_adequacy_pct,
            solvency_ratio, capital_adequacy_pct, expected_annual_claims, expected_annual_claims_count
        ) VALUES (
            v_group_id, 180.00, 250.00, 45.00, 110.00,
            1.45, 18.5, 2.4, 2.4
        );

        INSERT INTO public.audit_logs (id, actor_id, target_group_id, action, details, created_at) VALUES (gen_random_uuid(), v_admin_id, v_group_id, 'SAFE_SEED_GROUP_CREATED', jsonb_build_object('seed', true, 'group_number', g, 'members', 10), now()) ON CONFLICT (id) DO NOTHING;
    END LOOP;

    RAISE NOTICE 'SAFE_SEED completed: 10 groups + members + transactions + payouts + ledger entries.';
END$$;

