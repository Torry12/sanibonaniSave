-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — TEST DATA SEED (10 Groups / 100 Members)
-- Version: 6.1 (Money-Precise, Hybrid Co-op Inclusive - August 2026)
-- Self-contained except for pg_temp.seed_post_contribution, declared in 10_SEED_HELPERS.sql.
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    -- Constants
    v_qa_password TEXT := 'Pass123!';
    -- This fixture is NOT left on the shared QA password: it carries role='platform_admin', so
    -- is_platform_admin() answers for it and it can impersonate every group. Its credential is
    -- supplied out-of-band in the same transaction before this file runs:
    --     BEGIN;
    --     SET LOCAL sanibonani.seed_platform_admin_password = '<password>';
    --     ... run this file ...
    --     COMMIT;
    -- Unset behaviour mirrors 04_PLATFORM_ADMIN_SETUP.sql: an existing fixture keeps its current
    -- password (this file only ever creates the row, it never re-hashes one), and a fixture created
    -- here gets a NULL encrypted_password plus a NOTICE, so no rebuild can restore a published
    -- platform-admin credential.
    v_seed_admin_password TEXT := nullif(btrim(current_setting('sanibonani.seed_platform_admin_password', true)), '');
    v_now TIMESTAMPTZ := NOW();

    -- Arrays for randomization (includes Hybrid Savings Group)
    v_types TEXT ARRAY := ARRAY['burial_society', 'stokvel', 'rosca', 'investment_club', 'emergency_fund', 'community_savings', 'hybrid_savings_group', 'stokvel', 'burial_society', 'rosca'];
    v_group_names TEXT ARRAY := ARRAY['Eternal Peace Burial', 'Unity Savings Stokvel', 'Morning Star ROSCA', 'Blue Chip Investors', 'Crisis Response Fund', 'Neighborhood Savings', 'Ubuntu Hybrid Co-op', 'Friends of Soweto', 'Zion Funeral Society', 'Quick Turn ROSCA'];
    v_provinces TEXT ARRAY := ARRAY['Gauteng', 'Western Cape', 'KwaZulu-Natal', 'Eastern Cape', 'Limpopo', 'Free State', 'Mpumalanga', 'North West', 'Gauteng', 'Western Cape'];
    v_cities TEXT ARRAY := ARRAY['Johannesburg', 'Cape Town', 'Durban', 'Gqeberha', 'Polokwane', 'Bloemfontein', 'Nelspruit', 'Rustenburg', 'Pretoria', 'Stellenbosch'];
    v_banks TEXT ARRAY := ARRAY['First National Bank', 'Standard Bank', 'Absa Bank', 'Nedbank', 'Capitec Bank'];
    v_branch_codes TEXT ARRAY := ARRAY['250655', '051001', '632005', '198765', '470010'];

    -- Loop Variables
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_loan_id UUID;
    v_user_id UUID;
    v_type TEXT;
    v_group_name TEXT;
    v_monthly_contrib_amount NUMERIC(12,2) := 500.00;   -- Money-precise (2-dp scale); matches Money domain precision
    v_joining_fee_amount NUMERIC(12,2) := 150.00;       -- Money-precise (2-dp scale); matches Money domain precision
    v_email TEXT;
    v_full_name TEXT;
    v_status TEXT;

