-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — MISSING FINANCIAL RPCS
-- Version: 1.0 (July 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- RPC: Record Loan Settlement Extraction
-- Extracts a loan balance from a member's total_paid contributions.
CREATE OR REPLACE FUNCTION public.record_loan_settlement_extraction_v1(
    p_member_id UUID,
    p_group_id UUID,
    p_amount NUMERIC,
    p_loan_id UUID
) RETURNS JSONB AS $$
DECLARE
    v_contribution RECORD;
    v_new_balance NUMERIC;
    v_member_name TEXT;
    v_loan RECORD;
BEGIN
    SELECT full_name INTO v_member_name FROM public.members WHERE id = p_member_id;

    SELECT * INTO v_loan FROM public.loans WHERE id = p_loan_id;
    IF v_loan.id IS NULL THEN RAISE EXCEPTION 'Loan not found'; END IF;

    -- 1. Create a negative contribution (extraction) record
    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, type, transaction_id)
    VALUES (p_member_id, p_group_id, -p_amount, CURRENT_DATE, NOW(), 'paid', 'contribution', 'settlement-' || p_loan_id::text)
    RETURNING * INTO v_contribution;

    -- 2. Update member total_paid
    UPDATE public.members
    SET total_paid = total_paid - p_amount,
        updated_at = NOW()
    WHERE id = p_member_id;

    -- 3. Update loan status to completed (since it's settled)
    UPDATE public.loans
    SET total_repaid = total_repaid + p_amount,
        status = 'completed',
        updated_at = NOW()
    WHERE id = p_loan_id;

    -- 4. Update group balance (net zero effect on cash, but ledger track)
    -- Actually, since we are moving from member savings to group income (repayment),
    -- and the group balance already includes the member savings (conceptually),
    -- we don't change the aggregate balance, but we MUST update the ledger for transparency.

    SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category)
    VALUES (p_group_id, v_contribution.id::text, -p_amount, v_new_balance, 'Loan settlement extraction for ' || v_member_name, 'loan_settlement');

    RETURN jsonb_build_object(
        'contribution', to_jsonb(v_contribution),
        'new_balance', v_new_balance
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
