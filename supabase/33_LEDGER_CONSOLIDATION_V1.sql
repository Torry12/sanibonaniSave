-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — LEDGER CONSOLIDATION & AUDITING (Accounting Best Practice)
-- Version: 1.0 (July 2026)
-- ─────────────────────────────────────────────────────────────────────────────

/**
 * Recomputes the running balances (balance_after) for all entries in a group's ledger.
 * This ensures the audit trail is mathematically consistent from inception to the latest state.
 */
CREATE OR REPLACE FUNCTION public.recompute_ledger_running_balances_v1(p_group_id UUID)
RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_entry RECORD;
    v_running_balance NUMERIC := 0;
BEGIN
    -- 1. Iterate through all ledger entries for the group, chronologically
    -- We use a cursor to ensure memory-efficient processing of large histories
    FOR v_entry IN
        SELECT id, amount
        FROM public.group_ledger
        WHERE group_id = p_group_id
        ORDER BY created_at ASC, id ASC
    LOOP
        v_running_balance := v_running_balance + v_entry.amount;

        UPDATE public.group_ledger
        SET balance_after = v_running_balance
        WHERE id = v_entry.id;
    END LOOP;

    -- 2. Synchronize the group's reported balance with the final ledger state
    UPDATE public.groups
    SET balance = v_running_balance,
        updated_at = NOW()
    WHERE id = p_group_id;

    RETURN v_running_balance;
END;
$$;

/**
 * Consolidates the group ledger by reconstructing missing entries from sub-ledgers.
 * Sub-ledgers checked: Contributions, Repayments, Payouts, Claims.
 */
CREATE OR REPLACE FUNCTION public.consolidate_group_ledger_v1(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_reconstructed_count INT := 0;
    v_final_balance NUMERIC;
    v_report JSONB;
BEGIN
    -- 1. Reconstruct from Contributions (Principal)
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT
        c.group_id,
        COALESCE(c.transaction_id, 'recon-contrib-' || c.id::text),
        c.amount,
        0, -- Initial placeholder, will be fixed by recompute
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
            AND l.transaction_id = COALESCE(c.transaction_id, 'recon-contrib-' || c.id::text)
      )
    ON CONFLICT (group_id, transaction_id) WHERE transaction_id IS NOT NULL DO NOTHING;

    GET DIAGNOSTICS v_reconstructed_count = ROW_COUNT;

    -- 2. Reconstruct from Loan Repayments
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT
        lr.group_id,
        COALESCE(lr.transaction_id, 'recon-loan-' || lr.id::text),
        lr.amount,
        0,
        'Loan repayment from ' || m.full_name,
        'loan_repayment',
        lr.member_id,
        lr.paid_at
    FROM public.loan_repayments lr
    JOIN public.members m ON lr.member_id = m.id
    WHERE lr.group_id = p_group_id
      AND NOT EXISTS (
          SELECT 1 FROM public.group_ledger l
          WHERE l.group_id = p_group_id
            AND l.transaction_id = COALESCE(lr.transaction_id, 'recon-loan-' || lr.id::text)
      )
    ON CONFLICT (group_id, transaction_id) WHERE transaction_id IS NOT NULL DO NOTHING;

    v_reconstructed_count := v_reconstructed_count + (SELECT count(*) FROM public.group_ledger WHERE group_id = p_group_id AND transaction_id LIKE 'recon-loan-%');

    -- 3. Reconstruct from Payouts (Deductions)
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT
        p.group_id,
        COALESCE(p.payout_reference, 'recon-payout-' || p.id::text),
        -p.amount,
        0,
        'Group disbursement (Ref: ' || COALESCE(p.payout_reference, p.id::text) || ')',
        'payout',
        p.member_id,
        p.processed_at
    FROM public.payouts p
    WHERE p.group_id = p_group_id
      AND p.status = 'completed'
      AND NOT EXISTS (
          SELECT 1 FROM public.group_ledger l
          WHERE l.group_id = p_group_id
            AND l.transaction_id = COALESCE(p.payout_reference, 'recon-payout-' || p.id::text)
      )
    ON CONFLICT (group_id, transaction_id) WHERE transaction_id IS NOT NULL DO NOTHING;

    -- 4. Reconstruct from Burial Claims (Deductions)
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT
        bc.group_id,
        'recon-claim-' || bc.id::text,
        -bc.claim_amount,
        0,
        'Burial claim payout: ' || bc.beneficiary_name,
        'claim',
        bc.member_id,
        bc.reviewed_at
    FROM public.burial_claims bc
    WHERE bc.group_id = p_group_id
      AND bc.status = 'paid'
      AND NOT EXISTS (
          SELECT 1 FROM public.group_ledger l
          WHERE l.group_id = p_group_id
            AND l.transaction_id = 'recon-claim-' || bc.id::text
      )
    ON CONFLICT (group_id, transaction_id) WHERE transaction_id IS NOT NULL DO NOTHING;

    -- 5. PERFORM DEEP RECOMPUTE (Accounting Consolidation)
    SELECT public.recompute_ledger_running_balances_v1(p_group_id) INTO v_final_balance;

    -- 6. RECORD FORMAL AUDIT
    SELECT public.perform_group_reconciliation_v1(p_group_id) INTO v_report;

    RETURN jsonb_build_object(
        'group_id', p_group_id,
        'final_balance', v_final_balance,
        'reconciliation_report', v_report,
        'entries_reconstructed', v_reconstructed_count
    );
END;
$$;
