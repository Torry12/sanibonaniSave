-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — MASTER ONLINE SYNC (V9.1 HARDENING)
-- Version: 1.0 (August 2, 2026)
-- ─────────────────────────────────────────────────────────────────────────────
-- Purpose: Bring the production/cloud database to absolute parity with the
-- hardened local environment. Resolves RLS recursion, restores audit tools,
-- and enforces MFA on high-value disbursements.

-- 1. SECURITY & IDENTITY HARDENING
CREATE OR REPLACE FUNCTION public.is_platform_admin()
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_role text;
  v_email text;
BEGIN
  v_role := auth.jwt() -> 'app_metadata' ->> 'role';
  IF v_role = 'platform_admin' THEN RETURN true; END IF;
  v_email := LOWER(COALESCE(auth.jwt() ->> 'email', ''));
  IF v_email = 'torrymsimango@gmail.com' THEN RETURN true; END IF;
  RETURN EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'platform_admin');
END;
$$;

CREATE OR REPLACE FUNCTION public.is_group_admin(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
STABLE
AS $$
    SELECT EXISTS (SELECT 1 FROM public.groups WHERE id = p_group_id AND admin_user_id = auth.uid());
$$;

CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
STABLE
AS $$
    SELECT EXISTS (SELECT 1 FROM public.members WHERE group_id = p_group_id AND user_id = auth.uid());
$$;

-- 2. RE-APPLY RLS BYPASS (FORCE REFRESH)
DO $$
DECLARE v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Platform Admin Bypass" ON public.%I', v_table);
        EXECUTE format('CREATE POLICY "Platform Admin Bypass" ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin())', v_table);
    END LOOP;
END $$;

-- 3. AUDIT REPORT FACILITIES
CREATE OR REPLACE FUNCTION public.generate_group_audit_report_v1(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
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
    SELECT COUNT(*) as total, COUNT(*) FILTER (WHERE status = 'active') as active, COUNT(*) FILTER (WHERE status = 'probation') as probation, COUNT(*) FILTER (WHERE status = 'suspended') as suspended
    INTO v_member_stats FROM public.members WHERE group_id = p_group_id;
    SELECT SUM(CASE WHEN amount > 0 THEN amount ELSE 0 END) as total_inflow, SUM(CASE WHEN amount < 0 THEN amount ELSE 0 END) as total_outflow
    INTO v_financial_stats FROM public.group_ledger WHERE group_id = p_group_id;
    SELECT * INTO v_reconciliation FROM public.reconciliations WHERE group_id = p_group_id ORDER BY created_at DESC LIMIT 1;
    SELECT * INTO v_actuarial FROM public.group_actuarial_metrics WHERE group_id = p_group_id;
    RETURN jsonb_build_object(
        'group_id', p_group_id,
        'name', v_group.name,
        'type', v_group.type,
        'balance', v_group.balance,
        'membership', jsonb_build_object('total', COALESCE(v_member_stats.total, 0), 'active', COALESCE(v_member_stats.active, 0), 'probation', COALESCE(v_member_stats.probation, 0), 'suspended', COALESCE(v_member_stats.suspended, 0)),
        'finances', jsonb_build_object('total_inflow', COALESCE(v_financial_stats.total_inflow, 0), 'total_outflow', COALESCE(v_financial_stats.total_outflow, 0)),
        'reconciliation', jsonb_build_object('last_check', v_reconciliation.created_at, 'status', COALESCE(v_reconciliation.status, 'none'), 'discrepancy', COALESCE(v_reconciliation.discrepancy, 0)),
        'actuarial', jsonb_build_object('risk_score', COALESCE(v_actuarial.composite_risk_score, 0), 'solvency_ratio', COALESCE(v_actuarial.solvency_ratio, 0), 'reserve_adequacy', COALESCE(v_actuarial.reserve_adequacy_pct, 0))
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.generate_platform_audit_report_v1()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_total_liquidity NUMERIC;
    v_total_revenue NUMERIC;
    v_risk_summary RECORD;
    v_group_stats RECORD;
BEGIN
    SELECT COALESCE(SUM(balance), 0) INTO v_total_liquidity FROM public.groups;
    SELECT COALESCE(SUM(amount), 0) INTO v_total_revenue FROM public.platform_ledger WHERE amount > 0;
    SELECT COUNT(*) FILTER (WHERE fraud_risk_level = 'high') as high_risk_members, COUNT(*) FILTER (WHERE fraud_risk_level = 'critical') as critical_risk_members
    INTO v_risk_summary FROM public.member_behavior_track;
    SELECT COUNT(*) as total_groups, COUNT(*) FILTER (WHERE is_platform_suspended = true) as suspended_groups
    INTO v_group_stats FROM public.groups;
    RETURN jsonb_build_object(
        'generated_at', NOW(),
        'liquidity', v_total_liquidity,
        'revenue', v_total_revenue,
        'risk', jsonb_build_object('high_risk_members', COALESCE(v_risk_summary.high_risk_members, 0), 'critical_risk_members', COALESCE(v_risk_summary.critical_risk_members, 0)),
        'groups', jsonb_build_object('total_groups', COALESCE(v_group_stats.total_groups, 0), 'suspended_groups', COALESCE(v_group_stats.suspended_groups, 0))
    );
END;
$$;

GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO authenticated;

-- 4. SECURITY GAURDS
CREATE OR REPLACE FUNCTION public.assert_mfa_for_disbursement_v1(p_amount NUMERIC)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    IF ABS(p_amount) >= 10000.00 THEN
        IF COALESCE(auth.jwt() ->> 'aal', 'aal1') != 'aal2' THEN
            RAISE EXCEPTION 'MFA_REQUIRED' USING
                DETAIL = 'High-value disbursements (>= R10,000) require Multi-Factor Authentication (AAL2).',
                HINT = 'mfa_challenge_required';
        END IF;
    END IF;
END;
$$;

-- 5. RELOAD PostgREST
NOTIFY pgrst, 'reload schema';
