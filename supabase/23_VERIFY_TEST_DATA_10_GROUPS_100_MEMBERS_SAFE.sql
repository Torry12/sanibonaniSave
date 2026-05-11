-- Verification for 22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql

-- 1) Group count
SELECT count(*) AS groups_count
FROM public.groups
WHERE name LIKE 'SEED100-G%';

-- 2) Member count
SELECT count(*) AS members_count
FROM public.members
WHERE email LIKE 'seed100.member.%@example.com';

-- 3) Per-group member distribution
SELECT g.name AS group_name, count(m.id) AS members
FROM public.groups g
LEFT JOIN public.members m ON m.group_id = g.id
WHERE g.name LIKE 'SEED100-G%'
GROUP BY g.name
ORDER BY g.name;

-- 4) Member status matrix
SELECT m.status, count(*) AS members
FROM public.members m
WHERE m.email LIKE 'seed100.member.%@example.com'
GROUP BY m.status
ORDER BY m.status;

-- 5) Seed marker logs
SELECT action, count(*) AS rows
FROM public.audit_logs
WHERE action = 'SEED100_GROUP_CREATED'
GROUP BY action;

