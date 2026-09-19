-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — DATA INTEGRITY TRIGGERS
-- Version: 1.0 (High-Accuracy Automated Totals)
-- ─────────────────────────────────────────────────────────────────────────────

/**
 * Ensures that member.total_paid and member.total_contributions are always
 * perfectly in sync with the underlying transaction records.
 * This implements the "Ledger-as-Source-of-Truth" principle server-side.
 */
CREATE OR REPLACE FUNCTION public.fn_sync_member_financials()
RETURNS TRIGGER AS $$
DECLARE
    v_member_id UUID;
    v_contrib_total NUMERIC;
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
      AND type IN ('contribution', 'joining_fee');

    -- 2. Sum up all loan repayments
    SELECT COALESCE(SUM(amount), 0) INTO v_loan_total
    FROM public.loan_repayments
    WHERE member_id = v_member_id;

    -- 3. Update the member record
    UPDATE public.members
    SET total_paid = v_contrib_total + v_loan_total,
        total_contributions = v_contrib_count,
        updated_at = NOW()
    WHERE id = v_member_id;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Attach to contributions
DROP TRIGGER IF EXISTS tr_sync_member_financials_contrib ON public.contributions;
CREATE TRIGGER tr_sync_member_financials_contrib
AFTER INSERT OR UPDATE OR DELETE ON public.contributions
FOR EACH ROW EXECUTE FUNCTION public.fn_sync_member_financials();

-- Attach to loan_repayments
DROP TRIGGER IF EXISTS tr_sync_member_financials_loan ON public.loan_repayments;
CREATE TRIGGER tr_sync_member_financials_loan
AFTER INSERT OR UPDATE OR DELETE ON public.loan_repayments
FOR EACH ROW EXECUTE FUNCTION public.fn_sync_member_financials();
