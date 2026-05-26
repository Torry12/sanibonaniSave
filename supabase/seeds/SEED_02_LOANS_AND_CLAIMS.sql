-- SEED_02_LOANS_AND_CLAIMS.sql
-- Specialized seed for testing Loan workflows and Burial Claims.

DO $$
DECLARE
    v_burial_group_id UUID;
    v_member_id UUID;
    v_beneficiary_id UUID;
BEGIN
    -- Find our Unity Burial Society
    SELECT id INTO v_burial_group_id FROM public.groups WHERE name = 'Unity Burial Society' LIMIT 1;

    IF v_burial_group_id IS NOT NULL THEN
        -- Select a member to be the claimant
        SELECT id INTO v_member_id FROM public.members WHERE group_id = v_burial_group_id LIMIT 1;

        -- 1. Add Beneficiaries
        v_beneficiary_id := gen_random_uuid();
        INSERT INTO public.beneficiaries (group_id, member_id, id, full_name, relationship, date_of_birth)
        VALUES (v_burial_group_id, v_member_id, v_beneficiary_id, 'Late Relative', 'Sibling', '1980-01-01');

        -- 2. Create a Payout Claim (Escalated to Platform Admin)
        INSERT INTO public.beneficiary_payout_claims (
            group_id, member_id, beneficiary_id, beneficiary_name, cause_of_death, date_of_death,
            claim_amount, bank_name, account_no, branch_code, account_holder, status
        ) VALUES (
            v_burial_group_id, v_member_id, v_beneficiary_id, 'Late Relative', 'Natural Causes', current_date - 10,
            10000.00, 'Standard Bank', '1234567890', '051001', 'Survivor Name', 'escalated'
        );

        -- 3. Add variety of loan statuses
        INSERT INTO public.loans (member_id, group_id, amount, total_to_repay, monthly_repayment, status, purpose)
        SELECT id, group_id, 2000, 2200, 200, 'pending', 'Emergency'
        FROM public.members WHERE group_id = v_burial_group_id OFFSET 1 LIMIT 1;

        INSERT INTO public.loans (member_id, group_id, amount, total_to_repay, monthly_repayment, status, purpose)
        SELECT id, group_id, 3000, 3300, 300, 'approved', 'Education'
        FROM public.members WHERE group_id = v_burial_group_id OFFSET 2 LIMIT 1;

    END IF;

    RAISE NOTICE 'Loans and Claims seeded.';
END $$;
