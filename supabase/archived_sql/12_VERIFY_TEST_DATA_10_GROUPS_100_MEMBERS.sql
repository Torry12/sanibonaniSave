-- Quick verification for 11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql

SELECT 'groups' AS table_name, count(*) AS total
FROM public.groups
WHERE name LIKE 'SEED-G%'
UNION ALL
SELECT 'members', count(*)
FROM public.members
WHERE email LIKE 'seed.member.%@example.com'
UNION ALL
SELECT 'contributions', count(*)
FROM public.contributions c
JOIN public.groups g ON g.id = c.group_id
WHERE g.name LIKE 'SEED-G%'
UNION ALL
SELECT 'payments', count(*)
FROM public.payments p
JOIN public.groups g ON g.id = p.group_id
WHERE g.name LIKE 'SEED-G%'
UNION ALL
SELECT 'loans', count(*)
FROM public.loans l
JOIN public.groups g ON g.id = l.group_id
WHERE g.name LIKE 'SEED-G%'
UNION ALL
SELECT 'beneficiary_claims', count(*)
FROM public.beneficiary_payout_claims bpc
JOIN public.groups g ON g.id = bpc.group_id
WHERE g.name LIKE 'SEED-G%';

-- Optional: see distribution by group type.
SELECT g.type, count(*) AS groups
FROM public.groups g
WHERE g.name LIKE 'SEED-G%'
GROUP BY g.type
ORDER BY groups DESC, g.type;

