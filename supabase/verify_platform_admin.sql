-- Verify platform admin auth + profile alignment
-- Usage: run after create_platform_admin.sql

SELECT
    u.id,
    u.email,
    u.email_confirmed_at,
    u.raw_user_meta_data ->> 'role' AS auth_role,
    u.raw_user_meta_data ->> 'full_name' AS auth_full_name
FROM auth.users u
WHERE u.email = 'torryymsimango@gmail.com';

SELECT
    p.id,
    p.email,
    p.full_name,
    p.role,
    p.updated_at
FROM public.profiles p
WHERE p.email = 'torryymsimango@gmail.com';

-- Quick consistency check
SELECT
    u.email,
    (u.raw_user_meta_data ->> 'role') AS auth_role,
    p.role AS profile_role,
    CASE
        WHEN (u.raw_user_meta_data ->> 'role') = 'platform_admin' AND p.role = 'platform_admin' THEN 'OK'
        ELSE 'MISMATCH'
    END AS role_consistency
FROM auth.users u
LEFT JOIN public.profiles p ON p.id = u.id
WHERE u.email = 'torryymsimango@gmail.com';

