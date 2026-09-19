DO $$
DECLARE
    v_email text := 'torrymsimango@gmail.com';
    v_password text := 'torry123M';
    v_full_name text := 'Torry Msimango';
    v_user_id uuid := gen_random_uuid();
BEGIN
    INSERT INTO auth.users (
        id, instance_id, aud, role, email, encrypted_password, confirmed_at,
        raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
        confirmation_token, recovery_token, email_change
    )
    VALUES (
        v_user_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', v_email, extensions.crypt(v_password, extensions.gen_salt('bf', 10)), now(),
        '{"provider":"email","providers":["email"],"role":"platform_admin"}', jsonb_build_object('full_name', v_full_name, 'role', 'platform_admin'),
        now(), now(), '', '', ''
    );

    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (v_user_id, v_full_name, v_email, 'platform_admin');
END $$;
