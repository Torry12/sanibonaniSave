CREATE OR REPLACE FUNCTION public.recompute_ledger_running_balances_v1(p_group_id UUID)
RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_entry RECORD;
    v_running_balance NUMERIC := 0;
BEGIN
    FOR v_entry IN
        SELECT id, amount
        FROM public.group_ledger
        WHERE group_id = p_group_id
        ORDER BY created_at ASC, id ASC
    LOOP
        v_running_balance := v_running_balance + v_entry.amount;
        UPDATE public.group_ledger
        SET balance_after = v_running_balance
        WHERE id = v_entry.id;
    END LOOP;
    UPDATE public.groups
    SET balance = v_running_balance,
        updated_at = NOW()
    WHERE id = p_group_id;
    RETURN v_running_balance;
END;
$$;
