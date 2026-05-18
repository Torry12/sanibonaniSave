-- -----------------------------------------------------------------------------
-- SanibonaniSave - Remove YoCo coupling from disbursements
-- Date: 2026-05-17
-- Purpose:
--   Disbursements are manual/admin bank transfers. Payments can still use YoCo,
--   but payout/disbursement tracking must use neutral payout_reference fields.
-- -----------------------------------------------------------------------------

BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'payouts'
          AND column_name = 'yoco_payout_id'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'payouts'
          AND column_name = 'payout_reference'
    ) THEN
        ALTER TABLE public.payouts RENAME COLUMN yoco_payout_id TO payout_reference;
    ELSIF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'payouts'
          AND column_name = 'payout_reference'
    ) THEN
        ALTER TABLE public.payouts ADD COLUMN payout_reference TEXT;
    END IF;
END $$;

DROP FUNCTION IF EXISTS public.complete_payout_v1(UUID, UUID, TEXT);

CREATE OR REPLACE FUNCTION public.complete_payout_v1(
    p_payout_id UUID,
    p_admin_id UUID,
    p_payout_reference TEXT DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_payout public.payouts;
BEGIN
    SELECT * INTO v_payout FROM public.payouts WHERE id = p_payout_id;

    IF v_payout.id IS NULL THEN
        RAISE EXCEPTION 'Payout not found';
    END IF;

    IF v_payout.status = 'completed' THEN
        RETURN;
    END IF;

    UPDATE public.payouts
    SET status = 'completed',
        processed_at = NOW(),
        processed_by = p_admin_id,
        payout_reference = COALESCE(p_payout_reference, payout_reference),
        updated_at = NOW()
    WHERE id = p_payout_id;

    PERFORM public.record_disbursement_v1(
        v_payout.group_id,
        v_payout.amount,
        'Disbursement: ' || v_payout.bank_name || ' (' || v_payout.account_no || ')',
        'payout',
        p_payout_id
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.complete_payout_v1(UUID, UUID, TEXT) TO authenticated, service_role;

COMMIT;
