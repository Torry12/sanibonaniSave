-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — PAYMENT SYSTEM HARDENING (V2 RPCS)
-- Version: 2.0 (High-Fidelity Multi-Allocation - July 2026)
-- ─────────────────────────────────────────────────────────────────────────────

/**
 * Standardized process_complex_payment_v2.
 * Hardened with multi-level idempotency, atomic loan iteration, and forensic auditing.
 */
CREATE OR REPLACE FUNCTION public.process_complex_payment_v2(
    p_group_id UUID,
    p_member_id UUID,
    p_total_amount NUMERIC,
    p_tx_id TEXT,
    p_allocations JSONB, -- Array of {type: string, amount: numeric, loan_id: uuid?, due_date: date, description: text?}
    p_method TEXT DEFAULT 'bank',
    p_payment_type TEXT DEFAULT 'contribution'
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_new_balance NUMERIC;
    v_allocation RECORD;
    v_total_allocated NUMERIC := 0;
    v_member_name TEXT;
    v_res JSONB;
    v_log_id UUID;
BEGIN
    -- 0. Advanced Idempotency Check
    -- Check payments table and the ledger directly to ensure this TX hasn't touched the fund balance yet.
    IF EXISTS (SELECT 1 FROM public.payments WHERE transaction_id = p_tx_id AND status = 'completed') OR
       EXISTS (SELECT 1 FROM public.group_ledger WHERE transaction_id = p_tx_id) THEN
        SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;
        RETURN jsonb_build_object(
            'success', true,
            'new_balance', v_new_balance,
            'transaction_id', p_tx_id,
            'message', 'Transaction already processed (Idempotency Hit)'
        );
    END IF;

    -- 1. Context Verification
    SELECT full_name INTO v_member_name FROM public.members WHERE id = p_member_id;
    IF v_member_name IS NULL THEN RAISE EXCEPTION 'Member context missing for ID %', p_member_id; END IF;

    -- 2. Mathematical Integrity Check
    SELECT SUM((val->>'amount')::NUMERIC) INTO v_total_allocated
    FROM jsonb_array_elements(p_allocations) AS val;

    IF ABS(v_total_allocated - p_total_amount) > 0.01 THEN
        RAISE EXCEPTION 'Allocation mismatch: Total % != Sum %', p_total_amount, v_total_allocated;
    END IF;

    -- 3. Record Master Payment (Audit Trail Start)
    INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at)
    VALUES (p_member_id, p_group_id, p_total_amount, p_payment_type, p_method, p_tx_id, 'completed', NOW());

    -- 4. Execute Allocations
    FOR v_allocation IN SELECT * FROM jsonb_to_recordset(p_allocations) AS x(type text, amount numeric, loan_id uuid, due_date date, description text)
    LOOP
        IF v_allocation.amount <= 0 THEN CONTINUE; END IF;

        IF v_allocation.type = 'loan_repayment' THEN
            -- Record loan repayment (increments group balance)
            v_res := public.record_loan_repayment_v1(v_allocation.loan_id, p_member_id, p_group_id, v_allocation.amount, p_method);
            v_new_balance := (v_res->>'new_balance')::NUMERIC;
        ELSE
            -- Record contribution or fee (handles both group balance and platform revenue)
            v_res := public.record_contribution_v1(
                p_member_id,
                p_group_id,
                v_allocation.amount,
                v_allocation.due_date,
                NOW(),
                'paid',
                p_tx_id,
                v_allocation.type,
                COALESCE(v_allocation.description, initcap(replace(v_allocation.type, '_', ' ')))
            );
            v_new_balance := (v_res->>'new_balance')::NUMERIC;
        END IF;
    END LOOP;

    -- 5. Final State Audit
    IF v_new_balance IS NULL THEN
        SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;
    END IF;

    -- 6. Forensic Logging
    INSERT INTO public.audit_logs (actor_id, target_group_id, target_member_id, action, details)
    VALUES (p_member_id, p_group_id, p_member_id, 'COMPLEX_PAYMENT_EXECUTED', jsonb_build_object(
        'transaction_id', p_tx_id,
        'total_amount', p_total_amount,
        'allocations', p_allocations,
        'balance_after', v_new_balance
    )) RETURNING id INTO v_log_id;

    RETURN jsonb_build_object(
        'success', true,
        'new_balance', v_new_balance,
        'transaction_id', p_tx_id,
        'audit_log_id', v_log_id
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.process_complex_payment_v2(UUID, UUID, NUMERIC, TEXT, JSONB, TEXT, TEXT) TO authenticated;
