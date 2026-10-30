-- ────────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave - DELTA 2026-10-29  (caller-identity guards on the money RPCs,
-- the anonymous execute grant withdrawn, and cross-group member search given its
-- behaviour score)
-- Target: Supabase Cloud (Dashboard SQL editor) and the Local tier.
-- Hand-maintained. SYNC_DELTA_20261014/21/22/26/26B/27/28 are applied history - do
-- not re-run them and do not edit them to describe these changes.
-- ────────────────────────────────────────────────────────────────────────────────

-- WHAT THIS DOES
--   This is the follow-up that SYNC_DELTA_20261028 filed and deliberately left out:
--   "Guarding that the way the audit RPCs were guarded changes money-path behaviour,
--   so it needs its own delta plus client-side tests, not a privilege patch here."
--
--   P.1 installs fourteen function bodies, copied verbatim from
--   supabase/02_FUNCTIONS_AND_TRIGGERS.sql and
--   supabase/18_DEPLOY_PROCESS_COMPLEX_PAYMENT_V4.sql (both mirrored into
--   supabase/migrations/00000000000000_GROUND_ZERO.sql by
--   supabase/scripts/generate_ground_zero.ps1). Signatures are unchanged, so no client
--   call site moves.
--     is_own_member                 - NEW OBJECT. True when the row belongs to the
--                                     signed-in login (members.user_id = auth.uid()).
--                                     The four member-facing wrappers below need it
--                                     because the member id they act on is a value the
--                                     caller sends, not a value the session proves.
--     record_platform_revenue_v2    - [SEC_002] PLATFORM_REVENUE_ACCESS_DENIED
--     internal_post_contribution    - [SEC_002] CONTRIBUTION_ACCESS_DENIED
--     record_contribution_v2        - [SEC_002] CONTRIBUTION_ACCESS_DENIED
--     process_complex_payment_v4    - [SEC_002] COMPLEX_PAYMENT_ACCESS_DENIED
--     record_loan_repayment_v1      - [SEC_002] LOAN_REPAYMENT_ACCESS_DENIED
--     approve_and_disburse_loan_v2  - [SEC_002] LOAN_DISBURSEMENT_ACCESS_DENIED, and the
--                                     loan's reviewed_by now records the session rather
--                                     than the p_admin_id the caller typed
--     complete_payout_v2            - [SEC_002] PAYOUT_COMPLETION_ACCESS_DENIED, same
--                                     identity flip for payouts.processed_by
--     process_rosca_payout_v2/_v3   - [SEC_002] ROSCA_PAYOUT_ACCESS_DENIED. v2 is the
--                                     older rotation: no period key and no amount
--                                     checks, so it was the more dangerous of the two
--                                     to leave callable. Builds already in the field
--                                     sign in as a group admin, which still passes.
--     pay_platform_fee_v2           - [SEC_002] PLATFORM_FEE_ACCESS_DENIED
--     reconcile_bank_transaction_v1 - [SEC_002] BANK_RECONCILE_ACCESS_DENIED, and the
--                                     not-found line finally carries a code
--                                     ([SYS_001] BANK_TRANSACTION_NOT_FOUND) so
--                                     Throwable.toUserMessage() can render it
--     automate_monthly_billing_v1   - [SEC_002] MONTHLY_BILLING_ACCESS_DENIED
--     search_members_v1             - [SEC_002] PLATFORM_ADMIN_ONLY, plus the
--                                     'behavior_score' key the member-search card has
--                                     always rendered. The console read it from
--                                     platform_member_behavior_insights_v1, a view no
--                                     schema in this repo defines, so the search could
--                                     only ever 404 and the client turned that into an
--                                     empty roster.
--
--   Every guard has the same shape, taken from settle_expired_loan_v2 (installed by
--   20261028 and running in production since):
--       IF auth.role() IS DISTINCT FROM 'service_role'
--          AND NOT (public.is_platform_admin() OR public.is_group_admin(...)) THEN
--           RAISE EXCEPTION '[SEC_002] ..._ACCESS_DENIED';
--       END IF;
--   The service_role exemption is not a looseness, it is what keeps the workers and
--   edge functions alive: they sign with the service key, for which auth.uid() is NULL,
--   so every is_* helper answers false and a guard without the exemption would refuse
--   the very jobs that settle loans and bill fees. automate_monthly_billing_v1 adds a
--   second exemption, auth.jwt() IS NULL, because pg_cron runs it from inside the
--   database with no JWT at all.
--
--   Where a wrapper nests into a guarded function (process_complex_payment_v4 ->
--   internal_post_contribution -> record_platform_revenue_v2; the ROSCA rotation ->
--   record_loan_repayment_v1), the inner call still carries the OUTER session's JWT:
--   auth.uid() and auth.role() read the request, not current_user. That is why the
--   member-facing ones also admit the member's own login - without that allowance a
--   member paying their own contribution would be refused two frames into the call.
--
--   P.2 sets the privileges. The blanket
--       GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO anon, authenticated, service_role
--   in 03_SECURITY_AND_RLS.sql is why any of this was reachable without a login, so the
--   grant is taken back for the whole money surface, in two tiers:
--     * revoked from PUBLIC, anon and re-granted to authenticated + service_role for the
--       functions a signed-in member legitimately calls (contribution, repayment,
--       payment, loan decision, payout completion, rotation, fee, reconcile, search).
--     * revoked from authenticated as well for the primitives and maintenance jobs that
--       only ever run from inside a SECURITY DEFINER wrapper or from the scheduler:
--       increment_group_balance, recompute_ledger_running_balances_v2,
--       perform_group_reconciliation_v1, consolidate_group_ledger_v1,
--       create_ledger_checkpoint_v1, automate_monthly_billing_v1,
--       simulate_bulk_activity_v2, record_platform_revenue_v1. A nested call is
--       authorised against the wrapper's owner role, not the session role, so revoking
--       the client grant closes the HTTP door without breaking a single wrapper.
--       increment_group_balance is the one that matters: it is the raw ledger primitive,
--       it takes any signed amount, and before this delta a signed-in member could drain
--       any group balance from /rest/v1/rpc with no other credential.
--
--   CONSEQUENCE FOR THE CLIENT (read this before shipping the build, not after):
--   Tier two closes the HTTP door on increment_group_balance, which is the raw ledger
--   primitive: any signed amount, straight onto groups.balance, no ledger row. Nothing in
--   the shipped UI calls it - data/.../group/GroupRepositoryImpl.kt:81 and
--   data/.../equity/GroupFinancialRepositoryImpl.kt:16 exist only to serve
--   data/.../system/OutboxHandlers.kt:78 (BalanceAdjustmentOutboxHandler), and no
--   producer in :app or :domain enqueues that operation, so the production path is dead.
--   It is NOT true that nothing calls the RPC by name: the simulation harness does, in six
--   places, all debug or androidTest source sets and all of them sign in as an admin, so
--   they run as `authenticated` and will now get 403 permission denied -
--     app/src/debug/.../LiveSimulationRunner.kt:617  injects R50,000 of seed liquidity
--     .../LiveSimulationRunner.kt:390, :452, :536    debit by hand after a
--                                                     complete_payout_v2 or
--                                                     approve_and_disburse_loan_v2 failure
--                                                     and force the row to 'completed' /
--                                                     'active'
--     app/src/androidTest/.../LiveFinancialFlowSimulation.kt:78  same seed injection
--     app/src/androidTest/.../BusinessProcessSimulationTest.kt:322  through the repository
--   None of those legs is a shipped feature. They break because the primitive itself is the
--   problem: it appends a group_ledger row under category 'adjustment' and checks nothing
--   about who is asking - no admin check, no membership, either sign, any group. That is why
--   it is tier two and why a guard cannot simply replace the revoke: record_contribution_v2
--   calls it two frames deep with the paying member's JWT still in the session, so an admin
--   guard inside the body would refuse a member paying their own contribution. The revoke is
--   the only change that closes the HTTP door and keeps the wrappers, because a nested call
--   is authorised against the wrapper's owner.
--   A tier that has run this delta makes the harness loud instead of self-healing. Re-point
--   the seed injections at record_contribution_v2 (ledger-complete and stays granted) and
--   delete the three compensations; that is client work, filed separately, not a reason to
--   reopen this revoke.
--   .../admin/PlatformRepositoryImpl.kt searchMembers now calls rpc("search_members_v1").
--   Against a tier that has not run this delta the console gets a failure it can show,
--   not the empty list the phantom view used to return - which is the point.
--
--   P.3 verifies. Its blocks raise, and each one rolls back, so nothing is written; P.3.b,
--   P.3.c and P.3.f are read-only. Run them in a new session, after P.1 and P.2 have
--   committed. P.4 lists the client-side half - nothing to execute there.

-- ORDER OF WORK
--   1. Run P.0 on its own. It writes nothing. P.0.a must print a PRE row for the thirteen
--      bodies this delta replaces and say MISSING for is_own_member; if it already
--      prints POST rows, this delta has run on that tier - go to P.3.
--   2. Run P.1, then P.2. One paste is fine, in that order: a CREATE OR REPLACE of a NEW
--      object (is_own_member) inherits the default PUBLIC execute, and re-running any
--      section of 03_SECURITY_AND_RLS.sql re-grants the blanket, so P.2 after P.1 is the
--      only sequence that ends with the ACL this delta intends.
--   3. Run P.3.a through P.3.f. Then reload the API cache:
--        SELECT pg_notify('pgrst','reload schema');
--      A tier that skips the notify keeps routing names it no longer permits and answers
--      stale signatures.

