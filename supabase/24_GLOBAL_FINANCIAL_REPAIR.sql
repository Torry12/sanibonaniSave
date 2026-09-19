-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — GLOBAL FINANCIAL REPAIR
-- Version: 1.0 (Deep Data-Recalculation Utility)
-- ─────────────────────────────────────────────────────────────────────────────

/**
 * Re-synchronises all derived financial totals for a group and its members.
 * Reconstructs the state from the immutable ledger and transaction tables.
 */
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
        -- This trigger-based update effectively runs our recalculation logic
        -- But to be explicit and bypass any possible trigger disabling:
        UPDATE public.members m
        SET total_paid = (
                SELECT COALESCE(SUM(amount), 0) FROM public.contributions
                WHERE member_id = v_member.id AND status = 'paid' AND type IN ('contribution', 'joining_fee')
            ) + (
                SELECT COALESCE(SUM(amount), 0) FROM public.loan_repayments
                WHERE member_id = v_member.id
            ),
            total_contributions = (
                SELECT COUNT(*) FROM public.contributions
                WHERE member_id = v_member.id AND status = 'paid' AND type IN ('contribution', 'joining_fee')
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

    -- 5. Audit Log
    INSERT INTO public.audit_logs (actor_id, target_group_id, action, details)
    VALUES ('SYSTEM_REPAIR', p_group_id, 'GLOBAL_FINANCIAL_RECALCULATION', v_report);

    RETURN v_report;
END;
$$;
