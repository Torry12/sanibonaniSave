-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — FIX RPC SIGNATURES & AMBIGUITY
-- Version: 1.0 (August 3, 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. FIX record_platform_revenue_v1 ambiguity
DROP FUNCTION IF EXISTS public.record_platform_revenue_v1(NUMERIC, TEXT, TEXT, TEXT);
DROP FUNCTION IF EXISTS public.record_platform_revenue_v1(NUMERIC, TEXT, TEXT, TEXT, UUID);

CREATE OR REPLACE FUNCTION public.record_platform_revenue_v1(
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id TEXT DEFAULT NULL,
    p_group_id UUID DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_last_balance NUMERIC;
BEGIN
    SELECT balance_after INTO v_last_balance
    FROM public.platform_ledger
    ORDER BY created_at DESC LIMIT 1;

    INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category, group_id)
    VALUES (p_transaction_id, p_amount, COALESCE(v_last_balance, 0) + p_amount, p_description, p_category, p_group_id);
END;
$$;

-- 2. FIX record_contribution_v1 (Ensure 9-parameter version is primary)
DROP FUNCTION IF EXISTS public.record_contribution_v1(UUID, UUID, NUMERIC, DATE, TIMESTAMPTZ, TEXT, TEXT, TEXT, TEXT);

CREATE OR REPLACE FUNCTION public.record_contribution_v1(
    p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_due_date DATE,
    p_paid_at TIMESTAMPTZ, p_status TEXT, p_tx_id TEXT DEFAULT NULL,
    p_type TEXT DEFAULT 'contribution', p_description TEXT DEFAULT NULL
 ) RETURNS JSONB AS $$
DECLARE
    v_contribution RECORD;
    v_new_balance NUMERIC;
    v_member_name TEXT;
    v_ledger_desc TEXT;
    v_ledger_cat TEXT;
BEGIN
    SELECT full_name INTO v_member_name FROM public.members WHERE id = p_member_id;

    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, transaction_id, type)
    VALUES (p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_tx_id, p_type)
    RETURNING * INTO v_contribution;

    IF p_description IS NOT NULL THEN
        v_ledger_desc := p_description || ' (' || v_member_name || ')';
    ELSIF p_type IN ('member_fee', 'member_fee_ledger') THEN
        v_ledger_desc := 'Platform Member Fee (' || v_member_name || ')';
    ELSE
        v_ledger_desc := initcap(replace(p_type, '_', ' ')) || ' from ' || v_member_name;
    END IF;

    v_ledger_cat := CASE WHEN p_type IN ('member_fee', 'member_fee_ledger') THEN 'platform_fee' ELSE p_type END;

    IF p_type NOT IN ('member_fee', 'member_fee_ledger', 'registration_contribution') THEN
        SELECT public.increment_group_balance(
            p_group_id,
            p_amount,
            v_ledger_desc,
            v_ledger_cat,
            v_contribution.id::text,
            p_member_id
        ) INTO v_new_balance;
    ELSE
        -- Platform fees do not increase group balance
        SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;
        PERFORM public.record_platform_revenue_v1(p_amount, v_ledger_desc, v_ledger_cat, v_contribution.id::text, p_group_id);
    END IF;

    RETURN jsonb_build_object(
        'contribution', to_jsonb(v_contribution),
        'new_balance', v_new_balance
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
