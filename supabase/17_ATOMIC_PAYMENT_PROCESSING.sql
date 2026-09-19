-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — ATOMIC PAYMENT PROCESSING RPC
-- Version: 1.0 (High-Fidelity Transactional Integrity)
-- ─────────────────────────────────────────────────────────────────────────────

/**
 * Orchestrates a multi-allocation payment in a single atomic transaction.
 * Ensures that platform fees, loan repayments, and contributions are processed
 * together, preventing inconsistent states if a network error occurs mid-process.
 */
CREATE OR REPLACE FUNCTION public.process_complex_payment_v1(
    p_group_id UUID,
    p_member_id UUID,
    p_total_amount NUMERIC,
    p_tx_id TEXT,
    p_allocations JSONB, -- Array of {type: string, amount: numeric, loan_id: uuid?, due_date: date}
    p_method TEXT DEFAULT 'bank'
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_new_balance NUMERIC;
    v_allocation RECORD;
    v_total_allocated NUMERIC := 0;
    v_member_name TEXT;
    v_final_res JSONB;
BEGIN
    -- 0. Security & Context
    SELECT full_name INTO v_member_name FROM public.members WHERE id = p_member_id;
    IF v_member_name IS NULL THEN RAISE EXCEPTION 'Member not found'; END IF;

    -- 1. Integrity Check: Sum of allocations must match total_amount
    SELECT SUM((val->>'amount')::NUMERIC) INTO v_total_allocated
    FROM jsonb_array_elements(p_allocations) AS val;

    IF ABS(v_total_allocated - p_total_amount) > 0.01 THEN
        RAISE EXCEPTION 'Total amount % does not match sum of allocations %', p_total_amount, v_total_allocated;
    END IF;

    -- 2. Record the master payment record for audit
    INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at)
    VALUES (p_member_id, p_group_id, p_total_amount, 'contribution', p_method, p_tx_id, 'completed', NOW());

    -- 3. Process each allocation
    FOR v_allocation IN SELECT * FROM jsonb_to_recordset(p_allocations) AS x(type text, amount numeric, loan_id uuid, due_date date)
    LOOP
        IF v_allocation.amount <= 0 THEN CONTINUE; END IF;

        IF v_allocation.type = 'member_fee' THEN
            -- Record as platform revenue (does not affect group balance)
            INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, transaction_id, type)
            VALUES (p_member_id, p_group_id, v_allocation.amount, v_allocation.due_date, NOW(), 'paid', p_tx_id || '_fee', 'member_fee');

            PERFORM public.record_platform_revenue_v1(v_allocation.amount, 'Platform Member Fee from ' || v_member_name, 'platform_fee', p_tx_id);

        ELSIF v_allocation.type = 'loan_repayment' THEN
            -- Record loan repayment and increment group balance
            v_final_res := public.record_loan_repayment_v1(v_allocation.loan_id, p_member_id, p_group_id, v_allocation.amount, p_method);
            v_new_balance := (v_final_res->>'new_balance')::NUMERIC;

        ELSE
            -- Standard contribution or late fee
            v_final_res := public.record_contribution_v1(p_member_id, p_group_id, v_allocation.amount, v_allocation.due_date, NOW(), 'paid', p_tx_id, v_allocation.type);
            v_new_balance := (v_final_res->>'new_balance')::NUMERIC;
        END IF;
    END LOOP;

    -- 4. Get final balance if not updated by any allocation (e.g. only member fees paid)
    IF v_new_balance IS NULL THEN
        SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'new_balance', v_new_balance,
        'transaction_id', p_tx_id
    );
END;
$$;
