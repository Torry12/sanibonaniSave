-- SYNC_DELTA_20261030B.sql
-- Streamlines generate_group_audit_report_v1 with filtered aggregates.
-- The report shape, authorization checks, and persisted snapshot are unchanged.
--
-- Run P.0 first. Apply P.1 only when the current body is PRE; skip P.1 when POST.
-- Stop if P.0 reports MISSING or NEITHER and inspect the tier before writing.
-- PRE is the secured body installed by SYNC_DELTA_20261027; apply that delta first on Cloud.

-- P.0  Read-only body fingerprint pre-flight.
WITH actual AS (
    SELECT p.prosrc
    FROM (SELECT to_regprocedure('public.generate_group_audit_report_v1(uuid)') AS oid) target
    LEFT JOIN pg_proc p ON p.oid = target.oid
)
SELECT prosrc IS NOT NULL AS defined,
       CASE
           WHEN prosrc IS NULL THEN 'MISSING - inspect before writing'
           WHEN md5(btrim(regexp_replace(replace(prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g')))
                = '4b766cca85bac7c9c5c2bba3aee81352' THEN 'PRE - expected'
           WHEN md5(btrim(regexp_replace(replace(prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g')))
                = '58a02ed00526d3437709e76fa2304e37' THEN 'POST - already applied'
           ELSE 'NEITHER - inspect before writing'
       END AS verdict,
       CASE WHEN prosrc IS NULL THEN NULL
            ELSE md5(btrim(regexp_replace(replace(prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g')))
       END AS body_hash
FROM actual;
-- Expected: PRE / 4b766cca85bac7c9c5c2bba3aee81352 or POST / 58a02ed00526d3437709e76fa2304e37.

-- P.1  Replace the body with the canonical filtered-aggregate implementation.
CREATE OR REPLACE FUNCTION public.generate_group_audit_report_v1(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
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

    -- SECURITY DEFINER: the ledger flows, reconciliation and actuarial position this report
    -- assembles sit outside the caller's RLS bubble, so repeat the group-admin check here.
    IF NOT (public.is_platform_admin() OR public.is_group_admin(p_group_id)) THEN
        RAISE EXCEPTION '[SEC_002] AUDIT_ACCESS_DENIED' USING
            DETAIL = 'A group audit report may only be read by that group''s administrator or a platform administrator.',
            HINT = 'group_admin_or_platform_admin_required';
    END IF;

    -- Aggregate membership and ledger totals in one scan per table.
    SELECT COUNT(*),
           COUNT(*) FILTER (WHERE status = 'active'),
           COUNT(*) FILTER (WHERE status = 'probation'),
           COUNT(*) FILTER (WHERE status = 'suspended')
      INTO v_total, v_active, v_probation, v_suspended
      FROM public.members
     WHERE group_id = p_group_id;

    SELECT COALESCE(SUM(amount) FILTER (WHERE amount > 0), 0),
           COALESCE(SUM(ABS(amount)) FILTER (WHERE amount < 0), 0)
      INTO v_inflow, v_outflow
      FROM public.group_ledger
     WHERE group_id = p_group_id;

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

-- P.2  Refresh PostgREST's function cache after the body replacement.
SELECT pg_notify('pgrst', 'reload schema');

-- P.3  Read-only verification; expect the POST hash.
SELECT md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) AS body_hash
FROM pg_proc p
WHERE p.oid = to_regprocedure('public.generate_group_audit_report_v1(uuid)');
-- Expected: 58a02ed00526d3437709e76fa2304e37.
