-- Verification queries for 17_SEED_DEBUG_LOGIC_SCENARIOS.sql

-- 1) Group footprint
SELECT
    name,
    type,
    province,
    fee_status,
    is_platform_suspended,
    balance
FROM public.groups
WHERE name LIKE 'DBG-%'
ORDER BY name;

-- 2) Member distribution by group + status
SELECT
    g.name AS group_name,
    m.status,
    COUNT(*) AS members
FROM public.members m
JOIN public.groups g ON g.id = m.group_id
WHERE g.name LIKE 'DBG-%'
GROUP BY g.name, m.status
ORDER BY g.name, m.status;

-- 3) Loan status matrix per debug group
SELECT
    g.name AS group_name,
    l.status,
    COUNT(*) AS loans,
    ROUND(COALESCE(SUM(l.amount), 0), 2) AS total_amount,
    ROUND(COALESCE(SUM(GREATEST(l.total_to_repay - l.total_repaid, 0)), 0), 2) AS outstanding_amount
FROM public.loans l
JOIN public.groups g ON g.id = l.group_id
WHERE g.name LIKE 'DBG-%'
GROUP BY g.name, l.status
ORDER BY g.name, l.status;

-- 4) Escalation workloads (payouts + burial claims)
SELECT
    g.name AS group_name,
    p.status AS payout_status,
    COUNT(*) AS payout_count
FROM public.payouts p
JOIN public.groups g ON g.id = p.group_id
WHERE g.name LIKE 'DBG-%'
GROUP BY g.name, p.status
ORDER BY g.name, p.status;

SELECT
    g.name AS group_name,
    c.status AS claim_status,
    COUNT(*) AS claim_count
FROM public.beneficiary_payout_claims c
JOIN public.groups g ON g.id = c.group_id
WHERE g.name LIKE 'DBG-%'
GROUP BY g.name, c.status
ORDER BY g.name, c.status;

-- 5) Programmatic traceability markers
SELECT
    action,
    created_at,
    details
FROM public.audit_logs
WHERE action LIKE 'DBG_%'
ORDER BY created_at DESC
LIMIT 20;

-- 6) Server-backed behavior insights (if 16 script was applied)
SELECT
    g.name AS group_name,
    i.member_name,
    i.total_loan_requests,
    i.pending_requests,
    i.overdue_loans,
    i.outstanding_amount,
    i.risk_band
FROM public.platform_member_behavior_insights_v1 i
JOIN public.groups g ON g.id = i.group_id
WHERE g.name LIKE 'DBG-%'
ORDER BY
    CASE i.risk_band WHEN 'High' THEN 1 WHEN 'Elevated' THEN 2 WHEN 'Watch' THEN 3 ELSE 4 END,
    i.outstanding_amount DESC,
    i.member_name;

