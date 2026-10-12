-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — MASTER SEED DATA (Consolidated)
-- Version: 3.0 (Money-Precise, Ledger-Safe Demo Environment - October 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    -- Actors
    v_admin_id UUID;
    v_member_id UUID;
    v_admin_member_id UUID;
    v_user_id UUID;
    v_group_id UUID;
    v_loan_id UUID;
    v_beneficiary_id UUID;
    v_poll_id UUID;
    v_now TIMESTAMPTZ := NOW();

    -- Development/demo credentials only
    v_qa_password TEXT := 'Pass123!';

    -- Config using Money-precise amounts (2 decimal scale via toPlainString)
    v_types TEXT ARRAY := ARRAY['burial_society', 'stokvel', 'rosca', 'investment_club', 'emergency_fund', 'community_savings', 'hybrid_savings_group', 'other'];
    v_providers TEXT ARRAY := ARRAY['sim_bank', 'yoco', 'stitch', 'nedbank', 'fnb', 'sim_bank', 'sim_bank', 'sim_bank'];
    v_provinces TEXT ARRAY := ARRAY['Gauteng', 'Western Cape', 'KwaZulu-Natal', 'Eastern Cape', 'Limpopo', 'Free State', 'Mpumalanga', 'North West'];
    v_cities TEXT ARRAY := ARRAY['Johannesburg', 'Cape Town', 'Durban', 'Gqeberha', 'Polokwane', 'Bloemfontein', 'Nelspruit', 'Rustenburg'];
    v_townships TEXT ARRAY := ARRAY['Soweto', 'Khayelitsha', 'Umlazi', 'Mdantsane', 'Seshego', 'Mangaung', 'Kanyamazane', 'Tlhabane'];
    v_lats FLOAT8 ARRAY := ARRAY[-26.2041, -33.9249, -29.8587, -33.9608, -23.8962, -29.0852, -25.4753, -25.8560];
    v_lons FLOAT8 ARRAY := ARRAY[28.0473, 18.4241, 31.0218, 25.6022, 29.4486, 26.1596, 30.9694, 25.6403];

    v_type TEXT;
    v_provider_id TEXT;
    v_group_name TEXT;
    v_email TEXT;
    v_full_name TEXT;
    v_idx INT;
    v_lat FLOAT8;
    v_lon FLOAT8;
    v_monthly_contrib_amount NUMERIC(12,2) := 500.00;  -- Money-precise (2-dp scale); matches Money domain precision
    v_joining_fee_amount NUMERIC(12,2) := 150.00;       -- Money-precise (2-dp scale); matches Money domain precision

    v_opt1_id UUID;
    v_opt2_id UUID;

BEGIN
    -- 1. IDENTIFY OR CREATE PLATFORM ADMIN
    SELECT id INTO v_admin_id FROM auth.users WHERE lower(email) = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_admin_id IS NULL THEN
        v_admin_id := gen_random_uuid();
        INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
        VALUES (v_admin_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'torrymsimango@gmail.com', extensions.crypt('torry123M', extensions.gen_salt('bf')), now(), '{"provider":"email","providers":["email"],"role":"platform_admin"}', '{"full_name":"Torry Msimango","role":"platform_admin"}', now(), now());
    ELSE
        UPDATE auth.users
        SET encrypted_password = extensions.crypt('torry123M', extensions.gen_salt('bf')),
            raw_app_meta_data = '{"provider":"email","providers":["email"],"role":"platform_admin"}'::jsonb
        WHERE id = v_admin_id;
    END IF;

    INSERT INTO auth.identities (id, user_id, provider, provider_id, identity_data, created_at, updated_at)
    VALUES (gen_random_uuid(), v_admin_id, 'email', 'torrymsimango@gmail.com', jsonb_build_object('sub', v_admin_id, 'email', 'torrymsimango@gmail.com'), now(), now())
    ON CONFLICT (provider, provider_id) DO UPDATE SET user_id = EXCLUDED.user_id, identity_data = EXCLUDED.identity_data;

    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (v_admin_id, 'Torry Msimango', 'torrymsimango@gmail.com', 'platform_admin')
    ON CONFLICT (id) DO UPDATE SET role = 'platform_admin', full_name = 'Torry Msimango', email = 'torrymsimango@gmail.com';

    -- 2. CLEANUP (remove only rows owned by this seed; preserve the shared admin)
    RAISE NOTICE 'Starting Master Seeding Version 3.0...';
    DELETE FROM public.groups
    WHERE name = ANY (ARRAY[
        'LIGHT-Burial Society', 'LIGHT-Stokvel', 'LIGHT-Rosca', 'LIGHT-Investment Club',
        'LIGHT-Emergency Fund', 'LIGHT-Community Savings', 'LIGHT-Hybrid Savings Group',
        'LIGHT-Other'
    ]);
    DELETE FROM public.members
    WHERE email LIKE 'persona.%@master.example.com';
    DELETE FROM auth.users
    WHERE email LIKE 'persona.%@master.example.com'
      AND NOT EXISTS (SELECT 1 FROM public.groups g WHERE g.admin_user_id = auth.users.id);

    -- 3. TIER 1: CORE GROUPS (8 Types) with Money-precise financial seeding
    FOR v_idx IN 1..8 LOOP
        v_type := v_types[v_idx];
        v_provider_id := v_providers[v_idx];
        v_lat := v_lats[v_idx];
        v_lon := v_lons[v_idx];
        v_group_name := 'LIGHT-' || initcap(replace(v_type, '_', ' '));

        -- Create Group (Triggers handle metadata)
        INSERT INTO public.groups (
            name, type, province, city, township, description,
            joining_fee, monthly_contribution, balance,
            admin_user_id, fee_status, registration_paid,
            financial_provider_id, latitude, longitude
        ) VALUES (
            v_group_name, v_type, v_provinces[v_idx], v_cities[v_idx], v_townships[v_idx], 'Authoritative group for ' || v_type || ' testing.',
            v_joining_fee_amount, v_monthly_contrib_amount, 0.00,
            v_admin_id, 'paid', TRUE,
            v_provider_id, v_lat, v_lon
        ) RETURNING id INTO v_group_id;

        -- Update Admin Member (Torry)
        v_member_id := (SELECT id FROM public.members WHERE group_id = v_group_id AND user_id = v_admin_id LIMIT 1);
        UPDATE public.members
        SET full_name = 'Seed Admin (' || initcap(v_type) || ')',
            email = 'platform.admin@master.example.com',
            bank_name = 'Standard Bank',
            account_no = '1234567890',
            branch_code = '051001'
        WHERE id = v_member_id;
        v_admin_member_id := v_member_id;

        -- 🛡️ Admin Financials - use record_contribution_v2 with Money-precise amounts
        PERFORM public.record_contribution_v2(
            p_member_id => v_member_id,
            p_group_id => v_group_id,
            p_amount => v_monthly_contrib_amount,      -- Money-precise NUMERIC
            p_due_date => v_now::date,
            p_paid_at => v_now,
            p_status => 'paid',
            p_tx_id => 'REG-ADMIN-' || v_group_id,
            p_type => 'registration_contribution'
        );

        -- Add 3 Persona Members
        FOR m IN 1..3 LOOP
            v_email := 'persona.' || v_idx || '.' || m || '@master.example.com';
            v_full_name := 'Member ' || m || ' (' || initcap(v_type) || ')';
            v_user_id := gen_random_uuid();

            -- Create Auth & Profile
            INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
            VALUES (v_user_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', v_email, extensions.crypt(v_qa_password, extensions.gen_salt('bf')), now(), '{"provider":"email","providers":["email"],"role":"member"}', jsonb_build_object('full_name', v_full_name, 'role', 'member'), now(), now());

            INSERT INTO public.profiles (id, full_name, email, role) VALUES (v_user_id, v_full_name, v_email, 'member');

            -- Register Member
            INSERT INTO public.members (
                group_id, user_id, full_name, email, status, joined_at, member_key,
                bank_name, account_no, branch_code, province, city, rosca_position
            ) VALUES (
                v_group_id, v_user_id, v_full_name, v_email,
                'active',
                v_now - interval '12 months', 'K-' || v_group_id || '-' || m,
                'FNB', '9876543210', '250655', v_provinces[v_idx], v_cities[v_idx],
                CASE WHEN v_type = 'rosca' THEN m + 1 ELSE NULL END
            ) RETURNING id INTO v_member_id;

            INSERT INTO public.member_behavior_track (member_id, group_id, joined_at, last_activity_at)
            VALUES (v_member_id, v_group_id, v_now - interval '12 months', v_now)
            ON CONFLICT (member_id) DO NOTHING;

            -- 🛡️ Record Joining Fee (Principal) - using Money format
            PERFORM public.record_contribution_v2(
                p_member_id => v_member_id,
                p_group_id => v_group_id,
                p_amount => v_joining_fee_amount,        -- Money-precise NUMERIC
                p_due_date => (v_now - interval '12 months')::date,
                p_paid_at => v_now - interval '12 months',
                p_status => 'paid',
                p_tx_id => 'JOIN-' || v_member_id,
                p_type => 'registration_contribution'
            );

            -- Record 12 months of paid contributions
            FOR h IN 1..12 LOOP
                PERFORM public.record_contribution_v2(
                    p_member_id => v_member_id,
                    p_group_id => v_group_id,
                    p_amount => v_monthly_contrib_amount,  -- Money-precise NUMERIC
                    p_due_date => (v_now - (h * 30 || ' days')::interval)::date,
                    p_paid_at => v_now - (h * 30 || ' days')::interval,
                    p_status => 'paid',
                    p_tx_id => 'HIST-' || v_member_id || '-' || h,
                    p_type => 'contribution'
                );
            END LOOP;

            -- 📈 Profit Injection (Investment Club only)
            IF v_type = 'investment_club' AND m = 1 THEN
                PERFORM public.increment_group_balance(
                    v_group_id, 
                    v_monthly_contrib_amount,              -- Money-precise NUMERIC
                    'Market Gains - Institutional Dividends', 
                    'investment_gain', 
                    'MASTER-GAIN-' || v_group_id,
                    v_member_id
                );
            END IF;

            -- 🛡️ Section 29: Audit Reports
            -- These are dynamic, no need to seed table, but we ensure enough data exists.

            -- 👵 Beneficiary Seeding (Burial Society only)
            IF v_type = 'burial_society' THEN
                INSERT INTO public.beneficiaries (group_id, member_id, full_name, is_over_65, relationship)
                VALUES (v_group_id, v_member_id, 'Relative of ' || v_full_name, (m = 2), 'Parent');

                -- Update member beneficiary count
                UPDATE public.members
                SET beneficiary_count = 1,
                    beneficiary_over_65_count = CASE WHEN m = 2 THEN 1 ELSE 0 END
                WHERE id = v_member_id;
            END IF;

            -- Governance Seeding (Only for first group)
            IF v_idx = 1 AND m = 1 THEN
                INSERT INTO public.group_polls (id, group_id, created_by_member_id, title, description, type, status, ends_at)
                VALUES (gen_random_uuid(), v_group_id, v_member_id, 'Increase Monthly Contribution?', 'Proposal to increase from R500 to R600 to build larger reserves.', 'financial', 'open', v_now + interval '7 days')
                RETURNING id INTO v_poll_id;

                INSERT INTO public.group_poll_options (id, poll_id, label, position) VALUES
                (gen_random_uuid(), v_poll_id, 'Yes, R600 is fair', 1)
                RETURNING id INTO v_opt1_id;

                INSERT INTO public.group_poll_options (id, poll_id, label, position) VALUES
                (gen_random_uuid(), v_poll_id, 'No, keep it at R500', 2);
            END IF;

            -- Vote Casting (m=2 votes on m=1's poll)
            IF v_idx = 1 AND m = 2 AND v_poll_id IS NOT NULL THEN
                INSERT INTO public.group_poll_votes (poll_id, option_id, member_id, group_id)
                VALUES (v_poll_id, v_opt1_id, v_member_id, v_group_id);
            END IF;

            -- Fraud Seeding (m=3 in group 4 - Investment Club)
            IF v_idx = 4 AND m = 3 THEN
                INSERT INTO public.fraud_detection_events (member_id, group_id, event_type, severity, details)
                VALUES (v_member_id, v_group_id, 'VELOCITY_CHECK_FAILED', 'HIGH', '{"reason": "Multiple large transactions within 1 hour", "amount": 5000.00}');

                UPDATE public.member_behavior_track
                SET suspicious_activity_count = 1, unusual_payment_patterns = TRUE, behavior_score = 45.0, fraud_risk_level = 'high', is_flagged_for_review = TRUE
                WHERE member_id = v_member_id;
            END IF;

            -- Escalated Claim Seeding (Burial Society)
            IF v_type = 'burial_society' AND m = 1 THEN
                SELECT id INTO v_beneficiary_id
                FROM public.beneficiaries
                WHERE member_id = v_member_id
                ORDER BY created_at DESC
                LIMIT 1;

                INSERT INTO public.beneficiary_payout_claims (
                    group_id, member_id, beneficiary_id, beneficiary_name, claim_amount,
                    cause_of_death, date_of_death, status, bank_name, account_no, branch_code, account_holder
                ) VALUES (
                    v_group_id, v_member_id, v_beneficiary_id, 'Relative of ' || v_full_name, 15000.00,
                    'Natural', (v_now - interval '10 days')::date, 'escalated', 'SimBank', '123', '250', 'M'
                );
            END IF;

        END LOOP;

        PERFORM public.recalculate_all_group_financials_v3(v_group_id);
    END LOOP;

    -- 4. LOAN SEEDING (Emergency Fund) - using increment_group_balance with Money format
    SELECT id INTO v_group_id FROM public.groups WHERE name = 'LIGHT-Emergency Fund';
    SELECT id INTO v_member_id FROM public.members WHERE group_id = v_group_id AND status = 'active' AND user_id IS NOT NULL ORDER BY created_at LIMIT 1;

    IF v_group_id IS NOT NULL AND v_member_id IS NOT NULL THEN
        -- Inject seed liquidity first
        PERFORM public.increment_group_balance(
            v_group_id, 
            v_monthly_contrib_amount, 
            'Seed Liquidity - Reserve Fund', 
            'adjustment',
            'SCEN-EMERGENCY-SEED', 
            v_member_id
        );

        INSERT INTO public.loans (
            member_id, group_id, amount, interest_rate, total_to_repay, total_repaid,
            monthly_repayment, start_date, end_date, status, purpose
        ) VALUES (
            v_member_id, v_group_id, 2000.00, 10.00, 2200.00, 500.00, 550.00,
            (v_now - interval '1 month')::date, (v_now + interval '3 months')::date, 'active', 'Medical Expense'
        ) RETURNING id INTO v_loan_id;

        PERFORM public.record_loan_repayment_v1(
            v_loan_id,
            v_member_id, v_group_id, 500.00, 'bank'
        );
    END IF;

    RAISE NOTICE 'Master Seeding 3.0 complete: 8 core groups, 24 persona members, governance, claims, loan, and fraud demo data generated with Money-precise financial operations.';
END $$;