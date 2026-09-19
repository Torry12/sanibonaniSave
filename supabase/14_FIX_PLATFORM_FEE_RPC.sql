-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — PLATFORM FEE RPC FIX
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.pay_platform_fee_v1(
    p_group_id UUID,
    p_amount NUMERIC,
    p_fee_type TEXT,
    p_tx_id TEXT DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_last_balance NUMERIC;
BEGIN
    -- 1. Get the current cumulative balance from platform ledger
    SELECT balance_after INTO v_last_balance
    FROM public.platform_ledger
    ORDER BY created_at DESC LIMIT 1;

    -- 2. Record the transaction in platform ledger
    INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category)
    VALUES (
        p_tx_id,
        p_amount,
        COALESCE(v_last_balance, 0) + p_amount,
        'Platform ' || p_fee_type || ' fee from group ' || p_group_id,
        p_fee_type
    );

    -- 3. Update group status
    UPDATE public.groups
    SET registration_paid = CASE WHEN p_fee_type = 'registration' THEN TRUE ELSE registration_paid END,
        fee_status = 'paid'
    WHERE id = p_group_id;
END;
$$;
