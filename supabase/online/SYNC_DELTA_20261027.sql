-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave - DELTA 2026-10-27  (guard the four audit RPCs, single-pass
-- platform report, withdraw anon + PUBLIC execute)
-- Target: Supabase Cloud (Dashboard SQL editor) and, unchanged, the Local tier.
-- Hand-maintained. SYNC_DELTA_20261014/21/22/26/26B are applied history - do not
-- re-run them and do not edit them to describe these changes.
-- ─────────────────────────────────────────────────────────────────────────────
-- WHAT THIS DOES
--   P.1  replaces four function BODIES, signatures untouched, copied verbatim
--        from supabase/02_FUNCTIONS_AND_TRIGGERS.sql:
--          generate_platform_audit_report_v1()      - refuses a caller who is not
--                                                     a platform admin, and folds
--                                                     three scans of public.groups
--                                                     and two of
--                                                     member_behavior_track into
--                                                     one pass each.
--          generate_group_audit_report_v1(UUID)     - requires platform admin or
--                                                     that group's admin.
--          generate_member_audit_report_v1(UUID)    - requires the member, that
--                                                     member's group admin, or a
--                                                     platform admin.
--          audit_and_fix_group_balance_v1(UUID,BOOL)- requires platform admin or
--                                                     that group's admin; with
--                                                     p_fix it rewrites
--                                                     public.groups.balance.
--        Each refusal is `RAISE EXCEPTION '[SEC_002] ...'`, the same convention as
--        the [SEC_001] MFA guard, so the app surfaces one actionable line.
--   P.2  withdraws EXECUTE on those four functions from PUBLIC and from anon.
--        authenticated and service_role keep their explicit grants.
--   P.3  verifies. P.3.a to P.3.d are read-only; P.3.e is one optional live probe
--        that must run in its own session after the apply commits. There is no anon
--        probe: see P.3.e for why.
--
-- WHAT THIS DOES NOT DO
--   No table or column change, no data rewrite, no RLS policy change, no DROP, no
--   signature change, no other GRANT/REVOKE, no index. The bodies P.1 installs
--   write exactly the rows the old ones wrote: one platform_audit_reports row plus
--   the audit_reports 'platform' upsert per platform run, and one audit_reports
--   upsert per group/member run.
--
-- WHY (the defect)
--   All four are SECURITY DEFINER, so RLS never filters what they read, and
--   supabase/03_SECURITY_AND_RLS.sql section 2 hands EXECUTE to every role with
--   `GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO anon, authenticated,
--   service_role` - on top of the EXECUTE-to-PUBLIC that PostgreSQL grants when a
--   function is created. PostgREST therefore exposed them over
--   /rest/v1/rpc/<name> to any caller holding the anon key, with no JWT, and a
--   guard written with auth.uid() could never have passed for such a request:
--     - rpc/generate_platform_audit_report_v1 with no credentials inserted two
--       rows per call and echoed every group's balance, the platform revenue total
--       and the count of flagged members. The Platform Audit tab is supposed to be
--       an admin action; it was an unauthenticated, unthrottled write endpoint.
--     - rpc/generate_group_audit_report_v1 and ..._member_audit_report_v1 returned
--       any group's ledger flows, reconciliation and actuarial position, or any
--       member's name, contribution and loan position, for a UUID the caller only
--       had to have - and public groups are enumerable through the Tier-1
--       discovery policy.
--     - rpc/audit_and_fix_group_balance_v1(id, true) overwrote any group's balance
--       straight from its ledger: an unauthenticated write into the balance
--       invariant that 20261022 spent a whole delta closing.
--   P.0.b measured anon_execute=true and public_execute=true on all four on the
--   Local tier on 2026-10-27.
--   The client side of the same path is already hardened: the RPCs' NULL answer for
--   an unknown id now raises a plain "no member has the id ..." instead of a
--   serializer exception (SupabaseAuditDataSourceImpl), and PlatformAuditViewModel
--   reads the cached/latest snapshot instead of regenerating on every open.
--
-- ORDER OF WORK
--   1. Run P.0 on its own. It writes nothing. P.0.a must report four MATCH rows
--      against the pre-delta hashes below. If it instead prints the four post
--      hashes printed in P.3.a, this delta already ran on that tier - go to P.3.
--      DRIFT means the tier carries a hot fix this file does not know about: port
--      it into supabase/02_FUNCTIONS_AND_TRIGGERS.sql first. ABSENT means the
--      function is not installed at all.
--   2. Run P.1, then P.2. One paste is fine, in that order.
--   3. Run P.3.a to P.3.d. Expect four post-hash MATCH, the ACL matrix
--      anon=NO / public=NO / authenticated=YES / service_role=YES, and
--      guard_before_write=YES on all four bodies.
--   4. P.3.e is optional, is the only section that could write, and must run in a
--      new session once P.1/P.2 have committed. Never leave it under P.1/P.2 in one
--      editor run. Verify the anon revoke with the external curl in P.3.e, not from
--      SQL - `SET LOCAL ROLE anon` plus one of these calls crashes this image.
--
-- TIER NOTES
--   - LOCAL IS APPLIED as of 2026-10-27 (docker supabase_db_aszmodkqpafwtnfufmow).
--     P.0 measured it at the four pre hashes, SEC_002 absent, anon + PUBLIC execute
--     granted, and is_platform_admin()/is_group_admin(uuid)/auth.uid() present; P.1
--     and P.2 then ran and P.3.a/P.3.b/P.3.d report the expected post state: four
--     POST hashes, guarded=YES, anon=NO / public=NO / authenticated=YES /
--     service_role=YES, guard_before_write=YES, and 0 rows in
--     platform_audit_reports and audit_reports. Re-running this file there is a
--     no-op: P.0.a will print the four post hashes.
--   - The guards were then proven behaviourally on Local with read-only calls: the
--     audit RPC as a real group admin returned its report, and the same RPC with a
--     foreign `sub` claim answered
--     `ERROR: [SEC_002] AUDIT_ACCESS_DENIED ... HINT: group_admin_or_platform_admin_required`.
--     The anon leg was not testable from SQL on this image (P.3.e) and is covered by
--     P.3.b, which measures the committed ACL directly.
--   - CLOUD IS NOT APPLIED. It has never been guarded. Run P.0 there first.
--   - Do not rehearse P.1/P.2 by wrapping this file in BEGIN ... ROLLBACK and piping
--     it at psql. On 2026-10-27 two such wrappers were aborted cleanly by a backend
--     crash (the tier re-measured at its pre hashes), and a third committed anyway:
--     the ROLLBACK was reached, printed, and the new bodies and ACLs were still
--     durable. A rollback wrapper is not a rehearsal. Rehearse on a scratch tier, or
--     run P.0 to P.3.d as written and read the verdicts.
--   - CREATE OR REPLACE FUNCTION keeps the OID, so it keeps the ACL: re-running
--     P.1 after P.2 does not hand EXECUTE back to anon. What does is (a) any script
--     that re-runs the section-2 blanket GRANT EXECUTE ON ALL FUNCTIONS, and (b) a
--     DROP then CREATE, which resets to PostgreSQL's default PUBLIC execute. That
--     is why the same revoke is written into supabase/03_SECURITY_AND_RLS.sql as
--     section 2b, immediately after the blanket grant, so every full rebuild of
--     either tier re-issues it.
--   - Do NOT reach this state by re-applying supabase/01_DATABASE_SCHEMA.sql: it
--     drops and recreates tables. This delta is the only safe path to it.
--   - After P.2, an audit screen opened by someone who is not that group's admin,
--     that member, or a platform admin shows the [SEC_002] line instead of a
--     report. That refusal is the fix, not a regression: the app-side tabs are all
--     behind admin or member auth already.
--
-- FINGERPRINT METHOD (same as the three previous deltas)
--   md5(btrim(regexp_replace(replace(prosrc, E'\r',''), E'[[:space:]]+',' ', 'g')))
--   over prosrc only, so CRLF vs LF and indentation cannot change the hash.
--   The pre hashes are what supabase/02_FUNCTIONS_AND_TRIGGERS.sql carried at
--   HEAD before this change, and they are what Local measures today:
--     generate_platform_audit_report_v1   4f347198f963278542e6f3d4252abb93
--     generate_group_audit_report_v1      f11e72d0a9c2ef160476d5535162511c
--     generate_member_audit_report_v1     29f91d447fce44e9aaeec8175d8de816
--     audit_and_fix_group_balance_v1      cffdaec2419bcf34cca2f1a658ac6540

