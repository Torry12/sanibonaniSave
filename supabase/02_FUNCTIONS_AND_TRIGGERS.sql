-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — SUPABASE MASTER FUNCTIONS & TRIGGERS
-- Version: 7.3 (Auto-Aligning Schema - June 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 0. SCHEMA ALIGNMENT (Ensures Ledger compatibility)
DO $$
BEGIN
    -- Force platform_ledger transaction_id to TEXT
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'platform_ledger') THEN
        IF EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'public.platform_ledger'::regclass AND attname = 'transaction_id' AND atttypid = 'uuid'::regtype) THEN
            ALTER TABLE public.platform_ledger ALTER COLUMN transaction_id TYPE TEXT;
        END IF;
    END IF;
    -- Force group_ledger transaction_id to TEXT
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'group_ledger') THEN
        IF EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'public.group_ledger'::regclass AND attname = 'transaction_id' AND atttypid = 'uuid'::regtype) THEN
            ALTER TABLE public.group_ledger ALTER COLUMN transaction_id TYPE TEXT;
        END IF;
    END IF;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- 1. UTILITY FUNCTIONS
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. ATOMIC BALANCE HELPERS
-- Increments group balance and logs to ledger
CREATE OR REPLACE FUNCTION public.increment_group_balance(
    p_group_id UUID,
    p_amount NUMERIC,
    p_description TEXT DEFAULT 'Atomic balance update',
    p_category TEXT DEFAULT 'adjustment',
    p_transaction_id TEXT DEFAULT NULL
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_new_balance NUMERIC;
BEGIN
    UPDATE public.groups
    SET balance = balance + p_amount,
        updated_at = NOW()
    WHERE id = p_group_id
    RETURNING balance INTO v_new_balance;

    IF NOT FOUND THEN RAISE EXCEPTION 'Group not found'; END IF;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category)
    VALUES (p_group_id, p_transaction_id, p_amount, v_new_balance, p_description, p_category);

    RETURN v_new_balance;
END;
$$;

-- Records platform revenue and logs to platform ledger
CREATE OR REPLACE FUNCTION public.record_platform_revenue_v1(
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id TEXT DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_last_balance NUMERIC;
BEGIN
    SELECT balance_after INTO v_last_balance
    FROM public.platform_ledger
    ORDER BY created_at DESC LIMIT 1;

    INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category)
    VALUES (p_transaction_id, p_amount, COALESCE(v_last_balance, 0) + p_amount, p_description, p_category);
END;
$$;

-- 3. CONTRIBUTION & LOAN RPCS

-- RPC: Record Contribution
CREATE OR REPLACE FUNCTION public.record_contribution_v1(
    p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_due_date DATE,
    p_paid_at TIMESTAMPTZ, p_status TEXT, p_tx_id TEXT DEFAULT NULL, p_type TEXT DEFAULT 'contribution'
 ) RETURNS JSONB AS $$
DECLARE
    v_contribution RECORD;
    v_new_balance NUMERIC;
    v_ledger_desc TEXT;
    v_ledger_cat TEXT;
    v_member_name TEXT;
