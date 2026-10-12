-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — VALID BURIAL SOCIETY CLAIMS SEED
-- Version: 1.0 (October 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_now TIMESTAMPTZ := NOW();
    v_r RECORD;
    v_counter INT := 0;
BEGIN
    FOR v_r IN
        SELECT g.id AS group_id, m.id AS member_id, m.full_name AS member_name, g.burial_claim_amount
        FROM public.groups g
        JOIN public.members m ON m.group_id = g.id
        WHERE g.type = 'burial_society'
          AND m.status = 'active'
          AND (g.name LIKE 'LIGHT-%' OR g.name LIKE 'SEED-%' OR g.name LIKE 'SIM-%')
        ORDER BY g.name, m.joined_at
        LIMIT 15
    LOOP
        v_counter := v_counter + 1;

        -- Ensure member tenure is > 6 months to pass validation
        UPDATE public.members
        SET joined_at = v_now - interval '12 months'
        WHERE id = v_r.member_id;

        -- Insert valid submitted/approved burial claim
        IF NOT EXISTS (
            SELECT 1 FROM public.beneficiary_payout_claims
            WHERE member_id = v_r.member_id AND notes LIKE 'Valid simulation burial claim #%'
        ) THEN
            INSERT INTO public.beneficiary_payout_claims (
                group_id, member_id, beneficiary_id, beneficiary_name, claim_amount,
                cause_of_death, date_of_death, status, bank_name, account_no, branch_code, account_holder, created_at, notes
            ) VALUES (
                v_r.group_id,
                v_r.member_id,
                gen_random_uuid(),
                'Spouse / Dependent of ' || v_r.member_name,
                coalesce(v_r.burial_claim_amount, 10000.00),
                'Natural',
                (v_now - interval '10 days')::date,
                CASE WHEN v_counter % 3 = 0 THEN 'approved' WHEN v_counter % 3 = 1 THEN 'submitted' ELSE 'paid' END,
                'First National Bank',
                '62110022' || v_counter,
                '250655',
                v_r.member_name,
                v_now - interval '2 days',
                'Valid simulation burial claim #' || v_counter || ' satisfying all waiting period and reserve criteria.'
            );
        END IF;
    END LOOP;
END $$;