-- ─────────────────────────────────────────────────────────────────────────────
-- P.0  PRE-FLIGHT - READ ONLY.  Run this first, on its own.
-- ─────────────────────────────────────────────────────────────────────────────

-- P.0.a  The four bodies this delta replaces. Expect four MATCH on a tier that
--        still needs it.
WITH expected(name, hash) AS (VALUES
  ('generate_platform_audit_report_v1',   '4f347198f963278542e6f3d4252abb93'),
  ('generate_group_audit_report_v1',      'f11e72d0a9c2ef160476d5535162511c'),
  ('generate_member_audit_report_v1',     '29f91d447fce44e9aaeec8175d8de816'),
  ('audit_and_fix_group_balance_v1',      'cffdaec2419bcf34cca2f1a658ac6540')
), actual AS (
  SELECT e.name, e.hash AS want,
         (SELECT md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g')))
            FROM pg_proc p JOIN pg_namespace s ON s.oid = p.pronamespace
           WHERE s.nspname = 'public' AND p.proname = e.name) AS got
    FROM expected e
)
SELECT name, COALESCE(got, 'ABSENT') AS installed_body,
       CASE WHEN got IS NULL THEN 'ABSENT' WHEN got = want THEN 'MATCH' ELSE 'DRIFT' END AS verdict
  FROM actual ORDER BY name;

