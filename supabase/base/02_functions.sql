-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — SUPABASE MASTER FUNCTIONS & TRIGGERS
-- Version: 7.1 (Organized Layout - June 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. UTILITY FUNCTIONS
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. ATOMIC BALANCE UPDATES
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

-- 3. FINANCIAL RPCs (Versioned)

-- RPC: Record Contribution
CREATE OR REPLACE FUNCTION public.record_contribution_v1(
    p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_due_date DATE,
    p_paid_at TIMESTAMPTZ, p_status TEXT, p_tx_id TEXT DEFAULT NULL, p_type TEXT DEFAULT 'contribution'
 ) RETURNS JSONB AS $$
DECLARE
    v_contribution public.contributions;
    v_new_balance NUMERIC;
    v_ledger_desc TEXT;
    v_ledger_cat TEXT;
BEGIN
    -- 1. Create contribution record
    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, transaction_id, type)
    VALUES (p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_tx_id, p_type)
    RETURNING * INTO v_contribution;

    -- 2. Determine Ledger Context
    IF p_type = 'member_fee' THEN
        v_ledger_desc := 'Platform Member Fee';
        v_ledger_cat := 'platform_fee';
    ELSE
        v_ledger_desc := 'Contribution: ' || p_type;
        v_ledger_cat := 'contribution';
    END IF;

    -- 3. Update Member Stats (Excluding platform fees)
    IF p_type != 'member_fee' AND p_type != 'platform_fee' THEN
        UPDATE public.members
        SET total_contributions = total_contributions + 1,
            total_paid = total_paid + p_amount
        WHERE id = p_member_id;
    END IF;

    -- 4. Update Group Balance
    v_new_balance := (SELECT balance FROM public.groups WHERE id = p_group_id);
    IF p_type != 'member_fee' THEN
        SELECT public.increment_group_balance(
            p_group_id,
            p_amount,
            v_ledger_desc,
            v_ledger_cat,
            v_contribution.id::text
        ) INTO v_new_balance;
    END IF;

    -- 5. Platform Revenue Tracking
    IF p_type = 'member_fee' THEN
        PERFORM public.record_platform_ledger_entry(p_amount, 'Member Fee', 'member_fee', v_contribution.id::text);
    END IF;

    RETURN jsonb_build_object(
        'contribution', to_jsonb(v_contribution),
        'new_balance', v_new_balance
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 4. MAINTENANCE HELPERS

CREATE OR REPLACE FUNCTION public.record_platform_ledger_entry(
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id TEXT DEFAULT NULL
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

    INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category)
    VALUES (p_transaction_id, p_amount, COALESCE(v_last_balance, 0) + p_amount, p_description, p_category);
END;
$$;

-- 5. TRIGGER ATTACHMENTS
DROP TRIGGER IF EXISTS update_groups_updated_at ON public.groups;
CREATE TRIGGER update_groups_updated_at BEFORE UPDATE ON public.groups FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

DROP TRIGGER IF EXISTS update_members_updated_at ON public.members;
CREATE TRIGGER update_members_updated_at BEFORE UPDATE ON public.members FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- 6. GRANTS
GRANT EXECUTE ON FUNCTION public.increment_group_balance(UUID, NUMERIC, TEXT, TEXT, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.record_contribution_v1(UUID, UUID, NUMERIC, DATE, TIMESTAMPTZ, TEXT, TEXT, TEXT) TO authenticated, service_role;
