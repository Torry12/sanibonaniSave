-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — 50 PAYOUT REQUESTS & APPROVAL CONDITIONS SEED
-- Version: 1.0 (August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_group_id UUID;
    v_member_id UUID;
    v_bank TEXT;
    v_acc TEXT;
    v_branch TEXT;
    v_payout_types TEXT[] := ARRAY['standard', 'emergency', 'annual', 'disinvestment', 'claim', 'rotation'];
    v_banks TEXT[] := ARRAY['First National Bank', 'Standard Bank', 'Absa Bank', 'Nedbank', 'Capitec Bank'];
    v_branch_codes TEXT[] := ARRAY['250655', '051001', '632005', '198765', '470010'];
    v_type TEXT;
    v_counter INT := 0;
    v_now TIMESTAMPTZ := NOW();
BEGIN
    RAISE NOTICE 'Seeding 50 Payout Requests & Approval Conditions...';

    -- Replace only rows owned by this seed. Never overwrite group balances or statuses.
    DELETE FROM public.payouts
    WHERE description LIKE 'Seeded % Payout Request #%'
      AND group_id IN (SELECT id FROM public.groups WHERE name LIKE 'SEED-%');

    -- 4. Generate 50 Payout Requests across SEED groups and members
    FOR v_member_id, v_group_id, v_bank, v_acc, v_branch IN
        SELECT m.id, m.group_id, m.bank_name, m.account_no, m.branch_code
        FROM public.members m
        JOIN public.groups g ON m.group_id = g.id
        WHERE g.name LIKE 'SEED-%'
        ORDER BY m.group_id, m.id
    LOOP
        v_counter := v_counter + 1;
        IF v_counter > 50 THEN
            EXIT;
        END IF;

        v_type := v_payout_types[(v_counter % 6) + 1];

        INSERT INTO public.payouts (
            group_id, member_id, amount, bank_name, account_no, branch_code,
            status, payout_type, description, supporting_document_status, created_at
        ) VALUES (
            v_group_id,
            v_member_id,
            (1000.00 + (v_counter * 50.00))::NUMERIC(12,2),
            COALESCE(v_bank, v_banks[(v_counter % 5) + 1]),
            COALESCE(v_acc, '62' || (10000000 + v_counter * 123)::text),
            COALESCE(v_branch, v_branch_codes[(v_counter % 5) + 1]),
            'pending',
            v_type,
            'Seeded ' || UPPER(v_type) || ' Payout Request #' || v_counter,
            'verified',
            v_now - (v_counter || ' hours')::interval
        );
    END LOOP;

    RAISE NOTICE 'Seeded 50 Payout Requests with Verified Approval Conditions.';
END $$;