-- TIER NOTES
--   - NEITHER TIER HAS RUN THIS DELTA. It was cut from the masters on 2026-10-28 and has
--     not been applied anywhere, so P.0 has never been executed on cloud and the PRE
--     column below is the repo's own record, not a cloud measurement.
--   - LOCAL (aszmodkqpafwtnfufmow, postgres 17.6.1) is at SYNC_DELTA_20261028 (applied
--     2026-10-28 15:12, digest 1032|C=535 E=1 F=83 G=15 I=33 K=198 P=85 R=38 S=2 T=38 V=4|
--     9752967a0c8a1e095d1de650a6132efb). Applying this delta adds one object (F 83 -> 84,
--     plus a new function either way), so re-measure supabase/maintenance/
--     SCHEMA_PARITY_DIGEST.sql afterwards and update the expected baseline in its header.
--     Dump first: this delta changes who can move money, and a rollback needs the old
--     bodies, which only the dump has.
--   - CLOUD still owes SYNC_DELTA_20261027, then 20261028, then this one, in that order.
--     20261028 installs settle_expired_loan_v2, which the app calls by name, so cloud
--     cannot settle an expired loan until it runs. Do not paste P.1 over a tier whose
--     P.0.a prints NEITHER for a body - that tier holds a variant that never came back to
--     the masters, and reading that diff is the job before writing.
--   - There is no psql on the machine this delta was authored from, and cloud SQL is
--     executed by the project owner. This file is a handover, not something the author
--     ran.
--   - Do not rehearse P.1/P.2 by wrapping them in BEGIN ... ROLLBACK and piping them into
--     psql. Measured on this image: an identical wrapper printed ROLLBACK and still
--     committed. Rehearse on a scratch tier, or run P.0 and P.3 as written.

-- HOW TO PROVE A REVOKE ON LOCAL
--   Prove it with has_function_privilege (P.0.b / P.3.b) and nothing else. Never call one
--   of these SECURITY DEFINER RPCs as the anon role over the REST route on Local:
--   measured twice on 2026-10-28 that /rest/v1/rpc/<fn> with only the anon key crashed
--   the backend with signal 11. The curl leg is a CLOUD-ONLY check, where the answer is
--   a clean 403 'permission denied for function'.

-- ────────────────────────────────────────────────────────────────────────────────
-- P.0  PRE-FLIGHT - READ ONLY.  Run this first, on its own.

-- P.0.a  The bodies this delta replaces. PRE = the state a tier should be in before
--        this delta, POST = the state P.1 installs. A row that prints neither means a
--        third body is on that tier: stop and diff it before writing.
--        PRE provenance, because it is not uniform:
--          * internal_post_contribution, record_contribution_v2,
--            record_platform_revenue_v2, process_rosca_payout_v3 are the POST hashes
--            recorded in SYNC_DELTA_20261028 P.3.a - that delta ran on Local, so those
--            four are measured there and assumed, not measured, on cloud.
--          * record_loan_repayment_v1, pay_platform_fee_v2,
--            reconcile_bank_transaction_v1, approve_and_disburse_loan_v2,
--            complete_payout_v2, process_rosca_payout_v2, process_complex_payment_v4
--            carry hashes that appear in the fingerprint tables of
--            SYNC_DELTA_20261014/21/22, so they are the shipped bodies.
--          * automate_monthly_billing_v1 and search_members_v1 have never appeared in a
--            delta fingerprint table. Their PRE hash is the repo master at commit
--            0105914, unverified against either tier - if a tier prints a different
--            value, it is running something the masters do not describe.
--        is_own_member does not exist yet on any tier; P.0.a reports it as MISSING and
--        that is the expected pre-state.
select coalesce(p.proname, expected.proname) as proname,
       md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as body_hash,
       case md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g')))
         when 'd81ce97479007931e4375703e5ebd75e' then 'PRE'
         when 'dbdcb0ca1f0580df5ab60ede23a721fd' then 'PRE'
         when 'f9bad95316ec7160a98c89b8122a013f' then 'PRE'
         when '27a92966186dee34c4c41a56b15aec2e' then 'PRE'
         when '7ba14d8b9cd1d5f7035e6b03b82c7952' then 'PRE'
         when '42921eb9257bde763294c40e8f30dc8c' then 'PRE'
         when '4cc54a865a9c504b325a36c229b733bf' then 'PRE'
         when '148f26eafcfa11be50a22c6885674932' then 'PRE'
         when 'c16b444c02ef5ffab1d860a19da6054d' then 'PRE'
         when '5c9b00d5f769c0d1ea1f5467c939c71c' then 'PRE'
         when 'd32e03083a3dfb663533a650b3fb2097' then 'PRE'
         when 'c25c93062d892fcd013cc3dc2f407f62' then 'PRE'
         when 'cdebc423ace65bf00c38f0f4a1272bb7' then 'PRE'
         when '18d632ba725d3b60115283d513aca1a0' then 'POST'
         when 'e5f697487956b76254142531c6258b2b' then 'POST'
         when '2e9d91dbd6e3d95591f93b1dbf16a1bb' then 'POST'
         when '793f9e69a890b7a420d631735b727592' then 'POST'
         when 'c838e1b54121e50721c1f40dbb3e1c5b' then 'POST'
         when '39323430c91a240b2a03d215ba5bd437' then 'POST'
         when 'a57ed0d631d228d09b64a40f02713243' then 'POST'
         when '3b06a96f42c8a498fc6bf4d412f0fe14' then 'POST'
         when '5e936105b7dadbb7c043912e10c45388' then 'POST'
         when 'c40bc151cbe6542fb1dd0f62a0044cd1' then 'POST'
         when '10e0b1e6a14b085c41223f3a6a40a2b8' then 'POST'
         when '8bf783f2668d2cae418469b19c8caab3' then 'POST'
         when '3cb48745733b276dcb8e961aae053410' then 'POST'
         else case when p.proname is null then 'MISSING' else 'NEITHER' end
       end as verdict
from (values
        ('is_own_member'), ('record_platform_revenue_v2'), ('internal_post_contribution'),
        ('record_contribution_v2'), ('record_loan_repayment_v1'), ('pay_platform_fee_v2'),
        ('reconcile_bank_transaction_v1'), ('automate_monthly_billing_v1'),
        ('approve_and_disburse_loan_v2'), ('complete_payout_v2'), ('process_rosca_payout_v2'),
        ('process_rosca_payout_v3'), ('search_members_v1'), ('process_complex_payment_v4')
     ) as expected(proname)
left join pg_proc p on p.proname = expected.proname
join pg_namespace n on n.oid = p.pronamespace and n.nspname = 'public'
order by 1;
-- A left join that drops MISSING rows is fine too, but then count the rows: fourteen
-- minus one (is_own_member) means thirteen PRE/POST rows plus one absent object.

-- P.0.b  Who can reach the money surface right now - twenty-one rows. Before this delta
--        every row prints true/true/true, because 03_SECURITY_AND_RLS.sql grants EXECUTE to
--        all three roles in one line. After P.2 the anon column must be false everywhere and
--        the authenticated column must be false for the eight primitives in the second list.
--        is_own_member is not listed here: it does not exist yet, and P.3.b is the twenty-two
--        row version with per-column expectations.
select p.proname,
       has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
       has_function_privilege('authenticated', p.oid, 'EXECUTE') as authed_execute,
       has_function_privilege('service_role', p.oid, 'EXECUTE') as svc_execute
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in ('increment_group_balance', 'record_contribution_v2',
                    'internal_post_contribution', 'record_platform_revenue_v2',
                    'record_platform_revenue_v1', 'record_loan_repayment_v1',
                    'approve_and_disburse_loan_v2', 'complete_payout_v2',
                    'process_rosca_payout_v2', 'process_rosca_payout_v3',
                    'process_complex_payment_v4', 'pay_platform_fee_v2',
                    'reconcile_bank_transaction_v1', 'automate_monthly_billing_v1',
                    'recompute_ledger_running_balances_v2', 'perform_group_reconciliation_v1',
                    'consolidate_group_ledger_v1', 'create_ledger_checkpoint_v1',
                    'simulate_bulk_activity_v2', 'search_members_v1',
                    'recalculate_all_group_financials_v3')
order by p.proname;

-- P.0.c  Is a guard already inside a body? A tier that carries a SEC_002 line but a PRE
--        hash is running a hand-patched variant - read that diff before writing.
select p.proname, position('SEC_002' in p.prosrc) > 0 as has_sec_002
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in ('record_contribution_v2', 'internal_post_contribution',
                    'record_platform_revenue_v2', 'record_loan_repayment_v1',
                    'approve_and_disburse_loan_v2', 'complete_payout_v2',
                    'process_rosca_payout_v2', 'process_rosca_payout_v3',
                    'process_complex_payment_v4', 'pay_platform_fee_v2',
                    'reconcile_bank_transaction_v1', 'automate_monthly_billing_v1',
                    'search_members_v1')
order by p.proname;

-- ────────────────────────────────────────────────────────────────────────────────
-- P.1  WRITE. Fourteen CREATE OR REPLACE FUNCTION statements, verbatim copies of the
--      masters. Signatures unchanged. Must run before P.2.
-- >>> is_own_member
CREATE OR REPLACE FUNCTION public.is_own_member(p_member_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN p_member_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.members WHERE id = p_member_id AND user_id = auth.uid()
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE SET search_path = public, pg_temp;

-- >>> record_platform_revenue_v2
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
    v_existing RECORD;
BEGIN
    -- Reached directly over PostgREST and nested from the fee/contribution paths, so the session
    -- decides: a platform admin or the group's own people may book revenue for a group, and the
    -- billing worker books it as service_role. An anonymous caller has no claim on this ledger.
    IF auth.role() IS DISTINCT FROM 'service_role'
       AND NOT (public.is_platform_admin()
                OR (p_group_id IS NOT NULL AND (public.is_group_admin(p_group_id) OR public.is_group_member(p_group_id)))) THEN
        RAISE EXCEPTION '[SEC_002] PLATFORM_REVENUE_ACCESS_DENIED';
    END IF;

    IF p_amount IS NULL OR p_amount::TEXT IN ('NaN', 'Infinity', '-Infinity')
       OR p_amount <= 0 OR p_amount <> ROUND(p_amount, 2) THEN
        RAISE EXCEPTION '[FIN_003] INVALID_AMOUNT: Platform revenue must be positive and have at most two decimal places';
    END IF;

    -- Serialises every writer of this running total: two concurrent fee payments would otherwise
    -- read the same SUM and each store it as their own balance_after.
    PERFORM pg_advisory_xact_lock(hashtext('platform_ledger')::bigint);

    IF p_transaction_id IS NOT NULL THEN
        SELECT * INTO v_existing FROM public.platform_ledger WHERE transaction_id = p_transaction_id LIMIT 1;
        IF FOUND THEN
            IF v_existing.amount IS DISTINCT FROM p_amount
               OR v_existing.category IS DISTINCT FROM p_category
               OR v_existing.group_id IS DISTINCT FROM p_group_id THEN
                RAISE EXCEPTION '[SYS_002] TRANSACTION_ID_REUSED_WITH_DIFFERENT_PLATFORM_REVENUE';
            END IF;
            RETURN jsonb_build_object('amount', v_existing.amount, 'balance_after', v_existing.balance_after, 'idempotent', true);
        END IF;
    END IF;

    SELECT COALESCE(SUM(amount), 0) INTO v_last_balance FROM public.platform_ledger;
    v_new_balance := v_last_balance + p_amount;

    INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category, group_id)
    VALUES (p_transaction_id, p_amount, v_new_balance, p_description, p_category, p_group_id);

    RETURN jsonb_build_object('amount', p_amount, 'balance_after', v_new_balance);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

