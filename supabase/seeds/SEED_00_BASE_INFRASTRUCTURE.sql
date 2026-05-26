-- SEED_00_BASE_INFRASTRUCTURE.sql
-- Foundational seed for platform admins and global settings.
-- Idempotent script.

DO $$
DECLARE
    v_admin_id UUID := '00000000-0000-0000-0000-000000000001'; -- Fixed UUID for platform admin
    v_admin_email TEXT := 'platform.admin@sanibonani.com';
    v_admin_password TEXT := 'Admin@12345';
BEGIN
    -- 1. Ensure Platform Admin exists in auth.users
    IF NOT EXISTS (SELECT 1 FROM auth.users WHERE id = v_admin_id) THEN
        INSERT INTO auth.users (id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
        VALUES (
            v_admin_id,
            'authenticated',
            'authenticated',
            v_admin_email,
            crypt(v_admin_password, gen_salt('bf')),
            now(),
            '{"provider":"email","providers":["email"],"role":"platform_admin"}'::jsonb,
            '{"full_name": "Platform Administrator", "role": "platform_admin"}'::jsonb,
            now(),
            now()
        );

        INSERT INTO auth.identities (id, user_id, provider, provider_id, identity_data, created_at, updated_at)
        VALUES (
            gen_random_uuid(),
            v_admin_id,
            'email',
            v_admin_email,
            jsonb_build_object('sub', v_admin_id::text, 'email', v_admin_email),
            now(),
            now()
        );
    END IF;

    -- 2. Ensure Profile exists
    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (v_admin_id, 'Platform Administrator', v_admin_email, 'platform_admin')
    ON CONFLICT (id) DO UPDATE SET role = 'platform_admin';

    -- 3. Base Platform Settings
    INSERT INTO public.platform_settings (key, value) VALUES
    ('monthly_per_member', 10.0),
    ('registration_fee', 700.0),
    ('late_fee_standard', 50.0)
    ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

    RAISE NOTICE 'Base infrastructure seeded successfully.';
END $$;
