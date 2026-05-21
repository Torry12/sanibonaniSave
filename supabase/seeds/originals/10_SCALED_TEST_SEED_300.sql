-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — SCALED TEST SEED (10 Groups, 300 Members)
-- Populates: groups, members, beneficiaries, contributions, payments,
-- loans, payouts, and ledger entries.
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_loan_id UUID;
    v_beneficiary_id UUID;

    v_group_types TEXT[] := ARRAY['stokvel', 'burial_society', 'investment_club', 'rosca'];
    v_provinces TEXT[] := ARRAY['Gauteng', 'KwaZulu-Natal', 'Western Cape', 'Limpopo', 'Eastern Cape'];
    v_names TEXT[] := ARRAY['Thabo', 'Lerato', 'Sipho', 'Nomvula', 'Musa', 'Zanele', 'Jabu', 'Palesa', 'Kabelo', 'Tshepo', 'Bongani', 'Ayanda'];
    v_surnames TEXT[] := ARRAY['Mokoena', 'Dlamini', 'Khumalo', 'Ndlovu', 'Smit', 'Botha', 'Gumede', 'Molefe', 'Zwane', 'Langa', 'Mazibuko'];

    v_g_type TEXT;
    v_full_name TEXT;
    v_email TEXT;
    v_id_num TEXT;
BEGIN
    -- 1. Find the Platform Admin
    SELECT id INTO v_admin_id FROM auth.users WHERE email = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'Platform Admin (torrymsimango@gmail.com) not found in auth.users. Please create the user first.';
    END IF;

    -- 2. Create 10 Groups
    FOR i IN 1..10 LOOP
        v_g_type := v_group_types[1 + (i % 4)];
        INSERT INTO public.groups (
            id, name, type, province, city, township, admin_user_id, balance,
            monthly_contribution, joining_fee, registration_paid, logo_emoji
        )
        VALUES (
            gen_random_uuid(),
            'Community ' || initcap(v_g_type) || ' ' || i,
            v_g_type, v_provinces[1 + (i % 5)], 'City ' || i, 'Township ' || i,
            v_admin_id, (5000 + random()*50000)::numeric,
            500.0, 200.0, true, '🤝'
        )
        RETURNING id INTO v_group_id;

        -- 3. Add group admin as default member, then create 29 additional members (Total 300).
        INSERT INTO public.members (
            id,
            group_id,
            user_id,
            full_name,
            email,
            phone,
            status,
            joined_at
        ) VALUES (
            gen_random_uuid(),
            v_group_id,
            v_admin_id,
            'Scaled Seed Admin ' || i,
            'scaled.seed.admin.' || i || '@example.com',
            '0744400' || lpad(i::text, 3, '0'),
            'active',
            now() - interval '365 days'
        )
        ON CONFLICT (group_id, user_id) DO NOTHING;

        FOR j IN 1..29 LOOP
            v_full_name := v_names[1 + (floor(random()*12))::int] || ' ' || v_surnames[1 + (floor(random()*11))::int];
            -- Use unique email to avoid constraint violations
            v_email := lower(replace(v_full_name, ' ', '.')) || '.' || i || '.' || j || '@example.com';

            -- Generate valid 13-digit ID: YYMMDD S SSS C (simplified for seed)
            -- 800101 (DOB) + index padding (4 digits) + 08 (gender/origin) + checksum (1 digit)
            v_id_num := '800101' || lpad((i*30 + j)::text, 4, '0') || '08' || (j % 10)::text;

            INSERT INTO public.members (
                id, group_id, full_name, email, phone, status, joined_at, id_number
            )
            VALUES (
                gen_random_uuid(),
                v_group_id,
                v_full_name,
                v_email,
                '0' || (710000000 + floor(random()*89999999))::text,
                'active',
                now() - (random() * interval '730 days'),
                v_id_num
            )
            RETURNING id INTO v_member_id;

            -- 4. For Burial Societies, create 2-3 Beneficiaries
            IF v_g_type = 'burial_society' THEN
                FOR b IN 1..(2 + (random()*1)::int) LOOP
                    INSERT INTO public.beneficiaries (id, group_id, member_id, full_name, relationship, is_over_65)
                    VALUES (gen_random_uuid(), v_group_id, v_member_id, v_full_name || ' Relative ' || b, 'Family', (random() > 0.85));
                END LOOP;
            END IF;

            -- 5. Create 12 months of contributions (History)
            FOR k IN 0..11 LOOP
                INSERT INTO public.contributions (
                    group_id, member_id, amount, due_date, status, created_at
                )
                VALUES (
                    v_group_id,
                    v_member_id,
                    500.0,
                    now() - (k * interval '1 month'),
                    CASE WHEN (k > 2 OR random() > 0.3) THEN 'paid' ELSE 'due' END,
                    now() - (k * interval '1 month')
                );

                -- Create payment record only if 'paid' and ensure Uppercase constraints
                IF (k > 2 OR random() > 0.3) THEN
                    INSERT INTO public.payments (
                        group_id, member_id, amount, payment_type, payment_method, status, created_at
                    )
                    VALUES (
                        v_group_id, v_member_id, 500.0, 'contribution', 'yoco', 'completed', now() - (k * interval '1 month')
                    );
                END IF;
            END LOOP;

            -- 6. Create active loans for ~20% of members
            IF random() > 0.8 THEN
                INSERT INTO public.loans (
                    id, group_id, member_id, amount, interest_rate, total_to_repay,
                    total_repaid, monthly_repayment, status, start_date
                )
                VALUES (
                    gen_random_uuid(), v_group_id, v_member_id, 3000.0, 15.0, 3450.0,
                    1150.0, 575.0, 'active', now() - interval '3 months'
                )
                RETURNING id INTO v_loan_id;

                -- Add 2 repayments
                INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, payment_method, created_at)
                VALUES (v_loan_id, v_member_id, v_group_id, 575.0, 'yoco', now() - interval '1 month');
                INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, payment_method, created_at)
                VALUES (v_loan_id, v_member_id, v_group_id, 575.0, 'yoco', now());
            END IF;
        END LOOP;

        -- 7. Add Ledger entries and Payouts to give groups financial history
        FOR p in 1..3 LOOP
            INSERT INTO public.payouts (group_id, amount, bank_name, account_no, branch_code, status, created_at)
            VALUES (v_group_id, 1000.0 * p, 'Standard Bank', '123456789', '000123', 'completed', now() - (p * interval '2 months'));

            INSERT INTO public.group_ledger (id, group_id, amount, balance_after, description, category, created_at)
            VALUES (gen_random_uuid(), v_group_id, -1000.0 * p, 15000.0, 'Periodic Payout ' || p, 'withdrawal', now() - (p * interval '2 months'));
        END LOOP;

        UPDATE public.groups
        SET current_members = 30
        WHERE id = v_group_id;

    END LOOP;

    RAISE NOTICE 'Seed successful: 10 groups, 300 members, and extensive financial history generated.';
END $$;

