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
        VALUES (v_burial_group_id, v_member_id, v_beneficiary_id, 'Late Relative', 'Sibling', '1980-01-01')
        ON CONFLICT (id) DO NOTHING;

        -- 2. Create a Payout Claim (Escalated to Platform Admin)
        INSERT INTO public.beneficiary_payout_claims (
            group_id, member_id, beneficiary_id, beneficiary_name, cause_of_death, date_of_death,
            claim_amount, bank_name, account_no, branch_code, account_holder, status
        ) VALUES (
            v_burial_group_id, v_member_id, v_beneficiary_id, 'Late Relative', 'Natural Causes', current_date - 10,
            10000.00, 'Standard Bank', '1234567890', '051001', 'Survivor Name', 'escalated'
        ) ON CONFLICT DO NOTHING;

        -- Add notification for claim
        INSERT INTO public.notifications (id, group_id, member_id, message, channel, trigger_event, created_at)
        VALUES (gen_random_uuid(), v_burial_group_id, v_member_id, 'SEED02: Claim escalated for Late Relative', 'both', 'claim', now() - interval '2 days')
        ON CONFLICT (id) DO NOTHING;

        -- Add member document for claim
        INSERT INTO public.member_documents (id, member_id, group_id, label, document_url, document_type, status, created_at, updated_at)
        VALUES (gen_random_uuid(), v_member_id, v_burial_group_id, 'SEED02_DOC_CLAIM', 'https://example.com/claim.pdf', 'claim_form', 'verified', now() - interval '2 days', now())
        ON CONFLICT (id) DO NOTHING;

        -- 3. Add variety of loan statuses
        DECLARE v_loan1_id UUID := gen_random_uuid();
        DECLARE v_loan2_id UUID := gen_random_uuid();
        INSERT INTO public.loans (id, member_id, group_id, amount, total_to_repay, monthly_repayment, status, purpose, created_at, updated_at)
        SELECT v_loan1_id, id, group_id, 2000, 2200, 200, 'pending', 'Emergency', now() - interval '30 days', now()
        FROM public.members WHERE group_id = v_burial_group_id OFFSET 1 LIMIT 1
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO public.loans (id, member_id, group_id, amount, total_to_repay, monthly_repayment, status, purpose, created_at, updated_at)
        SELECT v_loan2_id, id, group_id, 3000, 3300, 300, 'approved', 'Education', now() - interval '40 days', now()
        FROM public.members WHERE group_id = v_burial_group_id OFFSET 2 LIMIT 1
        ON CONFLICT (id) DO NOTHING;

        -- Add repayments for loans
        INSERT INTO public.loan_repayments (id, loan_id, member_id, group_id, amount, paid_at, payment_method, transaction_id, created_at)
        SELECT gen_random_uuid(), v_loan1_id, id, group_id, 500.00, now() - interval '10 days', 'bank', 'seed02_loanrepay1', now() - interval '10 days'
        FROM public.members WHERE group_id = v_burial_group_id OFFSET 1 LIMIT 1
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO public.loan_repayments (id, loan_id, member_id, group_id, amount, paid_at, payment_method, transaction_id, created_at)
        SELECT gen_random_uuid(), v_loan2_id, id, group_id, 600.00, now() - interval '8 days', 'bank', 'seed02_loanrepay2', now() - interval '8 days'
        FROM public.members WHERE group_id = v_burial_group_id OFFSET 2 LIMIT 1
        ON CONFLICT (id) DO NOTHING;

        -- Add notifications for loans
        INSERT INTO public.notifications (id, group_id, member_id, message, channel, trigger_event, created_at)
        SELECT gen_random_uuid(), group_id, id, 'SEED02: Loan status update', 'both', 'loan', now() - interval '1 days'
        FROM public.members WHERE group_id = v_burial_group_id OFFSET 1 LIMIT 2
        ON CONFLICT (id) DO NOTHING;

    END IF;

    RAISE NOTICE 'Loans and Claims seeded.';
END $$;
