-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — PERFORMANCE & ANALYTICS VIEWS
-- Version: 1.0 (Organized Layout - June 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. PLATFORM SUMMARY STATS (Landing Screen Support)
DROP VIEW IF EXISTS public.platform_summary_stats;
CREATE OR REPLACE VIEW public.platform_summary_stats AS
SELECT
    COUNT(id)::INT AS total_groups,
    COALESCE(SUM(current_members), 0)::INT AS total_members,
    COALESCE(SUM(balance), 0)::NUMERIC(15,2) AS total_balance,
    (SELECT COUNT(DISTINCT province) FROM public.groups WHERE province IS NOT NULL AND province != '')::INT AS total_provinces,
    (SELECT COALESCE(SUM(amount), 0) FROM public.platform_ledger)::NUMERIC(15,2) AS platform_revenue,
    (
        SELECT COALESCE(AVG(composite_risk_score), 0)
        FROM public.group_actuarial_metrics
    )::FLOAT AS average_risk_score
FROM public.groups;

GRANT SELECT ON public.platform_summary_stats TO authenticated, anon;
