-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — PLATFORM FINANCIAL ESCALATIONS & PENDING REQUESTS SEED
-- Version: 1.0 (August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_group_id UUID;
    v_member_id UUID;
    v_now TIMESTAMPTZ := NOW();
    v_counter INT := 0;
BEGIN
    -- Seed cases are explicitly tagged and isolated to SEED groups.
    DELETE FROM public.payouts
    WHERE description LIKE 'Platform Escalation Payout Request #%'
      AND group_id IN (SELECT id FROM public.groups WHERE name LIKE 'SEED-%');

    FOR v_group_id, v_member_id IN
        SELECT g.id, m.id
        FROM public.groups g
        JOIN public.members m ON m.group_id = g.id
        WHERE g.name LIKE 'SEED-%'
        LIMIT 10
    LOOP
        v_counter := v_counter + 1;
        INSERT INTO public.payouts (
            group_id, member_id, amount, bank_name, account_no, branch_code,
            status, payout_type, description, supporting_document_status, created_at
        ) VALUES (
            v_group_id, v_member_id, (1500.00 + v_counter * 100.00)::NUMERIC(12,2),
            'First National Bank', '628899001' || v_counter, '250655',
            'pending', 'standard', 'Platform Escalation Payout Request #' || v_counter,
            'verified', v_now - (v_counter || ' hours')::interval
        );
    END LOOP;

    -- 3. Seed 5 Pending / Escalated Loan Requests
    DELETE FROM public.loans WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'SEED-%') AND purpose LIKE 'Seed Platform Escalation Loan #%';
    v_counter := 0;

    FOR v_group_id, v_member_id IN
        SELECT g.id, m.id
        FROM public.groups g
        JOIN public.members m ON m.group_id = g.id
        WHERE g.name LIKE 'SEED-%'
        LIMIT 5
    LOOP
        v_counter := v_counter + 1;
        INSERT INTO public.loans (
            group_id, member_id, amount, interest_rate, total_to_repay, total_repaid,
            monthly_repayment, start_date, end_date, status, purpose, amortization_type, created_at
        ) VALUES (
            v_group_id, v_member_id, (2000.00 + v_counter * 250.00)::NUMERIC(12,2),
            20.00, (2400.00 + v_counter * 300.00)::NUMERIC(12,2), 0.00,
            (800.00 + v_counter * 100.00)::NUMERIC(12,2),
            v_now::date, (v_now + interval '3 months')::date,
            'pending', 'Seed Platform Escalation Loan #' || v_counter, 'flat', v_now - (v_counter || ' hours')::interval
        );
    END LOOP;

    -- 4. Seed 5 Escalated Burial Claims
    DELETE FROM public.beneficiary_payout_claims
    WHERE notes LIKE 'Seed Platform Escalation Claim #%'
      AND group_id IN (SELECT id FROM public.groups WHERE name LIKE 'SEED-%');
    v_counter := 0;

    FOR v_group_id, v_member_id IN
        SELECT g.id, m.id
        FROM public.groups g
        JOIN public.members m ON m.group_id = g.id
        WHERE g.name LIKE 'SEED-%'
        LIMIT 5
    LOOP
        v_counter := v_counter + 1;
        INSERT INTO public.beneficiary_payout_claims (
            group_id, member_id, beneficiary_id, beneficiary_name, claim_amount,
            cause_of_death, date_of_death, status, bank_name, account_no, branch_code, account_holder, created_at, notes
        ) VALUES (
            v_group_id, v_member_id, gen_random_uuid(), 'Dependent Beneficiary #' || v_counter, 10000.00,
            'Natural', (v_now - interval '3 days')::date, 'escalated',
            'Standard Bank', '05123456' || v_counter, '051001', 'Claimant ' || v_counter,
            v_now - (v_counter || ' hours')::interval, 'Seed Platform Escalation Claim #' || v_counter
        );
    END LOOP;
END $$;
