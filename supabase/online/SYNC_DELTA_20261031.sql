-- ──────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave - DELTA 2026-10-31  (record_liquidity_v1: the guarded inflow twin of
-- record_disbursement_v1)
-- Target: Supabase Cloud (Dashboard SQL editor) and the Local tier.
-- Hand-maintained. The deltas up to 20261030B are cut history - never re-run one that has
-- already run and never edit one to describe a later change.
-- ──────────────────────────────────────────────────────────────────────────────

-- WHAT THIS DOES
--   20261029 withdrew increment_group_balance from `authenticated`, and 20261029B gave a
--   signed-in group admin exactly one guarded route back into it: record_disbursement_v1,
--   which books an outflow. It left no guarded inflow, so the only admin-callable way to add
--   money that is not a contribution disappeared with the primitive. Three real inflows have
--   no contribution row to hang on - bank interest, a dividend, an opening balance - and the
--   simulation harness (app/src/debug/.../LiveSimulationRunner.kt:617, which seeds the
--   liquidity every payout and loan flow is measured against) was the first caller to break:
--   it signs in as the group's admin, so it runs as `authenticated` and gets 403 on every tier
--   that has run 20261029.
--
--   Seeding through record_contribution_v2 would not do. A contribution row also moves
--   totalContributions, which is the unit denominator in
--   CalculateInvestmentClubValuationUseCase: injected capital would stop being capital and
--   start being a unit purchase, and the NAV-per-unit assertions in
--   BusinessProcessSimulationTest would measure the wrong thing.
--
--   P.1 installs the body, copied verbatim from supabase/02_FUNCTIONS_AND_TRIGGERS.sql
--   (mirrored into supabase/migrations/00000000000000_GROUND_ZERO.sql,
--   CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql and online/000_CONSOLIDATED_ONLINE_SYNC.sql by
--   supabase/scripts/generate_ground_zero.ps1 + generate_consolidated_schema.ps1).
--   record_liquidity_v1 is a caller check, a sign check and a description/category check in
--   front of increment_group_balance, nothing more. The primitive keeps the FOR UPDATE group
--   lock, the [SYS_001] GROUP_NOT_FOUND, the [SYS_002] idempotency key and the ledger write,
--   so balance and ledger still move in one transaction and this wrapper cannot create drift.
--   Amount is positive on the wire and positive in the ledger, the caller-facing convention
--   record_disbursement_v1 uses on the other side.

-- ──────────────────────────────────────────────────────────────────────────────
-- P.0  PRE-FLIGHT - READ ONLY.  Run this first, on its own.

