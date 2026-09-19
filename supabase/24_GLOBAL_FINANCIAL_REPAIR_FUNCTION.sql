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
    v_ledger_reconstructed INT := 0;
    v_balance_report JSONB;
    v_group_name TEXT;
BEGIN
    SELECT name INTO v_group_name FROM public.groups WHERE id = p_group_id;

    -- 1. RECONSTRUCT MISSING LEDGER ENTRIES
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, description, category, member_id, created_at)
    SELECT DISTINCT ON (COALESCE(c.transaction_id, 'reconstructed-' || c.id::text))
        c.group_id,
        COALESCE(c.transaction_id, 'reconstructed-' || c.id::text),
        c.amount,
        initcap(replace(c.type, '_', ' ')) || ' from ' || m.full_name,
        c.type,
        c.member_id,
        c.paid_at
    FROM public.contributions c
    JOIN public.members m ON c.member_id = m.id
    WHERE c.group_id = p_group_id
      AND c.status = 'paid'
      AND c.type NOT IN ('member_fee')
      AND NOT EXISTS (
          SELECT 1 FROM public.group_ledger l
          WHERE l.group_id = p_group_id
            AND l.transaction_id = COALESCE(c.transaction_id, 'reconstructed-' || c.id::text)
      )
    ORDER BY COALESCE(c.transaction_id, 'reconstructed-' || c.id::text), c.paid_at DESC;

    GET DIAGNOSTICS v_ledger_reconstructed = ROW_COUNT;

    -- 2. RECALCULATE MEMBER TOTALS
    FOR v_member IN SELECT id FROM public.members WHERE group_id = p_group_id
    LOOP
        UPDATE public.members m
        SET total_paid = (
                SELECT COALESCE(SUM(amount), 0) FROM public.contributions
                WHERE member_id = v_member.id AND status = 'paid' AND type IN ('contribution', 'joining_fee', 'registration_contribution')
            ) + (
                SELECT COALESCE(SUM(amount), 0) FROM public.contributions
                WHERE member_id = v_member.id AND status = 'paid' AND type = 'loan_settlement_extraction'
            ),
            total_contributions = (
                SELECT COUNT(*) FROM public.contributions
                WHERE member_id = v_member.id AND status = 'paid' AND type IN ('contribution', 'joining_fee', 'registration_contribution')
            ),
            updated_at = NOW()
        WHERE id = v_member.id;
        v_group_members_fixed := v_group_members_fixed + 1;
    END LOOP;

    -- 3. RECALCULATE LOAN TOTALS
    FOR v_loan IN SELECT id FROM public.loans WHERE group_id = p_group_id
    LOOP
        UPDATE public.loans l
        SET total_repaid = (SELECT COALESCE(SUM(amount), 0) FROM public.loan_repayments WHERE loan_id = v_loan.id),
            status = CASE
                WHEN (SELECT COALESCE(SUM(amount), 0) FROM public.loan_repayments WHERE loan_id = v_loan.id) >= l.total_to_repay - 0.01 THEN 'completed'
                WHEN (SELECT COALESCE(SUM(amount), 0) FROM public.loan_repayments WHERE loan_id = v_loan.id) > 0 THEN 'partially_paid'
                ELSE l.status
            END,
            updated_at = NOW()
        WHERE id = v_loan.id;
        v_group_loans_fixed := v_group_loans_fixed + 1; END LOOP; SELECT public.audit_and_fix_group_balance_v1(p_group_id, TRUE) INTO v_balance_report; UPDATE public.groups SET current_members = (SELECT count(*) FROM public.members WHERE group_id = p_group_id AND status != 'suspended'), updated_at = NOW() WHERE id = p_group_id; v_report := jsonb_build_object( 'group_id', p_group_id, 'group_name', v_group_name, 'ledger_entries_reconstructed', v_ledger_reconstructed, 'members_processed', v_group_members_fixed, 'loans_processed', v_group_loans_fixed, 'balance_audit', v_balance_report, 'new_member_count', (SELECT current_members FROM public.groups WHERE id = p_group_id) ); INSERT INTO public.audit_logs (actor_id, target_group_id, action, details) VALUES (p_group_id, p_group_id, 'GLOBAL_FINANCIAL_RECALCULATION_V1.2', v_report); RETURN v_report; END; $$;
