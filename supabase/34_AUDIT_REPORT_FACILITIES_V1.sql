-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — AUDIT REPORT FACILITIES (Accounting Best Practice)
-- Version: 1.0 (July 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. MEMBER AUDIT REPORT
CREATE OR REPLACE FUNCTION public.generate_member_audit_report_v1(p_member_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_member RECORD;
    v_behavior RECORD;
    v_contribution_stats RECORD;
    v_loan_stats RECORD;
    v_recent_logs JSONB;
BEGIN
    -- Get basic info
    SELECT * INTO v_member FROM public.members WHERE id = p_member_id;
    IF NOT FOUND THEN RETURN NULL; END IF;

    -- Get behavior tracking
    SELECT * INTO v_behavior FROM public.member_behavior_track WHERE member_id = p_member_id;

    -- Get contribution stats
    SELECT
        COUNT(*) as total_count,
        SUM(CASE WHEN status = 'paid' THEN amount ELSE 0 END) as total_paid,
        SUM(CASE WHEN status != 'paid' THEN amount ELSE 0 END) as total_outstanding,
        MAX(paid_at) as last_payment_date
    INTO v_contribution_stats
    FROM public.contributions
    WHERE member_id = p_member_id;

    -- Get loan stats
    SELECT
        COUNT(*) as loan_count,
        SUM(amount) as total_borrowed,
        SUM(total_repaid) as total_repaid,
        SUM(total_to_repay - total_repaid) as outstanding_debt
    INTO v_loan_stats
    FROM public.loans
    WHERE member_id = p_member_id AND status NOT IN ('rejected', 'cancelled');

    -- Get recent audit logs
    SELECT jsonb_agg(l) INTO v_recent_logs
    FROM (
        SELECT id, actor_id, target_member_id, target_group_id, action, details, created_at
        FROM public.audit_logs
        WHERE target_member_id = p_member_id
        ORDER BY created_at DESC
        LIMIT 10
    ) l;

    RETURN jsonb_build_object(
        'member_id', p_member_id,
        'full_name', v_member.full_name,
        'status', v_member.status,
        'joined_at', v_member.joined_at,
        'contributions', jsonb_build_object(
            'total_count', COALESCE(v_contribution_stats.total_count, 0),
            'total_paid', COALESCE(v_contribution_stats.total_paid, 0),
            'total_outstanding', COALESCE(v_contribution_stats.total_outstanding, 0),
            'last_payment', v_contribution_stats.last_payment_date
        ),
        'loans', jsonb_build_object(
            'count', COALESCE(v_loan_stats.loan_count, 0),
            'total_borrowed', COALESCE(v_loan_stats.total_borrowed, 0),
            'total_repaid', COALESCE(v_loan_stats.total_repaid, 0),
            'outstanding', COALESCE(v_loan_stats.outstanding_debt, 0)
        ),
        'behavior', jsonb_build_object(
            'score', COALESCE(v_behavior.behavior_score, 100),
            'risk_level', COALESCE(v_behavior.fraud_risk_level, 'low'),
            'consistency', COALESCE(v_behavior.payment_consistency_score, 0)
        ),
        'recent_audit_logs', COALESCE(v_recent_logs, '[]'::jsonb)
    );
END;
$$;

-- 2. GROUP AUDIT REPORT
CREATE OR REPLACE FUNCTION public.generate_group_audit_report_v1(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_group RECORD;
    v_member_stats RECORD;
    v_financial_stats RECORD;
    v_reconciliation RECORD;
    v_actuarial RECORD;
BEGIN
    SELECT * INTO v_group FROM public.groups WHERE id = p_group_id;
    IF NOT FOUND THEN RETURN NULL; END IF;

    -- Member distribution
    SELECT
        COUNT(*) as total,
        COUNT(*) FILTER (WHERE status = 'active') as active,
        COUNT(*) FILTER (WHERE status = 'probation') as probation,
        COUNT(*) FILTER (WHERE status = 'suspended') as suspended
    INTO v_member_stats
    FROM public.members
    WHERE group_id = p_group_id;

    -- Financial summary (In/Out)
    SELECT
        SUM(CASE WHEN amount > 0 THEN amount ELSE 0 END) as total_inflow,
        SUM(CASE WHEN amount < 0 THEN amount ELSE 0 END) as total_outflow
    INTO v_financial_stats
    FROM public.group_ledger
    WHERE group_id = p_group_id;

    -- Last reconciliation
    SELECT * INTO v_reconciliation
    FROM public.reconciliations
    WHERE group_id = p_group_id
    ORDER BY created_at DESC LIMIT 1;

    -- Actuarial health
    SELECT * INTO v_actuarial
    FROM public.group_actuarial_metrics
    WHERE group_id = p_group_id;

    RETURN jsonb_build_object(
        'group_id', p_group_id,
        'name', v_group.name,
        'type', v_group.type,
        'balance', v_group.balance,
        'membership', jsonb_build_object(
            'total', COALESCE(v_member_stats.total, 0),
            'active', COALESCE(v_member_stats.active, 0),
            'probation', COALESCE(v_member_stats.probation, 0),
            'suspended', COALESCE(v_member_stats.suspended, 0)
        ),
        'finances', jsonb_build_object(
            'total_inflow', COALESCE(v_financial_stats.total_inflow, 0),
            'total_outflow', COALESCE(v_financial_stats.total_outflow, 0)
        ),
        'reconciliation', jsonb_build_object(
            'last_check', v_reconciliation.created_at,
            'status', COALESCE(v_reconciliation.status, 'none'),
            'discrepancy', COALESCE(v_reconciliation.discrepancy, 0)
        ),
        'actuarial', jsonb_build_object(
            'risk_score', COALESCE(v_actuarial.composite_risk_score, 0),
            'solvency_ratio', COALESCE(v_actuarial.solvency_ratio, 0),
            'reserve_adequacy', COALESCE(v_actuarial.reserve_adequacy_pct, 0)
        )
    );
END;
$$;

-- 3. PLATFORM AUDIT REPORT
CREATE OR REPLACE FUNCTION public.generate_platform_audit_report_v1()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_total_liquidity NUMERIC;
    v_total_revenue NUMERIC;
    v_risk_summary RECORD;
    v_group_stats RECORD;
BEGIN
    SELECT COALESCE(SUM(balance), 0) INTO v_total_liquidity FROM public.groups;
    SELECT COALESCE(SUM(amount), 0) INTO v_total_revenue FROM public.platform_ledger WHERE amount > 0;

    SELECT
        COUNT(*) FILTER (WHERE fraud_risk_level = 'high') as high_risk_members,
        COUNT(*) FILTER (WHERE fraud_risk_level = 'critical') as critical_risk_members
    INTO v_risk_summary
    FROM public.member_behavior_track;

    SELECT
        COUNT(*) as total_groups,
        COUNT(*) FILTER (WHERE is_platform_suspended = true) as suspended_groups
    INTO v_group_stats
    FROM public.groups;

    RETURN jsonb_build_object(
        'generated_at', NOW(),
        'liquidity', v_total_liquidity,
        'revenue', v_total_revenue,
        'risk', jsonb_build_object(
            'high_risk_members', COALESCE(v_risk_summary.high_risk_members, 0),
            'critical_risk_members', COALESCE(v_risk_summary.critical_risk_members, 0)
        ),
        'groups', jsonb_build_object(
            'total_groups', COALESCE(v_group_stats.total_groups, 0),
            'suspended_groups', COALESCE(v_group_stats.suspended_groups, 0)
        ),
        'top_level_stats', (SELECT row_to_json(s) FROM public.platform_summary_stats s LIMIT 1)
    );
END;
$$;

-- 4. SYSTEM INTEGRITY CHECK (Accounting Best Practice)
-- This view identifies mathematical discrepancies in the system.
CREATE OR REPLACE VIEW public.audit_integrity_discrepancies AS
WITH GroupAudit AS (
    SELECT
        'Group Balance' as category,
        g.id::text as target_id,
        g.name as target_name,
        g.balance as db_value,
        COALESCE((SELECT SUM(amount) FROM public.group_ledger WHERE group_id = g.id), 0) as ledger_sum,
        (g.balance - COALESCE((SELECT SUM(amount) FROM public.group_ledger WHERE group_id = g.id), 0)) as discrepancy
    FROM public.groups g
),
MemberAudit AS (
    SELECT
        'Member TotalPaid' as category,
        m.id::text as target_id,
        m.full_name as target_name,
        m.total_paid as db_value,
        COALESCE((SELECT SUM(amount) FROM public.contributions WHERE member_id = m.id AND status = 'paid' AND type IN ('contribution', 'joining_fee')), 0) as ledger_sum,
        (m.total_paid - COALESCE((SELECT SUM(amount) FROM public.contributions WHERE member_id = m.id AND status = 'paid' AND type IN ('contribution', 'joining_fee')), 0)) as discrepancy
    FROM public.members m
),
LoanAudit AS (
    SELECT
        'Loan Repayment' as category,
        l.id::text as target_id,
        l.member_id::text as target_name,
        l.total_repaid as db_value,
        COALESCE((SELECT SUM(amount) FROM public.loan_repayments WHERE loan_id = l.id), 0) as ledger_sum,
        (l.total_repaid - COALESCE((SELECT SUM(amount) FROM public.loan_repayments WHERE loan_id = l.id), 0)) as discrepancy
    FROM public.loans l
)
SELECT * FROM GroupAudit WHERE ABS(discrepancy) > 0.01
UNION ALL
SELECT * FROM MemberAudit WHERE ABS(discrepancy) > 0.01
UNION ALL
SELECT * FROM LoanAudit WHERE ABS(discrepancy) > 0.01;

GRANT SELECT ON public.audit_integrity_discrepancies TO authenticated;
