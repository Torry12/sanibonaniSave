-- SanibonaniSave — Seed Validation Queries (works with schema v2.2 + reset_with_mock_data.sql)

-- 1) High-level counts
SELECT 'auth.users' AS entity, COUNT(*)::int AS count FROM auth.users
UNION ALL SELECT 'profiles', COUNT(*)::int FROM public.profiles
UNION ALL SELECT 'groups', COUNT(*)::int FROM public.groups
UNION ALL SELECT 'members', COUNT(*)::int FROM public.members
UNION ALL SELECT 'policies', COUNT(*)::int FROM public.policies
UNION ALL SELECT 'beneficiaries', COUNT(*)::int FROM public.beneficiaries
UNION ALL SELECT 'contributions', COUNT(*)::int FROM public.contributions
UNION ALL SELECT 'payments', COUNT(*)::int FROM public.payments
UNION ALL SELECT 'member_documents', COUNT(*)::int FROM public.member_documents
UNION ALL SELECT 'notifications', COUNT(*)::int FROM public.notifications
UNION ALL SELECT 'payouts', COUNT(*)::int FROM public.payouts
UNION ALL SELECT 'platform_fees', COUNT(*)::int FROM public.platform_fees;

-- 2) Scenario distributions
SELECT type, COUNT(*)::int AS groups FROM public.groups GROUP BY type ORDER BY groups DESC;

SELECT status, COUNT(*)::int AS members FROM public.members GROUP BY status ORDER BY members DESC;

SELECT type, status, COUNT(*)::int AS contributions
FROM public.contributions
GROUP BY type, status
ORDER BY type, status;

SELECT payment_type, status, COUNT(*)::int AS payments
FROM public.payments
GROUP BY payment_type, status
ORDER BY payment_type, status;

SELECT status, COUNT(*)::int AS payouts
FROM public.payouts
GROUP BY status
ORDER BY payouts DESC;

SELECT status, COUNT(*)::int AS platform_fees
FROM public.platform_fees
GROUP BY status
ORDER BY platform_fees DESC;

-- 3) Confirm platform admin exists
SELECT id, email, role, full_name
FROM public.profiles
WHERE role = 'platform_admin';

-- 4) Multi-group membership spot checks
-- member1@test.com should appear in multiple groups
SELECT g.name, g.type, m.status
FROM public.members m
JOIN public.groups g ON g.id = m.group_id
WHERE m.user_id = 'b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1'
ORDER BY g.created_at;

-- 5) Find overdue accounts
SELECT m.full_name, g.name AS group_name, COUNT(*)::int AS overdue_items
FROM public.contributions c
JOIN public.members m ON m.id = c.member_id
JOIN public.groups g ON g.id = c.group_id
WHERE c.status = 'overdue'
GROUP BY m.full_name, g.name
ORDER BY overdue_items DESC
LIMIT 50;

