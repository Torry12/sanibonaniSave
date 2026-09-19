-- 05_performance_views.sql

CREATE OR REPLACE VIEW public.platform_summary_stats AS
SELECT
    (SELECT COUNT(id) FROM public.groups)::INT AS total_groups,
    (SELECT COUNT(id) FROM public.members)::INT AS total_members,
    (SELECT COALESCE(SUM(balance), 0) FROM public.groups)::NUMERIC(15,2) AS total_balance;

GRANT SELECT ON public.platform_summary_stats TO authenticated, anon;
