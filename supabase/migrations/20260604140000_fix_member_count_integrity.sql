-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — FIX MEMBER COUNT INTEGRITY
-- Version: 1.0 (June 4, 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. DROP THE REDUNDANT INCREMENTAL TRIGGER
-- This trigger was causing double-counting when combined with the sync trigger.
DROP TRIGGER IF EXISTS tr_update_member_count ON public.members;
DROP FUNCTION IF EXISTS public.update_group_member_count();

-- 2. ENSURE THE SYNC TRIGGER IS CORRECTLY CONFIGURED
-- It should count only non-suspended members to match the app's 'Active Members' stat.
CREATE OR REPLACE FUNCTION public.fn_sync_group_member_count()
RETURNS TRIGGER AS $$
DECLARE
    v_group_id UUID;
BEGIN
    -- Handle both new records and deleted ones
    v_group_id := COALESCE(NEW.group_id, OLD.group_id);

    UPDATE public.groups
    SET current_members = (
        SELECT count(*) FROM public.members
        WHERE group_id = v_group_id AND status != 'suspended'
    )
    WHERE id = v_group_id;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Re-attach trigger if missing
DROP TRIGGER IF EXISTS tr_sync_group_member_count ON public.members;
CREATE TRIGGER tr_sync_group_member_count
AFTER INSERT OR UPDATE OR DELETE ON public.members
FOR EACH ROW EXECUTE FUNCTION public.fn_sync_group_member_count();

-- 3. REPAIR DATA FOR ALL GROUPS
-- Recalculate member counts to fix existing discrepancies
UPDATE public.groups g
SET current_members = (
    SELECT count(*) FROM public.members m
    WHERE m.group_id = g.id AND m.status != 'suspended'
);

RAISE NOTICE 'Member count integrity fix applied. Redundant triggers removed and all group counters re-synced.';
