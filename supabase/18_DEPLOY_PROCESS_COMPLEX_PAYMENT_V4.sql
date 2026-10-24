-- ─────────────
-- FIX & DEPLOY process_complex_payment_v4 FUNCTION
-- ─────────────

CREATE OR REPLACE FUNCTION public.process_complex_payment_v4(
    p_group_id UUID,
    p_member_id UUID,
    p_total_amount NUMERIC,
    p_tx_id TEXT,
    p_allocations JSONB,
    p_method TEXT DEFAULT 'bank',
    p_payment_type TEXT DEFAULT 'contribution',
    p_destination TEXT DEFAULT 'group'
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_bal NUMERIC;
    v_all RECORD;
    v_total_sum NUMERIC := 0;
BEGIN
    SELECT balance INTO v_bal FROM public.groups WHERE id = p_group_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND';
    END IF;

    IF jsonb_typeof(p_allocations) <> 'array' OR jsonb_array_length(p_allocations) = 0 THEN
        RAISE EXCEPTION '[FIN_003] At least one payment allocation is required';
    END IF;

    IF EXISTS (SELECT 1 FROM public.payments WHERE group_id = p_group_id AND transaction_id = p_tx_id AND status = 'completed') OR
       EXISTS (SELECT 1 FROM public.group_ledger WHERE group_id = p_group_id AND transaction_id = p_tx_id) THEN
        SELECT balance INTO v_bal FROM public.groups WHERE id = p_group_id;
        RETURN jsonb_build_object('success', true, 'new_balance', v_bal, 'transaction_id', p_tx_id, 'status', 'idempotent');
    END IF;

    SELECT SUM((val->>'amount')::NUMERIC) INTO v_total_sum FROM jsonb_array_elements(p_allocations) AS val;
    IF ABS(v_total_sum - p_total_amount) > 0.01 THEN
        RAISE EXCEPTION '[FIN_003] Allocation total % does not match requested amount %', v_total_sum, p_total_amount;
    END IF;

    INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at)
    VALUES (p_member_id, p_group_id, p_total_amount, p_payment_type, p_method, p_tx_id, 'completed', NOW());

    FOR v_all IN (SELECT * FROM jsonb_to_recordset(p_allocations) AS x(type text, amount numeric, loan_id uuid, due_date date, description text)) LOOP
        IF v_all.amount IS NULL OR v_all.amount <= 0 THEN
            RAISE EXCEPTION '[FIN_003] Allocation amounts must be positive';
        END IF;

        IF v_all.type = 'registration_contribution' OR v_all.type = 'joining_fee' THEN
            PERFORM public.internal_post_contribution(p_member_id, p_group_id, v_all.amount, COALESCE(v_all.due_date, CURRENT_DATE), NOW(), 'paid', p_tx_id || ':' || v_all.type, v_all.type, v_all.description);
        ELSIF v_all.type = 'contribution' THEN
            PERFORM public.internal_post_contribution(p_member_id, p_group_id, v_all.amount, COALESCE(v_all.due_date, CURRENT_DATE), NOW(), 'paid', p_tx_id || ':contribution', 'contribution', v_all.description);
        ELSIF v_all.type = 'loan_repayment' AND v_all.loan_id IS NOT NULL THEN
            PERFORM public.record_loan_repayment_v1(v_all.loan_id, p_member_id, p_group_id, v_all.amount, p_method);
        ELSIF v_all.type = 'late_fee' THEN
            PERFORM public.internal_post_contribution(p_member_id, p_group_id, v_all.amount, COALESCE(v_all.due_date, CURRENT_DATE), NOW(), 'paid', p_tx_id || ':late_fee', 'late_fee', COALESCE(v_all.description, 'Late payment fee'));
        ELSIF v_all.type = 'member_fee' OR v_all.type = 'member_fee_ledger' OR v_all.type = 'platform_fee' THEN
            PERFORM public.internal_post_contribution(p_member_id, p_group_id, v_all.amount, COALESCE(v_all.due_date, CURRENT_DATE), NOW(), 'paid', p_tx_id || ':' || v_all.type, v_all.type, COALESCE(v_all.description, 'Monthly Member Fee'));
        ELSE
            RAISE EXCEPTION '[FIN_003] Unsupported payment allocation type: %', v_all.type;
        END IF;
    END LOOP;

    PERFORM public.recalculate_all_group_financials_v3(p_group_id);

    SELECT balance INTO v_bal FROM public.groups WHERE id = p_group_id;
    RETURN jsonb_build_object('success', true, 'new_balance', v_bal, 'transaction_id', p_tx_id);
END;
$$;

GRANT EXECUTE ON FUNCTION public.process_complex_payment_v4(UUID, UUID, NUMERIC, TEXT, JSONB, TEXT, TEXT, TEXT) TO anon, authenticated, service_role;
COMMENT ON FUNCTION public.process_complex_payment_v4(UUID, UUID, NUMERIC, TEXT, JSONB, TEXT, TEXT, TEXT) IS 'Atomically records an allocated group payment and its ledger entries.';
NOTIFY pgrst, 'reload schema';
