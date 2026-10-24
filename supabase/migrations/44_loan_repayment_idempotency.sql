-- A repayment may reach Postgres while its response is lost. Replays carry the same
-- transaction id and return the already-posted repayment without moving the ledger again.
DROP FUNCTION IF EXISTS public.record_loan_repayment_v1(UUID, UUID, UUID, NUMERIC, TEXT);

CREATE OR REPLACE FUNCTION public.record_loan_repayment_v1(
    p_loan_id UUID,
    p_member_id UUID,
    p_group_id UUID,
    p_amount NUMERIC,
    p_payment_method TEXT DEFAULT 'bank',
    p_transaction_id TEXT DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_repay RECORD;
    v_m_name TEXT;
    v_new_bal NUMERIC;
BEGIN
    SELECT balance INTO v_new_bal FROM public.groups WHERE id = p_group_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND'; END IF;

    IF p_transaction_id IS NOT NULL THEN
        SELECT * INTO v_repay
        FROM public.loan_repayments
        WHERE group_id = p_group_id AND transaction_id = p_transaction_id;
        IF FOUND THEN
            SELECT balance INTO v_new_bal FROM public.groups WHERE id = p_group_id;
            RETURN jsonb_build_object('repayment', to_jsonb(v_repay), 'new_balance', v_new_bal, 'idempotent', true);
        END IF;
    END IF;

    SELECT full_name INTO v_m_name FROM public.members WHERE id = p_member_id;
    INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, payment_method, transaction_id)
    VALUES (p_loan_id, p_member_id, p_group_id, p_amount, p_payment_method, p_transaction_id)
    RETURNING * INTO v_repay;
    SELECT public.increment_group_balance(p_group_id, p_amount, 'Loan repayment from ' || v_m_name, 'loan_repayment', v_repay.id::text, p_member_id) INTO v_new_bal;

    RETURN jsonb_build_object('repayment', to_jsonb(v_repay), 'new_balance', v_new_bal);
END;
$$;
