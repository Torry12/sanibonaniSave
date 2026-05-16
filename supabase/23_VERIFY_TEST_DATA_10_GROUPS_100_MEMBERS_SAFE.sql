-- Verification for 22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql

-- 1) Group count
SELECT count(*) AS groups_count
FROM public.groups
WHERE name LIKE 'SEED100-G%';

-- 2) Member count
SELECT count(*) AS members_count
FROM public.members m
JOIN public.groups g ON g.id = m.group_id
WHERE g.name LIKE 'SEED100-G%';

-- 2b) Every group must keep its primary admin as a member
SELECT count(*) AS groups_with_primary_admin_member
FROM public.groups g
WHERE g.name LIKE 'SEED100-G%'
  AND EXISTS (
	  SELECT 1
	  FROM public.members m
	  WHERE m.group_id = g.id
		AND m.user_id = g.admin_user_id
  );

-- 3) Per-group member distribution
SELECT g.name AS group_name, count(m.id) AS members
FROM public.groups g
LEFT JOIN public.members m ON m.group_id = g.id
WHERE g.name LIKE 'SEED100-G%'
GROUP BY g.name
ORDER BY g.name;

-- 3b) Extra group admins must also be members in the same group
SELECT g.name AS group_name, count(*) AS extra_group_admin_members
FROM public.groups g
JOIN public.members m ON m.group_id = g.id
JOIN public.profiles p ON p.id = m.user_id
WHERE g.name LIKE 'SEED100-G%'
  AND p.role = 'group_admin'
GROUP BY g.name
ORDER BY g.name;

-- 4) Member status matrix
SELECT m.status, count(*) AS members
FROM public.members m
JOIN public.groups g ON g.id = m.group_id
WHERE g.name LIKE 'SEED100-G%'
GROUP BY m.status
ORDER BY m.status;

-- 5) Mock disbursements by status
SELECT p.status, count(*) AS payouts
FROM public.payouts p
JOIN public.groups g ON g.id = p.group_id
WHERE g.name LIKE 'SEED100-G%'
GROUP BY p.status
ORDER BY p.status;

-- 6) Group ledger coverage
SELECT g.name AS group_name, count(gl.id) AS ledger_rows
FROM public.groups g
LEFT JOIN public.group_ledger gl ON gl.group_id = g.id
WHERE g.name LIKE 'SEED100-G%'
GROUP BY g.name
ORDER BY g.name;

-- 7) Member transaction coverage
SELECT
	count(*) FILTER (WHERE t = 'contribution') AS contributions,
	count(*) FILTER (WHERE t = 'payment') AS payments
FROM (
	SELECT 'contribution'::text AS t
	FROM public.contributions c
	JOIN public.groups g ON g.id = c.group_id
	WHERE g.name LIKE 'SEED100-G%'
	UNION ALL
	SELECT 'payment'::text AS t
	FROM public.payments p
	JOIN public.groups g ON g.id = p.group_id
	WHERE g.name LIKE 'SEED100-G%'
) x;

-- 8) Seed marker logs
SELECT action, count(*) AS rows
FROM public.audit_logs
WHERE action = 'SEED100_GROUP_CREATED'
GROUP BY action;

