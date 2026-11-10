-- SYNC_DELTA_20261101C.sql
-- FUNCTION-BODY DELTA. Rewrites public.consolidate_group_ledger_v1 so that a ledger
-- reconstruction books what the live write path books, and closes the three defects
-- SYNC_DELTA_20261101B P.6 recorded but could only work around:
--   * block 1 replayed EVERY contribution into group_ledger, so registration_contribution /
--     member_fee / member_fee_ledger money - which internal_post_contribution
--     (02_FUNCTIONS_AND_TRIGGERS.sql:238-243) sends to platform_ledger instead - was re-filed into
--     the group on every rebuild. 20261101B P.1.c deleted those rows after the fact; the fixed
--     function never writes them.
--   * there was no platform_ledger side at all, so a rebuild dropped the R21,100 of Cloud
--     registration revenue that 20261101B P.5 measured as booked in neither ledger.
--   * entries_reconstructed counted rows created in the last five minutes while the rebuild stamps
--     the historical paid_at, so it always reported 0 and P.1.b's "reported" column was meaningless.
--     It now counts the rows actually inserted and returns per-source counts, the platform count,
--     the re-derived final balance and the group's ledger row count.
-- Two changes follow from making the two paths agree, and both can move data:
--   * status IN ('paid','partial') like the live path, instead of 'paid' only, so a rebuild no
--     longer loses a partial payment.
--   * the function now delegates to recompute_ledger_running_balances_v2, so it rewrites every
--     balance_after for the group and re-derives groups.balance from them. It used to insert rows
--     stamped 0 and leave the cached column alone, which is how a repaired ledger and the number on
--     the screen could disagree.
-- A typeless contribution (contributions.type IS NULL, which its CHECK allows) no longer aborts the
-- whole rebuild: group_ledger.category is NOT NULL and the old body passed the type through raw.
--
-- What this delta does NOT do: repair a ledger row that already exists under a source id with the
-- wrong amount. ON CONFLICT DO NOTHING leaves it, so the rebuild stays idempotent but is not
-- self-correcting there - [SEED_003] and maintenance/BALANCE_VS_LEDGER_PROBE.sql are what catch it.
--
-- Tier state before the run, measured 2026-10-31 through P.0 on both tiers:
--   Local  and Cloud  body hash d597d93c5eb64a624dbe31029c62d1b6 (the 20261014 body, unchanged by
--   any later delta), 19 groups / 1,397 group_ledger rows / 122 platform_ledger rows /
--   R702,500.00 held across the groups / R21,100.00 platform revenue / balance_drifts 0 /
--   0 partial contributions / 0 platform-type contributions with no platform_ledger row.
--   Both tiers are therefore already carrying the money the fixed function would book, which is why
--   P.3 - the replay - is expected to add 0 rows everywhere.
--
-- Re-runnable: P.1 is CREATE OR REPLACE over one function and P.3 is idempotent by construction
-- (ON CONFLICT DO NOTHING plus an anti-join on platform_ledger.transaction_id).
--
-- Applied on Local 2026-10-31 after a restore dump (pg_dump of schema public, 1.26 MB, taken before
-- P.1), and applied on Cloud the same day after a --data-only dump of the linked project (1.29 MB,
-- cloud_data_pre_deltaC_20261031_152313.sql). Both sections were run in order; P.4 records each tier.

-- ──────────────────────────────────────────────────────────────────────────────
-- P.0  PRE-FLIGHT - READ ONLY. Run each on its own, on the tier you are about to write.

-- P.0.a  Body fingerprint. Expect d597d93c5eb64a624dbe31029c62d1b6. If this already reads
--        860be7e1ad64b9fa4f2c5677292a301f the delta has run on this tier and P.1 must be skipped.
select 'consolidate_group_ledger_v1|'
    || md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as line
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public' and p.proname = 'consolidate_group_ledger_v1';

