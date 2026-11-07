-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — 12-MONTH TRANSACTION & LEDGER SEED DATA (Support & Sim Flows)
-- Version: 2.3 (October 2026)
-- Self-contained except for pg_temp.seed_post_contribution, declared in 10_SEED_HELPERS.sql.
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_now TIMESTAMPTZ := NOW();
    v_amount NUMERIC := 500.00;
    v_h INT;
    v_count INT;
    v_support_group_id UUID;
    v_support_member_id UUID;
    v_support_user_id UUID;
    v_qa_password TEXT := 'Pass123!';
    v_r_group RECORD;
    v_r_member RECORD;
BEGIN
    RAISE NOTICE 'Seeding 12 months of transaction data for all groups and support/sim-flow sessions...';

    -- 1. Explicitly ensure Support Mode simulation group (SIM-FLOWS-466557) exists with 12 months of transactions
    IF NOT EXISTS (SELECT 1 FROM public.groups WHERE name LIKE 'SIM-FLOWS-%') THEN
        v_support_user_id := gen_random_uuid();

        INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
        VALUES (v_support_user_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'sim.flow4@master.example.com', extensions.crypt(v_qa_password, extensions.gen_salt('bf')), now(), '{"provider":"email","providers":["email"],"role":"member"}', '{"full_name":"Sim Flow Member 4","role":"member"}', now(), now())
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO public.profiles (id, full_name, email, role)
        VALUES (v_support_user_id, 'Sim Flow Member 4', 'sim.flow4@master.example.com', 'member')
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO public.groups (
            name, type, province, city, township, description,
            joining_fee, monthly_contribution, balance, admin_user_id, fee_status, registration_paid
        ) VALUES (
            'SIM-FLOWS-466557', 'stokvel', 'Gauteng', 'Johannesburg', 'Soweto', 'Support Mode Simulation Group',
            150.00, 500.00, 0.00, v_support_user_id, 'paid', TRUE
        ) RETURNING id INTO v_support_group_id;

        INSERT INTO public.members (
            group_id, user_id, full_name, email, status, joined_at, member_key
        ) VALUES (
            v_support_group_id, v_support_user_id, 'Sim Flow Member 4', 'sim.flow4@master.example.com', 'active', now() - interval '1 year', 'SIM-MEMBER-4'
        )
        ON CONFLICT (group_id, user_id) DO NOTHING
        RETURNING id INTO v_support_member_id;

        IF v_support_member_id IS NULL THEN
            SELECT id INTO v_support_member_id FROM public.members WHERE group_id = v_support_group_id LIMIT 1;
        END IF;

        FOR v_h IN 1..12 LOOP
            PERFORM pg_temp.seed_post_contribution(
                v_support_member_id, v_support_group_id, v_amount,
                (v_now - (v_h * 30 || ' days')::interval)::date,
                v_now - (v_h * 30 || ' days')::interval,
                'paid', 'SIM-TX-' || v_support_member_id || '-' || v_h, 'contribution',
                'Sim Flow Member 4', 'Monthly Contribution - Month ' || (13 - v_h)
            );
        END LOOP;
    END IF;

    -- 2. Fill only this seed pack's demo groups; never add demo transactions
    -- to customer groups in a shared or production database.
    FOR v_r_group IN
        SELECT id, name FROM public.groups
        WHERE name LIKE 'LIGHT-%' OR name LIKE 'SEED-%' OR name LIKE 'SIM-FLOWS-%'
        ORDER BY name
    LOOP
        SELECT COUNT(*) INTO v_count FROM public.group_ledger WHERE group_id = v_r_group.id;

        IF v_count < 12 THEN
            FOR v_r_member IN SELECT id, full_name FROM public.members WHERE group_id = v_r_group.id AND status = 'active' LOOP
                FOR v_h IN 1..12 LOOP
                    PERFORM pg_temp.seed_post_contribution(
                        v_r_member.id, v_r_group.id, v_amount,
                        (v_now - (v_h * 30 || ' days')::interval)::date,
                        v_now - (v_h * 30 || ' days')::interval,
                        'paid', 'TX-12M-' || v_r_member.id || '-' || v_h, 'contribution',
                        v_r_member.full_name, 'Monthly Contribution - Month ' || (13 - v_h)
                    );
                END LOOP;
            END LOOP;

            -- Recalculate group balance
            BEGIN
                PERFORM public.recalculate_all_group_financials_v3(v_r_group.id);
            EXCEPTION WHEN OTHERS THEN
                NULL;
            END;
        END IF;
    END LOOP;

    RAISE NOTICE '12-month transaction and ledger seeding complete.';
END $$;
