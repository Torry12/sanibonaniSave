-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — LOAN INTEGRITY TRIGGERS
-- Version: 1.0 (Automated Loan Repayment Totals)
-- ─────────────────────────────────────────────────────────────────────────────

/**
 * Ensures that loan.total_repaid is always perfectly in sync with the
 * underlying loan_repayments records.
 * Also automatically updates loan status to 'completed' if fully repaid.
 */
CREATE OR REPLACE FUNCTION public.fn_sync_loan_repayment_total()
RETURNS TRIGGER AS $$
DECLARE
    v_loan_id UUID;
    v_total_repaid NUMERIC;
    v_total_to_repay NUMERIC;
BEGIN
    v_loan_id := COALESCE(NEW.loan_id, OLD.loan_id);

    -- 1. Calculate sum of all repayments for this loan
    SELECT COALESCE(SUM(amount), 0) INTO v_total_repaid
    FROM public.loan_repayments
    WHERE loan_id = v_loan_id;

    -- 2. Get target amount
    SELECT total_to_repay INTO v_total_to_repay FROM public.loans WHERE id = v_loan_id;

    -- 3. Update the loan record
    UPDATE public.loans
    SET total_repaid = v_total_repaid,
        status = CASE
            WHEN v_total_repaid >= v_total_to_repay THEN 'completed'::text
            WHEN v_total_repaid > 0 THEN 'partially_paid'::text
            ELSE status
        END,
        updated_at = NOW()
    WHERE id = v_loan_id;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_sync_loan_repayment_total ON public.loan_repayments;
CREATE TRIGGER tr_sync_loan_repayment_total
AFTER INSERT OR UPDATE OR DELETE ON public.loan_repayments
FOR EACH ROW EXECUTE FUNCTION public.fn_sync_loan_repayment_total();
