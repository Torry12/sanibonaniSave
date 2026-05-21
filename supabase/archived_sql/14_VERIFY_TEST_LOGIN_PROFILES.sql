-- Verification for 13_CREATE_TEST_LOGIN_PROFILES.sql

-- 1) Role coverage in profiles
SELECT role, count(*) AS users
FROM public.profiles
WHERE email IN (
    'torrymsimango@gmail.com',
    'test.groupadmin@example.com'
)
OR email LIKE 'seed.member.%@example.com'
GROUP BY role
ORDER BY role;

-- 2) Group admin assignment target
SELECT g.id, g.name, g.admin_user_id, p.email AS admin_email, p.role AS admin_role
FROM public.groups g
LEFT JOIN public.profiles p ON p.id = g.admin_user_id
WHERE g.name LIKE 'SEED-G%'
ORDER BY g.created_at ASC
LIMIT 1;

-- 3) Member auth links in the target seeded group
WITH first_seed_group AS (
    SELECT id
    FROM public.groups
    WHERE name LIKE 'SEED-G%'
    ORDER BY created_at ASC
    LIMIT 1
)
SELECT m.id, m.full_name, m.email, m.user_id, p.role
FROM public.members m
LEFT JOIN public.profiles p ON p.id = m.user_id
WHERE m.group_id = (SELECT id FROM first_seed_group)
ORDER BY m.created_at ASC
LIMIT 12;

