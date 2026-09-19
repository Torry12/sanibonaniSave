CREATE OR REPLACE FUNCTION public.audit_and_fix_group_balance_v1(
    p_group_id UUID,
    p_fix_balance BOOLEAN DEFAULT FALSE
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_ledger_sum NUMERIC;
    v_current_balance NUMERIC;
    v_discrepancy NUMERIC;
    v_was_fixed BOOLEAN := FALSE;
BEGIN
    SELECT COALESCE(SUM(amount), 0) INTO v_ledger_sum FROM public.group_ledger WHERE group_id = p_group_id;
    SELECT balance INTO v_current_balance FROM public.groups WHERE id = p_group_id;

    v_discrepancy := v_ledger_sum - v_current_balance;

    IF ABS(v_discrepancy) > 0.01 AND p_fix_balance THEN
        UPDATE public.groups SET balance = v_ledger_sum, updated_at = NOW() WHERE id = p_group_id;
        v_was_fixed := TRUE;
        v_current_balance := v_ledger_sum;
    END IF;

    RETURN jsonb_build_object(
        'group_id', p_group_id,
        'ledger_sum', v_ledger_sum,
        'current_balance', v_current_balance,
        'discrepancy', v_discrepancy,
        'was_fixed', v_was_fixed
    );
END;
$$;