-- >>> internal_post_contribution
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
SET search_path = public, pg_temp
AS $$
DECLARE
    v_contrib RECORD;
    v_new_bal NUMERIC;
    v_m_name TEXT;
    v_l_desc TEXT;
    v_l_cat TEXT;
BEGIN
    -- No client calls this by name today, but the blanket grant makes it callable, and it is the
    -- function that actually moves money: it is the one an anonymous request should not reach.
    IF auth.role() IS DISTINCT FROM 'service_role'
       AND NOT (public.is_platform_admin()
                OR public.is_group_admin(p_group_id)
                OR public.is_own_member(p_member_id)) THEN
        RAISE EXCEPTION '[SEC_002] CONTRIBUTION_ACCESS_DENIED';
    END IF;

    IF p_amount IS NULL OR p_amount::TEXT IN ('NaN', 'Infinity', '-Infinity')
       OR p_amount <= 0 OR p_amount <> ROUND(p_amount, 2) THEN
        RAISE EXCEPTION '[FIN_003] INVALID_AMOUNT: Contribution amount must be positive and have at most two decimal places';
    END IF;
    IF p_status IS NULL OR p_status NOT IN ('paid', 'due', 'overdue', 'partial', 'missed') THEN
        RAISE EXCEPTION '[FIN_003] INVALID_STATUS: Unsupported contribution status %', p_status;
    END IF;
    IF p_type IS NULL OR p_type NOT IN ('contribution', 'joining_fee', 'registration_contribution', 'late_fee', 'member_fee', 'member_fee_ledger', 'loan_settlement_extraction') THEN
        RAISE EXCEPTION '[FIN_003] INVALID_TYPE: Unsupported contribution type %', p_type;
    END IF;

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

-- >>> record_contribution_v2
CREATE OR REPLACE FUNCTION public.record_contribution_v2(
    p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_due_date DATE,
    p_paid_at TIMESTAMPTZ, p_status TEXT, p_tx_id TEXT DEFAULT NULL,
    p_type TEXT DEFAULT 'contribution', p_description TEXT DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_contrib RECORD;
    v_new_bal NUMERIC;
BEGIN
    IF auth.role() IS DISTINCT FROM 'service_role'
       AND NOT (public.is_platform_admin()
                OR public.is_group_admin(p_group_id)
                OR public.is_own_member(p_member_id)) THEN
        RAISE EXCEPTION '[SEC_002] CONTRIBUTION_ACCESS_DENIED';
    END IF;

    IF p_amount IS NULL OR p_amount::TEXT IN ('NaN', 'Infinity', '-Infinity')
       OR p_amount <= 0 OR p_amount <> ROUND(p_amount, 2) THEN
        RAISE EXCEPTION '[FIN_003] INVALID_AMOUNT: Contribution amount must be positive and have at most two decimal places';
    END IF;

    SELECT balance INTO v_new_bal FROM public.groups WHERE id = p_group_id FOR UPDATE;
    IF p_tx_id IS NOT NULL THEN
        SELECT * INTO v_contrib FROM public.contributions WHERE group_id = p_group_id AND transaction_id = p_tx_id;
        IF FOUND THEN
            IF v_contrib.member_id IS DISTINCT FROM p_member_id
               OR v_contrib.amount IS DISTINCT FROM p_amount
               OR v_contrib.type IS DISTINCT FROM p_type
               OR v_contrib.status IS DISTINCT FROM p_status
               OR v_contrib.due_date IS DISTINCT FROM p_due_date THEN
                RAISE EXCEPTION '[SYS_002] TRANSACTION_ID_REUSED_WITH_DIFFERENT_CONTRIBUTION';
            END IF;
            SELECT balance INTO v_new_bal FROM public.groups WHERE id = p_group_id;
            RETURN jsonb_build_object('contribution', to_jsonb(v_contrib), 'new_balance', v_new_bal, 'idempotent', true);
        END IF;
    END IF;

    RETURN public.internal_post_contribution(p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_tx_id, p_type, p_description);
END;
$$;

-- >>> record_loan_repayment_v1
CREATE OR REPLACE FUNCTION public.record_loan_repayment_v1(
    p_loan_id UUID, p_member_id UUID, p_group_id UUID, p_amount NUMERIC,
    p_payment_method TEXT DEFAULT 'bank', p_transaction_id TEXT DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_repay RECORD;
    v_loan RECORD;
    v_m_name TEXT;
    v_new_bal NUMERIC;
    v_remaining NUMERIC;
BEGIN
    -- The loan-ownership check below proves the loan belongs to p_member_id, but p_member_id is
    -- whatever the caller typed. This proves the caller is allowed to speak for that member.
    IF auth.role() IS DISTINCT FROM 'service_role'
       AND NOT (public.is_platform_admin()
                OR public.is_group_admin(p_group_id)
                OR public.is_own_member(p_member_id)) THEN
        RAISE EXCEPTION '[SEC_002] LOAN_REPAYMENT_ACCESS_DENIED';
    END IF;

    IF p_amount IS NULL OR p_amount <= 0 OR p_amount <> ROUND(p_amount, 2) THEN
        RAISE EXCEPTION '[FIN_003] INVALID_AMOUNT: Repayment amount must be positive and have at most two decimal places';
    END IF;

    SELECT balance INTO v_new_bal FROM public.groups WHERE id = p_group_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND'; END IF;

    SELECT * INTO v_loan
    FROM public.loans
    WHERE id = p_loan_id
    FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] LOAN_NOT_FOUND'; END IF;
    IF v_loan.group_id IS DISTINCT FROM p_group_id OR v_loan.member_id IS DISTINCT FROM p_member_id THEN
        RAISE EXCEPTION '[FIN_003] LOAN_OWNERSHIP_MISMATCH';
    END IF;
    IF p_transaction_id IS NOT NULL THEN
        SELECT * INTO v_repay
        FROM public.loan_repayments
        WHERE group_id = p_group_id AND transaction_id = p_transaction_id
        FOR UPDATE;
        IF FOUND THEN
            IF v_repay.loan_id IS DISTINCT FROM p_loan_id
               OR v_repay.member_id IS DISTINCT FROM p_member_id
               OR v_repay.amount IS DISTINCT FROM p_amount THEN
                RAISE EXCEPTION '[SYS_002] TRANSACTION_ID_REUSED_WITH_DIFFERENT_REPAYMENT';
            END IF;
            SELECT balance INTO v_new_bal FROM public.groups WHERE id = p_group_id;
            RETURN jsonb_build_object('repayment', to_jsonb(v_repay), 'new_balance', v_new_bal, 'idempotent', true);
        END IF;
    END IF;

    IF v_loan.status IS NULL OR v_loan.status NOT IN ('active', 'partially_paid', 'overdue', 'defaulted') THEN
        RAISE EXCEPTION '[FIN_002] INVALID_STATUS: Loan cannot accept repayments from status %', v_loan.status;
    END IF;

    v_remaining := v_loan.total_to_repay - COALESCE(v_loan.total_repaid, 0);
    IF p_amount > v_remaining THEN
        RAISE EXCEPTION '[FIN_003] REPAYMENT_EXCEEDS_BALANCE: remaining %, requested %', v_remaining, p_amount;
    END IF;

    SELECT full_name INTO v_m_name FROM public.members WHERE id = p_member_id;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] MEMBER_NOT_FOUND'; END IF;
    INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, payment_method, transaction_id)
    VALUES (p_loan_id, p_member_id, p_group_id, p_amount, p_payment_method, p_transaction_id)
    RETURNING * INTO v_repay;
    SELECT public.increment_group_balance(p_group_id, p_amount, 'Loan repayment from ' || v_m_name, 'loan_repayment', v_repay.id::text, p_member_id) INTO v_new_bal;
    RETURN jsonb_build_object('repayment', to_jsonb(v_repay), 'new_balance', v_new_bal);
END;
$$;