-- P.0.b  Caller, rights and arity. CREATE OR REPLACE keeps the existing grants, so this only
--        proves the role running P.1 may execute the function; overloads must be 1, because a
--        second signature would make the CREATE statement below create an overload instead of
--        replacing the body. Expect postgres / true / 1.
select current_user as role,
       has_function_privilege(current_user, 'public.consolidate_group_ledger_v1(uuid)', 'EXECUTE') as can_consolidate,
       (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
         where n.nspname = 'public' and p.proname = 'consolidate_group_ledger_v1') as overloads;

-- P.0.c  The money this delta must not move. Expect, on both tiers: 19 / 1397 / 122 /
--        702500.00 / 21100.00 / balance_drifts 0.
select (select count(*) from public.groups)                                            as groups_total,
       (select count(*) from public.group_ledger)                                       as ledger_rows,
       (select count(*) from public.platform_ledger)                                    as platform_rows,
       (select coalesce(sum(balance), 0)::numeric(14,2) from public.groups)             as group_balance_total,
       (select coalesce(sum(amount), 0)::numeric(14,2) from public.platform_ledger)     as platform_revenue_total,
       (select count(*) from public.groups g where g.balance <> coalesce(
          (select sum(l.amount) from public.group_ledger l where l.group_id = g.id), 0)) as balance_drifts;

-- P.0.d  The two rows the old body could not produce, counted. partial_contribution_rows is what
--        the widened status filter newly replays, and platform_unbooked is what the new block 6
--        would fill - both are the same predicate the seed gate asserts as [SEED_003] and
--        [SEED_004]. Expect 0 / 122 / 0 on both tiers today; a non-zero third column means this
--        delta's P.3 has real money to book rather than being a no-op proof.
select count(*) filter (where c.status = 'partial')                                    as partial_contribution_rows,
       count(*) filter (where coalesce(c.type, 'contribution')
                          in ('registration_contribution', 'member_fee', 'member_fee_ledger')
                        and c.status in ('paid', 'partial'))                            as platform_type_contributions,
       count(*) filter (where coalesce(c.type, 'contribution')
                          in ('registration_contribution', 'member_fee', 'member_fee_ledger')
                        and c.status in ('paid', 'partial')
                        and not exists (select 1 from public.platform_ledger pl
                                         where pl.transaction_id = c.id::text))         as platform_revenue_unbooked
  from public.contributions c;

-- P.0.e  No user trigger may sit on either ledger: a trigger that credits groups.balance as a side
--        effect of P.1's INSERTs would double the number the recompute in the same function derives.
--        Expect 0 rows.
select c.relname, t.tgname, pg_get_triggerdef(t.oid) as definition
  from pg_trigger t
  join pg_class c on c.oid = t.tgrelid
  join pg_namespace n on n.oid = c.relnamespace
 where n.nspname = 'public'
   and c.relname in ('group_ledger', 'platform_ledger')
   and not t.tgisinternal;

-- ──────────────────────────────────────────────────────────────────────────────
-- P.1  WRITE. One statement: the new body, verbatim from the master
--      (supabase/02_FUNCTIONS_AND_TRIGGERS.sql, section 10.3.2). Nothing else changes - the
--      signature, the return type, the search_path pin and the grants all stay as they are.