BEGIN
    SELECT full_name INTO v_member_name FROM public.members WHERE id = p_member_id;

    -- 1. Create contribution record
    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, transaction_id, type)
    VALUES (p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_tx_id, p_type)
    RETURNING * INTO v_contribution;

    -- 3. Determine Ledger Context
    IF p_type = 'member_fee' THEN
        v_ledger_desc := 'Platform Member Fee (' || v_member_name || ')';
        v_ledger_cat := 'platform_fee';
    ELSE
        v_ledger_desc := initcap(replace(p_type, '_', ' ')) || ' from ' || v_member_name;
        v_ledger_cat := p_type;
    END IF;

    -- 4. Update Group Balance
    -- (Note: members table stats are handled by triggers on contributions)
    IF p_type != 'member_fee' THEN
        SELECT public.increment_group_balance(
            p_group_id,
            p_amount,
            v_ledger_desc,
            v_ledger_cat,
            v_contribution.id::text
        ) INTO v_new_balance;
    ELSE
        SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;
    END IF;

    -- 5. Platform Revenue Tracking
    IF p_type = 'member_fee' THEN
        PERFORM public.record_platform_revenue_v1(p_amount, v_ledger_desc, v_ledger_cat, v_contribution.id::text);
    END IF;

    RETURN jsonb_build_object(
        'contribution', to_jsonb(v_contribution),
        'new_balance', v_new_balance
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- RPC: Record Loan Repayment
CREATE OR REPLACE FUNCTION public.record_loan_repayment_v1(
    p_loan_id UUID, p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_payment_method TEXT DEFAULT 'bank'
) RETURNS public.loan_repayments AS $$
DECLARE
    v_repayment RECORD;
    v_member_name TEXT;
BEGIN
    SELECT full_name INTO v_member_name FROM public.members WHERE id = p_member_id;

    -- 1. Record repayment
    INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, payment_method)
    VALUES (p_loan_id, p_member_id, p_group_id, p_amount, p_payment_method)
    RETURNING * INTO v_repayment;

    -- 2. Update loan status/balance
    UPDATE public.loans
    SET total_repaid = total_repaid + p_amount,
        status = CASE
            WHEN total_repaid + p_amount >= total_to_repay THEN 'completed'::text
            ELSE 'partially_paid'::text
        END,
        updated_at = NOW()
    WHERE id = p_loan_id;

    -- 3. Update group balance and ledger
    PERFORM public.increment_group_balance(
        p_group_id,
        p_amount,
        'Loan repayment from ' || v_member_name,
        'loan_repayment',
        v_repayment.id::text
    );

    RETURN v_repayment;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 4. DISBURSEMENT RPCS (Payouts & Claims)

