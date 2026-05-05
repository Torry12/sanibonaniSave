-- One-off backfill for legacy payouts created before the staged approval workflow.
--
-- Why this exists:
-- Before the new flow, platform-admin queue items were stored as `pending` directly.
-- After the workflow change, only `group_approved` payouts appear in the platform queue.
--
-- What this script does:
-- 1. Captures legacy candidate rows that are still untouched.
-- 2. Moves them from `pending` -> `group_approved`.
-- 3. Prints verification queries at the end.
--
-- Safety rule used:
-- - only rows created before the workflow cutoff
-- - only rows with no processing markers yet
-- - only rows still in `pending`
--
-- If you deployed the new workflow at a different time, adjust v_cutoff_ts below.

BEGIN;

DO $$
DECLARE
    v_cutoff_ts TIMESTAMPTZ := TIMESTAMPTZ '2026-05-05 00:00:00+00';
    v_candidates INTEGER;
    v_updated INTEGER;
BEGIN
    CREATE TEMP TABLE _legacy_pending_payout_candidates AS
    SELECT
        p.id,
        p.group_id,
        p.amount,
        p.created_at
    FROM public.payouts p
    WHERE p.status = 'pending'
      AND p.created_at < v_cutoff_ts
      AND p.processed_at IS NULL
      AND p.processed_by IS NULL
      AND COALESCE(p.yoco_payout_id, '') = '';

    SELECT COUNT(*)
    INTO v_candidates
    FROM _legacy_pending_payout_candidates;

    RAISE NOTICE 'Legacy pending payout candidates before cutoff %: %', v_cutoff_ts, v_candidates;

    UPDATE public.payouts p
    SET
        status = 'group_approved',
        updated_at = NOW()
    FROM _legacy_pending_payout_candidates c
    WHERE p.id = c.id;

    GET DIAGNOSTICS v_updated = ROW_COUNT;
    RAISE NOTICE 'Backfilled payouts to group_approved: %', v_updated;
END $$;

-- Verification snapshots
SELECT
    'updated_rows' AS check_name,
    COUNT(*)::INT AS result_count
FROM public.payouts
WHERE status = 'group_approved'
  AND updated_at >= NOW() - INTERVAL '5 minutes';

SELECT
    status,
    COUNT(*)::INT AS payout_count
FROM public.payouts
GROUP BY status
ORDER BY status;

SELECT
    p.id,
    p.group_id,
    p.amount,
    p.status,
    p.created_at,
    p.updated_at
FROM public.payouts p
WHERE p.status = 'group_approved'
  AND p.updated_at >= NOW() - INTERVAL '5 minutes'
ORDER BY p.updated_at DESC, p.created_at DESC
LIMIT 50;

COMMIT;