-- P.0.b  Who may call them today, and whether the body already refuses. Expect
--        guarded=f and anon_execute=t on a tier that needs this delta; the four
--        authed/service_role columns must stay t after P.2.
SELECT p.proname,
       (position('SEC_002' in p.prosrc) > 0)               AS guarded,
       has_function_privilege('anon',          p.oid, 'EXECUTE') AS anon_execute,
       has_function_privilege('authenticated', p.oid, 'EXECUTE') AS authed_execute,
       has_function_privilege('service_role',  p.oid, 'EXECUTE') AS svc_execute,
       has_function_privilege('public',        p.oid, 'EXECUTE') AS public_execute,
       p.prosecdef                                             AS security_definer
  FROM pg_proc p JOIN pg_namespace s ON s.oid = p.pronamespace
 WHERE s.nspname = 'public'
   AND p.proname IN ('generate_platform_audit_report_v1','generate_group_audit_report_v1',
                     'generate_member_audit_report_v1','audit_and_fix_group_balance_v1')
 ORDER BY 1;

-- P.0.c  The three helpers the new guards call must exist on this tier, or P.1
--        fails to compile the body. Expect three rows.
SELECT n.nspname || '.' || p.proname || '(' || pg_get_function_arguments(p.oid) || ')' AS helper
  FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
 WHERE (n.nspname = 'public' AND p.proname IN ('is_platform_admin','is_group_admin'))
    OR (n.nspname = 'auth'   AND p.proname = 'uid')
 ORDER BY 1;

-- P.0.d  Baseline for P.3.b: how much audit history the tier already holds. P.1
--        and P.2 change none of it.
SELECT 'platform_audit_reports_rows=' || (SELECT count(*) FROM public.platform_audit_reports) AS a,
       'audit_reports_platform_row='  || COALESCE((SELECT generated_at::text FROM public.audit_reports
                                                    WHERE id = 'platform'), 'none')            AS b,
       'groups='                      || (SELECT count(*) FROM public.groups)                  AS c;

-- ─────────────────────────────────────────────────────────────────────────────
-- P.1  THE FOUR GUARDED BODIES.  Verbatim from
--      supabase/02_FUNCTIONS_AND_TRIGGERS.sql; CREATE OR REPLACE only, no DROP,
--      so the OID, the ACL and every dependent policy survive.
-- ─────────────────────────────────────────────────────────────────────────────