-- >>> pay_platform_fee_v2
CREATE OR REPLACE FUNCTION public.pay_platform_fee_v2(
    p_group_id UUID,
    p_amount NUMERIC,
    p_fee_type TEXT, -- 'monthly', 'registration'
    p_tx_id TEXT DEFAULT NULL,
    p_from_group_fund BOOLEAN DEFAULT TRUE
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_current_bal NUMERIC;
    v_allocated NUMERIC := 0;
    v_fee RECORD;
    v_group_name TEXT;
BEGIN
    -- A fee submission debits the group's balance and credits the platform ledger, so the caller
    -- must either run the platform, run that group, or be the service role the billing worker uses.
    IF auth.role() IS DISTINCT FROM 'service_role'
       AND NOT (public.is_platform_admin() OR public.is_group_admin(p_group_id)) THEN
        RAISE EXCEPTION '[SEC_002] PLATFORM_FEE_ACCESS_DENIED';
    END IF;

    IF p_amount IS NULL OR p_amount::TEXT IN ('NaN', 'Infinity', '-Infinity')
       OR p_amount <= 0 OR p_amount <> ROUND(p_amount, 2) THEN
        RAISE EXCEPTION '[FIN_003] INVALID_AMOUNT: Platform fee must be positive and have at most two decimal places';
    END IF;
    IF p_fee_type IS NULL OR p_fee_type NOT IN ('monthly', 'registration') THEN
        RAISE EXCEPTION '[FIN_003] INVALID_FEE_TYPE: Unsupported platform fee type %', p_fee_type;
    END IF;

    -- Row lock first: serialises concurrent submissions of the same tx_id.
    SELECT name, balance INTO v_group_name, v_current_bal FROM public.groups WHERE id = p_group_id FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND: %', p_group_id;
    END IF;

    -- ── Idempotency guard ────────────────────────────────────────────────
    -- A NULL tx_id cannot be deduplicated, so fall through to normal processing;
    -- callers are expected to always supply a deterministic reference.
    IF p_tx_id IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM public.platform_ledger WHERE transaction_id = p_tx_id) THEN
            IF NOT EXISTS (
                SELECT 1 FROM public.platform_ledger
                WHERE transaction_id = p_tx_id
                  AND amount = p_amount
                  AND category = p_fee_type || '_fee'
                  AND group_id = p_group_id
            ) OR (
                p_from_group_fund AND NOT EXISTS (
                    SELECT 1 FROM public.group_ledger
                    WHERE group_id = p_group_id
                      AND transaction_id = p_tx_id
                      AND amount = -p_amount
                      AND category = 'platform_fee'
                )
            ) THEN
                RAISE EXCEPTION '[SYS_002] TRANSACTION_ID_REUSED_WITH_DIFFERENT_PLATFORM_FEE';
            END IF;

            RETURN jsonb_build_object(
                'paid', p_amount,
                'remaining_allocated', p_amount,
                'transaction_id', p_tx_id,
                'status', 'idempotent'
            );
        ELSIF EXISTS (
            SELECT 1 FROM public.group_ledger
            WHERE group_id = p_group_id AND transaction_id = p_tx_id
        ) OR EXISTS (
            SELECT 1 FROM public.platform_fees
            WHERE group_id = p_group_id AND transaction_id = p_tx_id
        ) THEN
            RAISE EXCEPTION '[SYS_002] TRANSACTION_ID_REUSED_WITH_DIFFERENT_PLATFORM_FEE';
        END IF;
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

-- >>> reconcile_bank_transaction_v1
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
        RAISE EXCEPTION '[SYS_001] BANK_TRANSACTION_NOT_FOUND: %', p_bank_tx_id;
    END IF;

    -- Reconciling marks real money as matched, and the row is picked by an id the caller supplies,
    -- so only the people who run that group may do it.
    IF auth.role() IS DISTINCT FROM 'service_role'
       AND NOT (public.is_platform_admin() OR public.is_group_admin(v_group_id)) THEN
        RAISE EXCEPTION '[SEC_002] BANK_RECONCILE_ACCESS_DENIED';
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

-- >>> automate_monthly_billing_v1
CREATE OR REPLACE FUNCTION public.automate_monthly_billing_v1()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_group RECORD;
    v_fee_amount NUMERIC;
    v_count INT := 0;
    v_monthly_fee NUMERIC;
BEGIN
    -- This bills every group on the platform at once. It belongs to the scheduler, and to nobody
    -- else who can reach the RPC endpoint. A caller with no JWT at all is a database-side job
    -- (pg_cron runs the documented monthly schedule without one), not an HTTP request.
    IF auth.jwt() IS NOT NULL
       AND auth.role() IS DISTINCT FROM 'service_role'
       AND NOT public.is_platform_admin() THEN
        RAISE EXCEPTION '[SEC_002] MONTHLY_BILLING_ACCESS_DENIED';
    END IF;

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

-- >>> approve_and_disburse_loan_v2
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
    -- The session is the authority on who approved this; p_admin_id is only the fallback a
    -- service_role worker has no login of its own to offer.
    v_actor UUID := COALESCE(auth.uid(), p_admin_id);
BEGIN
    -- Groups-first, the same order complete_payout_v2 and increment_group_balance use: the status
    -- and liquidity guards below must be evaluated with the lock already held.
    PERFORM id FROM public.groups WHERE id = (SELECT group_id FROM public.loans WHERE id = p_loan_id) FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] LOAN_NOT_FOUND'; END IF;

    SELECT * INTO v_loan FROM public.loans WHERE id = p_loan_id;
    IF v_loan.id IS NULL THEN RAISE EXCEPTION '[SYS_001] LOAN_NOT_FOUND'; END IF;

    IF auth.role() IS DISTINCT FROM 'service_role'
       AND NOT (public.is_platform_admin() OR public.is_group_admin(v_loan.group_id)) THEN
        RAISE EXCEPTION '[SEC_002] LOAN_DISBURSEMENT_ACCESS_DENIED';
    END IF;

    IF v_loan.status NOT IN ('pending', 'approved') THEN RAISE EXCEPTION '[FIN_002] INVALID_STATUS: Loan cannot be disbursed from status %', v_loan.status; END IF;

    PERFORM public.assert_mfa_for_disbursement_v1(v_loan.amount);

    SELECT * INTO v_group FROM public.groups WHERE id = v_loan.group_id;
    IF COALESCE(v_group.balance, 0) < v_loan.amount THEN RAISE EXCEPTION '[FIN_001] INSUFFICIENT_LIQUIDITY'; END IF;

    SELECT full_name INTO v_m_name FROM public.members WHERE id = v_loan.member_id;

    UPDATE public.loans SET status = 'active', reviewed_by = v_actor, reviewed_at = NOW(), start_date = NOW(), updated_at = NOW() WHERE id = p_loan_id;
    PERFORM public.increment_group_balance(v_loan.group_id, -v_loan.amount, 'Loan disbursement to ' || v_m_name || ' (' || p_payment_method || ')', 'loan_disbursement', p_loan_id::text, v_loan.member_id);

    PERFORM public.log_audit_event('LOAN_DISBURSED', COALESCE(v_actor::text, 'system_admin'), v_loan.member_id, v_loan.group_id, jsonb_build_object('loan_id', p_loan_id, 'amount', v_loan.amount), p_loan_id);
END;
$$;

-- >>> complete_payout_v2
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
    v_eff_admin_id UUID := COALESCE(auth.uid(), p_admin_id);
BEGIN
    PERFORM id FROM public.groups WHERE id = (SELECT group_id FROM public.payouts WHERE id = p_payout_id) FOR UPDATE;

    SELECT * INTO v_payout FROM public.payouts WHERE id = p_payout_id;
    IF v_payout.id IS NULL THEN RAISE EXCEPTION '[SYS_001] PAYOUT_NOT_FOUND'; END IF;

    IF auth.role() IS DISTINCT FROM 'service_role'
       AND NOT (public.is_platform_admin() OR public.is_group_admin(v_payout.group_id)) THEN
        RAISE EXCEPTION '[SEC_002] PAYOUT_COMPLETION_ACCESS_DENIED';
    END IF;

    IF v_payout.status NOT IN ('group_approved', 'processing') THEN RAISE EXCEPTION '[FIN_002] BAD_STATUS: Payout in % cannot be completed', v_payout.status; END IF;

    PERFORM public.assert_mfa_for_disbursement_v1(v_payout.amount);

    SELECT * INTO v_group FROM public.groups WHERE id = v_payout.group_id;
    IF COALESCE(v_group.balance, 0) < v_payout.amount THEN RAISE EXCEPTION '[FIN_001] NO_CASH: Group balance insufficient for payout.'; END IF;

    UPDATE public.payouts SET status = 'completed', processed_by = v_eff_admin_id, processed_at = NOW(), payout_reference = COALESCE(p_payout_reference, payout_reference), updated_at = NOW() WHERE id = p_payout_id;
    PERFORM public.increment_group_balance(v_payout.group_id, -v_payout.amount, 'Disbursement Ref: ' || COALESCE(p_payout_reference, p_payout_id::text), 'payout', p_payout_id::text, v_payout.member_id);

    PERFORM public.log_audit_event('PAYOUT_COMPLETED', COALESCE(v_eff_admin_id::text, 'system_admin'), v_payout.member_id, v_payout.group_id, jsonb_build_object('payout_id', p_payout_id, 'amount', v_payout.amount), p_payout_id);
END;
$$;

-- >>> process_rosca_payout_v2
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
    -- v2 has no period key and no amount checks, so it is the more dangerous of the two rotations
    -- to leave anonymously callable. Builds already in the field sign in as a group admin, which is
    -- what the guard still admits; only the service role passes without a login.
    IF auth.role() IS DISTINCT FROM 'service_role'
       AND NOT (public.is_platform_admin() OR public.is_group_admin(p_group_id)) THEN
        RAISE EXCEPTION '[SEC_002] ROSCA_PAYOUT_ACCESS_DENIED';
    END IF;

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

