DO $$
DECLARE
    v_uid UUID;
    v_is_admin BOOLEAN;
    v_member_count INT;
BEGIN
    -- Set session variables
    PERFORM set_config('request.jwt.claims', '{"sub": "5e289789-df5f-4e52-942b-5e3790540098", "email": "torrymsimango@gmail.com", "app_metadata": {"role": "platform_admin"}}', true);

    v_uid := auth.uid();
    v_is_admin := public.is_platform_admin();

    SELECT count(*) INTO v_member_count FROM public.members;

    RAISE NOTICE 'Session UID: %', v_uid;
    RAISE NOTICE 'Is Platform Admin: %', v_is_admin;
    RAISE NOTICE 'Visible Members: %', v_member_count;
END $$;
