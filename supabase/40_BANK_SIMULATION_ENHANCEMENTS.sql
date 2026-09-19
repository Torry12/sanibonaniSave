-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — BANK SIMULATION ENHANCEMENTS
-- Version: 2.0 (High-Speed Bulk Historical Runs - July 2026)
-- ─────────────────────────────────────────────────────────────────────────────

/**
 * High-speed RPC to generate historical data for testing and development.
 * Performs multi-month simulations in a single transaction.
 */
CREATE OR REPLACE FUNCTION public.simulate_bulk_activity_v2(
    p_group_id UUID,
    p_months INT DEFAULT 6,
    p_member_count INT DEFAULT 10
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_member_id UUID;
    v_admin_id UUID;
    v_loan_id UUID;
    v_month_idx INT;
    v_member_idx INT;
    v_contrib_amt NUMERIC;
    v_due_date DATE;
    v_paid_at TIMESTAMPTZ;
    v_res JSONB;
BEGIN
    SELECT admin_user_id, monthly_contribution INTO v_admin_id, v_contrib_amt
    FROM public.groups WHERE id = p_group_id;

    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'Group not found or missing admin_user_id';
    END IF;

    -- 1. Generate Members if not enough present
    FOR v_member_idx IN 1..p_member_count LOOP
        INSERT INTO public.members (
            group_id, full_name, email, phone, status, joined_at, member_key
        ) VALUES (
            p_group_id,
            'Sim Member ' || v_member_idx,
            'sim.' || v_member_idx || '.' || p_group_id || '@example.com',
            '071' || lpad(floor(random() * 10000000)::text, 7, '0'),
            'active',
            now() - (p_months || ' months')::interval,
            'SIM-KEY-' || p_group_id || '-' || v_member_idx
        ) ON CONFLICT (member_key) DO NOTHING;
    END LOOP;

    -- 2. Simulate Monthly Activity
    FOR v_month_idx IN 1..p_months LOOP
        v_due_date := (now() - ((p_months - v_month_idx) || ' months')::interval)::date;
        v_paid_at := v_due_date + interval '12 hours';

        FOR v_member_id IN SELECT id FROM public.members WHERE group_id = p_group_id LOOP
            -- Stochastic payment behavior
            IF random() < 0.85 THEN -- 85% pay
                PERFORM public.record_contribution_v1(
                    v_member_id, p_group_id,
                    CASE WHEN random() < 0.9 THEN v_contrib_amt ELSE v_contrib_amt / 2 END,
                    v_due_date, v_paid_at, 'paid',
                    'sim-bulk-' || v_member_id || '-' || v_month_idx,
                    'contribution'
                );
            END IF;
        END LOOP;

        -- 3. Random Loan creation every 2 months
        IF v_month_idx % 2 = 0 THEN
            SELECT id INTO v_member_id FROM public.members WHERE group_id = p_group_id ORDER BY random() LIMIT 1;
            v_loan_id := gen_random_uuid();

            INSERT INTO public.loans (
                id, member_id, group_id, amount, total_to_repay, status, monthly_repayment, start_date
            ) VALUES (
                v_loan_id, v_member_id, p_group_id, 2000.00, 2100.00, 'active', 700.00, v_due_date
            );
        END IF;
    END LOOP;

    -- 4. Final Repair
    SELECT public.recalculate_all_group_financials_v2(p_group_id) INTO v_res;

    RETURN jsonb_build_object(
        'status', 'success',
        'months_simulated', p_months,
        'repair_report', v_res
    );
END;
$$;
