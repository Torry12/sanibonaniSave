DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_success_count INT := 0;
    v_reject_count INT := 0;
    v_bug_count INT := 0;
BEGIN
    RAISE NOTICE 'Starting Registration Simulation...';

    -- Pick an arbitrary auth user as the admin
    SELECT id INTO v_admin_id FROM auth.users LIMIT 1;

    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'No auth users found for simulation.';
    END IF;

    -- Scenario 1: Group with negative joining fee
    BEGIN
        INSERT INTO public.groups (
            name, type, joining_fee, monthly_contribution, admin_user_id
        ) VALUES (
            'Invalid Group Neg Fee', 'stokvel', -100, 50, v_admin_id
        );
        RAISE NOTICE 'BUG|Negative Joining Fee|Allowed negative fee for group creation.';
        v_bug_count := v_bug_count + 1;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'REJECT|Negative Joining Fee|%|%', SQLSTATE, SQLERRM;
        v_reject_count := v_reject_count + 1;
    END;

    -- Scenario 2: Valid Group Creation
    BEGIN
        INSERT INTO public.groups (
            id, name, type, joining_fee, monthly_contribution, admin_user_id,
            province, city, township, latitude, longitude
        ) VALUES (
            gen_random_uuid(), 'Simulated Registration Test Group', 'stokvel', 150, 50, v_admin_id,
            'Gauteng', 'Johannesburg', 'Soweto', -26.2361, 27.8583
        ) RETURNING id INTO v_group_id;
        v_success_count := v_success_count + 1;

        -- Admin becomes member
        INSERT INTO public.members (
            group_id, user_id, full_name, status
        ) VALUES (
            v_group_id, v_admin_id, 'Sim Admin', 'active'
        );
        v_success_count := v_success_count + 1;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'BUG|Valid Group Creation Failed|%|%', SQLSTATE, SQLERRM;
        v_bug_count := v_bug_count + 1;
    END;

    -- Scenario 3: Valid Member Registration
    BEGIN
        FOR i IN 1..5 LOOP
            INSERT INTO public.members (
                group_id, full_name, status, email
            ) VALUES (
                v_group_id, 'Sim User ' || i, 'probation', 'simuser' || i || '@test.com'
            );
            v_success_count := v_success_count + 1;
        END LOOP;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'BUG|Valid Member Registration Failed|%|%', SQLSTATE, SQLERRM;
        v_bug_count := v_bug_count + 1;
    END;

    -- Scenario 4: Member Registration - Invalid Status
    BEGIN
        INSERT INTO public.members (
            group_id, full_name, status
        ) VALUES (
            v_group_id, 'Invalid Status User', 'super_admin'
        );
        RAISE NOTICE 'BUG|Invalid Member Status|Allowed invalid member status ENUM.';
        v_bug_count := v_bug_count + 1;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'REJECT|Invalid Member Status Blocked|%|%', SQLSTATE, SQLERRM;
        v_reject_count := v_reject_count + 1;
    END;

    -- Scenario 5: Duplicate Group Name
    BEGIN
        INSERT INTO public.groups (
            name, type, joining_fee, monthly_contribution, admin_user_id
        ) VALUES (
            'Simulated Registration Test Group', 'stokvel', 150, 50, v_admin_id
        );
        RAISE NOTICE 'BUG|Duplicate Group Name|Allowed duplicate group name.';
        v_bug_count := v_bug_count + 1;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'REJECT|Duplicate Group Name Blocked|%|%', SQLSTATE, SQLERRM;
        v_reject_count := v_reject_count + 1;
    END;

    -- Scenario 6: Member with too short name
    BEGIN
        INSERT INTO public.members (
            group_id, full_name, status
        ) VALUES (
            v_group_id, 'A', 'probation'
        );
        RAISE NOTICE 'BUG|Short Member Name|Allowed short member name.';
        v_bug_count := v_bug_count + 1;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'REJECT|Short Member Name Blocked|%|%', SQLSTATE, SQLERRM;
        v_reject_count := v_reject_count + 1;
    END;

    RAISE NOTICE 'REGISTRATION_SIM_DONE|Success:%|Reject:%|Bug:%', v_success_count, v_reject_count, v_bug_count;
END;
$$;
