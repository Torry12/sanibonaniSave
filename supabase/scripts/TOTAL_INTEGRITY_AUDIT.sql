-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — TOTAL SYSTEM INTEGRITY AUDIT
-- Version: 1.2 (Equity Reconciled - August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_report JSONB;
    v_group RECORD;
    v_total_members INT;
    v_orphan_count INT;
    v_discrepancy_count INT := 0;
    v_audit_entry RECORD;
BEGIN
    RAISE NOTICE '🚀 STARTING TOTAL SYSTEM INTEGRITY AUDIT';

    -- 1. Verify Global Statistics
    SELECT count(*) INTO v_total_members FROM public.members;
    RAISE NOTICE 'Step 1: Total Registered Members: %', v_total_members;

    -- 2. Audit all active groups for financial drift
    FOR v_group IN SELECT id, name, balance FROM public.groups LOOP
        SELECT public.audit_and_fix_group_balance_v1(v_group.id, FALSE) INTO v_report;

        IF ABS((v_report->>'discrepancy')::numeric) > 0.01 THEN
            RAISE WARNING '❗ DISCREPANCY DETECTED: Group: %, Balance: %, Ledger Sum: %, Drift: %',
                v_group.name,
                v_group.balance,
                v_report->>'ledger_sum',
                v_report->>'discrepancy';
            v_discrepancy_count := v_discrepancy_count + 1;
        ELSE
            RAISE NOTICE '✅ CONSISTENT: Group: % (Balance: %)', v_group.name, v_group.balance;
        END IF;
    END LOOP;

    -- 3. Check for orphaned members (no group)
    SELECT count(*) INTO v_orphan_count FROM public.members WHERE group_id IS NULL;
    IF v_orphan_count > 0 THEN
        RAISE WARNING '❗ ORPHANED MEMBERS: Found % members without a valid group_id.', v_orphan_count;
    END IF;

    -- 4. Check for loan-ledger sync
    FOR v_audit_entry IN
        SELECT l.id, l.total_repaid, COALESCE(SUM(lr.amount), 0) as calc_repaid
        FROM public.loans l
        LEFT JOIN public.loan_repayments lr ON l.id = lr.loan_id
        GROUP BY l.id, l.total_repaid
        HAVING ABS(l.total_repaid - COALESCE(SUM(lr.amount), 0)) > 0.01
    LOOP
        RAISE WARNING '❗ LOAN MISMATCH: Loan % has total_repaid % but sum of repayments is %',
            v_audit_entry.id, v_audit_entry.total_repaid, v_audit_entry.calc_repaid;
        v_discrepancy_count := v_discrepancy_count + 1;
    END LOOP;

    -- 5. Check for Member Equity Integrity (Total Paid vs Transaction Log)
    FOR v_audit_entry IN
        WITH member_calc AS (
            SELECT
                member_id,
                SUM(amount) as calc_paid
            FROM public.contributions
            WHERE status = 'paid'
            AND type IN ('contribution', 'joining_fee', 'registration_contribution', 'loan_settlement_extraction')
            GROUP BY member_id
        )
        SELECT
            m.id, m.full_name, m.total_paid,
            COALESCE(c.calc_paid, 0) as calc_paid
        FROM public.members m
        LEFT JOIN member_calc c ON m.id = c.member_id
        WHERE ABS(m.total_paid - COALESCE(c.calc_paid, 0)) > 0.01
    LOOP
        RAISE WARNING '❗ EQUITY MISMATCH: Member % (%) has total_paid % but calculated sum is %',
            v_audit_entry.id, v_audit_entry.full_name, v_audit_entry.total_paid, v_audit_entry.calc_paid;
        v_discrepancy_count := v_discrepancy_count + 1;
    END LOOP;

    -- 6. Final Verdict
    IF v_discrepancy_count = 0 THEN
        RAISE NOTICE '🏁 AUDIT COMPLETE: ALL CORE SYSTEMS ARE INTEGRATED AND CONSISTENT.';
    else
        RAISE NOTICE '🏁 AUDIT COMPLETE: % DISCREPANCIES FOUND. RUN GLOBAL REPAIR.', v_discrepancy_count;
    END IF;

END $$;
