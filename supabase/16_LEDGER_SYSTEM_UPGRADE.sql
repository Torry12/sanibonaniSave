-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — LEDGER SYSTEM UPGRADE (RPCs & LOGIC)
-- Version: 1.0 (Advanced Reconciliation & Integrity)
-- ─────────────────────────────────────────────────────────────────────────────

-- RPC: Perform Group Reconciliation
-- Calculates the sum of all ledger entries for a group and compares it to current balance.
CREATE OR REPLACE FUNCTION public.perform_group_reconciliation_v1(
    p_group_id UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_ledger_sum NUMERIC;
    v_current_balance NUMERIC;
    v_discrepancy NUMERIC;
    v_reconciliation_id UUID;
    v_last_reconciliation TIMESTAMPTZ;
    v_record JSONB;
BEGIN
    -- 1. Get current state
    SELECT balance INTO v_current_balance FROM public.groups WHERE id = p_group_id;
    SELECT SUM(amount) INTO v_ledger_sum FROM public.group_ledger WHERE group_id = p_group_id;

    v_discrepancy := v_current_balance - COALESCE(v_ledger_sum, 0);

    -- 2. Find last reconciliation to set the period
    SELECT created_at INTO v_last_reconciliation
    FROM public.reconciliations
    WHERE group_id = p_group_id
    ORDER BY created_at DESC LIMIT 1;

    -- 3. Create the record
    INSERT INTO public.reconciliations (
        group_id,
        period_start,
        period_end,
        status,
        ledger_balance,
        actual_balance,
        discrepancy,
        performed_by
    ) VALUES (
        p_group_id,
        COALESCE(v_last_reconciliation, NOW() - interval '30 days'),
        NOW(),
        CASE WHEN v_discrepancy = 0 THEN 'balanced' ELSE 'discrepancy_found' END,
        COALESCE(v_ledger_sum, 0),
        v_current_balance,
        v_discrepancy,
        auth.uid()
    ) RETURNING id INTO v_reconciliation_id;

    -- 4. Build return object
    SELECT jsonb_build_object(
        'id', v_reconciliation_id,
        'group_id', p_group_id,
        'period_start', COALESCE(v_last_reconciliation, NOW() - interval '30 days'),
        'period_end', NOW(),
        'status', CASE WHEN v_discrepancy = 0 THEN 'balanced' ELSE 'discrepancy_found' END,
        'ledger_balance', COALESCE(v_ledger_sum, 0),
        'actual_balance', v_current_balance,
        'discrepancy', v_discrepancy,
        'created_at', NOW()
    ) INTO v_record;

    RETURN v_record;
END;
$$;