-- P.0.a  The name. Expected MISSING on both tiers - that is why this delta exists. A tier that
--        prints a hash is running something the masters do not describe: read it before
--        overwriting it.
select expected.name,
       p.oid is not null as defined,
       md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as body_hash,
       length(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as body_len,
       case
         when p.oid is null then 'MISSING (expected pre-state)'
         when md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g')))
              = 'f01eff7792049d78589f87453a46e38e' then 'POST (this delta already ran)'
         else 'NEITHER - a variant the masters do not describe'
       end as verdict
from (values ('record_liquidity_v1')) as expected(name)
left join pg_proc p on p.proname = expected.name
left join pg_namespace n on n.oid = p.pronamespace and n.nspname = 'public'
order by expected.name;
-- expect: 1 row, defined=false, body_hash=null, verdict='MISSING (expected pre-state)'.

-- P.0.b  The dependencies this body calls. A missing one is an ordering problem, not a
--        privilege problem: run the delta that installs it before P.1.
select expected.name,
       bool_or(p.proname = expected.name) as defined
from (values ('is_platform_admin'), ('is_group_admin'), ('increment_group_balance')) as expected(name)
left join pg_proc p on p.proname = expected.name
left join pg_namespace n on n.oid = p.pronamespace and n.nspname = 'public'
group by expected.name
order by defined, expected.name;
-- expect: 3 rows, every defined=true. All three are baseline objects declared at the top of
--         02_FUNCTIONS_AND_TRIGGERS.sql, so a tier built from GROUND_ZERO already has them.

-- P.0.c  Where the primitive stands. This delta wraps increment_group_balance, so record which
--        side of 20261029 the tier is on: before that delta the primitive is still callable by
--        `authenticated`, and the wrapper is what makes the primitive containable afterwards.
select has_function_privilege('anon', 'public.increment_group_balance(uuid,numeric,text,text,text,uuid)', 'EXECUTE') as anon_execute,
       has_function_privilege('authenticated', 'public.increment_group_balance(uuid,numeric,text,text,text,uuid)', 'EXECUTE') as authed_execute,
       has_function_privilege('service_role', 'public.increment_group_balance(uuid,numeric,text,text,text,uuid)', 'EXECUTE') as svc_execute;
-- expect after 20261029: false / false / true. On a tier that has not run 20261029 yet
--         authed_execute is true - apply 20261029 first so this delta's P.3.b measures what it
--         claims to measure.

-- ──────────────────────────────────────────────────────────────────────────────
-- P.1  WRITE. One CREATE OR REPLACE FUNCTION statement, a verbatim copy of
--      02_FUNCTIONS_AND_TRIGGERS.sql.

CREATE OR REPLACE FUNCTION public.record_liquidity_v1(
    p_group_id UUID,
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id TEXT DEFAULT NULL,
    p_member_id UUID DEFAULT NULL
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    -- The inflow twin of record_disbursement_v1. 20261029 withdrew increment_group_balance from
    -- `authenticated`, which closed the only HTTP route by which a signed-in group admin could
    -- book money that is not a contribution: bank interest, a dividend, an opening balance, or
    -- the simulation harness's seed liquidity. Seeding through record_contribution_v2 instead
    -- would not do, because a contribution row also moves totalContributions, which is the unit
    -- denominator in CalculateInvestmentClubValuationUseCase - the injected capital would stop
    -- being capital and start being a unit purchase.
    IF auth.role() IS DISTINCT FROM 'service_role'
       AND NOT (public.is_platform_admin() OR public.is_group_admin(p_group_id)) THEN
        RAISE EXCEPTION '[SEC_002] LIQUIDITY_ACCESS_DENIED';
    END IF;

    -- Positive here and positive in the ledger: this is an inflow, and the wrapper keeps the
    -- caller-facing sign convention the client's guards already assume.
    IF p_amount IS NULL OR p_amount::TEXT IN ('NaN', 'Infinity', '-Infinity')
       OR p_amount <= 0 OR p_amount <> ROUND(p_amount, 2) THEN
        RAISE EXCEPTION '[FIN_003] INVALID_AMOUNT: Liquidity must be positive and have at most two decimal places';
    END IF;
    IF p_description IS NULL OR BTRIM(p_description) = '' THEN
        RAISE EXCEPTION '[FIN_003] INVALID_DESCRIPTION: A ledger description is required';
    END IF;
    -- group_ledger.category has no CHECK constraint, so the vocabulary is whatever the writers
    -- pass; only a blank is refused here, not an unlisted value.
    IF p_category IS NULL OR BTRIM(p_category) = '' THEN
        RAISE EXCEPTION '[FIN_003] INVALID_CATEGORY: A ledger category is required';
    END IF;

    -- The primitive owns the FOR UPDATE group lock, the idempotency key and the ledger write, so
    -- this wrapper adds the caller check and the sign and nothing else.
    RETURN public.increment_group_balance(
        p_group_id,
        p_amount,
        p_description,
        p_category,
        p_transaction_id,
        p_member_id
    );
END;
$$;

-- ──────────────────────────────────────────────────────────────────────────────
-- P.2  WRITE. The execute grants, taken back from the anonymous surface. Must run after P.1:
--      PostgreSQL grants EXECUTE to PUBLIC when a function is created, so revoking only the
--      explicit "anon" entry would leave anon executing through the PUBLIC entry.

REVOKE EXECUTE ON FUNCTION public.record_liquidity_v1(UUID, NUMERIC, TEXT, TEXT, TEXT, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.record_liquidity_v1(UUID, NUMERIC, TEXT, TEXT, TEXT, UUID) TO authenticated, service_role;

-- P.2.b  Do NOT re-run any other section of 03_SECURITY_AND_RLS.sql on a tier that has data:
--        section 1 re-enables RLS on every table and section 3+ rebuilds policies. On a fresh
--        tier the masters already carry these grants; this section exists for tiers that are
--        already built.

-- ──────────────────────────────────────────────────────────────────────────────
-- P.3  VERIFY - run in a new session, after P.1 and P.2 have committed.

-- P.3.a  Fingerprint after P.1. A DRIFT here means the paste was re-encoded or edited; the hash
--        strips \r first, so line endings cannot cause a false DRIFT - only a real body
--        difference can.
select p.proname,
       md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as body_hash,
       length(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as body_len
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.proname = 'record_liquidity_v1';
-- expect: record_liquidity_v1, f01eff7792049d78589f87453a46e38e, 1931.

-- P.3.b  ACL after P.2. anon_execute must be false; authenticated and service_role must be true.
select p.proname,
       p.pronargs,
       has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
       has_function_privilege('authenticated', p.oid, 'EXECUTE') as authed_execute,
       has_function_privilege('service_role', p.oid, 'EXECUTE') as svc_execute
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.proname = 'record_liquidity_v1';
-- expect: 1 row, pronargs 6, false / true / true. A second row is an overload - no name in the
--         masters is overloaded, so an extra OID is a stale entry to drop, not a probe to re-run.

-- P.3.c  The named-parameter surface PostgREST sees. The harness sends JSON keys, so each key
--        must equal a declared argument name or the call fails with PGRST202 after this delta:
--          p_group_id, p_amount, p_description, p_category, p_transaction_id, p_member_id
select pg_get_function_arguments(p.oid) as arguments,
       p.prorettype::regtype::text as return_type
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.proname = 'record_liquidity_v1';
-- expect: 'p_group_id uuid, p_amount numeric, p_description text, p_category text,
--          p_transaction_id text DEFAULT NULL, p_member_id uuid DEFAULT NULL' / numeric.

-- P.3.d  Behavioural probes as a signed-in non-admin. Each block is complete and
--        self-contained; SET LOCAL dies with its transaction, so the preamble is repeated.
--        Every block aborts on its own, so nothing is written. The group id is fabricated and
--        does not need to exist: the guard is the first statement, and it reads nothing but
--        the session identity.

begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000","role":"authenticated","aud":"authenticated"}';
  set local request.jwt.claim.role = 'authenticated';
  select public.record_liquidity_v1('22222222-2222-2222-2222-222222222222',
         100, 'Probe', 'liquidity');
rollback;
-- expect: ERROR: [SEC_002] LIQUIDITY_ACCESS_DENIED
--         Guard-first, before the amount check and before the primitive is entered. This is the
--         probe that proves the wrapper is not a way back into increment_group_balance for a
--         signed-in member.

begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","role":"authenticated","aud":"authenticated"}';
  set local request.jwt.claim.role = 'authenticated';
  select public.record_liquidity_v1('22222222-2222-2222-2222-222222222222',
         -100, 'Probe', 'liquidity');
rollback;
-- expect: [SEC_002] LIQUIDITY_ACCESS_DENIED for a non-admin. Run again as a real group admin
--         and it must answer [FIN_003] INVALID_AMOUNT: Liquidity must be positive... - a
--         negative inflow can never reach the primitive, which is why the sign check lives in
--         the wrapper and not only in the client.

-- P.3.e  The same surface from outside, over HTTP. CLOUD ONLY - see HOW TO PROVE A REVOKE ON
--        LOCAL above; on Local this exact request crashes the backend with signal 11.
--   curl -s -o /dev/null -w '%{http_code}\n' \
--     -X POST "$SUPABASE_URL/rest/v1/rpc/record_liquidity_v1" \
--     -H "apikey: $ANON_KEY" -H "Authorization: Bearer $ANON_KEY" \
--     -H 'content-type: application/json' \
--     -d '{"p_group_id":"22222222-2222-2222-2222-222222222222","p_amount":"100","p_description":"Probe","p_category":"liquidity"}'
-- expect: 403 'permission denied for function ...'. A 200 means P.2 did not run or the schema
--         cache was never reloaded. A PGRST202 means P.1 did not commit.

-- P.3.f  Ledger invariant spot check (read-only), using the shipped view. This delta adds a
--        writer, not a rule: after a real call, the group's balance must still equal the sum of
--        its ledger rows. Run it with GROUP_ID replaced by the group the probe credited.
-- select * from public.vw_ledger_integrity_check where group_id = 'GROUP_ID';
-- expect: status = 'CONSISTENT'. A 'DRIFT' row here means the wrapper wrote one side of the
--         primitive's transaction - diff sys_deltas against the master before trusting the call.

-- ──────────────────────────────────────────────────────────────────────────────
-- P.4  CLIENT SIDE, already in the tree. Nothing to execute in this section.
--   * app/src/debug/.../simulation/LiveSimulationRunner.kt - seed liquidity now calls
--     record_liquidity_v1 instead of the primitive it can no longer reach. The three hand-debit
--     fallbacks after complete_payout_v2 / approve_and_disburse_loan_v2 call
--     record_disbursement_v1 with a positive amount, so the harness stays on routes a
--     `authenticated` admin is entitled to.
--   * app/src/androidTest/.../BusinessProcessSimulationTest.kt - the investment-club NAV seed
--     calls record_liquidity_v1 over the injected client; the mock engine answers it the same
--     way it answered increment_group_balance (app/src/androidTest/.../di/TestAppModule.kt).
--   * app/src/androidTest/.../LiveFinancialFlowSimulation.kt stays on the primitive: that file
--     is @Ignore'd and builds a service_role client on purpose, and service_role keeps EXECUTE.
--   * The dead balance-adjustment lane is gone: BalanceAdjustmentOutboxHandler, its
--     SyncRepositoryImpl registration ("balance_adjustment"), and incrementGroupBalance on
--     GroupRepository, GroupFinancialRepository, their implementations and GroupDataSource -
--     nothing ever enqueued that operation, so no shipped screen could call it.
