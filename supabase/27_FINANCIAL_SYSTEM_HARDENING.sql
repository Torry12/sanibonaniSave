-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — FINANCIAL SYSTEM HARDENING
-- Version: 1.0 (UUID Compliance & Ledger Integrity)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. FIX CONSTRAINTS
-- Allow negative amounts in contributions for extractions (settlements)
ALTER TABLE public.contributions DROP CONSTRAINT IF EXISTS contributions_amount_check;
-- Note: We still want to ensure it's not NULL, which is already handled by NOT NULL.

-- 2. FIX UUID COMPLIANCE IN RPCS
-- Update recalculate_all_group_financials_v1 to use group_id as actor_id (valid UUID)
CREATE OR REPLACE FUNCTION public.recalculate_all_group_financials_v1(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_member RECORD;
    v_loan RECORD;
    v_report JSONB;
    v_group_members_fixed INT := 0;
    v_group_loans_fixed INT := 0;
    v_balance_report JSONB;
BEGIN
    -- 1. Recalculate each member's totals
    FOR v_member IN SELECT id FROM public.members WHERE group_id = p_group_id
    LOOP
        UPDATE public.members m
        SET total_paid = (
                SELECT COALESCE(SUM(amount), 0) FROM public.contributions
                WHERE member_id = v_member.id AND status = 'paid' AND type IN ('contribution', 'joining_fee', 'registration_contribution')
            ) + (
                -- SUBTRACT extractions
                SELECT COALESCE(SUM(amount), 0) FROM public.contributions
                WHERE member_id = v_member.id AND status = 'paid' AND type = 'loan_settlement_extraction'
            ) + (
                SELECT COALESCE(SUM(amount), 0) FROM public.loan_repayments
                WHERE member_id = v_member.id
            ),
            total_contributions = (
                SELECT COUNT(*) FROM public.contributions
                WHERE member_id = v_member.id AND status = 'paid' AND type IN ('contribution', 'joining_fee', 'registration_contribution')
            )
        WHERE id = v_member.id;
        v_group_members_fixed := v_group_members_fixed + 1;
    END LOOP;

    -- 2. Recalculate each loan's totals
    FOR v_loan IN SELECT id FROM public.loans WHERE group_id = p_group_id
    LOOP
        UPDATE public.loans l
        SET total_repaid = (SELECT COALESCE(SUM(amount), 0) FROM public.loan_repayments WHERE loan_id = v_loan.id),
            status = CASE
                WHEN (SELECT COALESCE(SUM(amount), 0) FROM public.loan_repayments WHERE loan_id = v_loan.id) >= l.total_to_repay THEN 'completed'
                WHEN (SELECT COALESCE(SUM(amount), 0) FROM public.loan_repayments WHERE loan_id = v_loan.id) > 0 THEN 'partially_paid'
                ELSE l.status
            END
        WHERE id = v_loan.id;
        v_group_loans_fixed := v_group_loans_fixed + 1;
    END LOOP;

    -- 3. Audit and fix the group balance from the ledger
    SELECT public.audit_and_fix_group_balance_v1(p_group_id, TRUE) INTO v_balance_report;

    -- 4. Build Report
    v_report := jsonb_build_object(
        'group_id', p_group_id,
        'members_processed', v_group_members_fixed,
        'loans_processed', v_group_loans_fixed,
        'balance_audit', v_balance_report
    );

    -- 5. Audit Log (Fixed actor_id)
    INSERT INTO public.audit_logs (actor_id, target_group_id, action, details)
    VALUES (p_group_id, p_group_id, 'GLOBAL_FINANCIAL_RECALCULATION', v_report);

    RETURN v_report;
END;
$$;

-- Update process_rosca_payout_v1 to use p_group_id as actor_id
CREATE OR REPLACE FUNCTION public.process_rosca_payout_v1(
    p_group_id UUID,
    p_member_id UUID,
    p_total_payout NUMERIC,
    p_cycle_month INT
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_payout_id UUID;
BEGIN
    -- Record payout
    INSERT INTO public.payouts (group_id, member_id, amount, status, type, description)
    VALUES (p_group_id, p_member_id, p_total_payout, 'completed', 'rosca_payout', 'Cycle Month ' || p_cycle_month)
    RETURNING id INTO v_payout_id;

    -- Update balance
    PERFORM public.increment_group_balance(
        p_group_id,
        -p_total_payout,
        'ROSCA Payout (Month ' || p_cycle_month || ')',
        'rosca_payout',
        v_payout_id::text,
        p_member_id
    );

    -- Fixed actor_id for audit logs
    INSERT INTO public.audit_logs (actor_id, target_member_id, target_group_id, action, details)
    VALUES (p_group_id, p_member_id, p_group_id, 'ROSCA_PAYOUT_COMPLETED', jsonb_build_object('payout_id', v_payout_id, 'amount', p_total_payout));

    RETURN jsonb_build_object('success', true, 'payout_id', v_payout_id);
END;
$$;

-- 3. UPDATE INTEGRITY TRIGGER
-- Update fn_sync_member_financials to account for settlements (extractions)
CREATE OR REPLACE FUNCTION public.fn_sync_member_financials()
RETURNS TRIGGER AS $$
DECLARE
    v_member_id UUID;
    v_contrib_total NUMERIC;
    v_extraction_total NUMERIC;
    v_loan_total NUMERIC;
    v_contrib_count INT;
BEGIN
    v_member_id := COALESCE(NEW.member_id, OLD.member_id);

    -- 1. Sum up all valid contributions (principal only)
    SELECT COALESCE(SUM(amount), 0), COUNT(*)
    INTO v_contrib_total, v_contrib_count
    FROM public.contributions
    WHERE member_id = v_member_id
      AND status = 'paid'
      AND type IN ('contribution', 'joining_fee', 'registration_contribution');

    -- 2. Sum up all extractions (negative values)
    SELECT COALESCE(SUM(amount), 0) INTO v_extraction_total
    FROM public.contributions
    WHERE member_id = v_member_id
      AND status = 'paid'
      AND type = 'loan_settlement_extraction';

    -- 3. Sum up all loan repayments
    SELECT COALESCE(SUM(amount), 0) INTO v_loan_total
    FROM public.loan_repayments
    WHERE member_id = v_member_id;

    -- 4. Update the member record
    UPDATE public.members
    SET total_paid = v_contrib_total + v_extraction_total + v_loan_total,
        total_contributions = v_contrib_count,
        updated_at = NOW()
    WHERE id = v_member_id;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
