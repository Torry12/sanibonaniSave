-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — SUPABASE MASTER FUNCTIONS & TRIGGERS
-- Version: 11.0 (Gold Standard Integrated - August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. SECURITY & AUTH HELPERS
CREATE OR REPLACE FUNCTION public.is_aal2()
RETURNS BOOLEAN AS $$
BEGIN
  RETURN (auth.jwt() ->> 'aal') = 'aal2';
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION public.is_platform_admin()
RETURNS BOOLEAN AS $$
BEGIN
  IF (auth.jwt() -> 'app_metadata' ->> 'role') = 'platform_admin' THEN RETURN true; END IF;
  RETURN EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'platform_admin');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION public.is_group_admin(p_group_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (SELECT 1 FROM public.groups WHERE id = p_group_id AND admin_user_id = auth.uid());
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (SELECT 1 FROM public.members WHERE group_id = p_group_id AND user_id = auth.uid());
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION public.can_access_member_storage(p_path text)
RETURNS BOOLEAN AS $$
DECLARE
    v_m_id text;
BEGIN
    v_m_id := (storage.foldername(p_path))[2];
    IF v_m_id IS NULL THEN RETURN false; END IF;
    IF public.is_platform_admin() THEN RETURN true; END IF;
    RETURN EXISTS (
        SELECT 1 FROM public.members m
        JOIN public.groups g ON m.group_id = g.id
        WHERE m.id::text = v_m_id AND (m.user_id = auth.uid() OR g.admin_user_id = auth.uid())
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE;

-- 2. UTILITY FUNCTIONS
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 3. ATOMIC ACCOUNTING CORE
DROP FUNCTION IF EXISTS public.increment_group_balance(UUID, NUMERIC, TEXT, TEXT, TEXT, UUID);
CREATE OR REPLACE FUNCTION public.increment_group_balance(
    p_group_id UUID,
    p_amount NUMERIC,
    p_description TEXT DEFAULT 'Atomic balance update',
    p_category TEXT DEFAULT 'adjustment',
    p_transaction_id TEXT DEFAULT NULL,
    p_member_id UUID DEFAULT NULL
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_new_balance NUMERIC;
    v_existing_amount NUMERIC;
BEGIN
    IF p_transaction_id IS NOT NULL THEN
        PERFORM 1 FROM public.groups WHERE id = p_group_id FOR UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND'; END IF;

        SELECT amount INTO v_existing_amount
        FROM public.group_ledger
        WHERE group_id = p_group_id AND transaction_id = p_transaction_id
        LIMIT 1;

        IF FOUND THEN
            IF v_existing_amount <> p_amount THEN
                RAISE EXCEPTION '[SYS_002] TRANSACTION_ID_REUSED_WITH_DIFFERENT_AMOUNT';
            END IF;
            SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;
            RETURN v_new_balance;
        END IF;
    END IF;

    UPDATE public.groups SET balance = balance + p_amount, updated_at = NOW()
    WHERE id = p_group_id RETURNING balance INTO v_new_balance;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND'; END IF;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id)
    VALUES (p_group_id, p_transaction_id, p_amount, v_new_balance, p_description, p_category, p_member_id);

    RETURN v_new_balance;
END;
$$;

CREATE OR REPLACE FUNCTION public.record_platform_revenue_v2(
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id TEXT DEFAULT NULL,
    p_group_id UUID DEFAULT NULL
) RETURNS JSONB AS $$
DECLARE
    v_last_balance NUMERIC;
    v_new_balance NUMERIC;
BEGIN
    SELECT COALESCE(SUM(amount), 0) INTO v_last_balance FROM public.platform_ledger;
    v_new_balance := v_last_balance + p_amount;

    INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category, group_id)
    VALUES (p_transaction_id, p_amount, v_new_balance, p_description, p_category, p_group_id);

    RETURN jsonb_build_object('amount', p_amount, 'balance_after', v_new_balance);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 4. CONTRIBUTION SYSTEM (V2)
CREATE OR REPLACE FUNCTION public.internal_post_contribution(
    p_member_id UUID,
    p_group_id UUID,
    p_amount NUMERIC,
    p_due_date DATE,
    p_paid_at TIMESTAMPTZ,
    p_status TEXT,
    p_tx_id TEXT,
    p_type TEXT,
    p_description TEXT DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_contrib RECORD;
    v_new_bal NUMERIC;
    v_m_name TEXT;
    v_l_desc TEXT;
    v_l_cat TEXT;
BEGIN
    SELECT full_name INTO v_m_name FROM public.members WHERE id = p_member_id AND group_id = p_group_id;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] Member not found in group'; END IF;

    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, transaction_id, type)
    VALUES (p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_tx_id, p_type)
    RETURNING * INTO v_contrib;

    v_l_desc := COALESCE(p_description, initcap(replace(p_type, '_', ' ')) || ' from ' || v_m_name);
    v_l_cat := CASE WHEN p_type IN ('member_fee', 'member_fee_ledger') THEN 'platform_fee' ELSE p_type END;

    IF p_status IN ('paid', 'partial') THEN
        IF p_type NOT IN ('member_fee', 'member_fee_ledger', 'registration_contribution') THEN
            v_new_bal := public.increment_group_balance(p_group_id, p_amount, v_l_desc, v_l_cat, v_contrib.id::text, p_member_id);
        ELSE
            SELECT balance INTO v_new_bal FROM public.groups WHERE id = p_group_id;
            PERFORM public.record_platform_revenue_v2(p_amount, v_l_desc, v_l_cat, v_contrib.id::text, p_group_id);
        END IF;
    ELSE
        SELECT balance INTO v_new_bal FROM public.groups WHERE id = p_group_id;
    END IF;

    RETURN jsonb_build_object('contribution', to_jsonb(v_contrib), 'new_balance', v_new_bal);
END;
$$;

CREATE OR REPLACE FUNCTION public.record_contribution_v2(
    p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_due_date DATE,
    p_paid_at TIMESTAMPTZ, p_status TEXT, p_tx_id TEXT DEFAULT NULL,
    p_type TEXT DEFAULT 'contribution', p_description TEXT DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_contrib RECORD;
    v_new_bal NUMERIC;
BEGIN
    SELECT balance INTO v_new_bal FROM public.groups WHERE id = p_group_id FOR UPDATE;
    IF p_tx_id IS NOT NULL THEN
        SELECT * INTO v_contrib FROM public.contributions WHERE group_id = p_group_id AND transaction_id = p_tx_id;
        IF FOUND THEN
            SELECT balance INTO v_new_bal FROM public.groups WHERE id = p_group_id;
            RETURN jsonb_build_object('contribution', to_jsonb(v_contrib), 'new_balance', v_new_bal, 'idempotent', true);
        END IF;
    END IF;

    RETURN public.internal_post_contribution(p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_tx_id, p_type, p_description);
END;
$$;

-- 5. LOANS
DROP FUNCTION IF EXISTS public.record_loan_repayment_v1(UUID, UUID, UUID, NUMERIC, TEXT);
CREATE OR REPLACE FUNCTION public.record_loan_repayment_v1(
    p_loan_id UUID, p_member_id UUID, p_group_id UUID, p_amount NUMERIC,
    p_payment_method TEXT DEFAULT 'bank', p_transaction_id TEXT DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_repay RECORD;
    v_m_name TEXT;
    v_new_bal NUMERIC;
BEGIN
    SELECT balance INTO v_new_bal FROM public.groups WHERE id = p_group_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND'; END IF;

    IF p_transaction_id IS NOT NULL THEN
        SELECT * INTO v_repay
        FROM public.loan_repayments
        WHERE group_id = p_group_id AND transaction_id = p_transaction_id;
        IF FOUND THEN
            SELECT balance INTO v_new_bal FROM public.groups WHERE id = p_group_id;
            RETURN jsonb_build_object('repayment', to_jsonb(v_repay), 'new_balance', v_new_bal, 'idempotent', true);
        END IF;
    END IF;

    SELECT full_name INTO v_m_name FROM public.members WHERE id = p_member_id;
    INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, payment_method, transaction_id)
    VALUES (p_loan_id, p_member_id, p_group_id, p_amount, p_payment_method, p_transaction_id)
    RETURNING * INTO v_repay;
    SELECT public.increment_group_balance(p_group_id, p_amount, 'Loan repayment from ' || v_m_name, 'loan_repayment', v_repay.id::text, p_member_id) INTO v_new_bal;
    RETURN jsonb_build_object('repayment', to_jsonb(v_repay), 'new_balance', v_new_bal);
END;
$$;

-- 6. AUDIT
DROP FUNCTION IF EXISTS public.recompute_ledger_running_balances_v2(UUID);
CREATE OR REPLACE FUNCTION public.recompute_ledger_running_balances_v2(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_entry RECORD;
    v_bal NUMERIC := 0;
    v_count INT := 0;
BEGIN
    FOR v_entry IN (SELECT id, amount FROM public.group_ledger WHERE group_id = p_group_id ORDER BY created_at ASC, id ASC) LOOP
        v_bal := v_bal + v_entry.amount;
        v_count := v_count + 1;
        UPDATE public.group_ledger SET balance_after = v_bal WHERE id = v_entry.id;
    END LOOP;
    UPDATE public.groups SET balance = v_bal WHERE id = p_group_id;
    RETURN jsonb_build_object('final_balance', v_bal, 'count', v_count);
END;
$$;

-- 6.1 AUDIT AND FIX (LEGACY WRAPPER FOR V3 COMPATIBILITY)
CREATE OR REPLACE FUNCTION public.audit_and_fix_group_balance_v1(p_group_id UUID, p_fix BOOLEAN DEFAULT FALSE)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_ledger_sum NUMERIC;
    v_current_bal NUMERIC;
    v_diff NUMERIC;
BEGIN
    SELECT COALESCE(SUM(amount), 0) INTO v_ledger_sum FROM public.group_ledger WHERE group_id = p_group_id;
    SELECT balance INTO v_current_bal FROM public.groups WHERE id = p_group_id;
    v_diff := v_current_bal - v_ledger_sum;

    IF p_fix AND ABS(v_diff) > 0.01 THEN
        UPDATE public.groups SET balance = v_ledger_sum, updated_at = NOW() WHERE id = p_group_id;
        v_current_bal := v_ledger_sum;
        v_diff := 0;
    END IF;

    RETURN jsonb_build_object(
        'group_id', p_group_id,
        'ledger_sum', v_ledger_sum,
        'current_balance', v_current_bal,
        'discrepancy', v_diff,
        'fixed', p_fix
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.recalculate_all_group_financials_v3(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_stats JSONB;
    v_recon_id UUID;
    v_report JSONB;
BEGIN
    v_recon_id := gen_random_uuid();
    SELECT public.recompute_ledger_running_balances_v2(p_group_id) INTO v_stats;

    -- Insert a record into reconciliations for forensic tracking
    INSERT INTO public.reconciliations (
        id, group_id, period_start, period_end, status,
        ledger_balance, actual_balance, discrepancy, notes, created_at
    ) VALUES (
        v_recon_id, p_group_id, NOW() - INTERVAL '365 days', NOW(), 'balanced',
        (v_stats->>'final_balance')::NUMERIC, (v_stats->>'final_balance')::NUMERIC, 0,
        'Deep ledger reconstruction performed via system repair.', NOW()
    );

    v_report := jsonb_build_object(
        'reconstruction_id', v_recon_id::text,
        'group_id', p_group_id::text,
        'entries_reconstructed', (v_stats->>'count')::int,
        'final_balance', (v_stats->>'final_balance')::text,
        'reconciliation_report', jsonb_build_object(
            'id', v_recon_id::text,
            'group_id', p_group_id::text,
            'period_start', (EXTRACT(EPOCH FROM (NOW() - INTERVAL '365 days')) * 1000)::bigint,
            'period_end', (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint,
            'status', 'balanced',
            'ledger_balance', (v_stats->>'final_balance')::text,
            'actual_balance', (v_stats->>'final_balance')::text,
            'discrepancy', '0.00',
            'notes', 'Deep ledger reconstruction performed via system repair.',
            'created_at', NOW()::text
        ),
        'timestamp', NOW()::text
    );

    RETURN v_report;
END;
$$;

-- 7. AUDIT REPORTS
DROP FUNCTION IF EXISTS public.generate_platform_audit_report_v1();
CREATE OR REPLACE FUNCTION public.generate_platform_audit_report_v1()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_liquidity NUMERIC;
    v_revenue NUMERIC;
    v_high_risk INT;
    v_critical_risk INT;
    v_total_groups INT;
    v_suspended_groups INT;
    v_total_members INT;
    v_report JSONB;
BEGIN
    SELECT COALESCE(SUM(balance), 0) INTO v_liquidity FROM public.groups;
    SELECT COALESCE(SUM(amount), 0) INTO v_revenue FROM public.platform_ledger;
    SELECT COUNT(*) INTO v_high_risk FROM public.member_behavior_track WHERE behavior_score <= 40;
    SELECT COUNT(*) INTO v_critical_risk FROM public.member_behavior_track WHERE behavior_score <= 20;
    SELECT COUNT(*) INTO v_total_groups FROM public.groups;
    SELECT COUNT(*) INTO v_suspended_groups FROM public.groups WHERE is_platform_suspended = true;
    SELECT COUNT(*) INTO v_total_members FROM public.members;

    v_report := jsonb_build_object(
        'generated_at', NOW()::text,
        'liquidity', v_liquidity::text,
        'revenue', v_revenue::text,
        'risk', jsonb_build_object(
            'high_risk_members', v_high_risk,
            'critical_risk_members', v_critical_risk
        ),
        'groups', jsonb_build_object(
            'total_groups', v_total_groups,
            'suspended_groups', v_suspended_groups
        ),
        'top_level_stats', jsonb_build_object(
            'total_groups', v_total_groups,
            'total_members', v_total_members,
            'total_liquidity', v_liquidity::text,
            'total_revenue', v_revenue::text
        )
    );

    INSERT INTO public.platform_audit_reports (generated_at, liquidity, revenue, risk, groups, top_level_stats)
    VALUES (NOW(), v_liquidity, v_revenue, v_report->'risk', v_report->'groups', v_report->'top_level_stats');

    -- Also cache in the generic audit_reports table for standard UI observation
    INSERT INTO public.audit_reports (id, report_type, content_json, generated_at, updated_at)
    VALUES ('platform', 'platform', v_report::text, NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET
        content_json = EXCLUDED.content_json,
        generated_at = EXCLUDED.generated_at,
        updated_at = EXCLUDED.updated_at;

    RETURN v_report;
END;
$$;

CREATE OR REPLACE FUNCTION public.generate_group_audit_report_v1(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_group RECORD;
    v_total INT;
    v_active INT;
    v_probation INT;
    v_suspended INT;
    v_inflow NUMERIC;
    v_outflow NUMERIC;
    v_last_check TIMESTAMPTZ;
    v_recon_status TEXT;
    v_recon_diff NUMERIC;
    v_risk INT;
    v_solvency NUMERIC;
    v_reserve NUMERIC;
    v_report JSONB;
BEGIN
    SELECT * INTO v_group FROM public.groups WHERE id = p_group_id;
    IF NOT FOUND THEN RETURN NULL; END IF;

    -- Membership
    SELECT COUNT(*) INTO v_total FROM public.members WHERE group_id = p_group_id;
    SELECT COUNT(*) INTO v_active FROM public.members WHERE group_id = p_group_id AND status = 'active';
    SELECT COUNT(*) INTO v_probation FROM public.members WHERE group_id = p_group_id AND status = 'probation';
    SELECT COUNT(*) INTO v_suspended FROM public.members WHERE group_id = p_group_id AND status = 'suspended';

    -- Finances
    SELECT COALESCE(SUM(amount), 0) INTO v_inflow FROM public.group_ledger WHERE group_id = p_group_id AND amount > 0;
    SELECT COALESCE(SUM(ABS(amount)), 0) INTO v_outflow FROM public.group_ledger WHERE group_id = p_group_id AND amount < 0;

    -- Reconciliation
    SELECT created_at, status, discrepancy INTO v_last_check, v_recon_status, v_recon_diff
    FROM public.reconciliations WHERE group_id = p_group_id ORDER BY created_at DESC LIMIT 1;

    -- Actuarial
    SELECT composite_risk_score, solvency_ratio, reserve_adequacy_pct INTO v_risk, v_solvency, v_reserve
    FROM public.group_actuarial_metrics WHERE group_id = p_group_id;

    v_report := jsonb_build_object(
        'group_id', p_group_id::text,
        'name', v_group.name,
        'type', v_group.type,
        'balance', v_group.balance::text,
        'membership', jsonb_build_object(
            'total', v_total,
            'active', v_active,
            'probation', v_probation,
            'suspended', v_suspended
        ),
        'finances', jsonb_build_object(
            'total_inflow', v_inflow::text,
            'total_outflow', v_outflow::text
        ),
        'reconciliation', jsonb_build_object(
            'last_check', v_last_check::text,
            'status', COALESCE(v_recon_status, 'none'),
            'discrepancy', COALESCE(v_recon_diff, 0)::text
        ),
        'actuarial', jsonb_build_object(
            'risk_score', COALESCE(v_risk, 0),
            'solvency_ratio', COALESCE(v_solvency, 0),
            'reserve_adequacy', COALESCE(v_reserve, 0)
        )
    );

    INSERT INTO public.audit_reports (id, report_type, target_id, content_json, generated_at, updated_at)
    VALUES ('group_' || p_group_id::text, 'group', p_group_id, v_report::text, NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET content_json = EXCLUDED.content_json, updated_at = NOW();

    RETURN v_report;
END;
$$;

CREATE OR REPLACE FUNCTION public.generate_member_audit_report_v1(p_member_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_member RECORD;
    v_behavior RECORD;
    v_total_count INT;
    v_total_paid NUMERIC;
    v_total_outstanding NUMERIC;
    v_last_payment TIMESTAMPTZ;
    v_loan_count INT;
    v_total_borrowed NUMERIC;
    v_total_repaid NUMERIC;
    v_recent_logs JSONB;
    v_report JSONB;
BEGIN
    SELECT * INTO v_member FROM public.members WHERE id = p_member_id;
    IF NOT FOUND THEN RETURN NULL; END IF;

    SELECT * INTO v_behavior FROM public.member_behavior_track WHERE member_id = p_member_id;

    -- Contributions
    SELECT COUNT(*), SUM(CASE WHEN status = 'paid' THEN amount ELSE 0 END), SUM(CASE WHEN status != 'paid' THEN amount ELSE 0 END), MAX(paid_at)
    INTO v_total_count, v_total_paid, v_total_outstanding, v_last_payment
    FROM public.contributions WHERE member_id = p_member_id;

    -- Loans
    SELECT COUNT(*), SUM(amount), SUM(total_repaid)
    INTO v_loan_count, v_total_borrowed, v_total_repaid
    FROM public.loans WHERE member_id = p_member_id AND status NOT IN ('rejected', 'cancelled');

    -- Logs
    SELECT jsonb_agg(row_to_json(l)) INTO v_recent_logs FROM (
        SELECT id, action, created_at::text FROM public.audit_logs
        WHERE target_member_id = p_member_id ORDER BY created_at DESC LIMIT 5
    ) l;

    v_report := jsonb_build_object(
        'member_id', p_member_id::text,
        'full_name', v_member.full_name,
        'status', v_member.status,
        'joined_at', v_member.joined_at::text,
        'contributions', jsonb_build_object(
            'total_count', v_total_count,
            'total_paid', COALESCE(v_total_paid, 0)::text,
            'total_outstanding', COALESCE(v_total_outstanding, 0)::text,
            'last_payment', v_last_payment::text
        ),
        'loans', jsonb_build_object(
            'count', v_loan_count,
            'total_borrowed', COALESCE(v_total_borrowed, 0)::text,
            'total_repaid', COALESCE(v_total_repaid, 0)::text,
            'outstanding', (COALESCE(v_total_borrowed, 0) - COALESCE(v_total_repaid, 0))::text
        ),
        'behavior', jsonb_build_object(
            'score', COALESCE(v_behavior.behavior_score, 100),
            'risk_level', COALESCE(v_behavior.fraud_risk_level, 'low'),
            'consistency', COALESCE(v_behavior.payment_consistency_score, 0)
        ),
        'recent_audit_logs', COALESCE(v_recent_logs, '[]'::jsonb)
    );

    INSERT INTO public.audit_reports (id, report_type, target_id, content_json, generated_at, updated_at)
    VALUES ('member_' || p_member_id::text, 'member', p_member_id, v_report::text, NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET content_json = EXCLUDED.content_json, updated_at = NOW();

    RETURN v_report;
END;
$$;

-- 8. TRIGGERS
CREATE OR REPLACE FUNCTION public.fn_sync_group_member_count()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE public.groups SET current_members = (SELECT count(*) FROM public.members WHERE group_id = COALESCE(NEW.group_id, OLD.group_id) AND status != 'suspended')
    WHERE id = COALESCE(NEW.group_id, OLD.group_id);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_sync_group_member_count ON public.members;
CREATE TRIGGER tr_sync_group_member_count AFTER INSERT OR UPDATE OR DELETE ON public.members FOR EACH ROW EXECUTE FUNCTION public.fn_sync_group_member_count();

CREATE OR REPLACE FUNCTION public.fn_trigger_ensure_admin_is_member()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.members (group_id, user_id, full_name, status, joined_at)
    VALUES (NEW.id, NEW.admin_user_id, 'Group Admin', 'active', NOW())
    ON CONFLICT (group_id, user_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_ensure_admin_is_member ON public.groups;
CREATE TRIGGER tr_ensure_admin_is_member AFTER INSERT ON public.groups FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_ensure_admin_is_member();

-- 9. AUDIT LOGGING SYSTEM
CREATE OR REPLACE FUNCTION public.log_audit_event(
    p_action TEXT,
    p_actor_id TEXT DEFAULT 'system',
    p_target_member_id UUID DEFAULT NULL,
    p_target_group_id UUID DEFAULT NULL,
    p_details JSONB DEFAULT '{}'::jsonb,
    p_related_id UUID DEFAULT NULL
) RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_id UUID;
BEGIN
    INSERT INTO public.audit_logs (actor_id, target_member_id, target_group_id, action, details, related_id)
    VALUES (p_actor_id, p_target_member_id, p_target_group_id, p_action, p_details, p_related_id)
    RETURNING id INTO v_id;
    RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.fn_member_status_audit() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status IS DISTINCT FROM NEW.status THEN
        PERFORM public.log_audit_event('MEMBER_STATUS_CHANGED', 'system', NEW.id, NEW.group_id, jsonb_build_object('old_status', OLD.status, 'new_status', NEW.status), NEW.id);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS tr_member_status_audit ON public.members;
CREATE TRIGGER tr_member_status_audit AFTER UPDATE OF status ON public.members FOR EACH ROW EXECUTE FUNCTION public.fn_member_status_audit();

CREATE OR REPLACE FUNCTION public.fn_member_document_audit() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.document_1_status IS DISTINCT FROM NEW.document_1_status THEN
        PERFORM public.log_audit_event('DOCUMENT_VERIFIED', 'system', NEW.id, NEW.group_id, jsonb_build_object('index', 1, 'status', NEW.document_1_status), NEW.id);
    END IF;
    IF OLD.document_2_status IS DISTINCT FROM NEW.document_2_status THEN
        PERFORM public.log_audit_event('DOCUMENT_VERIFIED', 'system', NEW.id, NEW.group_id, jsonb_build_object('index', 2, 'status', NEW.document_2_status), NEW.id);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS tr_member_document_audit ON public.members;
CREATE TRIGGER tr_member_document_audit AFTER UPDATE OF document_1_status, document_2_status ON public.members FOR EACH ROW EXECUTE FUNCTION public.fn_member_document_audit();

CREATE OR REPLACE FUNCTION public.fn_trigger_reconciliation_audit() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status IS DISTINCT FROM NEW.status AND NEW.status = 'resolved' THEN
        PERFORM public.log_audit_event('RECONCILIATION_RESOLVED', 'admin', NULL, NEW.group_id, jsonb_build_object('recon_id', NEW.id, 'notes', NEW.notes), NEW.id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS tr_reconciliation_audit ON public.reconciliations;
CREATE TRIGGER tr_reconciliation_audit AFTER UPDATE ON public.reconciliations FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_reconciliation_audit();

CREATE OR REPLACE FUNCTION public.reconcile_bank_transaction_v1(
    p_bank_tx_id UUID,
    p_ledger_id UUID
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_group_id UUID;
BEGIN
    SELECT group_id INTO v_group_id
    FROM public.bank_transactions
    WHERE id = p_bank_tx_id;

    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'Bank transaction % not found', p_bank_tx_id;
    END IF;

    UPDATE public.bank_transactions
    SET status = 'reconciled',
        reconciled_with = p_ledger_id
    WHERE id = p_bank_tx_id;

    PERFORM public.log_audit_event(
        'BANK_TX_RECONCILED',
        'admin',
        NULL,
        v_group_id,
        jsonb_build_object('bank_tx_id', p_bank_tx_id, 'ledger_id', p_ledger_id),
        p_bank_tx_id
    );
END;
$$;

-- 10. PLATFORM FEE & BILLING SYSTEM
CREATE OR REPLACE FUNCTION public.pay_platform_fee_v2(
    p_group_id UUID,
    p_amount NUMERIC,
    p_fee_type TEXT, -- 'monthly', 'registration'
    p_tx_id TEXT DEFAULT NULL,
    p_from_group_fund BOOLEAN DEFAULT TRUE
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_current_bal NUMERIC;
    v_allocated NUMERIC := 0;
    v_fee RECORD;
    v_group_name TEXT;
BEGIN
    -- Row lock first: serialises concurrent submissions of the same tx_id.
    SELECT name, balance INTO v_group_name, v_current_bal FROM public.groups WHERE id = p_group_id FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND: %', p_group_id;
    END IF;

    -- ── Idempotency guard ────────────────────────────────────────────────
    -- A NULL tx_id cannot be deduplicated, so fall through to normal processing;
    -- callers are expected to always supply a deterministic reference.
    IF p_tx_id IS NOT NULL AND (
        EXISTS (SELECT 1 FROM public.group_ledger   WHERE group_id = p_group_id AND transaction_id = p_tx_id)
        OR EXISTS (SELECT 1 FROM public.platform_ledger WHERE transaction_id = p_tx_id)
        OR EXISTS (SELECT 1 FROM public.platform_fees  WHERE group_id = p_group_id AND transaction_id = p_tx_id)
    ) THEN
        RETURN jsonb_build_object(
            'paid', p_amount,
            'remaining_allocated', p_amount,
            'transaction_id', p_tx_id,
            'status', 'idempotent'
        );
    END IF;

    IF p_from_group_fund THEN
        IF v_current_bal < p_amount THEN
            RAISE EXCEPTION '[FIN_002] INSUFFICIENT_FUNDS: Group balance is %', v_current_bal;
        END IF;

        -- Deduct from group
        PERFORM public.increment_group_balance(
            p_group_id,
            -p_amount,
            'Platform ' || p_fee_type || ' fee payment',
            'platform_fee',
            p_tx_id
        );
    END IF;

    -- Record platform revenue
    PERFORM public.record_platform_revenue_v2(
        p_amount,
        'Fee payment from ' || v_group_name,
        p_fee_type || '_fee',
        p_tx_id,
        p_group_id
    );

    -- Waterfall allocation for 'monthly' fees
    IF p_fee_type = 'monthly' THEN
        FOR v_fee IN (
            SELECT id, amount_due, amount_paid
            FROM public.platform_fees
            WHERE group_id = p_group_id AND status != 'paid' AND fee_type = 'monthly'
            ORDER BY due_date ASC
        ) LOOP
            IF p_amount - v_allocated <= 0 THEN EXIT; END IF;

            DECLARE
                v_to_pay NUMERIC := LEAST(v_fee.amount_due - v_fee.amount_paid, p_amount - v_allocated);
            BEGIN
                UPDATE public.platform_fees
                SET amount_paid = amount_paid + v_to_pay,
                    status = CASE WHEN (amount_paid + v_to_pay) >= amount_due THEN 'paid' ELSE status END,
                    paid_at = NOW(),
                    transaction_id = COALESCE(transaction_id, p_tx_id)
                WHERE id = v_fee.id;

                v_allocated := v_allocated + v_to_pay;
            END;
        END LOOP;
    ELSIF p_fee_type = 'registration' THEN
        UPDATE public.groups SET registration_paid = TRUE WHERE id = p_group_id;
    END IF;

    -- Update group standing
    IF NOT EXISTS (SELECT 1 FROM public.platform_fees WHERE group_id = p_group_id AND status != 'paid') THEN
        UPDATE public.groups SET fee_status = 'paid', is_platform_suspended = FALSE WHERE id = p_group_id;
    END IF;

    RETURN jsonb_build_object(
        'paid', p_amount,
        'remaining_allocated', p_amount - v_allocated,
        'transaction_id', p_tx_id,
        'status', 'processed'
    );
END;
$$;

/**
 * Automates the monthly billing for all active groups.
 * Should be run on the 1st of every month via pg_cron or external trigger.
 */
CREATE OR REPLACE FUNCTION public.automate_monthly_billing_v1()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_group RECORD;
    v_fee_amount NUMERIC;
    v_count INT := 0;
    v_monthly_fee NUMERIC;
BEGIN
    -- Get default monthly fee from platform_settings
    SELECT (value)::NUMERIC INTO v_monthly_fee FROM public.platform_settings WHERE key = 'monthly_per_member';

    FOR v_group IN (
        SELECT id, current_members FROM public.groups
        WHERE registration_paid = TRUE AND is_platform_suspended = FALSE
    ) LOOP
        v_fee_amount := v_group.current_members * COALESCE(v_monthly_fee, 10.0);

        IF v_fee_amount > 0 THEN
            INSERT INTO public.platform_fees (group_id, fee_type, amount_due, due_date, month_reference, status)
            VALUES (
                v_group.id,
                'monthly',
                v_fee_amount,
                (CURRENT_DATE + INTERVAL '1 month' - INTERVAL '1 day')::DATE, -- End of month
                date_trunc('month', CURRENT_DATE)::DATE,
                'due'
            )
            ON CONFLICT DO NOTHING; -- Unique constraint on (group_id, month_reference) recommended

            UPDATE public.groups SET fee_status = 'due' WHERE id = v_group.id AND fee_status = 'paid';
            v_count := v_count + 1;
        END IF;
    END LOOP;

    RETURN jsonb_build_object('groups_billed', v_count, 'processed_at', NOW());
END;
$$;

-- 10.1 AGGREGATE & GOVERNANCE SYNC TRIGGERS
-- Back-ported from the GROUND_ZERO baseline so master-built tiers maintain derived
-- totals server-side. groups.current_members is owned by tr_sync_group_member_count
-- above, so the baseline's duplicate counter trigger was intentionally not ported.

CREATE OR REPLACE FUNCTION public.fn_sync_single_member_financials(p_member_id UUID)
RETURNS VOID AS $$
BEGIN
    UPDATE public.members SET
        total_paid = (
            SELECT COALESCE(SUM(amount), 0)
            FROM public.contributions
            WHERE member_id = p_member_id AND status = 'paid'
            AND type IN ('contribution', 'joining_fee', 'registration_contribution', 'loan_settlement_extraction')
        ),
        total_contributions = (
            SELECT COUNT(*)
            FROM public.contributions
            WHERE member_id = p_member_id AND status = 'paid'
            AND type IN ('contribution', 'registration_contribution')
        ),
        updated_at = NOW()
    WHERE id = p_member_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.fn_trigger_sync_member_financials() RETURNS TRIGGER AS $$
DECLARE
    v_m_id UUID;
BEGIN
    v_m_id := COALESCE(NEW.member_id, OLD.member_id);
    IF v_m_id IS NOT NULL THEN
        PERFORM public.fn_sync_single_member_financials(v_m_id);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS tr_sync_member_financials_contrib ON public.contributions;
CREATE TRIGGER tr_sync_member_financials_contrib AFTER INSERT OR UPDATE OR DELETE ON public.contributions FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_sync_member_financials();

CREATE OR REPLACE FUNCTION public.fn_sync_single_loan_totals(p_loan_id UUID)
RETURNS VOID AS $$
DECLARE
    v_rep NUMERIC;
    v_target NUMERIC;
BEGIN
    SELECT COALESCE(SUM(amount), 0) INTO v_rep FROM public.loan_repayments WHERE loan_id = p_loan_id;
    SELECT total_to_repay INTO v_target FROM public.loans WHERE id = p_loan_id;
    UPDATE public.loans SET total_repaid = v_rep, status = CASE WHEN v_rep >= v_target - 0.01 THEN 'completed' WHEN v_rep > 0 THEN 'partially_paid' ELSE status END, updated_at = NOW() WHERE id = p_loan_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.fn_trigger_sync_loan_repayment_total() RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT' OR TG_OP = 'UPDATE') THEN PERFORM public.fn_sync_single_loan_totals(NEW.loan_id); END IF;
    IF (TG_OP = 'DELETE' OR (TG_OP = 'UPDATE' AND OLD.loan_id IS DISTINCT FROM NEW.loan_id)) THEN PERFORM public.fn_sync_single_loan_totals(OLD.loan_id); END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS tr_sync_loan_repayment_total ON public.loan_repayments;
CREATE TRIGGER tr_sync_loan_repayment_total AFTER INSERT OR UPDATE OR DELETE ON public.loan_repayments FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_sync_loan_repayment_total();

CREATE OR REPLACE FUNCTION public.fn_member_behavior_init() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.member_behavior_track (member_id, group_id, behavior_score, fraud_risk_level)
    VALUES (NEW.id, NEW.group_id, 100.0, 'low')
    ON CONFLICT (member_id) DO NOTHING;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS tr_member_behavior_init ON public.members;
CREATE TRIGGER tr_member_behavior_init AFTER INSERT ON public.members FOR EACH ROW EXECUTE FUNCTION public.fn_member_behavior_init();

CREATE OR REPLACE FUNCTION public.fn_member_financial_sync_on_change() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        -- No sync needed for deleted member (cascaded) or just clear?
        -- Usually we keep it.
    ELSE
        IF pg_trigger_depth() < 2 THEN
            PERFORM public.fn_sync_single_member_financials(NEW.id);
        END IF;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS tr_member_financial_sync_member ON public.members;
CREATE TRIGGER tr_member_financial_sync_member AFTER INSERT OR UPDATE OF status ON public.members FOR EACH ROW EXECUTE FUNCTION public.fn_member_financial_sync_on_change();

CREATE OR REPLACE FUNCTION public.fn_trigger_update_behavior_metrics() RETURNS TRIGGER AS $$
DECLARE
    v_m_id UUID;
    v_g_id UUID;
    v_score NUMERIC(5,2) := 100.0;
    v_risk TEXT := 'low';
    v_late_count INT;
BEGIN
    v_m_id := COALESCE(NEW.member_id, OLD.member_id);
    v_g_id := COALESCE(NEW.group_id, OLD.group_id);

    -- 1. Simple heuristic: Deduct 10 points for each overdue contribution
    SELECT COUNT(*) INTO v_late_count FROM public.contributions WHERE member_id = v_m_id AND status = 'overdue';
    v_score := (v_score - (v_late_count * 10.0))::NUMERIC(5,2);

    IF v_score < 40 THEN v_risk := 'high';
    ELSIF v_score < 70 THEN v_risk := 'medium';
    END IF;

    UPDATE public.member_behavior_track
    SET behavior_score = GREATEST(0, v_score),
        fraud_risk_level = v_risk,
        updated_at = NOW()
    WHERE member_id = v_m_id;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS tr_update_behavior_metrics ON public.contributions;
CREATE TRIGGER tr_update_behavior_metrics AFTER INSERT OR UPDATE ON public.contributions FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_update_behavior_metrics();

DROP TRIGGER IF EXISTS tr_update_behavior_metrics_loans ON public.loan_repayments;
CREATE TRIGGER tr_update_behavior_metrics_loans AFTER INSERT OR UPDATE ON public.loan_repayments FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_update_behavior_metrics();

CREATE OR REPLACE FUNCTION public.fn_trigger_sync_poll_vote_count() RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN UPDATE public.group_poll_options SET vote_count = vote_count + 1 WHERE id = NEW.option_id;
    ELSIF (TG_OP = 'DELETE') THEN UPDATE public.group_poll_options SET vote_count = vote_count - 1 WHERE id = OLD.option_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS tr_sync_poll_vote_count ON public.group_poll_votes;
CREATE TRIGGER tr_sync_poll_vote_count AFTER INSERT OR DELETE ON public.group_poll_votes FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_sync_poll_vote_count();

CREATE OR REPLACE FUNCTION public.fn_trigger_sync_poll_status() RETURNS TRIGGER AS $$
BEGIN
    -- This would normally be handled by a cron job, but we can hook into updates
    IF NEW.ends_at <= NOW() AND NEW.status = 'open' THEN
        NEW.status := 'closed';
        PERFORM public.log_audit_event('POLL_AUTO_CLOSED', 'system_gov', NULL, NEW.group_id, jsonb_build_object('poll_id', NEW.id), NEW.id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_sync_poll_status ON public.group_polls;
CREATE TRIGGER tr_sync_poll_status BEFORE UPDATE ON public.group_polls FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_sync_poll_status();

CREATE OR REPLACE FUNCTION public.fn_trigger_voting_audit() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF TG_TABLE_NAME = 'group_polls' THEN
            PERFORM public.log_audit_event('POLL_CREATED', NEW.created_by_member_id::text, NULL, NEW.group_id, jsonb_build_object('poll_id', NEW.id, 'title', NEW.title), NEW.id);
        ELSIF TG_TABLE_NAME = 'group_poll_votes' THEN
            PERFORM public.log_audit_event('VOTE_CAST', NEW.member_id::text, NEW.member_id, NEW.group_id, jsonb_build_object('poll_id', NEW.poll_id), NEW.poll_id);
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS tr_poll_audit ON public.group_polls;
CREATE TRIGGER tr_poll_audit AFTER INSERT ON public.group_polls FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_voting_audit();

DROP TRIGGER IF EXISTS tr_vote_audit ON public.group_poll_votes;
CREATE TRIGGER tr_vote_audit AFTER INSERT ON public.group_poll_votes FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_voting_audit();

-- 10.2 SIGNUP BOOTSTRAP
-- Defined for baseline parity. No trigger is attached to auth.users here, matching the
-- baseline: Supabase provisions that attachment out of band per project.
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
DECLARE
    v_full_name TEXT;
BEGIN
  v_full_name := COALESCE(NEW.raw_user_meta_data ->> 'full_name', 'New User');
  IF char_length(v_full_name) < 3 THEN v_full_name := v_full_name || ' (User)'; END IF;
  INSERT INTO public.profiles (id, full_name, email, role)
  VALUES (NEW.id, v_full_name, NEW.email, COALESCE(NEW.raw_user_meta_data ->> 'role', 'member'));
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

-- 10.3 DISBURSEMENT, RECONCILIATION & SIMULATION RPCs
-- Restored from the GROUND_ZERO baseline: these objects existed only in the
-- consolidated dashboard script and in no master, so a masters-only rebuild
-- silently dropped them. process_rosca_payout_v2 and simulate_bulk_activity_v2
-- are required by the ROSCA cycle and the Payment Sandbox (rule 6).

-- 10.3.1 DISBURSEMENT SECURITY

CREATE OR REPLACE FUNCTION public.assert_mfa_for_disbursement_v1(p_amount NUMERIC)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    IF ABS(p_amount) >= 10000.00 THEN
        IF NOT public.is_aal2() THEN
            RAISE EXCEPTION '[SEC_001] MFA_REQUIRED' USING
                DETAIL = 'High-value disbursements (>= R10,000) require Multi-Factor Authentication (AAL2).',
                HINT = 'mfa_challenge_required';
        END IF;
    END IF;
END;
$$;


CREATE OR REPLACE FUNCTION public.record_platform_revenue_v1(
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id TEXT DEFAULT NULL,
    p_group_id UUID DEFAULT NULL
) RETURNS VOID AS $$
DECLARE
    v_last_balance NUMERIC;
BEGIN
    SELECT balance_after INTO v_last_balance FROM public.platform_ledger ORDER BY created_at DESC LIMIT 1;
    INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category, group_id)
    VALUES (p_transaction_id, p_amount, COALESCE(v_last_balance, 0) + p_amount, p_description, p_category, p_group_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;


CREATE OR REPLACE FUNCTION public.record_loan_settlement_extraction_v1(
    p_member_id UUID,
    p_group_id UUID,
    p_amount NUMERIC,
    p_loan_id UUID
) RETURNS VOID AS $$
DECLARE
    v_m_name TEXT;
BEGIN
    SELECT full_name INTO v_m_name FROM public.members WHERE id = p_member_id;

    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, type, transaction_id)
    VALUES (p_member_id, p_group_id, -p_amount, CURRENT_DATE, NOW(), 'paid', 'loan_settlement_extraction', 'ext-' || p_loan_id::text);

    PERFORM public.record_loan_repayment_v1(p_loan_id, p_member_id, p_group_id, p_amount, 'wallet');

    INSERT INTO public.group_ledger (group_id, member_id, amount, balance_after, description, category, transaction_id)
    VALUES (p_group_id, p_member_id, 0, (SELECT balance FROM public.groups WHERE id = p_group_id), 'Loan settlement extraction for ' || v_m_name, 'loan_settlement', 'settle-' || p_loan_id::text);

    PERFORM public.log_audit_event('LOAN_FORCED_SETTLEMENT', 'system', p_member_id, p_group_id, jsonb_build_object('loan_id', p_loan_id, 'amount', p_amount), p_loan_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;


CREATE OR REPLACE FUNCTION public.approve_and_disburse_loan_v2(
    p_loan_id UUID,
    p_admin_id UUID,
    p_payment_method TEXT DEFAULT 'bank'
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_loan RECORD;
    v_m_name TEXT;
    v_group RECORD;
BEGIN
    SELECT * INTO v_loan FROM public.loans WHERE id = p_loan_id;
    IF v_loan.id IS NULL THEN RAISE EXCEPTION '[SYS_001] LOAN_NOT_FOUND'; END IF;
    IF v_loan.status NOT IN ('pending', 'approved') THEN RAISE EXCEPTION '[FIN_002] INVALID_STATUS: Loan cannot be disbursed from status %', v_loan.status; END IF;

    PERFORM public.assert_mfa_for_disbursement_v1(v_loan.amount);

    SELECT * INTO v_group FROM public.groups WHERE id = v_loan.group_id;
    IF v_group.balance < v_loan.amount THEN RAISE EXCEPTION '[FIN_001] INSUFFICIENT_LIQUIDITY'; END IF;

    SELECT full_name INTO v_m_name FROM public.members WHERE id = v_loan.member_id;

    UPDATE public.loans SET status = 'active', reviewed_by = p_admin_id, reviewed_at = NOW(), start_date = NOW(), updated_at = NOW() WHERE id = p_loan_id;
    PERFORM public.increment_group_balance(v_loan.group_id, -v_loan.amount, 'Loan disbursement to ' || v_m_name || ' (' || p_payment_method || ')', 'loan_disbursement', p_loan_id::text, v_loan.member_id);

    PERFORM public.log_audit_event('LOAN_DISBURSED', p_admin_id::text, v_loan.member_id, v_loan.group_id, jsonb_build_object('loan_id', p_loan_id, 'amount', v_loan.amount), p_loan_id);
END;
$$;



-- 10.3.2 DISBURSEMENTS

CREATE OR REPLACE FUNCTION public.complete_payout_v2(
    p_payout_id UUID,
    p_admin_id UUID DEFAULT NULL,
    p_payout_reference TEXT DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_payout RECORD;
    v_group RECORD;
    v_eff_admin_id UUID := COALESCE(p_admin_id, auth.uid());
BEGIN
    PERFORM id FROM public.groups WHERE id = (SELECT group_id FROM public.payouts WHERE id = p_payout_id) FOR UPDATE;

    SELECT * INTO v_payout FROM public.payouts WHERE id = p_payout_id;
    IF v_payout.id IS NULL THEN RAISE EXCEPTION '[SYS_001] PAYOUT_NOT_FOUND'; END IF;
    IF v_payout.status NOT IN ('group_approved', 'processing') THEN RAISE EXCEPTION '[FIN_002] BAD_STATUS: Payout in % cannot be completed', v_payout.status; END IF;

    PERFORM public.assert_mfa_for_disbursement_v1(v_payout.amount);

    SELECT * INTO v_group FROM public.groups WHERE id = v_payout.group_id;
    IF v_group.balance < v_payout.amount THEN RAISE EXCEPTION '[FIN_001] NO_CASH: Group balance insufficient for payout.'; END IF;

    UPDATE public.payouts SET status = 'completed', processed_by = v_eff_admin_id, processed_at = NOW(), payout_reference = COALESCE(p_payout_reference, payout_reference), updated_at = NOW() WHERE id = p_payout_id;
    PERFORM public.increment_group_balance(v_payout.group_id, -v_payout.amount, 'Disbursement Ref: ' || COALESCE(p_payout_reference, p_payout_id::text), 'payout', p_payout_id::text, v_payout.member_id);

    PERFORM public.log_audit_event('PAYOUT_COMPLETED', COALESCE(v_eff_admin_id::text, 'system_admin'), v_payout.member_id, v_payout.group_id, jsonb_build_object('payout_id', p_payout_id, 'amount', v_payout.amount), p_payout_id);
END;
$$;


CREATE OR REPLACE FUNCTION public.process_rosca_payout_v2(
    p_group_id UUID,
    p_member_id UUID,
    p_total_pot NUMERIC,
    p_loan_deductions JSONB,
    p_payout_amount NUMERIC,
    p_payout_data JSONB
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_loan RECORD;
    v_p_id UUID;
BEGIN
    PERFORM id FROM public.groups WHERE id = p_group_id FOR UPDATE;

    FOR v_loan IN (SELECT * FROM jsonb_to_recordset(p_loan_deductions) AS x(loan_id uuid, amount numeric)) LOOP
        IF v_loan.amount > 0 THEN
            PERFORM public.increment_group_balance(p_group_id, -v_loan.amount, 'ROSCA Offset', 'rosca_payout_offset', 'off-' || v_loan.loan_id::text, p_member_id);
            PERFORM public.record_loan_repayment_v1(v_loan.loan_id, p_member_id, p_group_id, v_loan.amount, 'wallet');
        END IF;
    END LOOP;

    IF p_payout_amount > 0 THEN
        INSERT INTO public.payouts (group_id, member_id, amount, bank_name, account_no, branch_code, status, payout_type)
        VALUES (p_group_id, p_member_id, p_payout_amount, p_payout_data->>'bank_name', p_payout_data->>'account_no', p_payout_data->>'branch_code', 'pending', 'rosca') RETURNING id INTO v_p_id;
    END IF;

    PERFORM public.log_audit_event('ROSCA_PAYOUT_INITIATED', 'system_rosca', p_member_id, p_group_id, jsonb_build_object('pot', p_total_pot, 'net_payout', p_payout_amount), v_p_id);

    RETURN jsonb_build_object('success', true, 'payout_id', v_p_id);
END;
$$;


CREATE OR REPLACE FUNCTION public.perform_group_reconciliation_v1(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_ledger_bal NUMERIC;
    v_actual_bal NUMERIC;
    v_diff NUMERIC;
    v_record RECORD;
BEGIN
    SELECT COALESCE(SUM(amount), 0) INTO v_ledger_bal FROM public.group_ledger WHERE group_id = p_group_id;
    SELECT balance INTO v_actual_bal FROM public.groups WHERE id = p_group_id;
    v_diff := v_actual_bal - v_ledger_bal;
    INSERT INTO public.reconciliations (group_id, period_start, period_end, status, ledger_balance, actual_balance, discrepancy, notes)
    VALUES (p_group_id, NOW() - interval '30 days', NOW(), CASE WHEN ABS(v_diff) < 0.01 THEN 'balanced' ELSE 'discrepancy_found' END, v_ledger_bal, v_actual_bal, v_diff, 'Auto-recon triggered')
    RETURNING * INTO v_record;
    RETURN to_jsonb(v_record);
END;
$$;


CREATE OR REPLACE FUNCTION public.consolidate_group_ledger_v1(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_total INT := 0;
BEGIN
    -- 1. Contributions
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT c.group_id, CAST(c.id AS TEXT), c.amount, 0, initcap(replace(c.type, '_', ' ')) || ' from ' || m.full_name, c.type, c.member_id, c.paid_at
    FROM public.contributions c JOIN public.members m ON c.member_id = m.id
    WHERE c.group_id = p_group_id AND c.status = 'paid'
    ON CONFLICT (group_id, transaction_id) DO NOTHING;
    v_total := v_total + (SELECT count(*) FROM public.group_ledger WHERE group_id = p_group_id AND transaction_id LIKE '%-%' AND created_at > NOW() - interval '5 minutes');

    -- 2. Repayments
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT lr.group_id, CAST(lr.id AS TEXT), lr.amount, 0, 'Loan repayment from ' || m.full_name, 'loan_repayment', lr.member_id, lr.paid_at
    FROM public.loan_repayments lr JOIN public.members m ON lr.member_id = m.id
    WHERE lr.group_id = p_group_id
    ON CONFLICT (group_id, transaction_id) DO NOTHING;

    -- 3. Disbursements (Payouts)
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT p.group_id, CAST(p.id AS TEXT), -p.amount, 0, 'Disbursement Ref: ' || COALESCE(p.payout_reference, p.id::text), 'payout', p.member_id, p.processed_at
    FROM public.payouts p WHERE p.group_id = p_group_id AND p.status = 'completed'
    ON CONFLICT (group_id, transaction_id) DO NOTHING;

    -- 4. Loan Disbursements
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT loan.group_id, CAST(loan.id AS TEXT), -loan.amount, 0, 'Loan disbursement to ' || m.full_name, 'loan_disbursement', loan.member_id, COALESCE(loan.reviewed_at, loan.created_at)
    FROM public.loans loan JOIN public.members m ON loan.member_id = m.id
    WHERE loan.group_id = p_group_id AND loan.status NOT IN ('pending', 'rejected', 'cancelled')
    ON CONFLICT (group_id, transaction_id) DO NOTHING;

    -- 5. Burial Claims
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT bc.group_id, CAST(bc.id AS TEXT), -bc.claim_amount, 0, 'Burial claim: ' || bc.beneficiary_name, 'claim', bc.member_id, bc.reviewed_at
    FROM public.beneficiary_payout_claims bc WHERE bc.group_id = p_group_id AND bc.status = 'paid'
    ON CONFLICT (group_id, transaction_id) DO NOTHING;

    RETURN jsonb_build_object('entries_reconstructed', v_total);
END;
$$;


-- 10.3.3 LEDGER CHECKPOINTS

CREATE OR REPLACE FUNCTION public.create_ledger_checkpoint_v1(p_group_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_latest_entry RECORD;
    v_checkpoint_id UUID;
BEGIN
    -- 1. Find the absolute latest ledger entry for this group
    SELECT * INTO v_latest_entry
    FROM public.group_ledger
    WHERE group_id = p_group_id
    ORDER BY created_at DESC, id DESC
    LIMIT 1;

    IF NOT FOUND THEN
        RAISE EXCEPTION '[SYS_001] NO_LEDGER_ENTRIES: Cannot create checkpoint for an empty ledger.';
    END IF;

    -- 2. Create the checkpoint
    INSERT INTO public.ledger_checkpoints (group_id, ledger_entry_id, balance)
    VALUES (p_group_id, v_latest_entry.id, v_latest_entry.balance_after)
    ON CONFLICT (group_id, ledger_entry_id) DO UPDATE SET balance = EXCLUDED.balance
    RETURNING id INTO v_checkpoint_id;

    PERFORM public.log_audit_event('CHECKPOINT_CREATED', 'system', NULL, p_group_id, jsonb_build_object('ledger_entry_id', v_latest_entry.id, 'balance', v_latest_entry.balance_after), v_checkpoint_id);

    RETURN v_checkpoint_id;
END;
$$;


-- 10.3.4 ADVANCED SEARCH

CREATE OR REPLACE FUNCTION public.search_members_v1(p_query TEXT)
RETURNS SETOF JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    RETURN QUERY
    SELECT jsonb_build_object(
        'member_id', m.id,
        'member_name', m.full_name,
        'group_id', m.group_id,
        'total_loan_requests', COALESCE(bt.total_loans_requested, 0),
        'overdue_loans', COALESCE(bt.overdue_loans, 0),
        'outstanding_amount', COALESCE(bt.total_outstanding_amount, 0),
        'completion_ratio', COALESCE(bt.loan_completion_rate, 0) / 100.0,
        'risk_band', COALESCE(bt.fraud_risk_level, 'low')
    )
    FROM public.members m
    LEFT JOIN public.member_behavior_track bt ON m.id = bt.member_id
    WHERE m.full_name ILIKE '%' || p_query || '%'
       OR m.id_number ILIKE '%' || p_query || '%'
    LIMIT 20;
END;
$$;


-- 10.3.5 SIMULATION TOOLS

CREATE OR REPLACE FUNCTION public.simulate_bulk_activity_v2(
    p_group_id UUID,
    p_months INT DEFAULT 6,
    p_member_count INT DEFAULT 10
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_member_id UUID;
    v_month_idx INT;
    v_member_idx INT;
    v_contrib_amt NUMERIC;
    v_due_date DATE;
    v_res JSONB;
BEGIN
    SELECT monthly_contribution INTO v_contrib_amt FROM public.groups WHERE id = p_group_id;
    FOR v_member_idx IN 1..p_member_count LOOP
        INSERT INTO public.members (group_id, full_name, email, status, joined_at, member_key)
        VALUES (p_group_id, 'Sim Member ' || v_member_idx, 'sim.' || v_member_idx || '.' || p_group_id || '@example.com', 'active', now() - (p_months || ' months')::interval, 'SIM-KEY-' || p_group_id || '-' || v_member_idx)
        ON CONFLICT (group_id, member_key) DO NOTHING;
    END LOOP;
    FOR v_month_idx IN 1..p_months LOOP
        v_due_date := (now() - ((p_months - v_month_idx) || ' months')::interval)::date;
        FOR v_member_id IN SELECT id FROM public.members WHERE group_id = p_group_id LOOP
            IF random() < 0.85 THEN
                PERFORM public.record_contribution_v2(v_member_id, p_group_id, CASE WHEN random() < 0.9 THEN v_contrib_amt ELSE v_contrib_amt / 2 END, v_due_date, v_due_date + interval '12 hours', 'paid', 'sim-bulk-' || v_member_id || '-' || v_month_idx, 'contribution');
            END IF;
        END LOOP;
    END LOOP;
    SELECT public.recalculate_all_group_financials_v3(p_group_id) INTO v_res;
    RETURN jsonb_build_object('status', 'success', 'months_simulated', p_months);
END;
$$;

-- 11. CACHE & GRANTS
NOTIFY pgrst, 'reload schema';
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO authenticated, service_role, anon;