-- Core function for recording any disbursement (outflow)
CREATE OR REPLACE FUNCTION public.record_disbursement_v1(
    p_group_id UUID,
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id TEXT DEFAULT NULL
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    IF p_amount <= 0 THEN RAISE EXCEPTION 'Disbursement amount must be positive'; END IF;

    RETURN public.increment_group_balance(
        p_group_id,
        -p_amount,
        p_description,
        p_category,
        p_transaction_id
    );
END;
$$;

-- RPC: Complete Payout
CREATE OR REPLACE FUNCTION public.complete_payout_v1(
    p_payout_id UUID,
    p_admin_id UUID DEFAULT NULL,
    p_payout_reference TEXT DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_payout RECORD;
    v_processed_by UUID := COALESCE(p_admin_id, auth.uid());
BEGIN
    SELECT * INTO v_payout FROM public.payouts WHERE id = p_payout_id;

    IF v_payout.id IS NULL THEN RAISE EXCEPTION 'Payout not found'; END IF;
    IF v_payout.status = 'completed' THEN RETURN; END IF;

    UPDATE public.payouts
    SET status = 'completed',
        processed_at = NOW(),
        processed_by = v_processed_by,
        payout_reference = COALESCE(p_payout_reference, payout_reference),
        updated_at = NOW()
    WHERE id = p_payout_id;

    PERFORM public.record_disbursement_v1(
        v_payout.group_id,
        v_payout.amount,
        'Payout to ' || v_payout.bank_name || ' (' || v_payout.account_no || ')',
        'payout',
        p_payout_id::text
    );
END;
$$;

-- RPC: Pay Burial Claim
CREATE OR REPLACE FUNCTION public.pay_burial_claim_v1(
    p_claim_id UUID,
    p_admin_id UUID DEFAULT NULL,
    p_notes TEXT DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_claim RECORD;
    v_reviewed_by UUID := COALESCE(p_admin_id, auth.uid());
BEGIN
    SELECT * INTO v_claim FROM public.beneficiary_payout_claims WHERE id = p_claim_id;

    IF v_claim.id IS NULL THEN RAISE EXCEPTION 'Claim not found'; END IF;
    IF v_claim.status = 'paid' THEN RETURN; END IF;

    UPDATE public.beneficiary_payout_claims
    SET status = 'paid',
        reviewed_at = NOW(),
        reviewed_by = v_reviewed_by,
        admin_notes = COALESCE(p_notes, admin_notes),
        updated_at = NOW()
    WHERE id = p_claim_id;

    PERFORM public.record_disbursement_v1(
        v_claim.group_id,
        v_claim.claim_amount,
        'Burial Payout: ' || v_claim.beneficiary_name,
        'burial_claim',
        p_claim_id::text
    );
END;
$$;

-- 5. MEMBER COUNT TRIGGER (Consolidated & Non-Suspended Only)
CREATE OR REPLACE FUNCTION public.fn_sync_group_member_count()
RETURNS TRIGGER AS $$
DECLARE
    v_group_id UUID;
BEGIN
    v_group_id := COALESCE(NEW.group_id, OLD.group_id);

    UPDATE public.groups
    SET current_members = (
        SELECT count(*) FROM public.members
        WHERE group_id = v_group_id AND status != 'suspended'
    )
    WHERE id = v_group_id;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_update_member_count ON public.members;
DROP TRIGGER IF EXISTS tr_sync_group_member_count ON public.members;
CREATE TRIGGER tr_sync_group_member_count
AFTER INSERT OR UPDATE OR DELETE ON public.members
FOR EACH ROW EXECUTE FUNCTION public.fn_sync_group_member_count();

-- 6. TRIGGER ATTACHMENTS
DROP TRIGGER IF EXISTS update_groups_updated_at ON public.groups;
CREATE TRIGGER update_groups_updated_at BEFORE UPDATE ON public.groups FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

DROP TRIGGER IF EXISTS update_members_updated_at ON public.members;
CREATE TRIGGER update_members_updated_at BEFORE UPDATE ON public.members FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- 7. REPAIR FUNCTION (Can be called via RPC for manual maintenance)
CREATE OR REPLACE FUNCTION public.repair_group_data_v1(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_report JSONB := '{}'::jsonb;
BEGIN
    -- Force sync group member count
    UPDATE public.groups g
    SET current_members = (SELECT count(*) FROM public.members m WHERE m.group_id = g.id AND m.status != 'suspended')
    WHERE id = p_group_id;

    -- Force sync all members in group
    UPDATE public.members m
    SET total_paid = COALESCE((
            SELECT sum(amount) FROM public.contributions c
            WHERE c.member_id = m.id AND c.status = 'paid'
            AND c.type NOT IN ('member_fee', 'platform_fee')
        ), 0),
        total_contributions = COALESCE((
            SELECT count(*) FROM public.contributions c
            WHERE c.member_id = m.id AND c.status = 'paid'
            AND c.type NOT IN ('member_fee', 'platform_fee')
        ), 0)
    WHERE group_id = p_group_id;

    -- Consistency Check: Ledger vs Balance
    SELECT jsonb_build_object(
        'group_id', p_group_id,
        'ledger_sum', (SELECT sum(amount) FROM public.group_ledger WHERE group_id = p_group_id),
        'current_balance', (SELECT balance FROM public.groups WHERE id = p_group_id),
        'discrepancy', (SELECT sum(amount) FROM public.group_ledger WHERE group_id = p_group_id) - (SELECT balance FROM public.groups WHERE id = p_group_id)
    ) INTO v_report;

    -- Log repair action
    INSERT INTO public.audit_logs (actor_id, target_group_id, action, details)
    VALUES (COALESCE(auth.uid(), '00000000-0000-0000-0000-000000000000'::uuid), p_group_id, 'DATA_REPAIR_EXECUTED', v_report);

    RETURN v_report;
END;
$$;

-- 8. GRANTS
GRANT EXECUTE ON FUNCTION public.increment_group_balance(UUID, NUMERIC, TEXT, TEXT, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.record_contribution_v1(UUID, UUID, NUMERIC, DATE, TIMESTAMPTZ, TEXT, TEXT, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.record_loan_repayment_v1(UUID, UUID, UUID, NUMERIC, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.complete_payout_v1(UUID, UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.pay_burial_claim_v1(UUID, UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.fn_sync_group_member_count() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.repair_group_data_v1(UUID) TO authenticated, service_role;
