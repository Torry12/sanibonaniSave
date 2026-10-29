-- ────────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave - DELTA 2026-10-28  (amount + period guards on the money-posting
-- RPCs, add settle_expired_loan_v2, retire the v1 settlement bypass)
-- Target: Supabase Cloud (Dashboard SQL editor). The Local tier is already at this
-- state - it was applied and measured on 2026-10-28, see TIER NOTES.
-- Hand-maintained. SYNC_DELTA_20261014/21/22/26/26B/27 are applied history - do not
-- re-run them and do not edit them to describe these changes.
-- ────────────────────────────────────────────────────────────────────────────────

-- WHAT THIS DOES
--   P.1  installs five function bodies, copied verbatim from
--        supabase/02_FUNCTIONS_AND_TRIGGERS.sql (mirrored into
--        supabase/migrations/00000000000000_GROUND_ZERO.sql by
--        supabase/scripts/generate_ground_zero.ps1). Signatures are unchanged, so no client
--        call site moves.
--          internal_post_contribution      - rejects a NULL / non-positive / NaN /
--                                            Infinity / sub-cent amount with
--                                            [FIN_003] INVALID_AMOUNT before it
--                                            touches members, contributions or the
--                                            ledger.
--          record_contribution_v2          - same amount guard ahead of the
--                                            idempotency lookup, so a duplicate
--                                            submission cannot mask a bad amount.
--          record_platform_revenue_v2      - same amount guard ahead of the
--                                            pg_advisory_xact_lock on platform_ledger.
--          process_rosca_payout_v3         - guards p_period against '^[0-9]{4}-(0[1-9]|1[0-2])$',
--                                            guards the pot/payout amounts, and turns
--                                            the duplicate checks into RECORD probes
--                                            so an indeterminate loan state cannot be
--                                            paid twice.
--          settle_expired_loan_v2          - NEW OBJECT. The settlement refactor renamed
--                                            the entry point and the client already calls
--                                            the new name (data/src/main/java/com/sanibonani/save/
--                                            data/remote/supabase/SupabaseLoanDataSourceImpl.kt:86),
--                                            so a tier that predates this delta answers that
--                                            call with PGRST202: Local did until this delta ran.
--                                            The retired v1 stays defined and is made
--                                            unreachable in P.2 rather than dropped, so an old
--                                            build fails loudly instead of losing its history.
--        Each refusal is a RAISE EXCEPTION with the '[FIN_003] ...' prefix, the same code-and-sentence
--        convention as [SEC_001] and [SEC_002]; the app renders the sentence through
--        Throwable.toUserMessage(), and no client code keys on the code itself.
--   P.2  sets the privileges that belong with those bodies: settle_expired_loan_v2 is
--        withdrawn from PUBLIC and anon and granted to authenticated + service_role,
--        and record_loan_settlement_extraction_v1 - the pre-v2 entry point whose
--        caller-supplied member, group and amount are not validated as one atomic
--        settlement - is withdrawn from every role, including service_role.
--   P.3  verifies. P.3.a to P.3.c are read-only or abort on their own; run them in a
--        new session after P.1/P.2 have committed.

-- ORDER OF WORK
--   1. Run P.0 on its own. It writes nothing. P.0.a must print four PRE rows for the
--      bodies this delta replaces; if it already prints the four POST hashes, this
--      delta has run on that tier - go to P.3.
--   2. Run P.1, then P.2. One paste is fine, in that order: the blanket
--        GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO anon, authenticated, service_role
--      in 03_SECURITY_AND_RLS.sql re-grants on every CREATE OR REPLACE, so P.1 after
--      P.2 hands EXECUTE back to anon.
--   3. Run P.3.a, P.3.b, P.3.c. Expect five POST hashes, the P.3.b ACL matrix, and the
--      guard probes each raising [FIN_003].