BEGIN
    -- 1. IDENTIFY OR CREATE PLATFORM ADMIN
    SELECT id INTO v_admin_id FROM auth.users WHERE email = 'platform.admin@master.example.com' LIMIT 1;
    IF v_admin_id IS NULL THEN
        v_admin_id := gen_random_uuid();
        INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
        VALUES (v_admin_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'platform.admin@master.example.com', CASE WHEN v_seed_admin_password IS NULL THEN NULL ELSE extensions.crypt(v_seed_admin_password, extensions.gen_salt('bf', 10)) END, now(), '{"provider":"email","providers":["email"],"role":"platform_admin"}', '{"full_name":"Seed Platform Admin","role":"platform_admin"}', now(), now());

        INSERT INTO public.profiles (id, full_name, email, role)
        VALUES (v_admin_id, 'Seed Platform Admin', 'platform.admin@master.example.com', 'platform_admin');

        IF v_seed_admin_password IS NULL THEN
            RAISE NOTICE 'sanibonani.seed_platform_admin_password is not set: the seeded platform_admin fixture was created with no usable password. Set it with supabase/maintenance/ROTATE_PLATFORM_ADMIN_PASSWORD_20261018.sql (target platform.admin@master.example.com) or re-seed with the setting.';
        END IF;
    END IF;

    -- 2. CLEANUP (Targeted)
    RAISE NOTICE 'Seeding Fully Integrated Test Data (10 Groups / 100 Members with Hybrid Co-op)...';
    -- platform_ledger.group_id is ON DELETE SET NULL, so these groups' registration receipts would
    -- outlive them and stack a second revenue history on every re-run. Same rule as MASTER: a
    -- receipt with no contribution row behind it is one of ours.
    DELETE FROM public.platform_ledger pl
    WHERE pl.group_id IN (
            SELECT id FROM public.groups
            WHERE name = ANY (ARRAY[
                'SEED-Eternal Peace Burial', 'SEED-Unity Savings Stokvel', 'SEED-Morning Star ROSCA',
                'SEED-Blue Chip Investors', 'SEED-Crisis Response Fund', 'SEED-Neighborhood Savings',
                'SEED-Ubuntu Hybrid Co-op', 'SEED-Friends of Soweto', 'SEED-Zion Funeral Society',
                'SEED-Quick Turn ROSCA'
            ])
          )
       OR (pl.group_id IS NULL
           AND pl.category = 'registration_contribution'
           AND NOT EXISTS (
               SELECT 1 FROM public.contributions c WHERE c.id::text = pl.transaction_id
           ));

    DELETE FROM public.groups
    WHERE name = ANY (ARRAY[
        'SEED-Eternal Peace Burial', 'SEED-Unity Savings Stokvel', 'SEED-Morning Star ROSCA',
        'SEED-Blue Chip Investors', 'SEED-Crisis Response Fund', 'SEED-Neighborhood Savings',
        'SEED-Ubuntu Hybrid Co-op', 'SEED-Friends of Soweto', 'SEED-Zion Funeral Society',
        'SEED-Quick Turn ROSCA'
    ]);
    DELETE FROM auth.users WHERE email LIKE 'seed.member.%@example.com';

    -- 3. GENERATE 10 GROUPS
    FOR i IN 1..10 LOOP
        v_type := v_types[i];
        v_group_name := 'SEED-' || v_group_names[i];
        -- Investment Club gets R1000 monthly, all others R500
        IF v_type = 'investment_club' THEN
            v_monthly_contrib_amount := 1000.00;
        ELSE
            v_monthly_contrib_amount := 500.00;
        END IF;

        INSERT INTO public.groups (
            name, type, province, city, joining_fee, monthly_contribution,
            admin_user_id, fee_status, registration_paid, balance,
            bank_name, account_number, branch_code, created_at
        ) VALUES (
            v_group_name, v_type, v_provinces[i], v_cities[i], v_joining_fee_amount, v_monthly_contrib_amount,
            v_admin_id, 'paid', TRUE, 0.00,
            v_banks[(i % 5) + 1], '62' || (10000000 + i * 12345)::text, v_branch_codes[(i % 5) + 1],
            v_now - interval '12 months'
        ) RETURNING id INTO v_group_id;

        -- Admin is automatically added as member by trigger.
        UPDATE public.members
        SET joined_at = v_now - interval '12 months',
            status = 'active',
            bank_name = 'First National Bank',
            account_no = '62889900112',
            branch_code = '250655'
        WHERE group_id = v_group_id AND user_id = v_admin_id;

        -- 4. GENERATE 9 MEMBERS PER GROUP (Total 100 with admin)
        FOR m IN 1..9 LOOP
            v_email := 'seed.member.' || i || '.' || m || '@example.com';
            v_full_name := 'Seed Member ' || i || '-' || m;
            v_user_id := gen_random_uuid();
            v_status := 'active';

            -- Auth & Profile
            INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
            VALUES (v_user_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', v_email, extensions.crypt(v_qa_password, extensions.gen_salt('bf')), now(), '{"provider":"email","providers":["email"],"role":"member"}', jsonb_build_object('full_name', v_full_name, 'role', 'member'), now(), now());

            INSERT INTO public.profiles (id, full_name, email, role) VALUES (v_user_id, v_full_name, v_email, 'member');

            -- Member Record with Banking Details
            INSERT INTO public.members (
                group_id, user_id, full_name, email, status, joined_at,
                rosca_position, province, city,
                bank_name, account_no, branch_code
            ) VALUES (
                v_group_id, v_user_id, v_full_name, v_email, v_status, v_now - interval '12 months',
                CASE WHEN v_type = 'rosca' THEN m + 1 ELSE NULL END,
                v_provinces[i], v_cities[i],
                v_banks[(m % 5) + 1], '10' || (20000000 + i * 1000 + m * 321)::text, v_branch_codes[(m % 5) + 1]
            ) RETURNING id INTO v_member_id;

            INSERT INTO public.member_behavior_track (member_id, group_id, joined_at, last_activity_at)
            VALUES (v_member_id, v_group_id, v_now - interval '12 months', v_now)
            ON CONFLICT (member_id) DO NOTHING;

            -- Pay Joining Fee
            PERFORM pg_temp.seed_post_contribution(
                v_member_id, v_group_id, v_joining_fee_amount,
                (v_now - interval '12 months')::date, v_now - interval '12 months',
                'paid', 'SEED-JOIN-' || v_member_id, 'registration_contribution', v_full_name
            );

            -- 12 Months History (100% paid up)
            FOR h IN 1..12 LOOP
                PERFORM pg_temp.seed_post_contribution(
                    v_member_id, v_group_id, v_monthly_contrib_amount,
                    (v_now - (h * 30 || ' days')::interval)::date,
                    v_now - (h * 30 || ' days')::interval,
                    'paid', 'SEED-HIST-' || v_member_id || '-' || h, 'contribution', v_full_name
                );
            END LOOP;

            -- Beneficiary Seeding (Burial Society only)
            IF v_type = 'burial_society' THEN
                INSERT INTO public.beneficiaries (group_id, member_id, full_name, is_over_65, relationship)
                VALUES (v_group_id, v_member_id, 'Beneficiary of ' || v_full_name, (m % 3 = 0), 'Relative');

                UPDATE public.members
                SET beneficiary_count = 1,
                    beneficiary_over_65_count = CASE WHEN m % 3 = 0 THEN 1 ELSE 0 END
                WHERE id = v_member_id;
            END IF;

            -- Integrated Loan Seeding (stokvel, emergency_fund, hybrid_savings_group)
            IF m = 1 AND v_type IN ('stokvel', 'emergency_fund', 'hybrid_savings_group') THEN
                INSERT INTO public.loans (
                    member_id, group_id, amount, interest_rate, total_to_repay, total_repaid,
                    monthly_repayment, start_date, end_date, status, purpose, amortization_type
                ) VALUES (
                    v_member_id, v_group_id, 2000.00, 20.00, 2415.00, 805.00, 402.50,
                    (v_now - interval '3 months')::date, (v_now + interval '3 months')::date, 'active', 'Integrated Seed Loan', 'flat'
                ) RETURNING id INTO v_loan_id;

                -- 'active' is a loan the group has already paid out, so the debit is booked with it.
                PERFORM pg_temp.seed_post_loan_disbursement(
                    v_loan_id, v_group_id, v_member_id, 2000.00, v_full_name,
                    v_now - interval '3 months'
                );
            END IF;

            -- Integrated Burial Claim Seeding
            IF v_type = 'burial_society' AND m = 2 THEN
                INSERT INTO public.beneficiary_payout_claims (
                    group_id, member_id, beneficiary_id, beneficiary_name, claim_amount,
                    cause_of_death, date_of_death, status, bank_name, account_no, branch_code, account_holder
                ) VALUES (
                    v_group_id, v_member_id, gen_random_uuid(), 'Dependent of ' || v_full_name, 5000.00,
                    'Natural', (v_now - interval '5 days')::date, 'submitted', v_banks[(m % 5) + 1], '62' || (30000000 + m)::text, v_branch_codes[(m % 5) + 1], v_full_name
                );
            END IF;
        END LOOP;

        -- Emergency Fund Hardening
        IF v_type = 'emergency_fund' THEN
            -- Keyed by group: this is capital no source table describes, so the id is the only thing
            -- that keeps a second run of this module from booking the same R500 twice.
            PERFORM public.increment_group_balance(
                v_group_id, 
                v_monthly_contrib_amount, 
                'Seed Liquidity - Reserve Fund', 
                'adjustment',
                'SEED-LIQUIDITY-' || v_group_id
            );
        END IF;

        -- Investment Club & Hybrid Hardening
        IF v_type IN ('investment_club', 'hybrid_savings_group') THEN
            PERFORM public.increment_group_balance(
                v_group_id, 
                v_monthly_contrib_amount, 
                'Seed Gains - Market Performance', 
                'investment_gain',
                'SEED-GAIN-' || v_group_id
            );
        END IF;

        PERFORM public.recalculate_all_group_financials_v3(v_group_id);
    END LOOP;

    RAISE NOTICE 'Fully Updated Master Seeding with Hybrid Co-op Complete.';
END $$;