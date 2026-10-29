-- ──────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave - DELTA 2026-10-29B  (the three RPCs the app has always called by name
-- and the database had never declared: close_poll_v1, pay_burial_claim_v1,
-- record_disbursement_v1)
-- Target: Supabase Cloud (Dashboard SQL editor) and the Local tier.
-- Hand-maintained. SYNC_DELTA_20261014/21/22/26/26B/27/28/29 are applied history - do not
-- re-run them and do not edit them to describe these changes.
-- ──────────────────────────────────────────────────────────────────────────────

-- WHAT THIS DOES
--   Three call sites in :data name an RPC that exists in no master and on no tier, so each
--   flow died with PGRST202 ('Could not find the function ... in the schema cache') before
--   it reached any table:
--     data/.../repository/group/VotingRepositoryImpl.kt:111   closePoll
--     data/.../remote/supabase/SupabaseClaimDataSourceImpl.kt:78  payClaimAtomic
--     data/.../remote/supabase/SupabaseGroupDataSourceImpl.kt:190 recordDisbursement
--   SYNC_DELTA_20261021 K.4.e recorded exactly this and deliberately did not fix it
--   ("Still-open gaps this delta does not close ... Expect definitions_in_db = 0 for all
--   three"). This delta is that fix.
--
--   P.1 installs the three bodies, copied verbatim from
--   supabase/02_FUNCTIONS_AND_TRIGGERS.sql (mirrored into
--   supabase/migrations/00000000000000_GROUND_ZERO.sql,
--   CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql and online/000_CONSOLIDATED_ONLINE_SYNC.sql by
--   supabase/scripts/generate_ground_zero.ps1 + generate_consolidated_schema.ps1).
--   close_poll_v1            - locks nothing, moves no money. Until now the only close path
--                              was fn_trigger_sync_poll_status flipping a poll at its own
--                              ends_at, so a poll with a deadline months away could not be
--                              closed early even though the voting screen offers a Close
--                              button. Tries are refused with [SYS_001] POLL_NOT_FOUND, then
--                              [SEC_002] POLL_CLOSE_ACCESS_DENIED, then [FIN_002] BAD_STATUS;
--                              an accepted close writes status='closed' and one POLL_CLOSED
--                              audit row. The tally stays owned by tr_sync_poll_vote_count
--                              and the auto-close audit stays owned by the BEFORE UPDATE
--                              trigger, which only fires for a poll it finds open, so closing
--                              here cannot double-count or double-audit.
--   pay_burial_claim_v1      - the money path that did not exist: a burial claim could not be
--                              paid over HTTP at all. It is the state machine
--                              domain/.../member/ProcessBurialClaimUseCase.kt already runs,
--                              moved to where the server decides final truth. Order is the
--                              one every other outflow uses (complete_payout_v2): claim
--                              lookup, groups FOR UPDATE, caller check, status check, amount
--                              check, [SEC_001] MFA floor at R10,000, [FIN_001] NO_CASH,
--                              then the write. The ledger row it posts is category 'claim',
--                              description 'Burial claim: ' || beneficiary_name, transaction
--                              id = the claim id - byte for byte what consolidate_group_ledger_v1
--                              reconstructs for a paid claim, so a live write and a ledger
--                              rebuild cannot diverge, and the claim id as the key means a
--                              replay cannot debit the group twice. Payable statuses are
--                              submitted / under_review / approved: an escalated claim must be
--                              approved before money moves, and paid / rejected / cancelled
--                              accept no further decision.
--                              reviewed_by records the session (auth.uid()) when there is one,
--                              else a real typed p_admin_id. The client sends the nil UUID
--                              whenever the reviewer is not a login it can name
--                              (BeneficiaryClaimRepositoryImpl.buildPayClaimRpcParams passes
--                              "00000000-0000-0000-0000-000000000000" for the platform
--                              console, which types "PLATFORM_ADMIN"), and that sentinel is
--                              dropped here - it is not a row in auth.users and reviewed_by
--                              has a foreign key to it.
--   record_disbursement_v1   - a guarded wrapper around increment_group_balance, and that is
--                              the whole function: caller check, sign/scale check, non-blank
--                              description and category, then the primitive with the amount
--                              negated. The primitive still owns the FOR UPDATE group lock,
--                              the [SYS_002] transaction-id idempotency key and
--                              [FIN_002] INSUFFICIENT_FUNDS, so no rule is restated here and
--                              nothing can drift between the two. It returns the new balance
--                              exactly as increment_group_balance does, which is what
--                              GroupRepositoryImpl / GroupFinancialRepositoryImpl cache as
--                              Money.
--
--   WHY record_disbursement_v1 HAS TO BE A WRAPPER (and why the order of these two deltas
--   does not matter): SYNC_DELTA_20261029 revokes EXECUTE on increment_group_balance from
--   PUBLIC and anon and re-grants it only to service_role, because it is the raw ledger
--   primitive - any signed amount, no caller check. A signed-in group admin therefore has no
--   HTTP route to book a disbursement, which is exactly the door record_disbursement_v1
--   reopens, one-sided and admin-only. PostgreSQL authorises the nested call against the
--   wrapper's owner role, not the session role, and REVOKE ... FROM PUBLIC leaves the owner's
--   own ACL entry alone, so the wrapper keeps working whichever of the two deltas runs first.
--   Do not "simplify" this by re-granting increment_group_balance to authenticated: 20261029
--   withdrew it for a reason and this wrapper is the reason-proof answer.
--
--   Every guard has the same shape as the ones 20261029 shipped, taken from
--   settle_expired_loan_v2:
--       IF auth.role() IS DISTINCT FROM 'service_role'
--          AND NOT (public.is_platform_admin() OR public.is_group_admin(...)) THEN
--           RAISE EXCEPTION '[SEC_002] ..._ACCESS_DENIED';
--       END IF;
--   The service_role exemption is not a looseness - the workers sign with the service key, for
--   which auth.uid() is NULL, so every is_* helper answers false and a guard without the
--   exemption would refuse the very jobs that pay fees and rotate pots. None of these three
--   admits is_own_member: closing a poll, paying a claim and booking a disbursement are all
--   decisions about the group's money or governance, never self-service.
--
--   P.2 sets the privileges, copied verbatim from section 2d of
--   supabase/03_SECURITY_AND_RLS.sql. PostgreSQL grants EXECUTE to PUBLIC when a function is
--   created and the blanket grant in section 2 of that master hands it to anon as well, so the
--   anonymous route is taken back immediately. All three stay callable by authenticated: the
--   shipped UI reaches them from a signed-in admin, and each body then decides for itself -
--   record_disbursement_v1 is grant-reachable by a member in name only, because its own guard
--   refuses anyone who is not that group's admin, a platform admin, or the service role.
--
--   CONSEQUENCE FOR THE CLIENT (already in the tree, see P.4): the two refusals that this
--   delta introduces were previously unreachable code. closePoll had no onFailure, so a
--   member who pressed Close on someone else's group saw nothing at all; the button also
--   posted item.poll.id.orEmpty(), which sends "" and fails a UUID cast on the way to a
--   clearer answer. Both are fixed in P.4's client list.
--
--   P.3 verifies. P.3.a/b/c are read-only. P.3.d blocks each raise, and each block rolls
--   back, so nothing is written; run it in a new session after P.1 and P.2 have committed.
--   P.4 lists the client-side half - nothing to execute there.

-- ORDER OF WORK
--   1. Run P.0 on its own. It writes nothing. P.0.a must print MISSING for all three names;
--      if it prints a definition, that tier already has a variant - read its body before
--      overwriting it. P.0.b must find all five dependencies. These bodies CALL
--      is_group_admin, is_platform_admin, assert_mfa_for_disbursement_v1,
--      increment_group_balance and log_audit_event, and a SECURITY DEFINER body that cannot
--      resolve one of those fails at run time, not at create time. All five arrive by
--      SYNC_DELTA_20261014/21, so any tier that has applied the history this file targets
--      already has them.
--   2. Run P.1, then P.2. One paste is fine, in that order: a CREATE OR REPLACE of a NEW
--      object inherits the default PUBLIC execute grant, so P.2 after P.1 is the only
--      sequence that ends with the ACL this delta intends. (The three bodies each raise on a
--      non-admin session before their first write, so the window is refused by the guard even
--      mid-paste - it is still not a window to leave open.)
--   3. Run P.3.a through P.3.d. Then reload the API cache:
--        SELECT pg_notify('pgrst','reload schema');
--      A tier that skips the notify keeps answering PGRST202 for names it now has, which is
--      indistinguishable from this delta not having run.

-- TIER NOTES
--   - NEITHER TIER HAS RUN THIS DELTA. It was cut from the masters on 2026-10-28, so the PRE
--     column below is the repo's own record, not a cloud measurement. P.0.a is the first
--     actual measurement anyone will take of these names on a tier.
--   - LOCAL (aszmodkqpafwtnfufmow, postgres 17.6.1) is at SYNC_DELTA_20261028 (applied
--     2026-10-28 15:12, digest 1032|C=535 E=1 F=83 G=15 I=33 K=198 P=85 R=38 S=2 T=38 V=4|
--     9752967a0c8a1e095d1de650a6132efb). SYNC_DELTA_20261029 adds one function (F 83 -> 84)
--     and this one adds three, so a Local tier that has run both measures F=87. Re-measure
--     supabase/maintenance/SCHEMA_PARITY_DIGEST.sql afterwards and update the expected
--     baseline in its header.
--   - CLOUD still owes SYNC_DELTA_20261027, then 20261028, then 20261029, then this one, in
--     that order. Nothing here depends on 27/28 (the five callees are baseline, P.0.b), but the
--     order matters for the ACL: this delta's P.3.b expectation is a tier where 20261029's
--     blanket-grant rollback has landed, and 20261028 installs settle_expired_loan_v2 - the
--     guard template these three bodies copy - which the app already calls by name.
--   - There is no psql on the machine this delta was authored from, and cloud SQL is executed
--     by the project owner. This file is a handover, not something the author ran.
--   - Do not rehearse P.1/P.2 by wrapping them in BEGIN ... ROLLBACK and piping them into
--     psql. Measured on this image: an identical wrapper printed ROLLBACK and still committed.
--     Rehearse on a scratch tier, or run P.0 and P.3 as written.

-- HOW TO PROVE A REVOKE ON LOCAL
--   Prove it with has_function_privilege (P.0.c / P.3.b) and nothing else. Never call one of
--   these SECURITY DEFINER RPCs as the anon role over the REST route on Local: measured twice
--   on 2026-10-28 that /rest/v1/rpc/<fn> with only the anon key crashed the backend with
--   signal 11. The curl leg is a CLOUD-ONLY check, where the answer is a clean 403
--   'permission denied for function'.

-- ──────────────────────────────────────────────────────────────────────────────
-- P.0  PRE-FLIGHT - READ ONLY.  Run this first, on its own.

-- P.0.a  The three names. All three are expected to be MISSING on both tiers - that is the
--        whole reason this delta exists. A tier that prints a hash is running something the
--        masters did not describe until 2026-10-28: read it before writing over it.
select expected.name,
       p.oid is not null as defined,
       md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as body_hash,
       length(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as body_len,
       case
         when p.oid is null then 'MISSING (expected pre-state)'
         when md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g')))
              in ('d371d45e490d8ae679c098851780ed02',
                  'a7594be8efb36ccbbeb32776ee32a0ae',
                  '729042ffce49ecfe608e19b7621f0462') then 'POST (this delta already ran)'
         else 'NEITHER - a variant the masters do not describe'
       end as verdict
from (values ('close_poll_v1'), ('pay_burial_claim_v1'), ('record_disbursement_v1')) as expected(name)
left join pg_proc p on p.proname = expected.name
left join pg_namespace n on n.oid = p.pronamespace and n.nspname = 'public'
order by expected.name;
-- expect: 3 rows, defined=false, body_hash=null, verdict='MISSING (expected pre-state)'.

-- P.0.b  The dependencies these bodies call. A missing one is not a privilege problem, it is
--        an ordering problem: run the earlier delta that installs it before P.1.
select expected.name,
       bool_or(p.proname = expected.name) as defined
from (values ('is_platform_admin'), ('is_group_admin'),
             ('assert_mfa_for_disbursement_v1'), ('increment_group_balance'),
             ('log_audit_event')) as expected(name)
left join pg_proc p on p.proname = expected.name
left join pg_namespace n on n.oid = p.pronamespace and n.nspname = 'public'
group by expected.name
order by defined, expected.name;
-- expect: 5 rows, every defined=true. All five are baseline objects declared at the top of
--         02_FUNCTIONS_AND_TRIGGERS.sql (is_platform_admin:14, is_group_admin:22,
--         increment_group_balance:74, log_audit_event:793) or by 20261014:570
--         (assert_mfa_for_disbursement_v1), so a tier built from
--         migrations/00000000000000_GROUND_ZERO.sql already has every one of them. A false row
--         means that tier was not built from this repo's baseline - stop and diff it before
--         writing. None of the three bodies here calls is_own_member, so 20261029 is not a
--         prerequisite for P.1; it is a prerequisite for the revoke P.0.c measures.

-- P.0.c  Where increment_group_balance stands right now. This delta wraps that primitive, so
--        record which side of 20261029 the tier is on before adding the wrapper.
select p.proname,
       has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
       has_function_privilege('authenticated', p.oid, 'EXECUTE') as authed_execute,
       has_function_privilege('service_role', p.oid, 'EXECUTE') as svc_execute
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.proname = 'increment_group_balance';
-- expect (before 20261029): true/true/true.  expect (after 20261029): false/false/true.
-- Either answer is safe for this delta: P.2 re-grants the three NEW names only, never the
-- primitive, and the wrapper's nested call is authorised against its owner.

-- ──────────────────────────────────────────────────────────────────────────────
-- P.1  WRITE. Three CREATE OR REPLACE FUNCTION statements, verbatim copies of
--      supabase/02_FUNCTIONS_AND_TRIGGERS.sql. Fingerprints must land on
--        close_poll_v1            d371d45e490d8ae679c098851780ed02  1193
--        pay_burial_claim_v1      a7594be8efb36ccbbeb32776ee32a0ae  3085
--        record_disbursement_v1   729042ffce49ecfe608e19b7621f0462  1629
--      (P.3.a checks this). Signatures are the ones the client already sends named-argument
--      JSON for, so no call site moves.

CREATE OR REPLACE FUNCTION public.close_poll_v1(p_poll_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_poll RECORD;
BEGIN
    SELECT * INTO v_poll FROM public.group_polls WHERE id = p_poll_id;
    IF v_poll.id IS NULL THEN RAISE EXCEPTION '[SYS_001] POLL_NOT_FOUND'; END IF;

    -- The worker that expires polls runs as service_role, so the exemption is what lets the
    -- scheduler close a poll on a device with no login attached to the request.
    IF auth.role() IS DISTINCT FROM 'service_role'
       AND NOT (public.is_platform_admin() OR public.is_group_admin(v_poll.group_id)) THEN
        RAISE EXCEPTION '[SEC_002] POLL_CLOSE_ACCESS_DENIED';
    END IF;

    IF v_poll.status <> 'open' THEN
        RAISE EXCEPTION '[FIN_002] BAD_STATUS: Poll in % cannot be closed', v_poll.status;
    END IF;

    -- The tally is already maintained per vote by tr_sync_poll_vote_count, and the BEFORE UPDATE
    -- trigger only writes its auto-close audit for a poll it finds open, so closing here arrives
    -- with the status already set and produces no second audit row.
    UPDATE public.group_polls SET status = 'closed', updated_at = NOW() WHERE id = p_poll_id;

    PERFORM public.log_audit_event(
        'POLL_CLOSED',
        COALESCE(auth.uid()::text, 'system_gov'),
        NULL,
        v_poll.group_id,
        jsonb_build_object('poll_id', p_poll_id, 'title', v_poll.title),
        p_poll_id
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.pay_burial_claim_v1(
    p_claim_id UUID,
    p_admin_id UUID DEFAULT NULL,
    p_notes TEXT DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_claim RECORD;
    v_balance NUMERIC;
    -- The client sends the nil UUID whenever the reviewer is not a login it can name (the platform
    -- console passes the literal "PLATFORM_ADMIN" and the param builder falls back to nil), and a
    -- nil reviewer would break the reviewed_by foreign key, so only a session or a real typed id
    -- survives. Same shape as complete_payout_v2: the session decides who paid.
    v_eff_admin_id UUID := COALESCE(
        auth.uid(),
        NULLIF(p_admin_id, '00000000-0000-0000-0000-000000000000'::uuid)
    );
BEGIN
    SELECT * INTO v_claim FROM public.beneficiary_payout_claims WHERE id = p_claim_id;
    IF v_claim.id IS NULL THEN RAISE EXCEPTION '[SYS_001] CLAIM_NOT_FOUND'; END IF;

    -- Groups are locked before claims everywhere else (approve_and_disburse_loan_v2,
    -- complete_payout_v2), so a concurrent payout and claim on one group cannot deadlock.
    PERFORM id FROM public.groups WHERE id = v_claim.group_id FOR UPDATE;

    IF auth.role() IS DISTINCT FROM 'service_role'
       AND NOT (public.is_platform_admin() OR public.is_group_admin(v_claim.group_id)) THEN
        RAISE EXCEPTION '[SEC_002] CLAIM_PAYMENT_ACCESS_DENIED';
    END IF;

    -- Exactly the statuses the client's own machine pays from: a group admin may approve and pay
    -- in one step, an escalated claim must be approved by the platform before money moves, and the
    -- three terminal states accept no further decision - paying twice would draw the cover twice.
    IF v_claim.status NOT IN ('submitted', 'under_review', 'approved') THEN
        RAISE EXCEPTION '[FIN_002] BAD_STATUS: Claim in % cannot be paid', v_claim.status;
    END IF;

    IF v_claim.claim_amount IS NULL OR v_claim.claim_amount::TEXT IN ('NaN', 'Infinity', '-Infinity')
       OR v_claim.claim_amount <= 0 OR v_claim.claim_amount <> ROUND(v_claim.claim_amount, 2) THEN
        RAISE EXCEPTION '[FIN_003] INVALID_AMOUNT: Claim amount must be positive and have at most two decimal places';
    END IF;

    PERFORM public.assert_mfa_for_disbursement_v1(v_claim.claim_amount);

    SELECT balance INTO v_balance FROM public.groups WHERE id = v_claim.group_id;
    IF COALESCE(v_balance, 0) < v_claim.claim_amount THEN
        RAISE EXCEPTION '[FIN_001] NO_CASH: Group balance insufficient for burial claim.';
    END IF;

    UPDATE public.beneficiary_payout_claims
        SET status = 'paid',
            reviewed_by = v_eff_admin_id,
            reviewed_at = NOW(),
            admin_notes = COALESCE(p_notes, admin_notes),
            updated_at = NOW()
        WHERE id = p_claim_id;

    -- Category, description and transaction id are the same ones consolidate_group_ledger_v1
    -- reconstructs for a paid claim, so the live write and a ledger rebuild cannot diverge. The
    -- claim id as the key also means a replayed call cannot debit the group a second time.
    PERFORM public.increment_group_balance(
        v_claim.group_id,
        -v_claim.claim_amount,
        'Burial claim: ' || v_claim.beneficiary_name,
        'claim',
        p_claim_id::text,
        v_claim.member_id
    );

    PERFORM public.log_audit_event(
        'CLAIM_PAID',
        COALESCE(v_eff_admin_id::text, 'system_admin'),
        v_claim.member_id,
        v_claim.group_id,
        jsonb_build_object('claim_id', p_claim_id, 'amount', v_claim.claim_amount, 'beneficiary_id', v_claim.beneficiary_id),
        p_claim_id
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.record_disbursement_v1(
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
    -- A disbursement is a decision about someone else's money, so it is an admin action: unlike the
    -- contribution path there is no is_own_member allowance here, and a member calling it for their
    -- own group is refused.
    IF auth.role() IS DISTINCT FROM 'service_role'
       AND NOT (public.is_platform_admin() OR public.is_group_admin(p_group_id)) THEN
        RAISE EXCEPTION '[SEC_002] DISBURSEMENT_ACCESS_DENIED';
    END IF;

    -- Positive on the wire, negative in the ledger: the client's own guard is "Disbursement amount
    -- must be positive", and the returned balance is what GroupRepositoryImpl caches as Money.
    IF p_amount IS NULL OR p_amount::TEXT IN ('NaN', 'Infinity', '-Infinity')
       OR p_amount <= 0 OR p_amount <> ROUND(p_amount, 2) THEN
        RAISE EXCEPTION '[FIN_003] INVALID_AMOUNT: Disbursement must be positive and have at most two decimal places';
    END IF;
    IF p_description IS NULL OR BTRIM(p_description) = '' THEN
        RAISE EXCEPTION '[FIN_003] INVALID_DESCRIPTION: A ledger description is required';
    END IF;
    -- group_ledger.category has no CHECK constraint, so the vocabulary is whatever the writers
    -- pass; only a blank is refused here, not an unlisted value.
    IF p_category IS NULL OR BTRIM(p_category) = '' THEN
        RAISE EXCEPTION '[FIN_003] INVALID_CATEGORY: A ledger category is required';
    END IF;

    -- The primitive owns the FOR UPDATE group lock, the idempotency key and the insufficient-funds
    -- check ([FIN_002] INSUFFICIENT_FUNDS), so this wrapper adds the caller check and the sign and
    -- nothing else.
    RETURN public.increment_group_balance(
        p_group_id,
        -p_amount,
        p_description,
        p_category,
        p_transaction_id,
        p_member_id
    );
END;
$$;

-- ──────────────────────────────────────────────────────────────────────────────
-- P.2  WRITE. The execute grants, taken back from the anonymous surface. Must run after P.1:
--      these are new objects, so they arrive owning the default PUBLIC execute. Verbatim copy
--      of section 2d of supabase/03_SECURITY_AND_RLS.sql.

REVOKE EXECUTE ON FUNCTION
    public.close_poll_v1(UUID),
    public.pay_burial_claim_v1(UUID, UUID, TEXT),
    public.record_disbursement_v1(UUID, NUMERIC, TEXT, TEXT, TEXT, UUID)
FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION
    public.close_poll_v1(UUID),
    public.pay_burial_claim_v1(UUID, UUID, TEXT),
    public.record_disbursement_v1(UUID, NUMERIC, TEXT, TEXT, TEXT, UUID)
TO authenticated, service_role;

-- P.2.b  Do NOT re-run any other section of 03_SECURITY_AND_RLS.sql on a tier that has
--        applied 20261029: section 2 is the blanket
--          GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO anon, authenticated, service_role
--        and pasting it back would undo that delta's whole point. This delta's privilege work
--        is the two statements above and nothing else.

-- ──────────────────────────────────────────────────────────────────────────────
-- P.3  VERIFY - run in a new session, after P.1 and P.2 have committed.

-- P.3.a  Fingerprints after P.1. Expect three POST rows, exactly these hashes and lengths.
--        A DRIFT here means the paste was re-encoded (the Dashboard keeps a CRLF prosrc; the
--        hash below strips \r first, so line endings cannot cause a false DRIFT - only a real
--        body difference can).
select p.proname,
       md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as body_hash,
       length(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as body_len,
       case
         when p.proname = 'close_poll_v1'           and md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) = 'd371d45e490d8ae679c098851780ed02' then 'POST'
         when p.proname = 'pay_burial_claim_v1'     and md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) = 'a7594be8efb36ccbbeb32776ee32a0ae' then 'POST'
         when p.proname = 'record_disbursement_v1'  and md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) = '729042ffce49ecfe608e19b7621f0462' then 'POST'
         else 'DRIFT'
       end as verdict
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in ('close_poll_v1', 'pay_burial_claim_v1', 'record_disbursement_v1')
order by p.proname;
-- expect: 3 rows, all verdict='POST', lengths 1193 / 3085 / 1629.

-- P.3.b  ACL after P.2, with the expectation spelled out as a join so the verdict column does
--        the reading. anon_execute must be false on all three; authenticated and service_role
--        must be true on all three.
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
        ('close_poll_v1'::text, false::boolean, true::boolean, true::boolean),
        ('pay_burial_claim_v1', false, true, true),
        ('record_disbursement_v1', false, true, true)
     ) as expected(proname, anon_execute, authed_execute, svc_execute)
join pg_proc p on p.proname = expected.proname
join pg_namespace n on n.oid = p.pronamespace and n.nspname = 'public'
order by verdict, p.proname;
-- expect: 3 rows, every verdict true, pronargs 1 / 3 / 6. A false in svc_execute means the
--         GRANT leg did not run and the workers are locked out of paying claims. A second row
--         with the same proname is an overload - no name in the masters is overloaded, so an
--         extra OID is a stale entry to drop, not a probe to re-run.

-- P.3.c  The named-parameter surface PostgREST actually sees. The client sends JSON keys, not
--        positional values, so every key in the three payloads must equal a declared argument
--        name or the call fails with PGRST202 even after this delta:
--          close_poll_v1          p_poll_id
--          pay_burial_claim_v1    p_claim_id, p_admin_id, p_notes
--          record_disbursement_v1 p_group_id, p_amount, p_description, p_category,
--                                 p_transaction_id, p_member_id
--        Amounts cross the wire as decimal strings (Money.amount.toPlainString()), never as
--        JSON floats; a NUMERIC parameter accepts the quoted form, which is how
--        increment_group_balance already receives them in production.
select p.proname,
       pg_get_function_arguments(p.oid) as arguments,
       p.prorettype::regtype::text as return_type
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in ('close_poll_v1', 'pay_burial_claim_v1', 'record_disbursement_v1')
order by p.proname;
-- expect: close_poll_v1 'p_poll_id uuid' / void,
--         pay_burial_claim_v1 'p_claim_id uuid, p_admin_id uuid DEFAULT NULL,
--                              p_notes text DEFAULT NULL' / void,
--         record_disbursement_v1 'p_group_id uuid, p_amount numeric, p_description text,
--                                 p_category text, p_transaction_id text DEFAULT NULL,
--                                 p_member_id uuid DEFAULT NULL' / numeric.

-- P.3.d  Behavioural probes as a signed-in non-admin. Each block is complete and
--        self-contained; the preamble is repeated on purpose, because SET LOCAL dies with its
--        transaction. Every block aborts on its own - the RAISE ends the transaction and the
--        trailing rollback is belt-and-braces - so nothing is written. The ids are fabricated,
--        and none of them needs to exist: the guard is the first thing in each body that reads
--        anything.
--
--        What auth.uid() and auth.role() actually report under a hand-set claim decides whether
--        these land on the guard, and SYNC_DELTA_20261029 P.3.c is the block that measures it.
--        Run that check first on a tier you have never probed.

begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000","role":"authenticated","aud":"authenticated"}';
  set local request.jwt.claim.role = 'authenticated';
  select public.close_poll_v1('22222222-2222-2222-2222-222222222222');
rollback;
-- expect: ERROR: [SYS_001] POLL_NOT_FOUND
--         This one reads before it guards, because is_group_admin needs the poll's group_id.
--         A fabricated id therefore answers NOT_FOUND, and that is the correct answer: no
--         caller without a real poll can close one either way. To land on the guard instead,
--         substitute a real open poll id in a group the claim is not a member of - then expect
--         [SEC_002] POLL_CLOSE_ACCESS_DENIED.

begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000","role":"authenticated","aud":"authenticated"}';
  set local request.jwt.claim.role = 'authenticated';
  select public.pay_burial_claim_v1('22222222-2222-2222-2222-222222222222');
rollback;
-- expect: ERROR: [SYS_001] CLAIM_NOT_FOUND  (same read-before-guard shape as above; with a
--         real claim id in a group the caller does not administer, [SEC_002]
--         CLAIM_PAYMENT_ACCESS_DENIED). With a real, already-paid claim id and a genuine
--         group admin's JWT the answer must be [FIN_002] BAD_STATUS: Claim in paid cannot be
--         paid - that is the double-draw refusal, and it is the probe worth doing by hand.

begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000","role":"authenticated","aud":"authenticated"}';
  set local request.jwt.claim.role = 'authenticated';
  select public.record_disbursement_v1('22222222-2222-2222-2222-222222222222',
         100, 'Probe', 'manual_payout');
rollback;
-- expect: ERROR: [SEC_002] DISBURSEMENT_ACCESS_DENIED
--         Guard-first, three statements after BEGIN, before the amount check and before the
--         primitive is entered. This is the probe that proves the wrapper is not a way back
--         into increment_group_balance for a signed-in member.

begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","role":"authenticated","aud":"authenticated"}';
  set local request.jwt.claim.role = 'authenticated';
  select public.record_disbursement_v1('22222222-2222-2222-2222-222222222222',
         -100, 'Probe', 'manual_payout');
rollback;
-- expect: ERROR: [SEC_002] DISBURSEMENT_ACCESS_DENIED  for a non-admin, and if you run this
--         leg as a real group admin instead it must answer
--         [FIN_003] INVALID_AMOUNT: Disbursement must be positive...  Either way a negative
--         disbursement can never reach the primitive, which is the point of the sign check
--         being in the wrapper rather than in the client.

-- P.3.e  The same surface from outside, over HTTP. CLOUD ONLY - see HOW TO PROVE A REVOKE ON
--        LOCAL above; on Local this exact request crashes the backend with signal 11.
--   curl -s -o /dev/null -w '%{http_code}\n' \
--     -X POST "$SUPABASE_URL/rest/v1/rpc/close_poll_v1" \
--     -H "apikey: $ANON_KEY" -H "Authorization: Bearer $ANON_KEY" \
--     -H 'content-type: application/json' -d '{"p_poll_id":"22222222-2222-2222-2222-222222222222"}'
--   (repeat for pay_burial_claim_v1 with {"p_claim_id":"..."} and for record_disbursement_v1
--    with {"p_group_id":"...","p_amount":"100","p_description":"Probe","p_category":"manual_payout"})
-- expect: 403 'permission denied for function ...' on all three. A 200 means P.2 did not run
--         or the schema cache was never reloaded. A PGRST202 means P.1 did not commit.

-- P.3.f  Ledger invariant spot check (read-only), using the shipped view. A tier that has run
--        this delta and then paid a claim must still reconcile: groups.balance equals the sum
--        of that group's ledger rows. Run it only after a real claim payment.
select g.id as group_id,
       g.balance as balance,
       coalesce(sum(l.amount), 0) as ledger_sum,
       g.balance - coalesce(sum(l.amount), 0) as drift
from public.groups g
left join public.group_ledger l on l.group_id = g.id
group by g.id, g.balance
having g.balance <> coalesce(sum(l.amount), 0)
limit 20;
-- expect: the same rows it expects today - this delta does not change the invariant, it only
--         adds one more writer that must obey it. A NEW row whose group has a paid claim is
--         the failure mode to look for.

-- ──────────────────────────────────────────────────────────────────────────────
-- P.4  CLIENT SIDE, already in the tree. Nothing to execute in this section; it is the list of
--      what the build does that a tier has to be ready for.
--   * data/.../repository/group/VotingRepositoryImpl.kt - closePoll now refuses a blank poll
--     id locally instead of posting "" (which reached the tier as a UUID cast error rather than
--     a decision), and the RPC name and its single key live in one build-tested companion.
--   * app/.../features/group/presentation/GroupVotingViewModel.kt - closePoll reports the
--     failure. It used to have only an onSuccess, so a refusal was invisible: the button
--     appeared to do nothing.
--   * data/.../remote/supabase/SupabaseGroupDataSourceImpl.kt - the disbursement payload is
--     built by a named, tested companion, matching the pattern
--     SupabasePayoutDataSourceImpl.buildRoscaPayoutRpcParams already uses, so the keys this
--     delta declares and the keys the client sends are covered by a test rather than by luck.
--   * data/.../remote/supabase/SupabaseClaimDataSourceImpl.kt - PAY_CLAIM_RPC names the target
--     the same way, and the reviewer rule in this delta's pay_burial_claim_v1 is the answer to
--     the nil UUID that BeneficiaryClaimRepositoryImpl.buildPayClaimRpcParams has always sent
--     for a platform-console payment.
--   * Tests added with this delta: DisbursementRpcParamsTest, ClosePollRpcParamsTest,
--     GroupVotingClosePollTest, and the two new cases in BeneficiaryClaimRepositoryImplTest.
--     They pin the wire contract only - no test here can decide entitlement, which is the
--     bodies' job and is what P.3.d probes.
--   * Nothing in :app calls increment_group_balance from a shipped screen, and this delta does
--     not change that. The simulation harness still does (20261029's client note lists where)
--     and stays filed as its own task.
