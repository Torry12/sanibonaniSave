DO $$
DECLARE
    v_group_id UUID;
    v_member_id UUID;
    v_admin_id UUID;
BEGIN
    SELECT id INTO v_admin_id FROM auth.users WHERE email = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_admin_id IS NULL THEN SELECT id INTO v_admin_id FROM auth.users LIMIT 1; END IF;

    INSERT INTO public.groups (name, type, province, city, admin_user_id, registration_paid)
    VALUES ('REMOTE-TRIGGER-TEST-' || floor(random()*1000000), 'stokvel', 'Gauteng', 'Pretoria', v_admin_id, TRUE)
    RETURNING id INTO v_group_id;

    INSERT INTO public.members (group_id, full_name, status)
    VALUES (v_group_id, 'Remote Test Member', 'active')
    RETURNING id INTO v_member_id;

    -- Record contribution via master function
    PERFORM public.record_contribution_v1(v_member_id, v_group_id, 1000.00, NOW()::date, NOW(), 'paid', 'rem-test-tx-' || v_group_id, 'contribution');

    -- Verify balance update
    IF (SELECT balance FROM public.groups WHERE id = v_group_id) = 1000.00 THEN
        RAISE NOTICE 'REMOTE_SYNC_SUCCESS: Triggers firing correctly on Online database.';
    ELSE
        RAISE EXCEPTION 'REMOTE_SYNC_FAILURE: Balance mismatch on Online database. (Found: %)', (SELECT balance FROM public.groups WHERE id = v_group_id);
    END IF;

    -- Cleanup
    -- DELETE FROM public.groups WHERE id = v_group_id;
END $$;
