-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — DATABASE RESET SCRIPT
-- WARNING: DESTRUCTIVE ACTION
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_table text;
BEGIN
    FOR v_table IN
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
    LOOP
        EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', v_table);
    END LOOP;
END $$;
