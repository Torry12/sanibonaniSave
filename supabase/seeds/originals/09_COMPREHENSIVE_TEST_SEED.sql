-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — COMPREHENSIVE TEST SEED
-- Populates all major tables: groups, members, beneficiaries, contributions,
-- payments, loans, payouts, and burial claims.
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_loan_id UUID;
    v_beneficiary_id UUID;

    v_group_types TEXT[] := ARRAY['stokvel', 'burial_society', 'investment_club'];
    v_names TEXT[] := ARRAY['Thabo', 'Lerato', 'Sipho', 'Nomvula', 'Musa', 'Zanele', 'Jabu', 'Palesa'];
    v_surnames TEXT[] := ARRAY['Mokoena', 'Dlamini', 'Khumalo', 'Ndlovu', 'Smit', 'Botha', 'Gumede', 'Molefe'];

    v_g_type TEXT;
    v_full_name TEXT;
    v_email TEXT;
BEGIN
    -- 1. Find the Platform Admin
    SELECT id INTO v_admin_id FROM auth.users WHERE email = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_admin_id IS NULL THEN
        RAISE NOTICE 'Platform Admin not found. Creating a stub profile for testing logic...';
        -- Note: We can't insert into auth.users easily here due to triggers/hashes,
        -- but we assume the user exists as per previous instructions.
        RETURN;
    END IF;

    -- 2. Create 3 Groups (One of each type)
    FOR i IN 1..3 LOOP
        v_g_type := v_group_types[i];
        INSERT INTO public.groups (
            id, name, type, province, city, township, admin_user_id, balance,
            monthly_contribution, joining_fee, registration_paid
        )
        VALUES (
            gen_random_uuid(),
            'Sample ' || initcap(v_g_type) || ' Group',
            v_g_type, 'Gauteng', 'Johannesburg', 'Soweto',
            v_admin_id, (random()*25000)::numeric,
            500.0, 150.0, true
        )
        RETURNING id INTO v_group_id;

        -- 3. Add group admin as default member, then generate 9 additional members.
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
            'Comprehensive Seed Admin ' || i,
            'comprehensive.seed.admin.' || i || '@example.com',
            '0755500' || lpad(i::text, 3, '0'),
            'active',
            now() - interval '365 days'
        )
        ON CONFLICT (group_id, user_id) DO NOTHING;

        FOR j IN 1..9 LOOP
            v_full_name := v_names[1 + (floor(random()*8))::int] || ' ' || v_surnames[1 + (floor(random()*8))::int];
            v_email := lower(replace(v_full_name, ' ', '.')) || j || '@example.com';

            INSERT INTO public.members (
                id, group_id, full_name, email, phone, status, joined_at, id_number
            )
            VALUES (
                gen_random_uuid(),
                v_group_id,
                v_full_name,
                v_email,
                '0' || (700000000 + floor(random()*99999999))::text,
                'active',
                now() - (random() * interval '365 days'),
                '8001015' || lpad(j::text, 3, '0') || '08' || (j % 10)::text
            )
            RETURNING id INTO v_member_id;

            -- 4. For Burial Societies, create 2 Beneficiaries
            IF v_g_type = 'burial_society' THEN
                FOR b IN 1..2 LOOP
                    INSERT INTO public.beneficiaries (id, group_id, member_id, full_name, relationship, is_over_65)
                    VALUES (gen_random_uuid(), v_group_id, v_member_id, v_full_name || ' relative ' || b, 'Sibling', random() > 0.8)
                    RETURNING id INTO v_beneficiary_id;

                    -- Create an occasional claim
                    IF random() > 0.95 THEN
                        INSERT INTO public.beneficiary_payout_claims (
                            id, group_id, member_id, beneficiary_id, beneficiary_name,
                            cause_of_death, date_of_death, claim_amount, status,
                            bank_name, account_no, branch_code, account_holder
                        )
                        VALUES (
                            gen_random_uuid(), v_group_id, v_member_id, v_beneficiary_id, v_full_name || ' relative ' || b,
                            'Natural Causes', now() - interval '10 days', 10000.0, 'submitted',
                            'FNB', '62000000000', '250655', v_full_name
                        );
                    END IF;
                END LOOP;
            END IF;

            -- 5. Create 6 months of contributions (Mix of Paid and Due)
            FOR k IN 0..5 LOOP
                INSERT INTO public.contributions (
                    group_id, member_id, amount, due_date, status, created_at
                )
                VALUES (
                    v_group_id,
                    v_member_id,
                    500.0,
                    now() - (k * interval '1 month'),
                    CASE WHEN k > 1 THEN 'paid' ELSE 'due' END,
                    now() - (k * interval '1 month')
                );

                -- Create payment record for 'paid' contributions
                IF k > 1 THEN
                    INSERT INTO public.payments (
                        group_id, member_id, amount, payment_type, payment_method, status, created_at
                    )
                    VALUES (
                        v_group_id, v_member_id, 500.0, 'contribution', 'YOCO', 'SUCCESS', now() - (k * interval '1 month')
                    );
                END IF;
            END LOOP;

            -- 6. Create 1 Loan for half the members
            IF random() > 0.5 THEN
                INSERT INTO public.loans (
                    id, group_id, member_id, amount, interest_rate, total_to_repay,
                    total_repaid, monthly_repayment, status, start_date
                )
                VALUES (
                    gen_random_uuid(), v_group_id, v_member_id, 2000.0, 10.0, 2200.0,
                    400.0, 200.0, 'active', now() - interval '2 months'
                )
                RETURNING id INTO v_loan_id;

                -- Add some loan repayments
                INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, payment_method, created_at)
                VALUES (v_loan_id, v_member_id, v_group_id, 200.0, 'yoco', now() - interval '1 month');
                INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, payment_method, created_at)
                VALUES (v_loan_id, v_member_id, v_group_id, 200.0, 'yoco', now());
            END IF;
        END LOOP;

        -- 7. Create 1 Payout per group
        INSERT INTO public.payouts (group_id, amount, bank_name, account_no, branch_code, status, created_at)
        VALUES (v_group_id, 2500.0, 'Standard Bank', '123456789', '000123', 'completed', now() - interval '5 days');

        -- 8. Add some Ledger entries
        INSERT INTO public.group_ledger (id, group_id, amount, balance_after, description, category, created_at)
        VALUES (gen_random_uuid(), v_group_id, -2500.0, 10000.0, 'Monthly Payout', 'withdrawal', now() - interval '5 days');

        UPDATE public.groups
        SET current_members = 10
        WHERE id = v_group_id;

    END LOOP;

    RAISE NOTICE 'Comprehensive seed complete: 3 groups, 30 members, and full history generated.';
END $$;

