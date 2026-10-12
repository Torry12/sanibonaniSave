-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — PERFORMANCE & ANALYTICS VIEWS
-- Version: 1.1 (Security Definer Update - July 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. PLATFORM SUMMARY STATS (Landing Screen Support)
-- Using a SECURITY DEFINER function to allow 'anon' users to see platform-wide
-- aggregate stats without granting them SELECT access to the full members/ledger tables.
CREATE OR REPLACE FUNCTION public.get_platform_summary_stats_v1()
RETURNS TABLE (
    total_groups INT,
    total_members INT,
    total_balance NUMERIC(15,2),
    total_provinces INT,
    platform_revenue NUMERIC(15,2),
    average_risk_score FLOAT
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    RETURN QUERY
    SELECT
        (SELECT COUNT(id) FROM public.groups)::INT,
        (SELECT COUNT(id) FROM public.members)::INT,
        (SELECT COALESCE(SUM(balance), 0) FROM public.groups)::NUMERIC(15,2),
        (SELECT COUNT(DISTINCT province) FROM public.groups WHERE province IS NOT NULL AND province != '')::INT,
        (SELECT COALESCE(SUM(amount), 0) FROM public.platform_ledger)::NUMERIC(15,2),
        (SELECT COALESCE(AVG(composite_risk_score), 0) FROM public.group_actuarial_metrics)::FLOAT;
END;
$$;

DROP VIEW IF EXISTS public.platform_summary_stats;
CREATE OR REPLACE VIEW public.platform_summary_stats AS
SELECT * FROM public.get_platform_summary_stats_v1();

GRANT SELECT ON public.platform_summary_stats TO authenticated, anon;

-- 2. COMBINED AUDIT TRAIL
/**
 * View: Combined Audit Trail.
 * Shows both group-level entries and relevant platform-level entries (like fees).
 */
CREATE OR REPLACE VIEW public.vw_consolidated_audit_trail AS
SELECT
    id, group_id, member_id, transaction_id,
    amount, balance_after, description, category,
    created_at, FALSE as is_platform
FROM public.group_ledger
UNION ALL
SELECT
    id, group_id, NULL as member_id, transaction_id,
    amount, balance_after, description, category,
    created_at, TRUE as is_platform
FROM public.platform_ledger
WHERE group_id IS NOT NULL;

-- 3. MEMBER STANDING
/**
 * View: Member Financial Standing.
 * Aggregates core principal and loan totals for quick reference.
 */
CREATE OR REPLACE VIEW public.vw_member_standing AS
SELECT
    m.id as member_id,
    m.group_id,
    m.full_name,
    m.status,
    m.total_paid as principal_paid,
    (SELECT COALESCE(SUM(l.amount), 0) FROM public.loans l WHERE l.member_id = m.id AND l.status IN ('active', 'partially_paid', 'overdue')) as active_loan_principal,
    (SELECT COALESCE(SUM(l2.total_repaid), 0) FROM public.loans l2 WHERE l2.member_id = m.id) as total_repaid
FROM public.members m;

GRANT SELECT ON public.vw_consolidated_audit_trail TO authenticated;
GRANT SELECT ON public.vw_member_standing TO authenticated;

-- 4. LEDGER INTEGRITY CHECK (Admin Diagnostic)
/**
 * View: Ledger Integrity Check.
 * Flags groups with financial drift or broken ledger chains.
 */
CREATE OR REPLACE VIEW public.vw_ledger_integrity_check AS
WITH group_audit AS (
    SELECT
        id as group_id,
        name as group_name,
        balance as table_balance,
        (SELECT COALESCE(SUM(amount), 0) FROM public.group_ledger WHERE group_id = public.groups.id) as ledger_sum
    FROM public.groups
)
SELECT
    group_id,
    group_name,
    table_balance,
    ledger_sum,
    (ledger_sum - table_balance) as discrepancy,
    CASE
        WHEN ABS(ledger_sum - table_balance) > 0.01 THEN 'DRIFT'
        ELSE 'CONSISTENT'
    END as status
FROM group_audit;

GRANT SELECT ON public.vw_ledger_integrity_check TO authenticated;
