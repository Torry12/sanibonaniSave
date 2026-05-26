-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — PERMISSION FIX FOR LANDING STATS
-- Version: 1.0 (May 2026)
--
-- This script fixes the "Database permissions are not configured" error
-- on the Landing Screen by granting SELECT access and creating public
-- RLS policies for global stats and settings.
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

-- 1. Aggregated Analytics View
GRANT SELECT ON public.platform_summary_stats TO anon, authenticated;

-- 2. Platform Settings (Required for fee display on registration)
GRANT SELECT ON public.platform_settings TO anon, authenticated;

DROP POLICY IF EXISTS "Platform Settings: Public Read" ON public.platform_settings;
CREATE POLICY "Platform Settings: Public Read" ON public.platform_settings
FOR SELECT TO anon, authenticated
USING (true);

-- 3. Underlying tables used by the platform_summary_stats view
-- (Standard views require SELECT grants on the underlying tables)
GRANT SELECT ON public.platform_ledger TO anon, authenticated;
GRANT SELECT ON public.group_actuarial_metrics TO anon, authenticated;

-- 4. RLS Policies for these tables to allow aggregate counts
DROP POLICY IF EXISTS "Platform Ledger: Public Summary Access" ON public.platform_ledger;
CREATE POLICY "Platform Ledger: Public Summary Access" ON public.platform_ledger
FOR SELECT TO anon, authenticated
USING (true);

DROP POLICY IF EXISTS "Actuarial Metrics: Public Summary Access" ON public.group_actuarial_metrics;
CREATE POLICY "Actuarial Metrics: Public Summary Access" ON public.group_actuarial_metrics
FOR SELECT TO anon, authenticated
USING (true);

COMMIT;