-- >>> process_rosca_payout_v3
CREATE OR REPLACE FUNCTION public.process_rosca_payout_v3(
    p_group_id UUID,
    p_member_id UUID,
    p_total_pot NUMERIC,
    p_loan_deductions JSONB,
    p_payout_amount NUMERIC,
    p_payout_data JSONB,
    p_period TEXT
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_loan RECORD;
    v_p_id UUID;
    v_existing RECORD;
    v_claimed UUID;
    v_offset_total NUMERIC := 0;
    v_repayment_count BIGINT;
BEGIN
    IF p_period IS NULL OR p_period !~ '^[0-9]{4}-(0[1-9]|1[0-2])$' THEN
        RAISE EXCEPTION '[FIN_003] INVALID_PERIOD: Expected YYYY-MM';
    END IF;
    IF p_total_pot IS NULL OR p_total_pot::TEXT IN ('NaN', 'Infinity', '-Infinity')
       OR p_total_pot <= 0 OR p_total_pot <> ROUND(p_total_pot, 2) THEN
        RAISE EXCEPTION '[FIN_003] INVALID_AMOUNT: ROSCA pot must be positive and have at most two decimal places';
    END IF;
    IF p_payout_amount IS NULL OR p_payout_amount::TEXT IN ('NaN', 'Infinity', '-Infinity')
       OR p_payout_amount < 0 OR p_payout_amount <> ROUND(p_payout_amount, 2) THEN
        RAISE EXCEPTION '[FIN_003] INVALID_AMOUNT: ROSCA payout must be non-negative and have at most two decimal places';
    END IF;
    IF p_loan_deductions IS NULL OR jsonb_typeof(p_loan_deductions) <> 'array' THEN
        RAISE EXCEPTION '[FIN_003] INVALID_LOAN_DEDUCTIONS: Expected a JSON array';
    END IF;
    IF p_payout_data IS NULL OR jsonb_typeof(p_payout_data) <> 'object' THEN
        RAISE EXCEPTION '[FIN_003] INVALID_PAYOUT_DATA: Expected a JSON object';
    END IF;

    -- A rotation moves the whole group's pot to one member, so only the people who run the group
    -- may start it. Checking before the lock keeps a refused caller from queueing writers behind it.
    IF auth.role() IS DISTINCT FROM 'service_role'
       AND NOT (public.is_platform_admin() OR public.is_group_admin(p_group_id)) THEN
        RAISE EXCEPTION '[SEC_002] ROSCA_PAYOUT_ACCESS_DENIED';
    END IF;

    PERFORM id FROM public.groups WHERE id = p_group_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND'; END IF;
    IF NOT EXISTS (SELECT 1 FROM public.members WHERE id = p_member_id AND group_id = p_group_id) THEN
        RAISE EXCEPTION '[FIN_003] MEMBER_NOT_IN_GROUP';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM jsonb_to_recordset(p_loan_deductions) AS x(loan_id uuid, amount numeric)
        WHERE x.loan_id IS NULL OR x.amount IS NULL
           OR x.amount::TEXT IN ('NaN', 'Infinity', '-Infinity')
           OR x.amount <= 0 OR x.amount <> ROUND(x.amount, 2)
    ) THEN
        RAISE EXCEPTION '[FIN_003] INVALID_LOAN_DEDUCTION: Each offset must have a loan ID and positive cent amount';
    END IF;
    IF (
        SELECT COUNT(*) <> COUNT(DISTINCT x.loan_id)
        FROM jsonb_to_recordset(p_loan_deductions) AS x(loan_id uuid, amount numeric)
    ) THEN
        RAISE EXCEPTION '[FIN_003] DUPLICATE_LOAN_DEDUCTION';
    END IF;

    SELECT COALESCE(sum(x.amount), 0) INTO v_offset_total
    FROM jsonb_to_recordset(p_loan_deductions) AS x(loan_id uuid, amount numeric);

    IF v_offset_total + p_payout_amount <> p_total_pot THEN
        RAISE EXCEPTION '[FIN_003] POT_IMBALANCE: offsets % plus payout % must equal pot %',
            v_offset_total, p_payout_amount, p_total_pot;
    END IF;

    SELECT * INTO v_existing
    FROM public.rosca_rotation_executions
    WHERE group_id = p_group_id AND member_id = p_member_id AND period = p_period;

    IF FOUND THEN
        SELECT COUNT(*) INTO v_repayment_count
        FROM public.loan_repayments
        WHERE transaction_id LIKE 'rosca:' || p_group_id::text || ':' || p_member_id::text || ':' || p_period || ':%';

        IF v_existing.total_pot IS DISTINCT FROM p_total_pot
           OR v_existing.net_amount IS DISTINCT FROM p_payout_amount
           OR v_existing.loan_offsets IS DISTINCT FROM v_offset_total
           OR v_repayment_count <> jsonb_array_length(p_loan_deductions)
           OR EXISTS (
               SELECT 1
               FROM jsonb_to_recordset(p_loan_deductions) AS x(loan_id uuid, amount numeric)
               LEFT JOIN public.loan_repayments r
                 ON r.transaction_id = 'rosca:' || p_group_id::text || ':' || p_member_id::text || ':' || p_period || ':' || x.loan_id::text
                AND r.group_id = p_group_id
                AND r.member_id = p_member_id
                AND r.loan_id = x.loan_id
                AND r.amount = x.amount
               WHERE r.id IS NULL
           ) THEN
            RAISE EXCEPTION '[SYS_002] ROSCA_PERIOD_REPLAY_MISMATCH';
        END IF;

        RETURN jsonb_build_object('success', true, 'payout_id', v_existing.payout_id, 'duplicate', true);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.members
        WHERE id = p_member_id AND group_id = p_group_id AND status = 'active'
    ) THEN
        RAISE EXCEPTION '[FIN_003] MEMBER_NOT_ACTIVE';
    END IF;

    INSERT INTO public.rosca_rotation_executions (group_id, member_id, period, total_pot, net_amount)
    VALUES (p_group_id, p_member_id, p_period, p_total_pot, p_payout_amount)
    ON CONFLICT (group_id, member_id, period) DO NOTHING
    RETURNING group_id INTO v_claimed;

    IF NOT FOUND THEN
        RAISE EXCEPTION '[SYS_002] ROSCA_PERIOD_ALREADY_CLAIMED';
    END IF;

    FOR v_loan IN (SELECT * FROM jsonb_to_recordset(p_loan_deductions) AS x(loan_id uuid, amount numeric)) LOOP
        PERFORM public.increment_group_balance(
            p_group_id,
            -v_loan.amount,
            'ROSCA Offset',
            'rosca_payout_offset',
            'rosca-offset:' || p_group_id::text || ':' || p_member_id::text || ':' || p_period || ':' || v_loan.loan_id::text,
            p_member_id
        );
        PERFORM public.record_loan_repayment_v1(
            v_loan.loan_id,
            p_member_id,
            p_group_id,
            v_loan.amount,
            'wallet',
            'rosca:' || p_group_id::text || ':' || p_member_id::text || ':' || p_period || ':' || v_loan.loan_id::text
        );
    END LOOP;

    IF p_payout_amount > 0 THEN
        INSERT INTO public.payouts (group_id, member_id, amount, bank_name, account_no, branch_code, description, status, payout_type)
        VALUES (p_group_id, p_member_id, p_payout_amount, p_payout_data->>'bank_name', p_payout_data->>'account_no', p_payout_data->>'branch_code', 'ROSCA rotation ' || p_period, 'pending', 'rosca') RETURNING id INTO v_p_id;
    END IF;

    UPDATE public.rosca_rotation_executions
       SET payout_id = v_p_id, loan_offsets = v_offset_total
     WHERE group_id = p_group_id AND member_id = p_member_id AND period = p_period;

    PERFORM public.log_audit_event('ROSCA_PAYOUT_INITIATED', 'system_rosca', p_member_id, p_group_id, jsonb_build_object('pot', p_total_pot, 'net_payout', p_payout_amount, 'loan_offsets', v_offset_total, 'period', p_period), v_p_id);

    RETURN jsonb_build_object('success', true, 'payout_id', v_p_id, 'duplicate', false);
END;
$$;

-- >>> search_members_v1
CREATE OR REPLACE FUNCTION public.search_members_v1(p_query TEXT)
RETURNS SETOF JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    -- This reads every group's roster and behaviour track past RLS, so it is the platform console's
    -- function and nobody else's. Without the check, a signed-in member could enumerate strangers by
    -- typing a surname.
    IF NOT public.is_platform_admin() THEN
        RAISE EXCEPTION '[SEC_002] PLATFORM_ADMIN_ONLY' USING
            DETAIL = 'search_members_v1 reads members and behaviour scores across every group.',
            HINT = 'platform_admin_role_required';
    END IF;

    RETURN QUERY
    SELECT jsonb_build_object(
        'member_id', m.id,
        'member_name', m.full_name,
        'group_id', m.group_id,
        'total_loan_requests', COALESCE(bt.total_loans_requested, 0),
        'overdue_loans', COALESCE(bt.overdue_loans, 0),
        'outstanding_amount', COALESCE(bt.total_outstanding_amount, 0),
        'completion_ratio', COALESCE(bt.loan_completion_rate, 0) / 100.0,
        'risk_band', COALESCE(bt.fraud_risk_level, 'low'),
        'behavior_score', COALESCE(bt.behavior_score, 100.0)
    )
    FROM public.members m
    LEFT JOIN public.member_behavior_track bt ON m.id = bt.member_id
    WHERE m.full_name ILIKE '%' || p_query || '%'
       OR m.id_number ILIKE '%' || p_query || '%'
    LIMIT 20;
END;
$$;

