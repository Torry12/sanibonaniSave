-- -----------------------------------------------------------------------------
-- SanibonaniSave - Verify Full App E2E Seed
-- Run after 25_SEED_FULL_APP_E2E.sql
-- -----------------------------------------------------------------------------

-- 1) Group coverage by type
SELECT
    type,
    COUNT(*) AS groups
FROM public.groups
WHERE name LIKE 'E2E-SEED-%'
GROUP BY type
ORDER BY type;

-- 2) Core table row counts in E2E scope
SELECT 'groups' AS table_name, COUNT(*) AS rows FROM public.groups WHERE name LIKE 'E2E-SEED-%'
UNION ALL
SELECT 'members', COUNT(*) FROM public.members WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
UNION ALL
SELECT 'beneficiaries', COUNT(*) FROM public.beneficiaries WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
UNION ALL
SELECT 'member_documents', COUNT(*) FROM public.member_documents WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
UNION ALL
SELECT 'contributions', COUNT(*) FROM public.contributions WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
UNION ALL
SELECT 'payments', COUNT(*) FROM public.payments WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
UNION ALL
SELECT 'loans', COUNT(*) FROM public.loans WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
UNION ALL
SELECT 'loan_repayments', COUNT(*) FROM public.loan_repayments WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
UNION ALL
SELECT 'payouts', COUNT(*) FROM public.payouts WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
UNION ALL
SELECT 'platform_fees', COUNT(*) FROM public.platform_fees WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
UNION ALL
SELECT 'beneficiary_payout_claims', COUNT(*) FROM public.beneficiary_payout_claims WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
UNION ALL
SELECT 'notifications', COUNT(*) FROM public.notifications WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
UNION ALL
SELECT 'audit_logs', COUNT(*) FROM public.audit_logs WHERE action LIKE 'E2E_SEED_%'
UNION ALL
SELECT 'group_actuarial_metrics', COUNT(*) FROM public.group_actuarial_metrics WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
UNION ALL
SELECT 'group_ledger', COUNT(*) FROM public.group_ledger WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
UNION ALL
SELECT 'platform_ledger_seed_rows', COUNT(*) FROM public.platform_ledger WHERE description LIKE 'E2E seed%';

-- 3) Member status distribution
SELECT
    status,
    COUNT(*) AS members
FROM public.members
WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
GROUP BY status
ORDER BY status;

-- 4) Contribution status/type matrix
SELECT
    status,
    type,
    COUNT(*) AS rows
FROM public.contributions
WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
GROUP BY status, type
ORDER BY status, type;

-- 5) Payment status/type matrix
SELECT
    status,
    payment_type,
    COUNT(*) AS rows
FROM public.payments
WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
GROUP BY status, payment_type
ORDER BY status, payment_type;

-- 6) Loan status distribution
SELECT
    status,
    COUNT(*) AS loans
FROM public.loans
WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
GROUP BY status
ORDER BY status;

-- 7) Payout status distribution
SELECT
    status,
    COUNT(*) AS payouts
FROM public.payouts
WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
GROUP BY status
ORDER BY status;

-- 8) Beneficiary claim status distribution
SELECT
    status,
    COUNT(*) AS claims
FROM public.beneficiary_payout_claims
WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
GROUP BY status
ORDER BY status;

-- 9) Platform fee status distribution
SELECT
    status,
    COUNT(*) AS fees
FROM public.platform_fees
WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
GROUP BY status
ORDER BY status;

-- 10) Group/member consistency check
SELECT
    g.name,
    g.type,
    g.current_members,
    COUNT(m.id) AS actual_members
FROM public.groups g
LEFT JOIN public.members m ON m.group_id = g.id
WHERE g.name LIKE 'E2E-SEED-%'
GROUP BY g.id, g.name, g.type, g.current_members
ORDER BY g.name;

-- 11) Validation alignment checks (latest app rules)
-- Expectation: 0 rows returned from each query.

-- 11a) Group payment due day must be 1..28
SELECT
    g.id,
    g.name,
    g.payment_due_day
FROM public.groups g
WHERE g.name LIKE 'E2E-SEED-%'
  AND (g.payment_due_day < 1 OR g.payment_due_day > 28);

-- 11b) Group bank account format must be 7..11 digits when present
SELECT
    g.id,
    g.name,
    g.account_number
FROM public.groups g
WHERE g.name LIKE 'E2E-SEED-%'
  AND g.account_number IS NOT NULL
  AND g.account_number !~ '^[0-9]{7,13}$';

-- 11c) Group branch code must be 6 digits when present
SELECT
    g.id,
    g.name,
    g.branch_code
FROM public.groups g
WHERE g.name LIKE 'E2E-SEED-%'
  AND g.branch_code IS NOT NULL
  AND g.branch_code !~ '^[0-9]{6}$';

-- 11d) Beneficiary claim account/branch formats
SELECT
    c.id,
    c.group_id,
    c.account_no,
    c.branch_code
FROM public.beneficiary_payout_claims c
WHERE c.group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%')
  AND (
      c.account_no !~ '^[0-9]{7,13}$'
      OR c.branch_code !~ '^[0-9]{6}$'
  );

