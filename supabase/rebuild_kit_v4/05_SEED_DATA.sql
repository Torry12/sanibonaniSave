-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — 05. SEED DATA (10 Groups / 100 Members)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    g INT;
    m INT;
    v_group_id UUID;
    v_admin_id UUID;
    v_member_id UUID;
    v_extra_id UUID;
    v_group_name TEXT;
    v_group_balance NUMERIC(15,2);
    v_contribution_amount NUMERIC(12,2);
    v_payment_id UUID;
    v_poll_id UUID;
    v_loan_id UUID;
    v_member_acc TEXT;
    v_group_acc TEXT;
    -- Location arrays for group seeding
    provinces TEXT[] := ARRAY['Gauteng','Western Cape','KwaZulu-Natal','Eastern Cape','Free State','North West','Limpopo','Mpumalanga','Northern Cape','Gauteng'];
    cities TEXT[] := ARRAY['Johannesburg','Cape Town','Durban','Port Elizabeth','Bloemfontein','Mahikeng','Polokwane','Nelspruit','Kimberley','Pretoria'];
    townships TEXT[] := ARRAY['Soweto','Khayelitsha','Umlazi','Zwide','Botshabelo','Mmabatho','Seshego','KwaMhlanga','Galeshewe','Mamelodi'];
    base_lat FLOAT8 := -26.0;
    base_lon FLOAT8 := 28.0;