-- >>> process_complex_payment_v4
CREATE OR REPLACE FUNCTION public.process_complex_payment_v4(
    p_group_id UUID,
    p_member_id UUID,
    p_total_amount NUMERIC,
    p_tx_id TEXT,
    p_allocations JSONB,
    p_method TEXT DEFAULT 'bank',
    p_payment_type TEXT DEFAULT 'contribution',
    p_destination TEXT DEFAULT 'group'
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_bal NUMERIC;
    v_all RECORD;
    v_total_sum NUMERIC := 0;
BEGIN
    -- This one call can post a contribution, repay a loan, and charge a joining fee for whichever
    -- member id the request names, so the caller has to be the platform, that group's admin, the
    -- member themself, or the service role the sync worker signs in as.
    IF auth.role() IS DISTINCT FROM 'service_role'
       AND NOT (public.is_platform_admin()
                OR public.is_group_admin(p_group_id)
                OR public.is_own_member(p_member_id)) THEN
        RAISE EXCEPTION '[SEC_002] COMPLEX_PAYMENT_ACCESS_DENIED';
    END IF;

    SELECT balance INTO v_bal FROM public.groups WHERE id = p_group_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND';
    END IF;

    IF jsonb_typeof(p_allocations) <> 'array' OR jsonb_array_length(p_allocations) = 0 THEN
        RAISE EXCEPTION '[FIN_003] At least one payment allocation is required';
    END IF;

    IF EXISTS (SELECT 1 FROM public.payments WHERE group_id = p_group_id AND transaction_id = p_tx_id AND status = 'completed') OR
       EXISTS (SELECT 1 FROM public.group_ledger WHERE group_id = p_group_id AND transaction_id = p_tx_id) THEN
        SELECT balance INTO v_bal FROM public.groups WHERE id = p_group_id;
        RETURN jsonb_build_object('success', true, 'new_balance', v_bal, 'transaction_id', p_tx_id, 'status', 'idempotent');
    END IF;

    SELECT SUM((val->>'amount')::NUMERIC) INTO v_total_sum FROM jsonb_array_elements(p_allocations) AS val;
    IF ABS(v_total_sum - p_total_amount) > 0.01 THEN
        RAISE EXCEPTION '[FIN_003] Allocation total % does not match requested amount %', v_total_sum, p_total_amount;
    END IF;

    INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at)
    VALUES (p_member_id, p_group_id, p_total_amount, p_payment_type, p_method, p_tx_id, 'completed', NOW());

    FOR v_all IN (SELECT * FROM jsonb_to_recordset(p_allocations) AS x(type text, amount numeric, loan_id uuid, due_date date, description text)) LOOP
        IF v_all.amount IS NULL OR v_all.amount <= 0 THEN
            RAISE EXCEPTION '[FIN_003] Allocation amounts must be positive';
        END IF;

        IF v_all.type = 'registration_contribution' OR v_all.type = 'joining_fee' THEN
            PERFORM public.internal_post_contribution(p_member_id, p_group_id, v_all.amount, COALESCE(v_all.due_date, CURRENT_DATE), NOW(), 'paid', p_tx_id || ':' || v_all.type, v_all.type, v_all.description);
        ELSIF v_all.type = 'contribution' THEN
            PERFORM public.internal_post_contribution(p_member_id, p_group_id, v_all.amount, COALESCE(v_all.due_date, CURRENT_DATE), NOW(), 'paid', p_tx_id || ':contribution', 'contribution', v_all.description);
        ELSIF v_all.type = 'loan_repayment' AND v_all.loan_id IS NOT NULL THEN
            PERFORM public.record_loan_repayment_v1(v_all.loan_id, p_member_id, p_group_id, v_all.amount, p_method);
        ELSIF v_all.type = 'late_fee' THEN
            PERFORM public.internal_post_contribution(p_member_id, p_group_id, v_all.amount, COALESCE(v_all.due_date, CURRENT_DATE), NOW(), 'paid', p_tx_id || ':late_fee', 'late_fee', COALESCE(v_all.description, 'Late payment fee'));
        ELSIF v_all.type = 'member_fee' OR v_all.type = 'member_fee_ledger' OR v_all.type = 'platform_fee' THEN
            PERFORM public.internal_post_contribution(p_member_id, p_group_id, v_all.amount, COALESCE(v_all.due_date, CURRENT_DATE), NOW(), 'paid', p_tx_id || ':' || v_all.type, v_all.type, COALESCE(v_all.description, 'Monthly Member Fee'));
        ELSE
            RAISE EXCEPTION '[FIN_003] Unsupported payment allocation type: %', v_all.type;
        END IF;
    END LOOP;

    PERFORM public.recalculate_all_group_financials_v3(p_group_id);

    SELECT balance INTO v_bal FROM public.groups WHERE id = p_group_id;
    RETURN jsonb_build_object('success', true, 'new_balance', v_bal, 'transaction_id', p_tx_id);
END;
$$;


-- ────────────────────────────────────────────────────────────────────────────────
-- P.2  WRITE. The execute grants, withdrawn. Must run after P.1: is_own_member is a new
--      object, so a CREATE OR REPLACE made before the revoke would hand it the default
--      PUBLIC execute and leave it anonymous-callable.
--
--      Mirror of 03_SECURITY_AND_RLS.sql section 2c, plus the one line pair that section
--      inherits from 18_DEPLOY_PROCESS_COMPLEX_PAYMENT_V4.sql. Re-running any other
--      section of 03 - or 04_PLATFORM_ADMIN_SETUP.sql - re-applies the blanket
--      "GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO anon, authenticated,
--      service_role", so a later ground-zero rebuild of a live tier silently undoes this.
--      If that happens, this section is the whole fix; it is idempotent.

-- Tier one: anonymous execute withdrawn, signed-in execute kept, because a member
-- legitimately calls all of these.
REVOKE EXECUTE ON FUNCTION
    public.record_platform_revenue_v2(NUMERIC, TEXT, TEXT, TEXT, UUID),
    public.internal_post_contribution(UUID, UUID, NUMERIC, DATE, TIMESTAMPTZ, TEXT, TEXT, TEXT, TEXT),
    public.record_contribution_v2(UUID, UUID, NUMERIC, DATE, TIMESTAMPTZ, TEXT, TEXT, TEXT, TEXT),
    public.record_loan_repayment_v1(UUID, UUID, UUID, NUMERIC, TEXT, TEXT),
    public.approve_and_disburse_loan_v2(UUID, UUID, TEXT),
    public.complete_payout_v2(UUID, UUID, TEXT),
    public.process_rosca_payout_v2(UUID, UUID, NUMERIC, JSONB, NUMERIC, JSONB),
    public.process_rosca_payout_v3(UUID, UUID, NUMERIC, JSONB, NUMERIC, JSONB, TEXT),
    public.pay_platform_fee_v2(UUID, NUMERIC, TEXT, TEXT, BOOLEAN),
    public.reconcile_bank_transaction_v1(UUID, UUID)
FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION
    public.record_platform_revenue_v2(NUMERIC, TEXT, TEXT, TEXT, UUID),
    public.internal_post_contribution(UUID, UUID, NUMERIC, DATE, TIMESTAMPTZ, TEXT, TEXT, TEXT, TEXT),
    public.record_contribution_v2(UUID, UUID, NUMERIC, DATE, TIMESTAMPTZ, TEXT, TEXT, TEXT, TEXT),
    public.record_loan_repayment_v1(UUID, UUID, UUID, NUMERIC, TEXT, TEXT),
    public.approve_and_disburse_loan_v2(UUID, UUID, TEXT),
    public.complete_payout_v2(UUID, UUID, TEXT),
    public.process_rosca_payout_v2(UUID, UUID, NUMERIC, JSONB, NUMERIC, JSONB),
    public.process_rosca_payout_v3(UUID, UUID, NUMERIC, JSONB, NUMERIC, JSONB, TEXT),
    public.pay_platform_fee_v2(UUID, NUMERIC, TEXT, TEXT, BOOLEAN),
    public.reconcile_bank_transaction_v1(UUID, UUID)
TO authenticated, service_role;

-- Tier two: withdrawn from `authenticated` too. These are the ledger primitives and the
-- scheduled maintenance jobs; production code has no business calling them by name from a
-- client (the simulation harness that does is listed in P.4). A nested call inside a
-- SECURITY DEFINER wrapper is authorised against the wrapper's OWNER (postgres on both
-- tiers), not the session role, so no wrapper breaks.
REVOKE EXECUTE ON FUNCTION
    public.increment_group_balance(UUID, NUMERIC, TEXT, TEXT, TEXT, UUID),
    public.recompute_ledger_running_balances_v2(UUID),
    public.perform_group_reconciliation_v1(UUID),
    public.consolidate_group_ledger_v1(UUID),
    public.create_ledger_checkpoint_v1(UUID),
    public.automate_monthly_billing_v1(),
    public.simulate_bulk_activity_v2(UUID, INT, INT),
    public.record_platform_revenue_v1(NUMERIC, TEXT, TEXT, TEXT, UUID)
FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION
    public.increment_group_balance(UUID, NUMERIC, TEXT, TEXT, TEXT, UUID),
    public.recompute_ledger_running_balances_v2(UUID),
    public.perform_group_reconciliation_v1(UUID),
    public.consolidate_group_ledger_v1(UUID),
    public.create_ledger_checkpoint_v1(UUID),
    public.automate_monthly_billing_v1(),
    public.simulate_bulk_activity_v2(UUID, INT, INT),
    public.record_platform_revenue_v1(NUMERIC, TEXT, TEXT, TEXT, UUID)
TO service_role;

-- The payment wrapper deployed by its own master, the two console reads, and the guard
-- helper. All had the blanket grant; none needs it.
REVOKE EXECUTE ON FUNCTION public.process_complex_payment_v4(UUID, UUID, NUMERIC, TEXT, JSONB, TEXT, TEXT, TEXT)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.process_complex_payment_v4(UUID, UUID, NUMERIC, TEXT, JSONB, TEXT, TEXT, TEXT)
  TO authenticated, service_role;

