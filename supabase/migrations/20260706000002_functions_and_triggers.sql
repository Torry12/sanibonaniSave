-- 02_functions_and_triggers.sql

-- 1. UTILITY FUNCTIONS
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. ATOMIC BALANCE HELPERS
CREATE OR REPLACE FUNCTION public.increment_group_balance(
    p_group_id UUID,
    p_amount NUMERIC,
    p_description TEXT DEFAULT 'Atomic balance update',
    p_category TEXT DEFAULT 'adjustment',
    p_transaction_id TEXT DEFAULT NULL
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_new_balance NUMERIC;
BEGIN
    UPDATE public.groups
    SET balance = balance + p_amount,
        updated_at = NOW()
    WHERE id = p_group_id
    RETURNING balance INTO v_new_balance;

    IF NOT FOUND THEN RAISE EXCEPTION 'Group not found'; END IF;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category)
    VALUES (p_group_id, p_transaction_id, p_amount, v_new_balance, p_description, p_category);

    RETURN v_new_balance;
END;
$$;

-- 3. RPCS
CREATE OR REPLACE FUNCTION public.record_contribution_v1(
    p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_due_date DATE,
    p_paid_at TIMESTAMPTZ, p_status TEXT, p_tx_id TEXT DEFAULT NULL, p_type TEXT DEFAULT 'contribution'
 ) RETURNS JSONB AS $$
DECLARE
    v_contribution RECORD;
    v_new_balance NUMERIC;
    v_member_name TEXT;
BEGIN
    SELECT full_name INTO v_member_name FROM public.members WHERE id = p_member_id;

    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, transaction_id, type)
    VALUES (p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_tx_id, p_type)
    RETURNING * INTO v_contribution;

    SELECT public.increment_group_balance(
        p_group_id,
        p_amount,
        initcap(replace(p_type, '_', ' ')) || ' from ' || v_member_name,
        p_type,
        v_contribution.id::text
    ) INTO v_new_balance;

    RETURN jsonb_build_object(
        'contribution', to_jsonb(v_contribution),
        'new_balance', v_new_balance
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 4. MEMBER COUNT TRIGGER
CREATE OR REPLACE FUNCTION public.fn_sync_group_member_count()
RETURNS TRIGGER AS $$
DECLARE
    v_group_id UUID;
BEGIN
    v_group_id := COALESCE(NEW.group_id, OLD.group_id);
    UPDATE public.groups
    SET current_members = (SELECT count(*) FROM public.members WHERE group_id = v_group_id AND status != 'suspended')
    WHERE id = v_group_id;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_sync_group_member_count
AFTER INSERT OR UPDATE OR DELETE ON public.members
FOR EACH ROW EXECUTE FUNCTION public.fn_sync_group_member_count();

-- 5. REPAIR FUNCTION
CREATE OR REPLACE FUNCTION public.repair_group_data_v1(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_report JSONB := '{}'::jsonb;
BEGIN
    UPDATE public.groups g
    SET current_members = (SELECT count(*) FROM public.members m WHERE m.group_id = g.id AND m.status != 'suspended')
    WHERE id = p_group_id;

    SELECT jsonb_build_object(
        'group_id', p_group_id,
        'ledger_sum', (SELECT sum(amount) FROM public.group_ledger WHERE group_id = p_group_id),
        'current_balance', (SELECT balance FROM public.groups WHERE id = p_group_id)
    ) INTO v_report;

    RETURN v_report;
END;
$$;