-- TIER NOTES
--   - LOCAL (aszmodkqpafwtnfufmow, postgres 17.6.1) APPLIED 2026-10-28 15:12. Pre was
--     measured at the four PRE hashes below with settle_expired_loan_v2 absent (it is new
--     in this tree's masters, not something the tier had and lost) and
--     record_loan_settlement_extraction_v1 granted to anon/authenticated/service_role.
--     After the apply, the five bodies re-measure at the P.3.a POST hashes, the schema
--     digest moves from 1031|C=535 E=1 F=82 ... |50386ad4b7f0b6f9f3295a0bda5394a7 to
--     1032|C=535 E=1 F=83 G=15 I=33 K=198 P=85 R=38 S=2 T=38 V=4|
--     9752967a0c8a1e095d1de650a6132efb (supabase/maintenance/SCHEMA_PARITY_DIGEST.sql,
--     read-only), and every function body defined by the repo matches GROUND_ZERO with
--     zero drift. P.3.c was run here: record_contribution_v2(...,0,...) raises
--     [FIN_003] INVALID_AMOUNT at body line 8, i.e. before the member lookup, and
--     10.005 raises the same line, so the two-decimal rule is enforced, not just
--     declared. process_rosca_payout_v3(...,'2026-13') raises
--     [FIN_003] INVALID_PERIOD: Expected YYYY-MM.
--   - A pg_dump of the Local tier was taken before the write:
--     local_pre_SYNC_20261028_1512.dump (-Fc, 846 KB, 1217 TOC entries). After the two crashes
--     below the five bodies, both ACL sets and the row counts were re-measured and are
--     unchanged, which is the expected behaviour of an aborted backend.

-- VERIFYING THE REVOKES - LOCAL IS NOT THE PLACE FOR THE CURL LEG
--   Prove an anon revoke on Local with has_function_privilege (P.0.b / P.3.b) and nothing else.
--   Measured 2026-10-28: 'curl -X POST /rest/v1/rpc/<fn>' carrying only the anon key crashed the
--   Local backend twice with signal 11 - the log DETAIL names generate_platform_audit_report_v1
--   and then settle_expired_loan_v2. PostgREST had not reloaded its schema cache after P.2, so it
--   still routed a call the tier no longer permits, and the anon execution is what kills this
--   postgres 17.6.1 image. The same settle_expired_loan_v2, run in psql under
--   'SET LOCAL ROLE authenticated' with a loan id that does not exist, raised
--   [SYS_001] LOAN_NOT_FOUND at body line 22 and left the server up - the body is sound, the
--   anon role is the variable. So the curl leg written into SYNC_DELTA_20261027 P.3.e is a
--   CLOUD-ONLY check; on cloud a 403 'permission denied for function' is the answer and nothing
--   crashes.

-- ROUTING THE NEW RPC ON A LIVE TIER
--   A tier that gains settle_expired_loan_v2 keeps answering PGRST202 for it until PostgREST
--   reloads: 'SELECT pg_notify('pgrst','reload schema');'. That notify was sent on Local
--   2026-10-28, but the reload could not be confirmed from here - the anon probes above took the
--   backend down while it was re-querying its cache. Confirm the route with a real user JWT
--   (the app's settlement screen, or DeepSimulationAndroidTest on the device), never with the
--   anon key. Until someone does, treat "the app can settle an expired loan" as unverified on
--   both tiers, not as proven by the DDL.
--   - CLOUD IS NOT APPLIED. Nobody has measured it against these five bodies. Until P.1
--     runs there, a build of this tree cannot settle an expired loan on cloud: the client
--     calls settle_expired_loan_v2 and that name does not exist on the tier yet. Run P.0
--     there first; if P.0.a prints a PRE row for a function whose body differs from the
--     hash below, do not paste P.1 over it before reading the diff - the cloud tier may
--     hold an emergency fix that never came back to the masters.
--   - Do not rehearse P.1/P.2 by wrapping them in BEGIN ... ROLLBACK and piping them
--     into psql. Measured on this image: an identical wrapper printed ROLLBACK and
--     still committed. Rehearse on a scratch tier, or run P.0 and P.3 as written and
--     read the verdicts.
--   - FOLLOW-UP, NOT IN THIS DELTA: the four money-posting bodies stay executable by
--     anon (03_SECURITY_AND_RLS.sql grants EXECUTE to anon blanket and only the audit +
--     settlement functions were withdrawn). They are SECURITY DEFINER and none of the
--     four reads auth.uid(), so an unauthenticated caller with the anon key can post a
--     contribution or platform revenue against any member/group id. Guarding that the
--     way the audit RPCs were guarded changes money-path behaviour, so it needs its own
--     delta plus client-side tests, not a privilege patch here.

-- P.0  PRE-FLIGHT - READ ONLY.  Run this first, on its own.

-- P.0.a  The bodies this delta replaces. PRE = the state before 2026-10-28, POST = the
--        state P.1 installs. A row that prints neither means a third body is on that
--        tier: stop and diff it before writing.
select p.proname,
       md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as body_hash,
       case md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g')))
         when '5616535efa7a6de44836dfdf03001f2f' then 'PRE'
         when '90ef8d9ad34e7f6d8fb9b9be20de577f' then 'PRE'
         when '69b90494975e790a284b09669a643a4b' then 'PRE'
         when '138002dafaebd53db091b4cd9948141a' then 'PRE'
         when 'd81ce97479007931e4375703e5ebd75e' then 'POST'
         when 'dbdcb0ca1f0580df5ab60ede23a721fd' then 'POST'
         when 'f9bad95316ec7160a98c89b8122a013f' then 'POST'
         when '27a92966186dee34c4c41a56b15aec2e' then 'POST'
         else 'NEITHER'
       end as verdict
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in ('internal_post_contribution', 'record_contribution_v2',
                    'record_platform_revenue_v2', 'process_rosca_payout_v3')
order by p.proname;

-- P.0.b  Does the settlement entry point the app calls even exist here, and who can
--        still reach the retired v1? Expect: one row for settle_expired_loan_v2 after
--        P.1 (absent before it), and record_loan_settlement_extraction_v1 false/false/false
--        after P.2.
select p.proname,
       has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
       has_function_privilege('authenticated', p.oid, 'EXECUTE') as authed_execute,
       has_function_privilege('service_role', p.oid, 'EXECUTE') as svc_execute
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in ('settle_expired_loan_v2', 'record_loan_settlement_extraction_v1')
order by p.proname;

select case when exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
                         where n.nspname = 'public' and p.proname = 'settle_expired_loan_v2')
       then 'settle_expired_loan_v2 PRESENT' else 'settle_expired_loan_v2 MISSING - the app loan settlement RPC returns PGRST202' end;

-- P.0.c  Is the guard already inside the body? A tier that carries a FIN_003 line but a
--        PRE hash is running a hand-patched variant.
select p.proname, position('FIN_003' in p.prosrc) > 0 as has_fin_003
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in ('internal_post_contribution', 'record_contribution_v2',
                    'record_platform_revenue_v2', 'process_rosca_payout_v3', 'settle_expired_loan_v2')
order by p.proname;

-- P.1  WRITE. Five CREATE OR REPLACE FUNCTION statements, verbatim copies of the
--      masters. Signatures unchanged. Must run before P.2.

-- >>> internal_post_contribution (verbatim from supabase/migrations/00000000000000_GROUND_ZERO.sql)
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

-- >>> record_contribution_v2 (verbatim from supabase/migrations/00000000000000_GROUND_ZERO.sql)
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

-- >>> record_platform_revenue_v2 (verbatim from supabase/migrations/00000000000000_GROUND_ZERO.sql)
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

-- >>> process_rosca_payout_v3 (verbatim from supabase/migrations/00000000000000_GROUND_ZERO.sql)
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

-- >>> settle_expired_loan_v2 (verbatim from supabase/migrations/00000000000000_GROUND_ZERO.sql)
CREATE OR REPLACE FUNCTION public.settle_expired_loan_v2(
    p_loan_id UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_group_id UUID;
    v_loan RECORD;
    v_contribution RECORD;
    v_repayment RECORD;
    v_member_name TEXT;
    v_has_contribution BOOLEAN;
    v_has_repayment BOOLEAN;
    v_remaining NUMERIC;
    v_extraction_tx_id TEXT := 'forced-settlement-extraction:' || p_loan_id::text;
    v_repayment_tx_id TEXT := 'forced-settlement-repayment:' || p_loan_id::text;
    v_result JSONB;
BEGIN
    IF p_loan_id IS NULL THEN
        RAISE EXCEPTION '[FIN_003] INVALID_LOAN_ID';
    END IF;

    SELECT group_id INTO v_group_id
    FROM public.loans
    WHERE id = p_loan_id;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] LOAN_NOT_FOUND'; END IF;

    PERFORM id FROM public.groups WHERE id = v_group_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND'; END IF;

    SELECT * INTO v_loan
    FROM public.loans
    WHERE id = p_loan_id
    FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] LOAN_NOT_FOUND'; END IF;
    IF v_loan.group_id IS DISTINCT FROM v_group_id THEN
        RAISE EXCEPTION '[SYS_002] LOAN_GROUP_CHANGED';
    END IF;
    IF auth.role() IS DISTINCT FROM 'service_role'
       AND NOT (public.is_platform_admin() OR public.is_group_admin(v_group_id)) THEN
        RAISE EXCEPTION '[SEC_002] LOAN_SETTLEMENT_ACCESS_DENIED';
    END IF;

    SELECT * INTO v_contribution
    FROM public.contributions
    WHERE group_id = v_group_id AND transaction_id = v_extraction_tx_id
    FOR UPDATE;
    v_has_contribution := FOUND;
    SELECT * INTO v_repayment
    FROM public.loan_repayments
    WHERE group_id = v_group_id AND transaction_id = v_repayment_tx_id
    FOR UPDATE;
    v_has_repayment := FOUND;

    IF v_has_contribution AND v_contribution.id IS NOT NULL THEN
        IF v_contribution.member_id IS DISTINCT FROM v_loan.member_id
           OR v_contribution.amount >= 0
           OR v_contribution.type IS DISTINCT FROM 'loan_settlement_extraction'
           OR v_contribution.status IS DISTINCT FROM 'paid'
           OR v_repayment.id IS NULL
           OR v_repayment.loan_id IS DISTINCT FROM p_loan_id
           OR v_repayment.member_id IS DISTINCT FROM v_loan.member_id
           OR v_repayment.amount IS DISTINCT FROM ABS(v_contribution.amount) THEN
            RAISE EXCEPTION '[SYS_002] INCOMPLETE_OR_MISMATCHED_LOAN_SETTLEMENT';
        END IF;
        RETURN public.record_loan_repayment_v1(
            p_loan_id, v_loan.member_id, v_group_id, v_repayment.amount,
            'wallet', v_repayment_tx_id
        );
    ELSIF v_has_repayment AND v_repayment.id IS NOT NULL THEN
        RAISE EXCEPTION '[SYS_002] INCOMPLETE_OR_MISMATCHED_LOAN_SETTLEMENT';
    END IF;

    IF v_loan.status IS NULL OR v_loan.status NOT IN ('active', 'partially_paid', 'overdue', 'defaulted') THEN
        RAISE EXCEPTION '[FIN_002] INVALID_STATUS: Loan cannot be settled from status %', v_loan.status;
    END IF;
    IF v_loan.start_date IS NULL OR CURRENT_DATE < (v_loan.start_date + INTERVAL '3 months')::date THEN
        RAISE EXCEPTION '[FIN_002] LOAN_TERM_NOT_EXPIRED';
    END IF;

    v_remaining := v_loan.total_to_repay - COALESCE(v_loan.total_repaid, 0);
    IF v_remaining IS NULL OR v_remaining <= 0
       OR v_remaining::text IN ('NaN', 'Infinity', '-Infinity')
       OR v_remaining <> ROUND(v_remaining, 2) THEN
        RAISE EXCEPTION '[FIN_003] INVALID_REMAINING_BALANCE';
    END IF;

    SELECT full_name INTO v_member_name
    FROM public.members
    WHERE id = v_loan.member_id AND group_id = v_group_id;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] MEMBER_NOT_FOUND'; END IF;

    INSERT INTO public.contributions (
        member_id, group_id, amount, due_date, paid_at, payment_method,
        status, type, transaction_id
    ) VALUES (
        v_loan.member_id, v_group_id, -v_remaining, CURRENT_DATE, NOW(), 'wallet',
        'paid', 'loan_settlement_extraction', v_extraction_tx_id
    ) RETURNING * INTO v_contribution;

    PERFORM public.increment_group_balance(
        v_group_id, -v_remaining,
        'Forced loan settlement extraction for ' || v_member_name,
        'loan_settlement_extraction', v_extraction_tx_id, v_loan.member_id
    );

    v_result := public.record_loan_repayment_v1(
        p_loan_id, v_loan.member_id, v_group_id, v_remaining,
        'wallet', v_repayment_tx_id
    );

    PERFORM public.log_audit_event(
        'LOAN_FORCED_SETTLEMENT', 'system', v_loan.member_id, v_group_id,
        jsonb_build_object('loan_id', p_loan_id, 'amount', v_remaining),
        p_loan_id
    );

    RETURN v_result;
