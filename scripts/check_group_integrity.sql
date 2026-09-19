DO $$
DECLARE
    v_group_id UUID;
    v_member_count INT;
    v_active_member_count INT;
BEGIN
    SELECT id INTO v_group_id FROM public.groups WHERE name = 'SIM_STRESS_TEST_GROUP_PERSIST' LIMIT 1;

    IF v_group_id IS NULL THEN
        RAISE NOTICE 'Group SIM_STRESS_TEST_GROUP_PERSIST not found.';
        RETURN;
    END IF;

    SELECT COUNT(*) INTO v_member_count FROM public.members WHERE group_id = v_group_id;
    SELECT COUNT(*) INTO v_active_member_count FROM public.members WHERE group_id = v_group_id AND status IN ('active', 'probation');

    RAISE NOTICE 'INTEGRITY_CHECK|Group:%|TotalMembers:%|ActiveMembers:%', v_group_id, v_member_count, v_active_member_count;
END;
$$;
