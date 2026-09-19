-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — MASTER SEED DATA (Consolidated)
-- Version: 1.0 (High-Fidelity Gold Standard - August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    -- Actors
    v_admin_id UUID;
    v_member_id UUID;
    v_group_id UUID;
    v_loan_id UUID;
    v_now TIMESTAMPTZ := NOW();

    -- Config
    v_types TEXT[] := ARRAY['burial_society', 'stokvel', 'rosca', 'investment_club', 'emergency_fund', 'community_savings', 'tontine', 'other'];
    v_providers TEXT[] := ARRAY['sim_bank', 'yoco', 'stitch', 'nedbank', 'fnb', 'sim_bank', 'sim_bank', 'sim_bank'];
    v_type TEXT;
    v_provider_id TEXT;
    v_group_name TEXT;
    v_idx INT;
    v_monthly_contrib NUMERIC := 500.00;
    v_joining_fee NUMERIC := 150.00;

BEGIN
    -- 1. IDENTIFY PLATFORM ADMIN
    SELECT id INTO v_admin_id FROM auth.users WHERE email = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_admin_id IS NULL THEN SELECT id INTO v_admin_id FROM auth.users LIMIT 1; END IF;

    -- 2. CLEANUP
    RAISE NOTICE '🧹 Starting Master Seeding...';
    DELETE FROM public.groups WHERE name LIKE 'MASTER-%' OR name LIKE 'LIGHT-%' OR name LIKE 'SCENARIO-%' OR name LIKE 'SIM_STRESS_TEST_%';

    -- 3. TIER 1: CORE GROUPS (One of each type)
    FOR v_idx IN 1..8 LOOP
        v_type := v_types[v_idx];
        v_provider_id := v_providers[v_idx];
        v_group_name := 'LIGHT-' || initcap(replace(v_type, '_', ' '));

        -- Create Group
        INSERT INTO public.groups (
            name, type, province, city, township, description,
            joining_fee, monthly_contribution, balance,
            admin_user_id, fee_status, registration_paid,
            financial_provider_id, financial_provider_metadata
        ) VALUES (
            v_group_name, v_type, 'Gauteng', 'Johannesburg', 'Soweto', 'High fidelity developer test group.',
            v_joining_fee, v_monthly_contrib, 0.00,
            v_admin_id, 'paid', TRUE,
            v_provider_id, jsonb_build_object('account_holder', v_group_name)
        ) RETURNING id INTO v_group_id;

        -- Admin Member Details (Handled by trigger usually, but we update for testing)
        v_member_id := (SELECT id FROM public.members WHERE group_id = v_group_id AND user_id = v_admin_id);
        IF v_member_id IS NOT NULL THEN
            UPDATE public.members SET bank_name = 'Standard Bank', account_no = '1234567890', branch_code = '051001' WHERE id = v_member_id;
            PERFORM public.record_contribution_v1(v_member_id, v_group_id, v_joining_fee, (v_now - interval '30 days')::date, v_now - interval '30 days', 'paid', 'M-REG-' || v_member_id, 'joining_fee', 'Initial Joining Fee');
            PERFORM public.record_contribution_v1(v_member_id, v_group_id, v_monthly_contrib, (v_now - interval '1 day')::date, v_now, 'paid', 'M-TX-' || v_member_id, 'contribution', 'Monthly Contribution');
        END IF;

        -- Add 3 High-Fidelity Members
        FOR m IN 1..3 LOOP
            INSERT INTO public.members (
                group_id, full_name, email, status, joined_at, member_key, province, city,
                bank_name, account_no, branch_code
            ) VALUES (
                v_group_id, 'Persona Member ' || m || ' (' || v_group_name || ')',
                'persona.' || v_idx || '.' || m || '@master.example.com',
                CASE WHEN m = 1 THEN 'active' WHEN m = 2 THEN 'probation' ELSE 'active' END,
                v_now - interval '45 days', 'P-' || v_group_id || '-' || m,
                'Gauteng', 'Johannesburg', 'FNB', '9876543210', '250655'
            ) RETURNING id INTO v_member_id;

            -- Financials
            PERFORM public.record_contribution_v1(v_member_id, v_group_id, v_joining_fee, (v_now - interval '45 days')::date, v_now - interval '45 days', 'paid', 'P-JOIN-' || v_member_id, 'joining_fee');
            PERFORM public.record_contribution_v1(v_member_id, v_group_id, v_monthly_contrib, (v_now - interval '15 days')::date, v_now - interval '10 days', 'paid', 'P-TX-' || v_member_id, 'contribution');

            -- Documents for Member 1
            IF m = 1 THEN
                INSERT INTO public.member_documents (member_id, group_id, document_type, document_url, status)
                VALUES (v_member_id, v_group_id, 'Identity Document', 'https://example.com/id.pdf', 'pending');
            END IF;

            -- Beneficiaries for Burial Society
            IF v_type = 'burial_society' THEN
                INSERT INTO public.beneficiaries (group_id, member_id, full_name, relationship, date_of_birth)
                VALUES (v_group_id, v_member_id, 'Beneficiary ' || m, 'Spouse', '1990-01-01');
            END IF;
        END LOOP;

        PERFORM public.recalculate_all_group_financials_v1(v_group_id);
    END LOOP;

    -- 4. TIER 2: ADVANCED SCENARIOS
    -- (The Perfect Stokvel)
    INSERT INTO public.groups (name, type, joining_fee, monthly_contribution, admin_user_id, fee_status, registration_paid, description)
    VALUES ('SCENARIO-Perfect Stokvel', 'stokvel', 100.00, 1000.00, v_admin_id, 'paid', TRUE, 'Historical year of perfection.')
    RETURNING id INTO v_group_id;

    FOR i IN 1..5 LOOP
        INSERT INTO public.members (group_id, full_name, email, status, joined_at, member_key)
        VALUES (v_group_id, 'Perfect Investor ' || i, 'perf.' || i || '@scen.com', 'active', v_now - interval '1 year', 'S-PERF-' || i)
        RETURNING id INTO v_member_id;

        FOR m IN 1..12 LOOP
            PERFORM public.record_contribution_v1(v_member_id, v_group_id, 1000.00, (v_now - (m * 30 || ' days')::interval)::date, v_now - (m * 30 || ' days')::interval, 'paid', 'S-TX-' || i || '-' || m, 'contribution');
        END LOOP;
    END LOOP;

    -- Governance for Perfect Stokvel
    DECLARE
        v_poll_id UUID;
    BEGIN
        INSERT INTO public.group_polls (group_id, title, description, status, ends_at)
        VALUES (v_group_id, 'Increase Monthly Contribution?', 'Target R1200 next year?', 'open', v_now + interval '7 days')
        RETURNING id INTO v_poll_id;
        INSERT INTO public.group_poll_options (poll_id, label, position) VALUES (v_poll_id, 'Yes, R1200', 1), (v_poll_id, 'No, keep R1000', 2);
    END;

    PERFORM public.recalculate_all_group_financials_v1(v_group_id);

    RAISE NOTICE '✅ Master Seeding Complete.';
END $$;
