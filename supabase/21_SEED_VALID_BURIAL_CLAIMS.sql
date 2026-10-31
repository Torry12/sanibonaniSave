-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — VALID BURIAL SOCIETY CLAIMS SEED
-- Version: 2.0 (October 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_now TIMESTAMPTZ := NOW();
    v_r RECORD;
    v_counter INT := 0;
    v_claim_amount NUMERIC;
    v_claim_id UUID;
    v_status TEXT;
    v_balance NUMERIC;
BEGIN
    FOR v_r IN
        SELECT g.id AS group_id, m.id AS member_id, m.full_name AS member_name, g.burial_claim_amount
        FROM public.groups g
        JOIN public.members m ON m.group_id = g.id
        WHERE g.type = 'burial_society'
          AND m.status = 'active'
          AND (g.name LIKE 'LIGHT-%' OR g.name LIKE 'SEED-%')
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
            v_claim_amount := coalesce(v_r.burial_claim_amount, 10000.00);
            v_status := CASE WHEN v_counter % 3 = 0 THEN 'approved' WHEN v_counter % 3 = 1 THEN 'submitted' ELSE 'paid' END;

            -- A paid claim has to have paid. pay_burial_claim_v1 books it as a 'claim' debit keyed
            -- by the claim id, so a fixture marked paid without that row shows a settlement the
            -- ledger never saw. A group that cannot cover the claim keeps it approved instead.
            IF v_status = 'paid' THEN
                SELECT balance INTO v_balance FROM public.groups WHERE id = v_r.group_id FOR UPDATE;
                IF COALESCE(v_balance, 0) < v_claim_amount THEN
                    v_status := 'approved';
                END IF;
            END IF;

            INSERT INTO public.beneficiary_payout_claims (
                group_id, member_id, beneficiary_id, beneficiary_name, claim_amount,
                cause_of_death, date_of_death, status, bank_name, account_no, branch_code, account_holder, created_at, notes
            ) VALUES (
                v_r.group_id,
                v_r.member_id,
                gen_random_uuid(),
                'Spouse / Dependent of ' || v_r.member_name,
                v_claim_amount,
                'Natural',
                (v_now - interval '10 days')::date,
                v_status,
                'First National Bank',
                '62110022' || v_counter,
                '250655',
                v_r.member_name,
                v_now - interval '2 days',
                'Valid simulation burial claim #' || v_counter || ' satisfying all waiting period and reserve criteria.'
            ) RETURNING id INTO v_claim_id;

            IF v_status = 'paid' THEN
                PERFORM public.increment_group_balance(
                    v_r.group_id,
                    -v_claim_amount,
                    'Burial claim: Spouse / Dependent of ' || v_r.member_name,
                    'claim',
                    v_claim_id::text,
                    v_r.member_id
                );
            END IF;
        END IF;
    END LOOP;
END $$;