-- P.1.a  public.generate_platform_audit_report_v1()  [SEC_002] PLATFORM_ADMIN_ONLY
CREATE OR REPLACE FUNCTION public.generate_platform_audit_report_v1()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
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
    -- SECURITY DEFINER and reachable by any role PostgREST hands a request to, so the platform
    -- snapshot has to carry the check that RLS would apply to the rows it reads and writes.
    IF NOT public.is_platform_admin() THEN
        RAISE EXCEPTION '[SEC_002] PLATFORM_ADMIN_ONLY' USING
            DETAIL = 'generate_platform_audit_report_v1() reads every group balance and writes the platform snapshot rows.',
            HINT = 'platform_admin_role_required';
    END IF;

    -- One pass over groups and one over the behaviour track: the three earlier scans of
    -- public.groups and two of member_behavior_track were the same rows read again.
    SELECT COALESCE(SUM(balance), 0),
           COUNT(*),
           COUNT(*) FILTER (WHERE is_platform_suspended)
      INTO v_liquidity, v_total_groups, v_suspended_groups
      FROM public.groups;

    SELECT COALESCE(SUM(amount), 0) INTO v_revenue FROM public.platform_ledger;

    SELECT COUNT(*) FILTER (WHERE behavior_score <= 40),
           COUNT(*) FILTER (WHERE behavior_score <= 20)
      INTO v_high_risk, v_critical_risk
      FROM public.member_behavior_track;

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

-- P.1.b  public.generate_group_audit_report_v1(UUID)  [SEC_002] AUDIT_ACCESS_DENIED
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

