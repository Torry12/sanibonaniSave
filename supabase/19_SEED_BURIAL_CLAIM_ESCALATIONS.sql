-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — BURIAL SOCIETY CLAIM ESCALATIONS SEED
-- Version: 2.0 (August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_now TIMESTAMPTZ := NOW();
    v_counter INT := 0;
    v_r RECORD;
BEGIN
    -- Generate scenarios only for explicitly owned demo groups. Do not change
    -- balances, suspension flags, member statuses, or claims for real groups.
    FOR v_r IN
        SELECT g.id AS group_id, m.id AS member_id, m.full_name AS member_name
        FROM public.groups g
        JOIN public.members m ON m.group_id = g.id
        WHERE g.type = 'burial_society'
          AND (g.name LIKE 'LIGHT-%' OR g.name LIKE 'SEED-%' OR g.name LIKE 'SIM-FLOWS-%')
        ORDER BY g.name, m.id
        LIMIT 10
    LOOP
        v_counter := v_counter + 1;

        -- Check if an escalated claim already exists for this member to avoid duplicates
        IF NOT EXISTS (
            SELECT 1 FROM public.beneficiary_payout_claims
            WHERE member_id = v_r.member_id AND status = 'escalated'
              AND notes LIKE 'Integrated seed burial escalation #%'
        ) THEN
            INSERT INTO public.beneficiary_payout_claims (
                group_id, member_id, beneficiary_id, beneficiary_name, claim_amount,
                cause_of_death, date_of_death, status, bank_name, account_no, branch_code, account_holder, created_at, notes
            ) VALUES (
                v_r.group_id,
                v_r.member_id,
                gen_random_uuid(),
                'Dependent Relative #' || v_counter,
                (7500.00 + v_counter * 750.00)::NUMERIC(12,2),
                CASE WHEN v_counter % 2 = 0 THEN 'Natural' ELSE 'Accidental' END,
                (v_now - (v_counter || ' days')::interval)::date,
                'escalated',
                'First National Bank',
                '62889900' || v_counter,
                '250655',
                'Claimant Admin ' || v_counter,
                v_now - (v_counter || ' hours')::interval,
                'Integrated seed burial escalation #' || v_counter || ' requiring platform intervention.'
            );
        END IF;
    END LOOP;
END $$;
