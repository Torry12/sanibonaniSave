DO $$
DECLARE
    v_gid UUID;
    v_mid UUID;
    v_count INT;
BEGIN
    -- Setup
    INSERT INTO public.groups (name, admin_user_id) VALUES ('TEST-CONSOLIDATE', gen_random_uuid()) RETURNING id INTO v_gid;
    INSERT INTO public.members (group_id, full_name) VALUES (v_gid, 'Tester') RETURNING id INTO v_mid;

    -- Insert a platform fee contribution
    INSERT INTO public.contributions (group_id, member_id, amount, type, due_date, status)
    VALUES (v_gid, v_mid, 10.00, 'member_fee_ledger', CURRENT_DATE, 'paid');

    -- Consolidate
    PERFORM public.consolidate_group_ledger_v1(v_gid);

    -- Check
    SELECT count(*) INTO v_count FROM public.group_ledger WHERE group_id = v_gid;

    RAISE NOTICE 'Ledger Count (should be 0): %', v_count;

    -- Cleanup
    DELETE FROM public.groups WHERE id = v_gid;
END $$;
