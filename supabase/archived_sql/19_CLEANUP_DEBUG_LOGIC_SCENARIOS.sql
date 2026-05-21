-- -----------------------------------------------------------------------------
-- SanibonaniSave - DEBUG LOGIC SCENARIO CLEANUP
-- Removes deterministic debug-only data created by 17_SEED_DEBUG_LOGIC_SCENARIOS.sql
-- Safe: only targets DBG-* / DBG_% markers.
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    v_deleted_groups INTEGER := 0;
    v_deleted_ledger INTEGER := 0;
    v_deleted_audit INTEGER := 0;
BEGIN
    DELETE FROM public.audit_logs
    WHERE action LIKE 'DBG_%';
    GET DIAGNOSTICS v_deleted_audit = ROW_COUNT;

    DELETE FROM public.platform_ledger
    WHERE description LIKE 'DBG %';
    GET DIAGNOSTICS v_deleted_ledger = ROW_COUNT;

    DELETE FROM public.groups
    WHERE name LIKE 'DBG-%';
    GET DIAGNOSTICS v_deleted_groups = ROW_COUNT;

    RAISE NOTICE 'Debug cleanup complete. groups=% platform_ledger=% audit_logs=%',
        v_deleted_groups,
        v_deleted_ledger,
        v_deleted_audit;
END $$;

