DO $$
DECLARE
    v_loan_id UUID;
    v_member_id UUID;
    v_group_id UUID;
BEGIN
    SELECT id, member_id, group_id INTO v_loan_id, v_member_id, v_group_id
    FROM public.loans
    LIMIT 1;

    IF v_loan_id IS NOT NULL THEN
        RAISE NOTICE 'Testing negative loan repayment for loan %', v_loan_id;
        PERFORM public.record_loan_repayment_v1(v_loan_id, v_member_id, v_group_id, -100.00);
        RAISE NOTICE 'BUG: Negative loan repayment allowed!';
    ELSE
        RAISE NOTICE 'No loans found to test.';
    END IF;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'Caught expected rejection or error: %', SQLERRM;
END $$;