REVOKE EXECUTE ON FUNCTION public.recalculate_all_group_financials_v3(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.recalculate_all_group_financials_v3(UUID) TO authenticated, service_role;

REVOKE EXECUTE ON FUNCTION public.search_members_v1(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.search_members_v1(TEXT) TO authenticated, service_role;

REVOKE EXECUTE ON FUNCTION public.is_own_member(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_own_member(UUID) TO authenticated, service_role;

-- ────────────────────────────────────────────────────────────────────────────────
-- P.3  VERIFY - run in a new session, after P.1 and P.2 have committed.

-- P.3.a  Expect fourteen POST rows, exactly these hashes and lengths. A body that
--        predates this delta keeps the hash its own section documents, so the two tables
--        are not interchangeable.
select p.proname,
       md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as body_hash,
       length(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as norm_len,
       case md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g')))
         when '18d632ba725d3b60115283d513aca1a0' then 'MATCH'
         when 'e5f697487956b76254142531c6258b2b' then 'MATCH'
         when '2e9d91dbd6e3d95591f93b1dbf16a1bb' then 'MATCH'
         when '793f9e69a890b7a420d631735b727592' then 'MATCH'
         when 'c838e1b54121e50721c1f40dbb3e1c5b' then 'MATCH'
         when '39323430c91a240b2a03d215ba5bd437' then 'MATCH'
         when 'a57ed0d631d228d09b64a40f02713243' then 'MATCH'
         when '02ff622b56d49c28d402871f01c10fcb' then 'MATCH'
         when '3b06a96f42c8a498fc6bf4d412f0fe14' then 'MATCH'
         when '5e936105b7dadbb7c043912e10c45388' then 'MATCH'
         when 'c40bc151cbe6542fb1dd0f62a0044cd1' then 'MATCH'
         when '10e0b1e6a14b085c41223f3a6a40a2b8' then 'MATCH'
         when '8bf783f2668d2cae418469b19c8caab3' then 'MATCH'
         when '3cb48745733b276dcb8e961aae053410' then 'MATCH'
         else 'MISMATCH'
       end as verdict
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in ('is_own_member', 'record_platform_revenue_v2', 'internal_post_contribution',
                    'record_contribution_v2', 'record_loan_repayment_v1', 'pay_platform_fee_v2',
                    'reconcile_bank_transaction_v1', 'automate_monthly_billing_v1',
                    'approve_and_disburse_loan_v2', 'complete_payout_v2', 'process_rosca_payout_v2',
                    'process_rosca_payout_v3', 'search_members_v1', 'process_complex_payment_v4')
order by p.proname;

-- expected: approve_and_disburse_loan_v2   3b06a96f42c8a498fc6bf4d412f0fe14  1986
--           automate_monthly_billing_v1    02ff622b56d49c28d402871f01c10fcb  1508
--           complete_payout_v2             5e936105b7dadbb7c043912e10c45388  1645
--           internal_post_contribution     2e9d91dbd6e3d95591f93b1dbf16a1bb  2452
--           is_own_member                  18d632ba725d3b60115283d513aca1a0   134
--           pay_platform_fee_v2            39323430c91a240b2a03d215ba5bd437  4080
--           process_complex_payment_v4     3cb48745733b276dcb8e961aae053410  3689
--           process_rosca_payout_v2        c40bc151cbe6542fb1dd0f62a0044cd1  1560
--           process_rosca_payout_v3        10e0b1e6a14b085c41223f3a6a40a2b8  5803
--           record_contribution_v2         793f9e69a890b7a420d631735b727592  1439
--           record_loan_repayment_v1       c838e1b54121e50721c1f40dbb3e1c5b  2824
--           record_platform_revenue_v2     e5f697487956b76254142531c6258b2b  2045
--           reconcile_bank_transaction_v1  a57ed0d631d228d09b64a40f02713243   855
--           search_members_v1              8bf783f2668d2cae418469b19c8caab3  1094
--
--        internal_post_contribution, record_contribution_v2, record_platform_revenue_v2 and
--        process_rosca_payout_v3 all grew from the 20261028 values (2039 -> 2452, 1212 ->
--        1439, 1502 -> 2045, 5421 -> 5803): the guard plus its comment is the whole
--        difference. If a tier prints the 20261028 hash after P.1, P.1 did not run there.

-- P.3.b  ACL after P.2 - all twenty-two rows, expectations spelled out as a join so the
--        verdict column does the reading. anon_execute must be false on every row;
--        authed_execute must be false on exactly the eight tier-two primitives.
select p.proname,
       p.pronargs,
       has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
       has_function_privilege('authenticated', p.oid, 'EXECUTE') as authed_execute,
       has_function_privilege('service_role', p.oid, 'EXECUTE') as svc_execute,
       (expected.anon_execute, expected.authed_execute, expected.svc_execute)
         is not distinct from
       (has_function_privilege('anon', p.oid, 'EXECUTE'),
        has_function_privilege('authenticated', p.oid, 'EXECUTE'),
        has_function_privilege('service_role', p.oid, 'EXECUTE')) as verdict
from (values
        ('increment_group_balance'::text, false::boolean, false::boolean, true::boolean),
        ('recompute_ledger_running_balances_v2', false, false, true),
        ('perform_group_reconciliation_v1', false, false, true),
        ('consolidate_group_ledger_v1', false, false, true),
        ('create_ledger_checkpoint_v1', false, false, true),
        ('automate_monthly_billing_v1', false, false, true),
        ('simulate_bulk_activity_v2', false, false, true),
        ('record_platform_revenue_v1', false, false, true),
        ('record_platform_revenue_v2', false, true, true),
        ('internal_post_contribution', false, true, true),
        ('record_contribution_v2', false, true, true),
        ('record_loan_repayment_v1', false, true, true),
        ('approve_and_disburse_loan_v2', false, true, true),
        ('complete_payout_v2', false, true, true),
        ('process_rosca_payout_v2', false, true, true),
        ('process_rosca_payout_v3', false, true, true),
        ('process_complex_payment_v4', false, true, true),
        ('pay_platform_fee_v2', false, true, true),
        ('reconcile_bank_transaction_v1', false, true, true),
        ('recalculate_all_group_financials_v3', false, true, true),
        ('search_members_v1', false, true, true),
        ('is_own_member', false, true, true)
     ) as expected(proname, anon_execute, authed_execute, svc_execute)
join pg_proc p on p.proname = expected.proname
join pg_namespace n on n.oid = p.pronamespace and n.nspname = 'public'
order by verdict, p.proname;
-- expect: 22 rows, every verdict true, sorted so any failure is on top. A second row with
--         the same proname is an overload left on that tier (no name in the masters is
--         overloaded) - P.2 addressed the signature it names, so the extra OID is a stale
--         entry to drop, not a probe to re-run. A false in svc_execute means the GRANT leg
--         did not run and the workers are now locked out of their own primitives - that is
--         an outage, not a hardening.

-- P.3.c  Self-check: what do auth.role() and auth.uid() actually report inside a session
--        with a hand-set claim? Read this BEFORE trusting any P.3.d probe. Supabase's
--        auth.jwt() reads request.jwt.claims (older images: request.jwt.claim), and some
--        builds of auth.role() fall back to the session role instead of the claim, so a
--        probe can silently test the wrong thing.
begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000","role":"authenticated","aud":"authenticated"}';
  set local request.jwt.claim.role = 'authenticated';
  select auth.role() as jwt_role, auth.uid() as jwt_uid, public.is_platform_admin() as is_admin;
rollback;
-- expect: jwt_role = 'authenticated', jwt_uid = NULL, is_admin = false.
--         If jwt_role comes back as 'postgres', auth.role() on this image reads the
--         session role and not the claim: the P.3.d claim probes then prove nothing about
--         a real login and the guard has to be exercised over /rest/v1/rpc from CLOUD
--         instead (P.3.e). Never as anon on Local - it crashes the backend, signal 11.

-- P.3.d  Behavioural probes as a signed-in non-admin. Each block is complete and
--        self-contained; the preamble is repeated on purpose, because SET LOCAL dies with
--        its transaction. Every block aborts on its own - the RAISE ends the transaction
--        and the trailing rollback is belt-and-braces - so nothing is written. The ids are
--        fabricated.
--
--        Which side of the guard you land on depends on what P.2 did:
--          * tier-one functions -> the session role still has EXECUTE, so the body runs
--            and the guard answers. Expect [SEC_002] *_ACCESS_DENIED.
--          * tier-two primitives -> the session role has NO EXECUTE, so PostgreSQL
--            answers before the body is entered. Expect
--            ERROR: permission denied for function <name>.
--            That is the hardened answer, not a failed probe.
--
--        Replace the fabricated ids only where a section below says to.

begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000","role":"authenticated","aud":"authenticated"}';
  set local request.jwt.claim.role = 'authenticated';
  select public.record_contribution_v2('22222222-2222-2222-2222-222222222222',
         '11111111-1111-1111-1111-111111111111', -1, now()::date, now(), 'paid');
rollback;
-- expect: ERROR: [SEC_002] CONTRIBUTION_ACCESS_DENIED
--         Guard-first (five statements after BEGIN). Before this delta the same call with
--         a real member id and a positive amount credited that member and moved the group
--         balance from an unauthenticated request.

begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000","role":"authenticated","aud":"authenticated"}';
  set local request.jwt.claim.role = 'authenticated';
  select public.internal_post_contribution('22222222-2222-2222-2222-222222222222',
         '11111111-1111-1111-1111-111111111111', -1, now()::date, now(), 'paid', 'probe-tx-1');
rollback;
-- expect: ERROR: [SEC_002] CONTRIBUTION_ACCESS_DENIED  (guard-first, three statements in)

begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000","role":"authenticated","aud":"authenticated"}';
  set local request.jwt.claim.role = 'authenticated';
  select public.record_platform_revenue_v2(-1, 'probe', 'fee');
rollback;
-- expect: ERROR: [SEC_002] PLATFORM_REVENUE_ACCESS_DENIED

begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000","role":"authenticated","aud":"authenticated"}';
  set local request.jwt.claim.role = 'authenticated';
  select public.record_loan_repayment_v1('33333333-3333-3333-3333-333333333333',
         '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', -1);
rollback;
-- expect: ERROR: [SEC_002] LOAN_REPAYMENT_ACCESS_DENIED
--         It fires before [SYS_001] GROUP_NOT_FOUND, so the fabricated ids are never
--         looked up - that ordering is the point: the caller is proved before the tier is
--         read.

begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000","role":"authenticated","aud":"authenticated"}';
  set local request.jwt.claim.role = 'authenticated';
  select public.pay_platform_fee_v2('11111111-1111-1111-1111-111111111111', -1, 'monthly');
rollback;
-- expect: ERROR: [SEC_002] PLATFORM_FEE_ACCESS_DENIED  (before INVALID_AMOUNT and before
--         the [SEC_001] MFA floor, which is where 20261027 left it)

begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000","role":"authenticated","aud":"authenticated"}';
  set local request.jwt.claim.role = 'authenticated';
  select public.process_rosca_payout_v2('11111111-1111-1111-1111-111111111111',
         '22222222-2222-2222-2222-222222222222', 100, '[]'::jsonb, 100, '{}'::jsonb);
rollback;
-- expect: ERROR: [SEC_002] ROSCA_PAYOUT_ACCESS_DENIED
--         The most important block here: v2 has no period key and no amount checks, so a
--         replay of one call used to rotate the pot again. Do NOT re-run it with a
--         service_role claim - the guard is the only RAISE in that body, so a permitted
--         caller goes on to the rotation logic.

begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000","role":"authenticated","aud":"authenticated"}';
  set local request.jwt.claim.role = 'authenticated';
  select public.process_complex_payment_v4('11111111-1111-1111-1111-111111111111',
         '22222222-2222-2222-2222-222222222222', 100, '[]'::jsonb, 'probe');
rollback;
-- expect: ERROR: [SEC_002] COMPLEX_PAYMENT_ACCESS_DENIED
--         Fires before [SYS_001] GROUP_NOT_FOUND and before the allocation checks, and the
--         same guard admits the member's own login through is_own_member, which is what
--         lets a signed-in member post an allocated payment at all.

begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000","role":"authenticated","aud":"authenticated"}';
  set local request.jwt.claim.role = 'authenticated';
  select public.search_members_v1('probe');
rollback;
-- expect: ERROR: [SEC_002] PLATFORM_ADMIN_ONLY, plus the DETAIL and HINT lines.
--         This guard has no service_role exemption on purpose - the platform console is
--         the only caller and it is always signed in - so it refuses a service-key request
--         too. If an edge function ever needs it, that is a second identity decision, not
--         a line to delete here. This is also the read that the client used to answer with
--         an empty roster (see P.4).

begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000","role":"authenticated","aud":"authenticated"}';
  set local request.jwt.claim.role = 'authenticated';
  select public.process_rosca_payout_v3('11111111-1111-1111-1111-111111111111',
         '22222222-2222-2222-2222-222222222222', 100, '[]'::jsonb, 100, '{}'::jsonb, 'nope');
rollback;
-- expect: ERROR: [FIN_003] INVALID_PERIOD: Expected YYYY-MM
--         NOT a guard failure: v3 validates its payload before it asks who is asking, so a
--         malformed period answers first. Well formed, the guard is reached:
begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000","role":"authenticated","aud":"authenticated"}';
  set local request.jwt.claim.role = 'authenticated';
  select public.process_rosca_payout_v3('11111111-1111-1111-1111-111111111111',
         '22222222-2222-2222-2222-222222222222', 100, '[]'::jsonb, 100, '{}'::jsonb, '2026-11');
rollback;
-- expect: ERROR: [SEC_002] ROSCA_PAYOUT_ACCESS_DENIED
--         The guard sits immediately before the FOR UPDATE lock, so a refused caller never
--         queues writers behind it.

begin;
  set local role authenticated;
  select public.increment_group_balance('11111111-1111-1111-1111-111111111111', 100, 'probe');
rollback;
-- expect: ERROR: permission denied for function increment_group_balance
--         Tier two: the body is never entered. This is the probe showing the raw ledger
--         primitive - any signed amount, any group - is no longer a client endpoint.

begin;
  set local role authenticated;
  select public.recompute_ledger_running_balances_v2('11111111-1111-1111-1111-111111111111');
rollback;
-- expect: ERROR: permission denied for function recompute_ledger_running_balances_v2

begin;
  set local role authenticated;
  select public.perform_group_reconciliation_v1('11111111-1111-1111-1111-111111111111');
rollback;
-- expect: ERROR: permission denied for function perform_group_reconciliation_v1

-- Three functions are left unprobed as `authenticated` with fabricated ids, because their
-- guard sits behind a row lookup and [SYS_001] answers first, which proves nothing about
-- the guard. To exercise them, take a real id from the tier inside the same preamble:
--   reconcile_bank_transaction_v1     select id from public.bank_transactions limit 1;
--   approve_and_disburse_loan_v2      select id from public.loans where status = 'pending' limit 1;
--   complete_payout_v2                select id from public.payouts where status in ('group_approved','processing') limit 1;
--   then: select public.reconcile_bank_transaction_v1(:bank_id, '44444444-4444-4444-4444-444444444444');
--         select public.approve_and_disburse_loan_v2(:loan_id, '55555555-5555-5555-5555-555555555555');
--         select public.complete_payout_v2(:payout_id, '55555555-5555-5555-5555-555555555555');
--   expect: [SEC_002] BANK_RECONCILE_ACCESS_DENIED / LOAN_DISBURSEMENT_ACCESS_DENIED /
--           PAYOUT_COMPLETION_ACCESS_DENIED. The ledger and admin ids stay fabricated, so
--           even a permitted call could not complete - and the transaction is rolled back.
--   With no rows in those tables the answer is [SYS_001] BANK_TRANSACTION_NOT_FOUND /
--   LOAN_NOT_FOUND / PAYOUT_NOT_FOUND. For reconcile that message is NEW in this delta
--   (it used to be an uncoded 'Bank transaction not found'), so it also proves P.1 ran.

-- NEVER run these as a permitted (service_role-claim) probe:
--   automate_monthly_billing_v1()           - no arguments and no other RAISE: a permitted
--                                             caller bills EVERY group in the tier.
--   increment_group_balance with a real id  - writes groups.balance directly.
--   process_rosca_payout_v2 with real ids   - rotates a real pot, with nothing to replay.
-- The pg_cron path for automate_monthly_billing_v1 is proved by its exemption instead: the
-- guard is `IF auth.jwt() IS NOT NULL AND ...`, and a session with no claim has auth.jwt()
-- = NULL, so the scheduler is never asked who it is. That half is read-only - confirm it:
begin;
  set local role authenticated;
  select auth.jwt() is null as no_jwt;
rollback;
-- expect: true, which is why the billing job still runs from pg_cron after this delta.

-- P.3.e  The same surface from outside, over HTTP. CLOUD ONLY - see HOW TO PROVE A REVOKE
--        ON LOCAL above; the anon leg crashes Local Postgres and is not a test there.
--        With only the publishable (anon) key the router refuses before the body:
--   curl -s -w '\n%{http_code}\n' \
--     -H "apikey: $ANON_KEY" -H "Authorization: Bearer $ANON_KEY" \
--     -H 'Content-Type: application/json' \
--     "$URL/rest/v1/rpc/record_contribution_v2" \
--     -d '{"p_member_id":"22222222-2222-2222-2222-222222222222","p_group_id":"11111111-1111-1111-1111-111111111111","p_amount":1,"p_due_date":"2026-10-29","p_paid_at":"2026-10-29T00:00:00Z","p_status":"paid"}'
-- expect: 403, body contains 'permission denied for function
--         record_contribution_v2'. Before this delta the identical request returned 200
--         and posted the contribution. Run the same leg against increment_group_balance
--         for the tier-two answer.
--        The [SEC_002] arm over HTTP needs a real user JWT for a member who is not an
--        admin; 20261028 recorded the same limitation for settle_expired_loan_v2 and it
--        still applies. Until someone signs in with such an account, P.3.d is the only
--        proof of the guards.

-- P.3.f  Ledger invariant spot check (read-only), using the shipped view. Balance must
--        still equal the ledger sum on every group; a guard that aborted mid-write would
--        show up here as DRIFT.
select count(*) as groups_not_consistent
from public.vw_ledger_integrity_check
where status <> 'CONSISTENT';
-- expect: 0   (measured on Local 2026-10-28: 19 groups, all CONSISTENT)

-- Then, in its own statement, so PostgREST stops routing names it no longer permits:
SELECT pg_notify('pgrst','reload schema');

-- ────────────────────────────────────────────────────────────────────────────────
-- P.4  CLIENT SIDE, already in the tree at commit 0105914 + this session's work. Nothing
--      to run here; this is the list of what the app does differently against a tier that
--      has this delta, and against one that has not.
--   data/.../admin/PlatformRepositoryImpl.kt  searchMembers() now calls
--       rpc("search_members_v1") and decodes MemberBehaviorInsight from it. The read it
--       replaces targeted platform_member_behavior_insights_v1, a view no schema in this
--       repo ever created, so it could only 404 - and getOrElse turned that into an empty
--       roster on the admin screen. Against a tier that has not run P.1/P.2 the console
--       now shows a failure instead of "no members match". That is the intended
--       difference; do not paper over it with a fallback.
--       Proved by data/src/test/.../admin/PlatformMemberSearchTest.kt (2 cases).
--   Tier two, live callers of the revoked primitive - all debug or androidTest, none of
--       them a feature, all of them now 403 as `authenticated`:
--         app/src/debug/.../LiveSimulationRunner.kt:617 and
--         app/src/androidTest/.../LiveFinancialFlowSimulation.kt:78  seed liquidity
--         LiveSimulationRunner.kt:390, :452, :536  hand-debit after a disbursement RPC
--                                                    fails, then force the row status
--         app/src/androidTest/.../BusinessProcessSimulationTest.kt:322  via the repository
--       The production path really is dead: GroupRepositoryImpl.kt:81 and
--       GroupFinancialRepositoryImpl.kt:16 exist only to serve OutboxHandlers.kt:78
--       (BalanceAdjustmentOutboxHandler), and nothing in :app or :domain enqueues that
--       operation. Both halves want the same follow-up: re-point the seed injections at
--       record_contribution_v2 and delete the compensations, then drop the two repository
--       methods, the two domain interface methods, GroupDataSource.incrementGroupBalance,
--       the handler and its SyncRepositoryImpl wiring. Not done here: that is simulation
--       semantics plus an instrumentation run, which this machine cannot do.
--       Why the primitive is revoked rather than guarded: it appends a group_ledger row
--       under category 'adjustment' and asks nothing about the caller - no admin check, no
--       membership, either sign, any group. An in-body guard cannot replace that, because
--       record_contribution_v2 calls it nested with the paying member's JWT in the session,
--       and an admin guard would refuse a member paying their own contribution.