-- Rebuilds one group's ledger from the five source tables and re-derives its cached balance.
-- The contract is that this and the live write paths produce the SAME rows, because a rebuild is
-- how a corrupted ledger gets repaired (SYNC_DELTA_20261101B ran it over 49 groups). Three ways it
-- used to break that:
--   * it replayed every contribution into group_ledger, so registration/member-fee money - which
--     internal_post_contribution (line 238) books to platform_ledger instead - was re-filed into
--     the group on each rebuild, and the platform side was never rebuilt at all.
--   * it replayed only status = 'paid' while the live path books 'partial' too, so a rebuild lost
--     that money.
--   * it stamped balance_after = 0 for everything it booked, then reported "entries_reconstructed"
--     as the rows created in the last five minutes - always zero, because a reconstruction stamps
--     the historical paid_at instead.
-- Contributions are read through COALESCE(type, 'contribution'): the CHECK on contributions.type
-- passes NULL, while group_ledger.category is NOT NULL, so one typeless row aborted the whole rebuild.
-- What it still does not do: repair a ledger row that exists under a source id with the WRONG amount.
-- ON CONFLICT DO NOTHING leaves it, so the rebuild is idempotent but not self-correcting there - the
-- mismatch is what [SEED_003] and BALANCE_VS_LEDGER_PROBE.sql are for.
CREATE OR REPLACE FUNCTION public.consolidate_group_ledger_v1(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_contrib  INT := 0;
    v_repay    INT := 0;
    v_payout   INT := 0;
    v_loan     INT := 0;
    v_claim    INT := 0;
    v_platform INT := 0;
    v_recon    JSONB;
BEGIN
    -- One lock for the whole reconstruction: a concurrent live payment and this rebuild would
    -- otherwise race on the same transaction ids (platform_ledger has no unique key to fall back on).
    PERFORM id FROM public.groups WHERE id = p_group_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND: %', p_group_id; END IF;

    -- 1. Contributions the group keeps. The type filter and the category are internal_post_contribution's
    --    (lines 235-243): the three platform types belong to the platform ledger, not this one.
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT c.group_id, CAST(c.id AS TEXT), c.amount, 0,
           initcap(replace(COALESCE(c.type, 'contribution'), '_', ' ')) || ' from ' || m.full_name,
           COALESCE(c.type, 'contribution'), c.member_id, COALESCE(c.paid_at, c.created_at)
    FROM public.contributions c JOIN public.members m ON m.id = c.member_id
    WHERE c.group_id = p_group_id AND c.status IN ('paid', 'partial')
      AND COALESCE(c.type, 'contribution') NOT IN ('registration_contribution', 'member_fee', 'member_fee_ledger')
    ON CONFLICT (group_id, transaction_id) DO NOTHING;
    GET DIAGNOSTICS v_contrib = ROW_COUNT;

    -- 2. Repayments
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT lr.group_id, CAST(lr.id AS TEXT), lr.amount, 0,
           'Loan repayment from ' || m.full_name, 'loan_repayment', lr.member_id,
           COALESCE(lr.paid_at, lr.created_at)
    FROM public.loan_repayments lr JOIN public.members m ON m.id = lr.member_id
    WHERE lr.group_id = p_group_id
    ON CONFLICT (group_id, transaction_id) DO NOTHING;
    GET DIAGNOSTICS v_repay = ROW_COUNT;

    -- 3. Disbursements (Payouts)
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT p.group_id, CAST(p.id AS TEXT), -p.amount, 0, 'Disbursement Ref: ' || COALESCE(p.payout_reference, p.id::text), 'payout', p.member_id, p.processed_at
    FROM public.payouts p WHERE p.group_id = p_group_id AND p.status = 'completed'
    ON CONFLICT (group_id, transaction_id) DO NOTHING;
    GET DIAGNOSTICS v_payout = ROW_COUNT;

    -- 4. Loan Disbursements
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT loan.group_id, CAST(loan.id AS TEXT), -loan.amount, 0,
           'Loan disbursement to ' || m.full_name, 'loan_disbursement', loan.member_id,
           COALESCE(loan.reviewed_at, loan.created_at)
    FROM public.loans loan JOIN public.members m ON m.id = loan.member_id
    WHERE loan.group_id = p_group_id AND loan.status NOT IN ('pending', 'rejected', 'cancelled')
    ON CONFLICT (group_id, transaction_id) DO NOTHING;
    GET DIAGNOSTICS v_loan = ROW_COUNT;

    -- 5. Burial Claims
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT bc.group_id, CAST(bc.id AS TEXT), -bc.claim_amount, 0, 'Burial claim: ' || bc.beneficiary_name, 'claim', bc.member_id, bc.reviewed_at
    FROM public.beneficiary_payout_claims bc WHERE bc.group_id = p_group_id AND bc.status = 'paid'
    ON CONFLICT (group_id, transaction_id) DO NOTHING;
    GET DIAGNOSTICS v_claim = ROW_COUNT;

    -- 6. Platform revenue the group paid. The type filter is internal_post_contribution's else-branch
    --    (lines 238-243) and member_fee landing as 'platform_fee' is its v_l_cat CASE (line 235), so
    --    these rows are what the live path would have written. platform_ledger.transaction_id has no
    --    unique constraint, so the live path dedupes by lookup (record_platform_revenue_v2, line 163)
    --    and so does this. balance_after is a running total over the whole table - the live path
    --    stores SUM(amount) (line 174) - and is recomputed after the insert rather than stamped here,
    --    because a rebuild backdates its rows and every total after them would otherwise sit too high.
    --    The advisory lock is the one that writer takes (line 160); without it a concurrent fee
    --    payment and this block would each read the same SUM and call it their own balance_after.
    PERFORM pg_advisory_xact_lock(hashtext('platform_ledger')::bigint);
    WITH new_rows AS (
        SELECT c.group_id,
               CAST(c.id AS TEXT) AS tx_id,
               c.amount,
               COALESCE(c.paid_at, c.created_at) AS posted_at,
               CASE WHEN COALESCE(c.type, 'contribution') IN ('member_fee', 'member_fee_ledger') THEN 'platform_fee'
                    ELSE COALESCE(c.type, 'contribution') END AS cat,
               m.full_name
        FROM public.contributions c JOIN public.members m ON m.id = c.member_id
        WHERE c.group_id = p_group_id AND c.status IN ('paid', 'partial')
          AND COALESCE(c.type, 'contribution') IN ('registration_contribution', 'member_fee', 'member_fee_ledger')
          AND NOT EXISTS (SELECT 1 FROM public.platform_ledger pl WHERE pl.transaction_id = CAST(c.id AS TEXT))
    )
    INSERT INTO public.platform_ledger (group_id, transaction_id, amount, balance_after, description, category, created_at)
    SELECT n.group_id, n.tx_id, n.amount, 0,
           initcap(replace(n.cat, '_', ' ')) || ' from ' || n.full_name,
           n.cat, n.posted_at
    FROM new_rows n;
    GET DIAGNOSTICS v_platform = ROW_COUNT;

    UPDATE public.platform_ledger pl
       SET balance_after = running.balance_after
      FROM (
        SELECT id, SUM(amount) OVER (ORDER BY created_at ASC NULLS LAST, id ASC) AS balance_after
          FROM public.platform_ledger
      ) running
     WHERE pl.id = running.id
       AND pl.balance_after IS DISTINCT FROM running.balance_after;

    -- 7. balance_after across the group's ledger, and groups.balance re-derived from it. Same
    --    primitive the live repair path uses, so this function never disagrees with it.
    v_recon := public.recompute_ledger_running_balances_v2(p_group_id);

    RETURN jsonb_build_object(
        'entries_reconstructed', v_contrib + v_repay + v_payout + v_loan + v_claim,
        'group_entries', v_contrib + v_repay + v_payout + v_loan + v_claim,
        'platform_entries', v_platform,
        'contributions', v_contrib,
        'loan_repayments', v_repay,
        'payouts', v_payout,
        'loan_disbursements', v_loan,
        'burial_claims', v_claim,
        'final_balance', v_recon -> 'final_balance',
        'ledger_rows', v_recon -> 'count'
    );
END;
$$;

-- P.1.b  PostgREST caches the old signature and body; without this the app keeps calling the
--        version it loaded at startup.
select pg_notify('pgrst', 'reload schema');

-- ──────────────────────────────────────────────────────────────────────────────
-- P.2  VERIFY - read-only.

-- P.2.a  Expect 860be7e1ad64b9fa4f2c5677292a301f (the body above, whitespace- and newline-normalised,
--        computed from the master and re-measured on Local).
select 'consolidate_group_ledger_v1|'
    || md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as line
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public' and p.proname = 'consolidate_group_ledger_v1';

-- P.2.b  Same numbers as P.0.c: replacing a body moves no money by itself.
select (select count(*) from public.groups)                                            as groups_total,
       (select count(*) from public.group_ledger)                                       as ledger_rows,
       (select count(*) from public.platform_ledger)                                    as platform_rows,
       (select coalesce(sum(balance), 0)::numeric(14,2) from public.groups)             as group_balance_total,
       (select coalesce(sum(amount), 0)::numeric(14,2) from public.platform_ledger)     as platform_revenue_total,
       (select count(*) from public.groups g where g.balance <> coalesce(
          (select sum(l.amount) from public.group_ledger l where l.group_id = g.id), 0)) as balance_drifts;

-- P.2.c  The mis-filing this delta removes, checked as a predicate rather than by count: a category
--        the live path never books must be gone from the group ledger, and every platform-type
--        contribution must now have exactly one platform_ledger row. Expect 0 / 0 / 0.
select (select count(*) from public.group_ledger
         where category in ('registration_contribution', 'member_fee', 'member_fee_ledger')) as platform_types_in_group_ledger,
       (select count(*) from public.platform_ledger
         where transaction_id is null)                                                   as unkeyed_platform_rows,
       (select count(*) from public.contributions c
         where coalesce(c.type, 'contribution') in ('registration_contribution', 'member_fee', 'member_fee_ledger')
           and c.status in ('paid', 'partial')
           and (select count(*) from public.platform_ledger pl where pl.transaction_id = c.id::text) <> 1) as platform_rows_not_exactly_one;

-- ──────────────────────────────────────────────────────────────────────────────
-- P.3  REPLAY - WRITES, and the actual test of the fix. Runs the new body over every group.

--      On a healthy tier each block finds its rows already present, so this inserts nothing and only
--      re-affirms balance_after and groups.balance through the same primitive the live repair path
--      uses. On a tier whose ledger was built by the OLD body, this is what books the missing
--      partials and platform revenue and stops the mis-filing - which is why it belongs in the
--      delta even though the answer here is expected to be all zeros.
--      It takes the group's FOR UPDATE row lock, so a concurrent app payment waits for it rather
--      than racing it; run it when the tier is quiet.
with called as (
    select g.name as group_name, public.consolidate_group_ledger_v1(g.id) as res
      from public.groups g
)
select group_name || '|group_rows_added=' || (res ->> 'entries_reconstructed')
       || '|platform_rows_added=' || (res ->> 'platform_entries')
       || '|final_balance=' || (res ->> 'final_balance')
       || '|ledger_rows=' || (res ->> 'ledger_rows') as line
  from called
 order by 1;

-- P.3.b  After the replay: expect ledger_rows 1397, platform_rows 122, group_balance_total
--        702500.00, platform_revenue_total 21100.00, balance_drifts 0 - the same numbers as P.0.c,
--        reached by the fixed function this time.
select (select count(*) from public.group_ledger)                                       as ledger_rows,
       (select count(*) from public.platform_ledger)                                    as platform_rows,
       (select coalesce(sum(balance), 0)::numeric(14,2) from public.groups)             as group_balance_total,
       (select coalesce(sum(amount), 0)::numeric(14,2) from public.platform_ledger)     as platform_revenue_total,
       (select count(*) from public.groups g where g.balance <> coalesce(
          (select sum(l.amount) from public.group_ledger l where l.group_id = g.id), 0)) as balance_drifts,
       (select count(*) from public.groups where balance < 0)                           as negative_balances;

-- P.3.c  The shipped view must still agree: expect 0.
select count(*) as drift_groups from public.vw_ledger_integrity_check where status = 'DRIFT';

-- ──────────────────────────────────────────────────────────────────────────────
-- P.4  RESULT.
--
-- LOCAL: applied 2026-10-31, every section run in order.
--   P.0  postgres / true / 1 overload; 19 / 1397 / 122 / 702500.00 / 21100.00 / drift 0;
--        0 partial / 122 platform-type contributions / 0 of them unbooked; 0 triggers on either
--        ledger. Body hash d597d93c5eb64a624dbe31029c62d1b6 before, as expected.
--   P.2.a 860be7e1ad64b9fa4f2c5677292a301f - the hash the master body computes to, which is what
--        tells you the paste did not mangle the SQL.
--   P.2.b 19 / 1397 / 122 / 702500.00 / 21100.00 / drift 0: identical to P.0.c, so the new body
--        moved no money on its own.
--   P.2.c 0 / 0 / 0.
--   P.3   all 19 groups returned group_rows_added=0 and platform_rows_added=0. final_balance and
--        ledger_rows per group are the numbers the group already had (SEED-Blue Chip Investors
--        109000.00 over 109 rows is the largest), so the replay re-affirmed the ledger rather than
--        rebuilding it. P.3.b 1397 / 122 / 702500.00 / 21100.00 / drift 0 / negatives 0; P.3.c
--        vw_ledger_integrity_check DRIFT 0.
--   Behavioural proof is maintenance/CONSOLIDATE_REBUILD_PROBE.sql, which rolls itself back: on the
--        four contribution shapes the old body got wrong it now books the R500 partial and the
--        typeless R100 into group_ledger as 'contribution', the R200 registration and the R50
--        member_fee into platform_ledger as 'registration_contribution' and 'platform_fee', reports
--        entries_reconstructed=2 / platform_entries=2 / final_balance=22600.00 (= 22000.00 cached +
--        the R600 the group keeps), recomputes groups.balance so drift stays 0 across all 19 groups,
--        returns all zeros on the second call, and leaves the platform running total monotonic
--        (sum 21350.00 = last balance_after, out_of_order 0). After the ROLLBACK the tier reads
--        1397 / 122 / 702500.00 / 21100.00 / 0 probe rows.
--   Schema digest after this delta: 8405ef938e6325bf03b6002e54f5ecca over the same 1037 objects
--        (F=88 unchanged - one body replaced, nothing added), recorded as the new expected baseline
--        in maintenance/SCHEMA_PARITY_DIGEST.sql.
--
-- CLOUD: applied 2026-10-31 through `npx supabase db query --linked`, after a --data-only restore dump.
--   P.0  measured first, as written: body hash d597d93c5eb64a624dbe31029c62d1b6 (the pre-delta body,
--        so P.1 was correct to run), postgres / true / 1 overload, 19 / 1397 / 122 / 702500.00 /
--        21100.00 / drift 0, 0 partial / 122 platform-type contributions / 0 unbooked, 0 triggers on
--        either ledger. Same numbers as Local's P.0 - the two tiers were data-identical and differed
--        only in this one definition.
--   P.2.a 860be7e1ad64b9fa4f2c5677292a301f, i.e. the body pasted into the linked tier is the master
--        body; P.2.b 19 / 1397 / 122 / 702500.00 / 21100.00 / drift 0 (unchanged from P.0.c); P.2.c
--        0 / 0 / 0.
--   P.3   all 19 groups returned group_rows_added=0 and platform_rows_added=0, and the per-group
--        final_balance and ledger_rows are what the group already had (the 19 reported balances sum
--        to 702500.00 and the 19 reported row counts to 1397, so nothing was added or lost). P.3.b
--        1397 / 122 / 702500.00 / 21100.00 / drift 0 / negatives 0; P.3.c vw_ledger_integrity_check
--        DRIFT 0.
--   Schema digest after: 8405ef938e6325bf03b6002e54f5ecca over 1037 objects (C=535 E=1 F=88 G=15
--        I=33 K=198 P=85 R=38 S=2 T=38 V=4) - identical to Local, which closes the deliberate drift
--        this delta opened. The seed gate re-run read-only after P.3 still passes: 1397 rows, 0 drift.
--   Why it still had to run even though its replay added nothing: the money was already correct
--   because 20261101B P.1.c deleted the mis-filed registration rows and the R21,100 was booked by
--   hand, but the stored body was still the one that re-files that revenue and drops partials, so
--   the next Cloud reconstruction would have undone both. That is now false on both tiers.
--
-- P.5  FOLLOW-UPS THIS DELTA LEAVES OPEN.
--   * The seed gate (90_SEED_INTEGRITY_GATE.sql) now reads contributions through
--     COALESCE(type, 'contribution') in [SEED_003] so its expectation matches this body row for row.
--     That is a module change, not a tier object, so it ships through SEED_ALL_DATA.sql - regenerated
--     with this delta, and it takes effect on the next seed run.
--   * 20261101B P.6 stays as written. Its three defects are this delta's, and its P.1.c workaround
--     is now redundant - it simply deletes rows the fixed body no longer produces.

