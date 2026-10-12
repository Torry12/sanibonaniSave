-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — ONLINE SPECIFIC SETUP
-- Version: 1.1 (Production Overrides - Fixed Block - August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
BEGIN
    -- 1. PRODUCTION PLATFORM SETTINGS
    -- Ensure real-world values for production environment
    UPDATE public.platform_settings SET value = 10.0 WHERE key = 'monthly_per_member';
    UPDATE public.platform_settings SET value = 700.0 WHERE key = 'registration_fee';

    -- 2. DISABLE DEBUG LOGGING (Optional - reduces DB noise in cloud)
    -- TRUNCATE public.debug_log;

    -- 3. ENSURE IDEMPOTENCE MARKERS
    -- If you need specific production groups pre-provisioned, add them here.

    RAISE NOTICE 'Online production overrides applied successfully.';
END $$;
