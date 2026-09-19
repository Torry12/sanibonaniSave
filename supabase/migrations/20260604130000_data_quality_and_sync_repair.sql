-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — DATA QUALITY & SYNC IMPROVEMENTS
-- Version: 1.0 (June 4, 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. EXTEND ACTUARIAL METRICS TABLE
-- These columns are present in the domain model but were missing from the master schema
ALTER TABLE public.group_actuarial_metrics ADD COLUMN IF NOT EXISTS pure_premium NUMERIC(12,2) DEFAULT 0;
ALTER TABLE public.group_actuarial_metrics ADD COLUMN IF NOT EXISTS gross_premium NUMERIC(12,2) DEFAULT 0;
ALTER TABLE public.group_actuarial_metrics ADD COLUMN IF NOT EXISTS solvency_margin_pct NUMERIC(10,2) DEFAULT 0;
ALTER TABLE public.group_actuarial_metrics ADD COLUMN IF NOT EXISTS capital_adequacy_pct NUMERIC(10,2) DEFAULT 0;
ALTER TABLE public.group_actuarial_metrics ADD COLUMN IF NOT EXISTS expected_annual_claims NUMERIC(12,2) DEFAULT 0;
ALTER TABLE public.group_actuarial_metrics ADD COLUMN IF NOT EXISTS expected_annual_claims_count NUMERIC(10,2) DEFAULT 0;
ALTER TABLE public.group_actuarial_metrics ADD COLUMN IF NOT EXISTS break_even_members INTEGER DEFAULT 0;
ALTER TABLE public.group_actuarial_metrics ADD COLUMN IF NOT EXISTS actuarial_present_value NUMERIC(12,2) DEFAULT 0;
ALTER TABLE public.group_actuarial_metrics ADD COLUMN IF NOT EXISTS insolvency_months INTEGER DEFAULT 0;

-- 2. AUTOMATIC SYNC FUNCTIONS & TRIGGERS

-- Trigger Function: Sync Member Stats (total_paid, total_contributions)
CREATE OR REPLACE FUNCTION public.fn_sync_member_stats_on_contribution()
RETURNS TRIGGER AS $$
DECLARE
    v_member_id UUID;
BEGIN
    v_member_id := COALESCE(NEW.member_id, OLD.member_id);

    UPDATE public.members m
    SET total_paid = COALESCE((
            SELECT sum(amount) FROM public.contributions
            WHERE member_id = v_member_id AND status = 'paid'
            AND type NOT IN ('member_fee', 'platform_fee')
        ), 0),
        total_contributions = (
            SELECT count(*) FROM public.contributions
            WHERE member_id = v_member_id AND status = 'paid'
            AND type NOT IN ('member_fee', 'platform_fee')
        )
    WHERE id = v_member_id;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Attach trigger to contributions
DROP TRIGGER IF EXISTS tr_sync_member_stats_on_contribution ON public.contributions;
CREATE TRIGGER tr_sync_member_stats_on_contribution
AFTER INSERT OR UPDATE OR DELETE ON public.contributions
FOR EACH ROW EXECUTE FUNCTION public.fn_sync_member_stats_on_contribution();


-- Trigger Function: Sync Group Member Count
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

-- Attach trigger to members
DROP TRIGGER IF EXISTS tr_sync_group_member_count ON public.members;
CREATE TRIGGER tr_sync_group_member_count
AFTER INSERT OR UPDATE OR DELETE ON public.members
FOR EACH ROW EXECUTE FUNCTION public.fn_sync_group_member_count();


-- 3. REPAIR FUNCTION (Can be called via RPC for manual maintenance)
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

-- 4. GRANTS
GRANT EXECUTE ON FUNCTION public.repair_group_data_v1(UUID) TO authenticated, service_role;