END;
$$;

-- P.2  WRITE. Privileges. 03_SECURITY_AND_RLS.sql keeps a blanket
--      GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO anon, authenticated, service_role
--      and ALTER DEFAULT PRIVILEGES, so these three statements have to come after P.1 or
--      P.1's own re-grant undoes them.
REVOKE EXECUTE ON FUNCTION public.settle_expired_loan_v2(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.settle_expired_loan_v2(UUID) TO authenticated, service_role;
REVOKE EXECUTE ON FUNCTION public.record_loan_settlement_extraction_v1(UUID, UUID, NUMERIC, UUID)
  FROM PUBLIC, anon, authenticated, service_role;

-- P.3  VERIFY - run in a new session, after P.1 and P.2 have committed.

-- P.3.a  Expect five POST rows, exactly these hashes and lengths.
select p.proname,
       md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as body_hash,
       length(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as norm_len,
       case md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g')))
         when 'd81ce97479007931e4375703e5ebd75e' then 'MATCH'
         when 'dbdcb0ca1f0580df5ab60ede23a721fd' then 'MATCH'
         when 'f9bad95316ec7160a98c89b8122a013f' then 'MATCH'
         when '27a92966186dee34c4c41a56b15aec2e' then 'MATCH'
         when '3e5c7ff592e7889324b4fe3195a100f7' then 'MATCH'
         else 'MISMATCH'
       end as verdict
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in ('internal_post_contribution', 'record_contribution_v2',
                    'record_platform_revenue_v2', 'process_rosca_payout_v3', 'settle_expired_loan_v2')
order by p.proname;

-- expected: internal_post_contribution   d81ce97479007931e4375703e5ebd75e  2039
--           record_contribution_v2       dbdcb0ca1f0580df5ab60ede23a721fd  1212
--           record_platform_revenue_v2   f9bad95316ec7160a98c89b8122a013f  1502
--           process_rosca_payout_v3      27a92966186dee34c4c41a56b15aec2e  5421
--           settle_expired_loan_v2       3e5c7ff592e7889324b4fe3195a100f7  3963

-- P.3.b  ACL after P.2. Expect anon_execute = false on both rows and authed_execute =
--        true only on settle_expired_loan_v2 (the retired v1 is false/false/false; a
--        service_role job that still calls it will now fail loudly, by design).
select p.proname,
       has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
       has_function_privilege('authenticated', p.oid, 'EXECUTE') as authed_execute,
       has_function_privilege('service_role', p.oid, 'EXECUTE') as svc_execute
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in ('settle_expired_loan_v2', 'record_loan_settlement_extraction_v1')
order by p.proname;

-- P.3.c  Behavioural probes. Every one of these raises, which aborts its own
--        transaction, so nothing is written. The ids are deliberately not real rows:
--        if the guard did NOT fire, the next statement would be a member lookup that
--        raises '[SYS_001] Member not found in group' instead, and that answer means the
--        amount rule is missing. Keep each block separate; never call an audit RPC as
--        anon from a SQL session (see SYNC_DELTA_20261027 P.3.e - it crashes the backend).
begin;  select public.record_contribution_v2('11111111-1111-1111-1111-111111111111',
          '22222222-2222-2222-2222-222222222222', 0, now()::date, now(), 'paid');  rollback;
-- expect: ERROR:  [FIN_003] INVALID_AMOUNT: Contribution amount must be positive and have at most two decimal places

begin;  select public.record_contribution_v2('11111111-1111-1111-1111-111111111111',
          '22222222-2222-2222-2222-222222222222', 10.005, now()::date, now(), 'paid');  rollback;
-- expect: the same [FIN_003] INVALID_AMOUNT line - proves the two-decimal rule, not just the sign

begin;  select public.record_platform_revenue_v2(-5, 'probe', 'fee');  rollback;
-- expect: ERROR:  [FIN_003] INVALID_AMOUNT: Platform revenue must be positive and have at most two decimal places

begin;  select public.process_rosca_payout_v3('22222222-2222-2222-2222-222222222222',
          '11111111-1111-1111-1111-111111111111', 100, '[]'::jsonb, 100, '{}'::jsonb, '2026-13');  rollback;
-- expect: ERROR:  [FIN_003] INVALID_PERIOD: Expected YYYY-MM

-- P.3.d  Ledger invariant spot check (read-only), using the shipped view. Balance must
--        still equal the ledger sum on every group; a guard that aborted mid-write would
--        show up here as DRIFT.
select count(*) as groups_not_consistent
from public.vw_ledger_integrity_check
where status <> 'CONSISTENT';
-- expect: 0   (measured on Local 2026-10-28: 19 groups, all CONSISTENT)