BEGIN
    -- 0. Virtual Bank Platform Setup
    INSERT INTO public.virtual_bank_accounts (account_number, account_holder_name, bank_name, branch_code, balance)
    VALUES
        ('SANIBONANI_PLATFORM_MASTER', 'Sanibonani Platform Master', 'Reserve Bank', '999999', 10000000.00),
        ('SANIBONANI_PLATFORM_FEES', 'Sanibonani Platform Fees', 'Reserve Bank', '999999', 0.00)
    ON CONFLICT (account_number) DO UPDATE SET balance = EXCLUDED.balance;

    -- 1. Platform Settings
    INSERT INTO public.platform_settings (key, value) VALUES
        ('registration_fee', 700.00),
        ('monthly_member_fee', 10.00),
        ('payout_fee', 5.00),
        ('whatsapp_fee', 0.50),
        ('late_fee_percent', 10.00),
        ('auto_suspension_days', 30)
    ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

    -- 2. Core Groups Loop (10 Groups)
    FOR g IN 1..10 LOOP
        v_group_name := format('SEED10-G%s Community', lpad(g::text,2,'0'));
        v_group_balance := (1000.00 + (g * 500))::numeric(15,2);
        v_group_acc := lpad((100000 + g)::text,10,'0');

        -- create admin user
        v_admin_id := gen_random_uuid();
        IF NOT EXISTS (SELECT 1 FROM auth.users WHERE email = format('seed.admin.%s@example.com', lpad(g::text,2,'0'))) THEN
            INSERT INTO auth.users (id, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
            VALUES (
                v_admin_id,
                format('seed.admin.%s@example.com', lpad(g::text,2,'0')),
                crypt('SeedAdmin@1234', gen_salt('bf')),
                now(),
                '{"provider":"email","providers":["email"],"role":"group_admin"}'::jsonb,
                ('{"full_name":"' || format('Seed Admin %s', lpad(g::text,2,'0')) || '","role":"group_admin"}')::jsonb,
                now(),
                now()
            );
        ELSE
            SELECT id INTO v_admin_id FROM auth.users WHERE email = format('seed.admin.%s@example.com', lpad(g::text,2,'0'));
        END IF;

        -- create admin profile
        INSERT INTO public.profiles (id, full_name, email, role, created_at, updated_at)
        VALUES (v_admin_id, format('Seed Admin %s', lpad(g::text,2,'0')), format('seed.admin.%s@example.com', lpad(g::text,2,'0')), 'group_admin', now(), now())
        ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name, email = EXCLUDED.email, role = EXCLUDED.role, updated_at = now();

        -- create group (Trigger will automatically create admin member)
        INSERT INTO public.groups (
            id, name, type, province, city, township, description, logo_emoji, joining_fee, monthly_contribution,
            late_fee, late_fee_grace_days, probation_months, payment_due_day, max_members, is_public, allow_partial_payment,
            auto_suspend_after, bank_name, account_number, branch_code, account_type, balance,
            admin_user_id, fee_status, registration_paid, is_platform_suspended, constitution_status, latitude, longitude,
            max_beneficiaries, beneficiary_increase_pct, goal_amount, period_months, loan_interest_rate, loan_max_amount, loan_max_months, created_at, updated_at
        ) VALUES (
            gen_random_uuid(), v_group_name, 'community_savings',
            provinces[g], cities[g], townships[g],
            'SeededIntegrationTestGroup', '🤝', 50.00, 200.00,
            10.00, 5, 3, 28, 120, TRUE, FALSE,
            2, 'FNB', v_group_acc, '000001', 'Savings', v_group_balance,
            v_admin_id, 'paid', TRUE, FALSE, 'verified',
            base_lat + (random() * 6.0 - 3.0),
            base_lon + (random() * 6.0 - 3.0),
            5, 5.00, 100000.00, 12, 12.5, 12000.00, 12, now(), now()
        ) ON CONFLICT (name) DO UPDATE SET updated_at = now(), balance = EXCLUDED.balance
        RETURNING id INTO v_group_id;

        -- Create virtual bank account for the group
        INSERT INTO public.virtual_bank_accounts (account_number, account_holder_name, bank_name, branch_code, balance)
        VALUES (v_group_acc, v_group_name, 'FNB', '000001', v_group_balance)
        ON CONFLICT (account_number) DO UPDATE SET balance = EXCLUDED.balance;

        -- Update the automatically created admin member with more details
        UPDATE public.members
        SET
            phone = format('079%07s', lpad(g::text,7,'0')),
            bank_name = 'FNB',
            account_number = lpad((300000 + g)::text,10,'0'),
            branch_code = '000001',
            joined_at = now() - interval '100 days',
            probation_end_at = now() + interval '30 days',
            member_key = format('SEED_ADMIN_KEY_%s', lpad(g::text,2,'0'))
        WHERE group_id = v_group_id AND user_id = v_admin_id
        RETURNING id INTO v_member_id;

        -- Virtual bank for admin
        INSERT INTO public.virtual_bank_accounts (account_number, account_holder_name, bank_name, branch_code, balance)
        VALUES (lpad((300000 + g)::text,10,'0'), format('Seed Admin %s', lpad(g::text,2,'0')), 'FNB', '000001', 15000.00)
        ON CONFLICT (account_number) DO NOTHING;

        -- Opening ledger entry
        IF NOT EXISTS (SELECT 1 FROM public.group_ledger WHERE group_id = v_group_id AND description LIKE 'SEED opening balance %') THEN
            PERFORM public.increment_group_balance(v_group_id, v_group_balance, 'Initial Seed Balance', 'opening_balance');
        END IF;

        -- additional members (9 per group, total 10 per group including admin)
        FOR m IN 1..9 LOOP
            v_extra_id := gen_random_uuid();
            v_member_acc := lpad((200000 + (g*100) + m)::text,10,'0');

            IF NOT EXISTS (SELECT 1 FROM auth.users WHERE email = format('seed.member.%s.%s@example.com', lpad(g::text,2,'0'), lpad(m::text,2,'0'))) THEN
                INSERT INTO auth.users (id, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
                VALUES (
                    v_extra_id,
                    format('seed.member.%s.%s@example.com', lpad(g::text,2,'0'), lpad(m::text,2,'0')),
                    crypt('SeedMember@1234', gen_salt('bf')),
                    now(),
                    '{"provider":"email","providers":["email"],"role":"member"}'::jsonb,
                    ('{"full_name":"' || format('Seed Member %s-%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')) || '","role":"member"}')::jsonb,
                    now(),
                    now()
                );
            ELSE
                SELECT id INTO v_extra_id FROM auth.users WHERE email = format('seed.member.%s.%s@example.com', lpad(g::text,2,'0'), lpad(m::text,2,'0'));
            END IF;

            INSERT INTO public.profiles (id, full_name, email, role, created_at, updated_at)
            VALUES (v_extra_id, format('Seed Member %s-%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), format('seed.member.%s.%s@example.com', lpad(g::text,2,'0'), lpad(m::text,2,'0')), 'member', now(), now())
            ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name, email = EXCLUDED.email, updated_at = now();

            INSERT INTO public.members (id, group_id, user_id, full_name, phone, email, notification_pref, status, joined_at, probation_end_at, beneficiary_count, total_contributions, total_paid, member_key, bank_name, account_number, branch_code, created_at, updated_at)
            VALUES (gen_random_uuid(), v_group_id, v_extra_id, format('Seed Member %s-%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), format('071%07s', lpad((g*100 + m)::text,7,'0')), format('seed.member.%s.%s@example.com', lpad(g::text,2,'0'), lpad(m::text,2,'0')), 'both', CASE WHEN m < 8 THEN 'active' ELSE 'probation' END, now() - ((m+g) || ' days')::interval, now() + interval '60 days', 0, 0, 0, format('SEED_KEY_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), 'Standard Bank', v_member_acc, '123456', now(), now())
            ON CONFLICT (group_id, user_id) DO NOTHING
            RETURNING id INTO v_member_id;

            -- Create virtual bank account for the member
            INSERT INTO public.virtual_bank_accounts (account_number, account_holder_name, bank_name, branch_code, balance)
            VALUES (v_member_acc, format('Seed Member %s-%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), 'Standard Bank', '123456', 5000.00)
            ON CONFLICT (account_number) DO NOTHING;

            IF v_member_id IS NOT NULL THEN
                v_contribution_amount := (100.00 + (g * 5) + m)::numeric(12,2);
                -- Use the atomic integrated RPC
                PERFORM public.record_contribution_v1(v_member_id, v_group_id, v_contribution_amount, (now() + interval '30 days')::date, now(), 'paid', gen_random_uuid()::text, 'contribution');

                -- Add beneficiary
                INSERT INTO public.beneficiaries (id, group_id, member_id, full_name, id_number, relationship, date_of_birth, is_over_65, document_status, created_at, updated_at)
                VALUES (gen_random_uuid(), v_group_id, v_member_id, format('SEED10_BENEFICIARY_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), lpad((9900000000000 + (g * 100) + m)::text,13,'0'), 'child', '2010-01-01', FALSE, 'verified', now() - interval '10 days', now())
                ON CONFLICT (id) DO NOTHING;
            END IF;
        END LOOP;

        -- Record platform fee payment for group
        INSERT INTO public.platform_fees (id, group_id, fee_type, amount, status, due_date, paid_at, transaction_id, created_at, updated_at)
        VALUES (gen_random_uuid(), v_group_id, 'registration', 700.00, 'paid', (now() - interval '100 days')::date, now() - interval '99 days', format('SEED10_FEE_%s', lpad(g::text,2,'0')), now() - interval '100 days', now() - interval '99 days');

        -- Move money from group to platform bank for fee
        PERFORM public.virtual_bank_transfer(v_group_acc, 'SANIBONANI_PLATFORM_FEES', 700.00, 'Registration Fee: ' || v_group_name);
        PERFORM public.record_platform_ledger_entry(700.00, 'Registration Fee from ' || v_group_name, 'registration_fee');
    END LOOP;
END;
$$;
