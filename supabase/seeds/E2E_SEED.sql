-- E2E_SEED.sql
-- Extended comprehensive E2E seed. Includes canonical SAFE_SEED and then creates additional heavy data for end-to-end tests.

-- Include SAFE canonical seed first (idempotent)
\i 'supabase/seeds/SAFE_SEED.sql'

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_idx INT;
BEGIN
    -- ensure admin exists
    SELECT id INTO v_admin_id FROM auth.users WHERE lower(email) = lower('torrymsimango@gmail.com') LIMIT 1;
    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'Platform admin missing for E2E_SEED.';
    END IF;

    -- create additional heavy groups for E2E scenarios
    FOR v_idx IN 1..5 LOOP
        INSERT INTO public.groups (id, name, type, admin_user_id, account_number, gateway_public_key, balance, created_at, updated_at)
        VALUES (gen_random_uuid(), format('E2E-G%02s', v_idx), 'other', v_admin_id, '6200888' || lpad(v_idx::text,3,'0'), 'pk_test_e2e_' || v_idx, 10000.00 + (v_idx * 5000), now(), now())
        ON CONFLICT (name) DO NOTHING;

        SELECT id INTO v_group_id FROM public.groups WHERE name = format('E2E-G%02s', v_idx) LIMIT 1;

        -- create 50 members per group to stress test lists and paging
        FOR i IN 1..50 LOOP
            INSERT INTO public.members (id, group_id, full_name, id_number, phone, email, joined_at, member_key)
            VALUES (gen_random_uuid(), v_group_id, format('E2E Member %s-%s', v_idx, i), lpad((9001000000000 + v_idx*100 + i)::text,13,'0'), format('07%s', lpad((v_idx*1000 + i)::text,8,'0')), format('e2e.member.%s.%s@example.com', v_idx, i), now() - ((i%30) || ' days')::interval, gen_random_uuid()::text)
            ON CONFLICT DO NOTHING;
        END LOOP;

        -- seed multiple contributions to create ledger churn
        INSERT INTO public.contributions (id, member_id, group_id, amount, type, due_date, paid_at, payment_method, transaction_id, status, created_at)
        SELECT gen_random_uuid(), id, v_group_id, 200.00 + (random()*100)::numeric(10,2), 'contribution', current_date - (trunc(random()*60))::int, now() - (trunc(random()*30))::int * interval '1 day', 'bank', gen_random_uuid()::text, 'paid', now()
        FROM public.members WHERE group_id = v_group_id LIMIT 200;

        -- record platform fees and platform ledger entries
        INSERT INTO public.platform_fees (id, group_id, fee_type, amount, status, due_date, created_at)
        VALUES (gen_random_uuid(), v_group_id, 'monthly', 10.00 * 50, 'due', to_char(current_date + 5, 'YYYY-MM-DD'), now())
        ON CONFLICT DO NOTHING;

    END LOOP;

    RAISE NOTICE 'E2E_SEED completed: SAFE_SEED + additional heavy groups.';
END$$;

