-- -----------------------------------------------------------------------------
-- SanibonaniSave - PLATFORM MEMBER BEHAVIOR INSIGHTS VIEW
-- Aggregates loan request and repayment behavior for platform analytics.
-- -----------------------------------------------------------------------------

DROP VIEW IF EXISTS public.platform_member_behavior_insights_v1;

CREATE VIEW public.platform_member_behavior_insights_v1 AS
WITH loan_rollup AS (
    SELECT
        l.member_id,
        l.group_id,
        COUNT(*)::int AS total_loan_requests,
        COUNT(*) FILTER (WHERE l.status = 'pending')::int AS pending_requests,
        COUNT(*) FILTER (WHERE l.status = 'overdue')::int AS overdue_loans,
        COALESCE(SUM(l.amount), 0)::double precision AS total_requested_amount,
        COALESCE(SUM(GREATEST(l.total_to_repay - l.total_repaid, 0)), 0)::double precision AS outstanding_amount,
        COALESCE(
            AVG(
                CASE WHEN l.status = 'completed' THEN 1.0 ELSE 0.0 END
            ),
            0
        )::double precision AS completion_ratio
    FROM public.loans l
    GROUP BY l.member_id, l.group_id
)
SELECT
    lr.member_id,
    COALESCE(m.full_name, CONCAT('Member ', LEFT(lr.member_id::text, 8))) AS member_name,
    lr.group_id,
    lr.total_loan_requests,
    lr.pending_requests,
    lr.overdue_loans,
    lr.total_requested_amount,
    lr.outstanding_amount,
    lr.completion_ratio,
    CASE
        WHEN lr.overdue_loans > 0 THEN 'High'
        WHEN lr.pending_requests >= 2 OR lr.outstanding_amount > 20000 THEN 'Elevated'
        WHEN lr.completion_ratio >= 0.5 THEN 'Stable'
        ELSE 'Watch'
    END AS risk_band
FROM loan_rollup lr
LEFT JOIN public.members m ON m.id = lr.member_id;

GRANT SELECT ON public.platform_member_behavior_insights_v1 TO authenticated;

