-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — INTEGRITY AUDIT (Scenarios 1-5)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    r RECORD;
    v_ledger_sum NUMERIC;
    v_contrib_sum NUMERIC;
    v_loan_sum NUMERIC;
    v_bug_found BOOLEAN := FALSE;
BEGIN
    RAISE NOTICE '--- STARTING BUSINESS & FINANCIAL INTEGRITY AUDIT ---';

    -- 1. GROUP BALANCE AUDIT
    FOR r IN (SELECT id, name, balance FROM public.groups WHERE description LIKE '[SCENARIO]%') LOOP
        SELECT COALESCE(SUM(amount), 0) INTO v_ledger_sum FROM public.group_ledger WHERE group_id = r.id;

        IF ABS(v_ledger_sum - r.balance) > 0.01 THEN
            RAISE WARNING 'BUG_DETECTED|Group Balance Mismatch|Group: %|DB Balance: %|Ledger Sum: %', r.name, r.balance, v_ledger_sum;
            v_bug_found := TRUE;
        ELSE
            RAISE NOTICE 'CHECK_PASS|Group Balance|Group: %', r.name;
        END IF;
    END LOOP;

    -- 2. MEMBER TOTAL_PAID AUDIT
    FOR r IN (
        SELECT m.id, m.full_name, m.total_paid, g.name as group_name
        FROM public.members m
        JOIN public.groups g ON m.group_id = g.id
        WHERE g.description LIKE '[SCENARIO]%'
    ) LOOP
        -- total_paid should include contributions and joining fees, but EXCLUDE platform fees (as per AGENTS.md)
        SELECT COALESCE(SUM(amount), 0) INTO v_contrib_sum
        FROM public.contributions
        WHERE member_id = r.id
        AND status = 'paid'
        AND type IN ('contribution', 'joining_fee');

        IF ABS(v_contrib_sum - r.total_paid) > 0.01 THEN
            RAISE WARNING 'BUG_DETECTED|Member TotalPaid Mismatch|Member: % (%|Group: %)|DB TotalPaid: %|Contrib Sum: %', r.full_name, r.id, r.group_name, r.total_paid, v_contrib_sum;
            v_bug_found := TRUE;
        END IF;
    END LOOP;

    -- 3. LOAN REPAYMENT AUDIT
    FOR r IN (
        SELECT l.id, l.total_repaid, m.full_name, g.name as group_name
        FROM public.loans l
        JOIN public.members m ON l.member_id = m.id
        JOIN public.groups g ON l.group_id = g.id
        WHERE g.description LIKE '[SCENARIO]%'
    ) LOOP
        SELECT COALESCE(SUM(amount), 0) INTO v_loan_sum FROM public.loan_repayments WHERE loan_id = r.id;

        IF ABS(v_loan_sum - r.total_repaid) > 0.01 THEN
            RAISE WARNING 'BUG_DETECTED|Loan Repayment Mismatch|LoanID: %|Borrower: %|DB Repaid: %|Repayment Sum: %', r.id, r.full_name, r.total_repaid, v_loan_sum;
            v_bug_found := TRUE;
        END IF;
    END LOOP;

    -- 4. PLATFORM FEE AUDIT
    -- Verify that platform_ledger entries exist for every scenario group
    FOR r IN (SELECT id, name FROM public.groups WHERE description LIKE '[SCENARIO]%') LOOP
        IF NOT EXISTS (SELECT 1 FROM public.platform_ledger WHERE group_id = r.id AND category = 'registration_fee') THEN
            RAISE WARNING 'BUG_DETECTED|Missing Platform Registration Fee|Group: %', r.name;
            v_bug_found := TRUE;
        END IF;
    END LOOP;

    IF NOT v_bug_found THEN
        RAISE NOTICE '--- AUDIT COMPLETE: NO FINANCIAL BUGS DETECTED ---';
    ELSE
        RAISE NOTICE '--- AUDIT COMPLETE: BUGS FOUND (See Warnings) ---';
    END IF;
END;
$$;
