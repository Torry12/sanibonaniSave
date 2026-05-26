-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — 03. PERFORMANCE VIEWS
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

-- 1. ANALYTICS SUMMARY VIEW (Landing Screen Support)
DROP VIEW IF EXISTS public.platform_summary_stats;
CREATE VIEW public.platform_summary_stats AS
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

-- 2. SEARCH OPTIMIZATION
-- Ensure GIN indexes for fuzzy search are present (from file 01)
-- CREATE INDEX IF NOT EXISTS idx_members_full_name_trgm ON public.members USING gin (full_name gin_trgm_ops);
-- CREATE INDEX IF NOT EXISTS idx_groups_name_trgm ON public.groups USING gin (name gin_trgm_ops);

COMMIT;
