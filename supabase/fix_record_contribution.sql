CREATE OR REPLACE FUNCTION public.record_contribution_v1(
    p_member_id uuid,
    p_group_id uuid,
    p_amount numeric,
    p_due_date date,
    p_paid_at timestamp with time zone,
    p_status text,
    p_tx_id text DEFAULT NULL,
    p_type text DEFAULT 'contribution'
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_contribution RECORD;
    v_new_balance NUMERIC;
    v_member_name TEXT;
    v_ledger_desc TEXT;
    v_ledger_cat TEXT;
BEGIN
    -- Security Check
    IF p_amount <= 0 THEN
        RAISE EXCEPTION 'Contribution amount must be positive';
    END IF;

    SELECT full_name INTO v_member_name FROM public.members WHERE id = p_member_id;

    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, transaction_id, type)
    VALUES (p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_tx_id, p_type)
    RETURNING * INTO v_contribution;

    IF p_type = 'member_fee' THEN
        v_ledger_desc := 'Platform Member Fee (' || v_member_name || ')';
        v_ledger_cat := 'platform_fee';
    ELSE
        v_ledger_desc := initcap(replace(p_type, '_', ' ')) || ' from ' || v_member_name;
        v_ledger_cat := p_type;
    END IF;

    IF p_type != 'member_fee' THEN
        SELECT public.increment_group_balance(
            p_group_id,
            p_amount,
            v_ledger_desc,
            v_ledger_cat,
            v_contribution.id::text
        ) INTO v_new_balance;
    ELSE
        SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;
        PERFORM public.record_platform_revenue_v1(p_amount, v_ledger_desc, v_ledger_cat, v_contribution.id::text);
    END IF;

    RETURN jsonb_build_object(
        'contribution', to_jsonb(v_contribution),
        'new_balance', v_new_balance
    );
END;
$$;