-- P.1.c  public.generate_member_audit_report_v1(UUID)  [SEC_002] AUDIT_ACCESS_DENIED
CREATE OR REPLACE FUNCTION public.generate_member_audit_report_v1(p_member_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
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

    -- SECURITY DEFINER: this report assembles a member's name, contribution and loan position and
    -- the last audit-log rows, all of which RLS would otherwise filter per member.
    IF NOT (public.is_platform_admin()
            OR public.is_group_admin(v_member.group_id)
            OR v_member.user_id = auth.uid()) THEN
        RAISE EXCEPTION '[SEC_002] AUDIT_ACCESS_DENIED' USING
            DETAIL = 'A member audit report may only be read by the member, that member''s group administrator, or a platform administrator.',
            HINT = 'member_self_or_admin_required';
    END IF;

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

-- P.1.d  public.audit_and_fix_group_balance_v1(UUID, BOOLEAN)  [SEC_002] AUDIT_ACCESS_DENIED
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
    -- With p_fix this rewrites public.groups.balance, and even read-only it reports the exact
    -- ledger sum of any group, so the caller must control that group.
    IF NOT (public.is_platform_admin() OR public.is_group_admin(p_group_id)) THEN
        RAISE EXCEPTION '[SEC_002] AUDIT_ACCESS_DENIED' USING
            DETAIL = 'audit_and_fix_group_balance_v1() may only be run by that group''s administrator or a platform administrator.',
            HINT = 'group_admin_or_platform_admin_required';
    END IF;

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


-- ─────────────────────────────────────────────────────────────────────────────
-- P.2  WITHDRAW THE ANONYMOUS AND PUBLIC EXECUTE.  Identical to
--      supabase/03_SECURITY_AND_RLS.sql section 2b, which is what carries this
--      onto a rebuilt tier. PUBLIC is revoked as well: PostgreSQL grants EXECUTE
--      to PUBLIC when a function is created, so revoking only "anon" would leave
--      anon executing through the PUBLIC entry.
-- ─────────────────────────────────────────────────────────────────────────────
REVOKE EXECUTE ON FUNCTION
    public.generate_platform_audit_report_v1(),
    public.generate_group_audit_report_v1(UUID),
    public.generate_member_audit_report_v1(UUID),
    public.audit_and_fix_group_balance_v1(UUID, BOOLEAN)
FROM PUBLIC, anon;

-- Let PostgREST drop the revoked functions from its cached catalog.
NOTIFY pgrst, 'reload schema';

-- ─────────────────────────────────────────────────────────────────────────────
-- P.3  VERIFICATION - READ ONLY, except the optional live probe P.3.e.  Run
--      P.3.a to P.3.d together, then P.3.e on its own if you want the running
--      denial proven, not just the definition.
-- ─────────────────────────────────────────────────────────────────────────────

-- P.3.a  The four bodies must now be the master bodies. Expect four MATCH.
WITH expected(name, hash) AS (VALUES
  ('generate_platform_audit_report_v1',   '437d21c38b5833e00aa4d2c9db6472bb'),
  ('generate_group_audit_report_v1',      '4b766cca85bac7c9c5c2bba3aee81352'),
  ('generate_member_audit_report_v1',     '79d8fb5cf33f02fcb3b743191139a36f'),
  ('audit_and_fix_group_balance_v1',      'caf9e3cab1d7786e9f6ad9d447818bf1')
), actual AS (
  SELECT e.name, e.hash AS want,
         (SELECT md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g')))
            FROM pg_proc p JOIN pg_namespace s ON s.oid = p.pronamespace
           WHERE s.nspname = 'public' AND p.proname = e.name) AS got
    FROM expected e
)
SELECT name, COALESCE(got, 'ABSENT') AS installed_body,
       CASE WHEN got IS NULL THEN 'ABSENT' WHEN got = want THEN 'MATCH' ELSE 'DRIFT' END AS verdict
  FROM actual ORDER BY name;

-- P.3.b  The privilege matrix. Expect exactly:
--        guarded=YES  anon=NO  public=NO  authenticated=YES  service_role=YES.
--        A NO on authenticated or service_role is over-revocation - the admin's
--        own audit tab would start failing: re-run the GRANT printed below it.
SELECT p.proname,
       CASE WHEN position('SEC_002' in p.prosrc) > 0 THEN 'YES' ELSE 'NO' END    AS guarded,
       CASE WHEN has_function_privilege('anon', p.oid, 'EXECUTE')
            THEN 'NO - STILL OPEN' ELSE 'YES' END                                AS anon_blocked,
       CASE WHEN has_function_privilege('public', p.oid, 'EXECUTE')
            THEN 'NO - STILL OPEN' ELSE 'YES' END                                AS public_blocked,
       CASE WHEN has_function_privilege('authenticated', p.oid, 'EXECUTE')
            THEN 'YES' ELSE 'NO - OVER-REVOKED' END                              AS authed_kept,
       CASE WHEN has_function_privilege('service_role', p.oid, 'EXECUTE')
            THEN 'YES' ELSE 'NO - OVER-REVOKED' END                              AS svc_kept
  FROM pg_proc p JOIN pg_namespace s ON s.oid = p.pronamespace
 WHERE s.nspname = 'public'
   AND p.proname IN ('generate_platform_audit_report_v1','generate_group_audit_report_v1',
                     'generate_member_audit_report_v1','audit_and_fix_group_balance_v1')
 ORDER BY 1;

-- Repair only if P.3.b over-revoked; it grants nothing back to anon or PUBLIC.
-- GRANT EXECUTE ON FUNCTION
--     public.generate_platform_audit_report_v1(),
--     public.generate_group_audit_report_v1(UUID),
--     public.generate_member_audit_report_v1(UUID),
--     public.audit_and_fix_group_balance_v1(UUID, BOOLEAN)
-- TO authenticated, service_role;

-- P.3.c  Nothing here rewrites history: expect the P.0.d counts unchanged, unless
--        P.3.e ran first and rolled itself back, or an admin ran a real audit.
SELECT 'platform_audit_reports_rows=' || (SELECT count(*) FROM public.platform_audit_reports) AS a,
       'audit_reports_platform_row='  || COALESCE((SELECT generated_at::text FROM public.audit_reports
                                                    WHERE id = 'platform'), 'none')            AS b,
       'groups='                      || (SELECT count(*) FROM public.groups)                  AS c;

-- P.3.d  Static proof that the refusal comes before the damage. Read-only: it
--        measures where in each stored body the [SEC_002] raise sits against that
--        body's first write. Expect guarded=YES, writes=YES, guard_before_write=YES
--        on all four. A NO on the last column would mean the report is stored and
--        the caller is refused afterwards - an access log, not an access control.
WITH target(name, first_write) AS (VALUES
  ('generate_platform_audit_report_v1',   'INSERT INTO public.platform_audit_reports'),
  ('generate_group_audit_report_v1',      'INSERT INTO public.audit_reports'),
  ('generate_member_audit_report_v1',     'INSERT INTO public.audit_reports'),
  ('audit_and_fix_group_balance_v1',      'UPDATE public.groups')
)
SELECT t.name,
       CASE WHEN position('SEC_002' in p.prosrc) > 0 THEN 'YES' ELSE 'NO' END          AS guarded,
       CASE WHEN position(t.first_write in p.prosrc) > 0 THEN 'YES' ELSE 'NO' END      AS writes,
       CASE WHEN position('SEC_002' in p.prosrc) > 0
             AND position('SEC_002' in p.prosrc) < position(t.first_write in p.prosrc)
            THEN 'YES' ELSE 'NO' END                                                   AS guard_before_write
  FROM target t
  JOIN pg_proc p ON p.proname = t.name
  JOIN pg_namespace s ON s.oid = p.pronamespace AND s.nspname = 'public'
 ORDER BY 1;

-- P.3.e  OPTIONAL live probe.  Plain SQL, one probe per paste, and only in a NEW
--        session once P.1 and P.2 have committed.
--
--        There is no anon probe here on purpose. Measured four times on Local on
--        2026-10-27 (postgres 17.6.1): a psql session that does `SET LOCAL ROLE anon`
--        and then calls one of these audit functions takes the backend out with
--        signal 11 and drives the whole cluster into crash recovery. The container
--        log records `Failed process was running: SELECT
--        public.generate_platform_audit_report_v1();` for every crash. Two of them
--        happened while installing the bodies in the same open transaction, which is
--        what a first reading blamed, but a third came later against the tier that had
--        already committed and measured at the post hashes, with no body replacement
--        anywhere in that transaction - so the trigger is the anon call itself, not
--        the sequence. Do not reintroduce an in-SQL anon probe on this image. The
--        `authenticated` probe below ran cleanly several times.
--
--        What the crash does not cost: nothing persisted. The aborting transaction
--        left no body, ACL or row change and the tier re-measured healthy afterwards.
--        But a crash-recovery cycle is a real outage for every other connection, so
--        the anon half of P.2 is verified externally instead of from SQL.
--
--        The block still opens its own transaction and ends in ROLLBACK, which is what
--        makes it safe to run against a committed tier: if the protection it tests for
--        is missing, the call underneath really runs and writes (one
--        platform_audit_reports insert plus the audit_reports 'platform' upsert) and
--        the ROLLBACK undoes it.
--
--        Probe - the P.1 in-body guard, as a role that can execute but holds no JWT
--        here, so auth.uid() is NULL and no admin check can pass. PASS is
--          ERROR:  [SEC_002] PLATFORM_ADMIN_ONLY
--        An EXECUTED report instead means the protection is not there (and the
--        ROLLBACK undid its writes). "permission denied to set role" means this
--        tier's editor user cannot assume authenticated - skip the probe and read
--        P.3.b instead.
--
--        The anon revoke (P.2) is verified from outside, with no SQL at all: holding
--        only the anon key,
--          curl -X POST 'https://<host>/rest/v1/rpc/generate_platform_audit_report_v1' \
--               -H "apikey: <anon key>" -H "Authorization: Bearer <anon key>"
--        must be refused and public.platform_audit_reports must gain no row. PostgREST
--        evaluates the committed ACL as the anon role, so this is the stronger test and
--        the one to run on cloud. P.3.b is the same answer read straight from the ACL.
--
-- Probe:
BEGIN;
SET LOCAL ROLE authenticated;
SELECT public.generate_platform_audit_report_v1();
ROLLBACK;
