-- Balance adjustments may commit before the client receives the response. Replaying
-- the outbox transaction id must return the current balance without applying twice.
CREATE OR REPLACE FUNCTION public.increment_group_balance(
    p_group_id UUID,
    p_amount NUMERIC,
    p_description TEXT DEFAULT 'Atomic balance update',
    p_category TEXT DEFAULT 'adjustment',
    p_transaction_id TEXT DEFAULT NULL,
    p_member_id UUID DEFAULT NULL
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_new_balance NUMERIC;
    v_existing_amount NUMERIC;
BEGIN
    IF p_transaction_id IS NOT NULL THEN
        PERFORM 1 FROM public.groups WHERE id = p_group_id FOR UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND'; END IF;

        SELECT amount INTO v_existing_amount
        FROM public.group_ledger
        WHERE group_id = p_group_id AND transaction_id = p_transaction_id
        LIMIT 1;

        IF FOUND THEN
            IF v_existing_amount <> p_amount THEN
                RAISE EXCEPTION '[SYS_002] TRANSACTION_ID_REUSED_WITH_DIFFERENT_AMOUNT';
            END IF;
            SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;
            RETURN v_new_balance;
        END IF;
    END IF;

    UPDATE public.groups SET balance = balance + p_amount, updated_at = NOW()
    WHERE id = p_group_id RETURNING balance INTO v_new_balance;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND'; END IF;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id)
    VALUES (p_group_id, p_transaction_id, p_amount, v_new_balance, p_description, p_category, p_member_id);

    RETURN v_new_balance;
END;
$$;
