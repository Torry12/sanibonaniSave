-- BACKFILL_PAYMENTS_FROM_CONTRIBUTIONS.sql
-- Creates payment records from existing paid contributions for REALISTIC_E2E_SEED groups.
-- Idempotent: skips if matching payment already exists.

DO $$
DECLARE
    v_group_ids UUID[] := ARRAY[
        '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000003',
        '00000000-0000-0000-0000-000000000004'
    ];
    v_gid UUID;
    v_before BIGINT;
    v_after BIGINT;
BEGIN
    SELECT count(*) INTO v_before FROM public.payments;

    FOREACH v_gid IN ARRAY v_group_ids LOOP
        INSERT INTO public.payments (id, member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at, created_at)
        SELECT
            gen_random_uuid(),
            c.member_id,
            c.group_id,
            c.amount,
            COALESCE(c.type, 'contribution'),
            COALESCE(c.payment_method, 'bank'),
            format('backfill_%s', c.id),
            'completed',
            c.paid_at,
            c.created_at
        FROM public.contributions c
        WHERE c.group_id = v_gid
          AND c.status = 'paid'
          AND NOT EXISTS (
            SELECT 1 FROM public.payments p
            WHERE p.group_id = c.group_id
              AND p.member_id = c.member_id
              AND p.amount = c.amount
          )
        ON CONFLICT (id) DO NOTHING;

        RAISE NOTICE 'Backfilled payments for group %', v_gid;
    END LOOP;

    SELECT count(*) INTO v_after FROM public.payments;
    RAISE NOTICE 'Payment backfill complete. Before: %, After: %, Added: %', v_before, v_after, v_after - v_before;
END$$;
