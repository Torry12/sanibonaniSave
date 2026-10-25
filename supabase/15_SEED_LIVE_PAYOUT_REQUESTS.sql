-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — LIVE PAYOUT REQUESTS SEED FOR EXISTING DATA
-- Version: 1.0 (August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_group_id UUID;
    v_member_id UUID;
    v_now TIMESTAMPTZ := NOW();
BEGIN
    RAISE NOTICE 'Seeding Live Payout Requests for Existing Seed Data...';

    FOR v_group_id, v_member_id IN
        SELECT g.id, m.id
        FROM public.groups g
        JOIN public.members m ON m.group_id = g.id
        WHERE g.name LIKE 'SEED-%' AND m.status = 'active'
        LIMIT 15
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM public.payouts
            WHERE group_id = v_group_id AND member_id = v_member_id
              AND description = 'Integrated seed payout request'
        ) THEN
        INSERT INTO public.payouts (
            group_id, member_id, amount, payout_type, status,
            bank_name, account_no, branch_code, description, created_at
        ) VALUES (
            v_group_id,
            v_member_id,
            (random() * 2000 + 500)::NUMERIC(10,2),
            CASE WHEN random() < 0.5 THEN 'standard' ELSE 'emergency' END,
            'pending',
            'First National Bank',
            '62' || floor(random() * 900000000 + 100000000)::TEXT,
            '250655',
            'Integrated seed payout request',
            v_now
        );
        END IF;
    END LOOP;

    RAISE NOTICE 'Live Payout Requests Seeding Complete.';
END $$;
