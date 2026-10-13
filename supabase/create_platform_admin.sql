-- =============================================================================
-- create_platform_admin.sql
-- Idempotent script: create or upgrade the platform-admin user.
--
-- BEFORE RUNNING:
--   1. Replace REPLACE_WITH_ADMIN_EMAIL below with the actual admin email.
--   2. Replace REPLACE_WITH_STRONG_PASSWORD below with the desired password.
--      Never commit real credentials to version control.
--
-- HOW TO RUN:
--   Paste this ENTIRE file into the Supabase SQL Editor and click "Run".
--   Do NOT split the DO block from the verification SELECT — run the whole
--   file as one execution unit so that the v_user_id variable stays in scope.
--
-- WHAT IT DOES:
--   1. Creates the auth user (email + bcrypt-hashed password) if it does not
--      already exist, or updates the role metadata if it does.
--   2. Upserts the public.profiles row so getUserRole() resolves correctly
--      regardless of whether it reads user-metadata or the profiles table.
--   3. Prints the resulting user-id via RAISE NOTICE.
--
-- After execution the verification SELECT at the bottom shows the final state.
-- =============================================================================

DO $$
DECLARE
  -- *** Replace these two values before running ***
  v_admin_email    TEXT := 'REPLACE_WITH_ADMIN_EMAIL';
  v_admin_password TEXT := 'REPLACE_WITH_STRONG_PASSWORD';

  v_user_id UUID;
BEGIN

  -- ----------------------------------------------------------------
  -- 0. Guard: abort if placeholder values haven't been replaced
  -- ----------------------------------------------------------------
  IF v_admin_email    = 'REPLACE_WITH_ADMIN_EMAIL'     OR
     v_admin_password = 'REPLACE_WITH_STRONG_PASSWORD' THEN
    RAISE EXCEPTION
      'Placeholder values detected. Replace v_admin_email and v_admin_password before running this script.';
  END IF;

  -- ----------------------------------------------------------------
  -- 1. Look up the user in auth.users
  -- ----------------------------------------------------------------
  SELECT id
    INTO v_user_id
    FROM auth.users
   WHERE email = v_admin_email
   LIMIT 1;

  -- ----------------------------------------------------------------
  -- 2a. User does not exist → create it
  -- ----------------------------------------------------------------
  IF v_user_id IS NULL THEN

    v_user_id := gen_random_uuid();

    INSERT INTO auth.users (
      id,
      instance_id,
      aud,
      role,
      email,
      encrypted_password,
      email_confirmed_at,
      raw_app_meta_data,
      raw_user_meta_data,
      created_at,
      updated_at,
      confirmation_token,
      email_change,
      email_change_token_new,
      recovery_token
    )
    VALUES (
      v_user_id,
      '00000000-0000-0000-0000-000000000000',
      'authenticated',
      'authenticated',
      v_admin_email,
      crypt(v_admin_password, gen_salt('bf')),
      NOW(),
      '{"provider":"email","providers":["email"]}'::jsonb,
      '{"role":"platform_admin"}'::jsonb,
      NOW(),
      NOW(),
      '',
      '',
      '',
      ''
    );

    RAISE NOTICE 'Created new platform-admin user: %', v_user_id;

  -- ----------------------------------------------------------------
  -- 2b. User already exists → patch metadata to include the role
  -- ----------------------------------------------------------------
  ELSE

    UPDATE auth.users
       SET raw_user_meta_data = raw_user_meta_data || '{"role":"platform_admin"}'::jsonb,
           updated_at          = NOW()
     WHERE id = v_user_id;

    RAISE NOTICE 'Updated existing user to platform_admin: %', v_user_id;

  END IF;

  -- ----------------------------------------------------------------
  -- 3. Upsert public.profiles so both lookup paths agree
  --    (getUserRole() checks metadata first, then profiles table)
  -- ----------------------------------------------------------------
  INSERT INTO public.profiles (id, role)
  VALUES (v_user_id, 'platform_admin')
  ON CONFLICT (id)
  DO UPDATE SET role = 'platform_admin';

  RAISE NOTICE 'Platform-admin provisioning complete. User id: %', v_user_id;

END $$;

-- =============================================================================
-- Verification query — runs AFTER the DO block, uses no PL/pgSQL variables.
-- *** ACTION REQUIRED: replace 'REPLACE_WITH_ADMIN_EMAIL' below with the
--     same email address you set in v_admin_email inside the DO block. ***
-- Expected result: meta_role = platform_admin, profile_role = platform_admin.
-- =============================================================================
SELECT
  u.id,
  u.email,
  u.raw_user_meta_data ->> 'role'  AS meta_role,
  p.role                            AS profile_role,
  u.email_confirmed_at              IS NOT NULL AS email_confirmed,
  u.created_at
FROM  auth.users    u
LEFT  JOIN public.profiles p ON p.id = u.id
WHERE u.email = 'REPLACE_WITH_ADMIN_EMAIL'; -- ← update this to match v_admin_email above
