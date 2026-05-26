-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — 05. SEED DATA (15 Groups / 150 Members)
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
BEGIN
    -- 1. Platform Settings
    INSERT INTO public.platform_settings (key, value) VALUES
        ('registration_fee', 700.00),
        ('monthly_member_fee', 10.00),
        ('payout_fee', 5.00),
        ('whatsapp_fee', 0.50),
        ('late_fee_percent', 10.00),
        ('auto_suspension_days', 30)
    ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

    -- 2. Core Groups Loop
    FOR g IN 1..15 LOOP
        v_group_name := format('SEED15-G%s Community', lpad(g::text,2,'0'));
        v_group_balance := (1000.00 + (g * 500))::numeric(15,2);

        -- create admin user in auth.users
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

        -- create group
        INSERT INTO public.groups (
            id, name, type, province, city, township, description, logo_emoji, joining_fee, monthly_contribution,
            late_fee, late_fee_grace_days, probation_months, payment_due_day, max_members, is_public, allow_partial_payment,
            auto_suspend_after, bank_name, account_number, branch_code, account_type, gateway_public_key, balance,
            admin_user_id, fee_status, registration_paid, is_platform_suspended, constitution_status, latitude, longitude,
            max_beneficiaries, beneficiary_increase_pct, goal_amount, period_months, loan_interest_rate, loan_max_amount, loan_max_months, created_at, updated_at
        ) VALUES (
            gen_random_uuid(), v_group_name, 'community_savings', 'Gauteng', 'Johannesburg', 'Soweto',
            'SeededIntegrationTestGroup', '🤝', 50.00, 200.00,
            10.00, 5, 3, 28, 120, TRUE, FALSE,
            2, 'FNB', lpad((100000 + g)::text,10,'0'), '000001', 'Savings', 'pk_test_seed_group_' || g, v_group_balance,
            v_admin_id, 'paid', TRUE, FALSE, 'verified', -26.0 + (g * 0.1), 28.0 + (g * 0.1), 5, 5.00, 100000.00, 12, 12.5, 12000.00, 12, now(), now()
        ) ON CONFLICT (name) DO UPDATE SET updated_at = now(), balance = EXCLUDED.balance
        RETURNING id INTO v_group_id;

        -- create admin member
        INSERT INTO public.members (id, group_id, user_id, full_name, phone, email, notification_pref, status, joined_at, probation_end_at, beneficiary_count, total_contributions, total_paid, member_key, created_at, updated_at)
        VALUES (gen_random_uuid(), v_group_id, v_admin_id, format('Seed Group Admin %s', lpad(g::text,2,'0')), format('079%07s', lpad(g::text,7,'0')), format('seed.admin.%s@example.com', lpad(g::text,2,'0')), 'both', 'active', now() - interval '100 days', now() + interval '30 days', 0, 0, 0, format('SEED_ADMIN_KEY_%s', lpad(g::text,2,'0')), now(), now())
        ON CONFLICT (group_id, user_id) DO NOTHING
        RETURNING id INTO v_member_id;

        -- Opening ledger entry
        IF NOT EXISTS (SELECT 1 FROM public.group_ledger WHERE group_id = v_group_id AND description LIKE 'SEED opening balance %') THEN
            PERFORM public.increment_group_balance(v_group_id, v_group_balance);
        END IF;

        -- additional members
        FOR m IN 1..9 LOOP
            v_extra_id := gen_random_uuid();
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

            INSERT INTO public.members (id, group_id, user_id, full_name, phone, email, notification_pref, status, joined_at, probation_end_at, beneficiary_count, total_contributions, total_paid, member_key, created_at, updated_at)
            VALUES (gen_random_uuid(), v_group_id, v_extra_id, format('Seed Member %s-%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), format('071%07s', lpad((g*100 + m)::text,7,'0')), format('seed.member.%s.%s@example.com', lpad(g::text,2,'0'), lpad(m::text,2,'0')), 'both', CASE WHEN m < 8 THEN 'active' ELSE 'probation' END, now() - ((m+g) || ' days')::interval, now() + interval '60 days', 0, 0, 0, format('SEED_KEY_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), now(), now())
            ON CONFLICT (id) DO NOTHING
            RETURNING id INTO v_member_id;

            -- activity
            v_contribution_amount := (100.00 + (g * 5) + m)::numeric(12,2);
            INSERT INTO public.contributions (id, member_id, group_id, amount, type, due_date, paid_at, payment_method, transaction_id, status, created_at)
            VALUES (gen_random_uuid(), v_member_id, v_group_id, v_contribution_amount, 'contribution', now() + interval '30 days', now() + interval '31 days', 'bank', gen_random_uuid()::text, 'paid', now())
            ON CONFLICT (id) DO NOTHING;

            INSERT INTO public.payments (id, member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at, created_at)
            VALUES (gen_random_uuid(), v_member_id, v_group_id, v_contribution_amount, 'contribution', 'bank', gen_random_uuid()::text, 'completed', now(), now())
            ON CONFLICT (id) DO NOTHING;

            PERFORM public.increment_group_balance(v_group_id, v_contribution_amount);
            UPDATE public.members SET total_contributions = COALESCE(total_contributions,0) + 1, total_paid = COALESCE(total_paid,0) + v_contribution_amount WHERE id = v_member_id;
        END LOOP;
    END LOOP;
END
$$ LANGUAGE plpgsql;
