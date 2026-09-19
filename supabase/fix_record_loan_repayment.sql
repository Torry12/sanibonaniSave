CREATE OR REPLACE FUNCTION public.record_loan_repayment_v1(
    p_loan_id UUID,
    p_member_id UUID,
    p_group_id UUID,
    p_amount NUMERIC,
    p_payment_method TEXT DEFAULT 'bank'
) RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_repayment RECORD;
    v_member_name TEXT;
    v_new_balance NUMERIC;
    v_loan RECORD;
BEGIN
    -- Security Check
    IF p_amount <= 0 THEN
        RAISE EXCEPTION 'Repayment amount must be positive';
    END IF;

    SELECT full_name INTO v_member_name FROM public.members WHERE id = p_member_id;

    SELECT * INTO v_loan FROM public.loans WHERE id = p_loan_id;
    IF v_loan.id IS NULL THEN RAISE EXCEPTION 'Loan not found'; END IF;

    IF v_loan.total_repaid + p_amount > v_loan.total_to_repay THEN
        RAISE EXCEPTION 'Repayment amount exceeds the remaining loan balance';
    END IF;

    INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, payment_method)
    VALUES (p_loan_id, p_member_id, p_group_id, p_amount, p_payment_method)
    RETURNING * INTO v_repayment;

    UPDATE public.loans
    SET total_repaid = total_repaid + p_amount,
        status = CASE
            WHEN total_repaid + p_amount >= total_to_repay THEN 'completed'
            ELSE 'partially_paid'
        END,
        updated_at = NOW()
    WHERE id = p_loan_id;

    v_new_balance := public.increment_group_balance(
        p_group_id,
        p_amount,
        'Loan repayment from ' || v_member_name,
        'loan_repayment',
        v_repayment.id::text
    );

    RETURN jsonb_build_object(
        'repayment', to_jsonb(v_repayment),
        'new_balance', v_new_balance
    );
END;
$$;
