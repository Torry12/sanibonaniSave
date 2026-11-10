-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — SEED INTEGRITY GATE
-- Version: 1.0 (October 2026)
-- ─────────────────────────────────────────────────────────────────────────────
-- The last word on a seed run. Every check below is a statement about the money the
-- seed just wrote, and any one of them failing aborts the transaction, so a tier is
-- never left holding fixtures that add up only until the first repair.
--
-- Why a gate and not another probe: the seed posts through pg_temp.seed_post_contribution
-- and public.increment_group_balance, and a fixture row can be inserted beside those
-- writers (loans, claims, payouts are written directly). The 2026-10-31 Cloud
-- reconstruction (SYNC_DELTA_20261101B) is the proof: consolidate_group_ledger_v1
-- rebuilds a group ledger from five source tables, and every seeded outflow that had been
-- inserted without booking its ledger row turned into a debit the group could not cover -
-- six SIM-FLOWS-* at -R26,200.00 and LIGHT-Burial Society at -R1,050.00. Those balances
-- were "consistent" right after seeding because groups.balance agreed with a ledger that
-- simply did not contain the loan. A balance-vs-ledger check cannot see that; only a
-- source-coverage check can.
--
-- What the gate asserts, in one line each:
--   SEED_001  groups.balance = SUM(group_ledger.amount), for every group.
--   SEED_002  no group is in the red.
--   SEED_003  every source row that the live path would have booked has that exact ledger
--             row (same group, same transaction_id, same amount, same category), so a
--             reconstruction is a no-op rather than a rewrite.
--   SEED_004  every platform-type paid contribution has its platform_ledger revenue row.
--   SEED_005  no platform category ever sits in the group ledger.
--   SEED_006  no ledger row is anonymous: a NULL transaction_id cannot be de-duplicated,
--             so re-running the seed double-books it.
--   SEED_007  no ledger row has a blank category.
--
-- Deliberately NOT asserted:
--   * Ledger rows with no source row. Those are legitimate - record_liquidity_v1 exists
--     precisely because bank interest, dividends and an opening balance are money the group
--     has and no contributions/loans/payouts/claims row describes (02_FUNCTIONS_AND_TRIGGERS
--     .sql:2055). They are keyed, and SEED_002 is what catches them if a wipe-and-rebuild
--     ever drops them and leaves a group short.
--   * Descriptions. The live path decorates ('Loan disbursement to X (bank)'), the
--     reconstruction does not, so the text is not a comparable field. Amount and category are.
--
-- SEED_003 is a contract, not a snapshot: its expected CTE has to stay row-for-row equal to
-- consolidate_group_ledger_v1's five group-side blocks and to internal_post_contribution's
-- type split, or the gate and the repair function disagree about what the ledger should contain.
-- Both read contributions through COALESCE(type, 'contribution') and both replay 'partial'; that
-- was not true before 2026-11-01, when the function skipped 'partial' rows and the gate had to be
-- stricter than it in order to make the loss loud (SYNC_DELTA_20261101C).
--
-- Read-only. This module writes nothing, so it is safe to paste on any tier on its own, and
-- that is how it is run against an existing tier - as a check, not as part of a repair.
--
-- Where it is wired: SEED_ALL_DATA.sql (generate_integrated_seed.ps1 inlines it last, inside the
-- BEGIN/COMMIT the generated file opens), so a failing check rolls the whole seed back - that is
-- the fail-hard. In 00_REBUILD_ALL.sql it is applied through \i, which autocommits per file, so
-- there it reports and the \i run continues; psql needs ON_ERROR_STOP=1 to treat it as fatal.
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_bad  INT;
    v_list TEXT;
BEGIN
    -- SEED_001  The cached column must equal its own ledger.
    SELECT count(*) INTO v_bad
    FROM public.groups g
    WHERE g.balance <> COALESCE((SELECT sum(l.amount) FROM public.group_ledger l WHERE l.group_id = g.id), 0);

    IF v_bad > 0 THEN
        SELECT string_agg(x, ' | ' ORDER BY x) INTO v_list
        FROM (
            SELECT g.name || ': balance=' || g.balance || ' ledger=' ||
                   COALESCE((SELECT sum(l.amount) FROM public.group_ledger l WHERE l.group_id = g.id), 0) AS x
            FROM public.groups g
            WHERE g.balance <> COALESCE((SELECT sum(l.amount) FROM public.group_ledger l WHERE l.group_id = g.id), 0)
            ORDER BY 1
            LIMIT 8
        ) offenders;
        RAISE EXCEPTION '[SEED_001] BALANCE_BUCKET_MISMATCH: % group(s) where groups.balance <> SUM(group_ledger.amount): %', v_bad, v_list;
    END IF;

    -- SEED_002  A savings group cannot have spent more than it holds. A negative here means an
    --           outflow was booked against capital that no inflow row backs.
    SELECT count(*) INTO v_bad FROM public.groups WHERE balance < 0;

    IF v_bad > 0 THEN
        SELECT string_agg(x, ' | ' ORDER BY x) INTO v_list
        FROM (SELECT name || '=' || balance AS x FROM public.groups WHERE balance < 0 ORDER BY balance LIMIT 8) offenders;
        RAISE EXCEPTION '[SEED_002] NEGATIVE_BALANCE: % group(s) reconstructed in the red: %', v_bad, v_list;
    END IF;

    -- SEED_003  Source-coverage. The CTE is consolidate_group_ledger_v1's five blocks written as
    --           an expectation instead of an insert (02_FUNCTIONS_AND_TRIGGERS.sql:1887-1932), and
    --           the anti-join asks whether each one is already in the ledger with the right money
    --           and the right category. Contributions split by type because that is what
    --           internal_post_contribution does (02_FUNCTIONS_AND_TRIGGERS.sql:238), and the type is
    --           read through COALESCE for the same reason the function reads it that way: the CHECK
    --           on contributions.type admits NULL, while group_ledger.category cannot be NULL.
    WITH expected AS (
        SELECT c.group_id, c.id::text AS tx_id, c.amount,
               COALESCE(c.type, 'contribution') AS category,
               'contribution ' || c.id AS what
        FROM public.contributions c
        WHERE c.status IN ('paid', 'partial')
          AND COALESCE(c.type, 'contribution') NOT IN ('registration_contribution', 'member_fee', 'member_fee_ledger')
        UNION ALL
        SELECT lr.group_id, lr.id::text, lr.amount, 'loan_repayment', 'loan_repayment ' || lr.id
        FROM public.loan_repayments lr
        UNION ALL
        SELECT p.group_id, p.id::text, -p.amount, 'payout', 'payout ' || p.id
        FROM public.payouts p
        WHERE p.status = 'completed'
        UNION ALL
        SELECT loan.group_id, loan.id::text, -loan.amount, 'loan_disbursement', 'loan ' || loan.id
        FROM public.loans loan
        WHERE loan.status NOT IN ('pending', 'rejected', 'cancelled')
        UNION ALL
        SELECT bc.group_id, bc.id::text, -bc.claim_amount, 'claim', 'claim ' || bc.id
        FROM public.beneficiary_payout_claims bc
        WHERE bc.status = 'paid'
    )
    SELECT count(*) INTO v_bad
    FROM expected e
    WHERE NOT EXISTS (
        SELECT 1 FROM public.group_ledger l
        WHERE l.group_id = e.group_id
          AND l.transaction_id = e.tx_id
          AND l.amount = e.amount
          AND l.category IS NOT DISTINCT FROM e.category
    );

    IF v_bad > 0 THEN
        WITH expected AS (
            SELECT c.group_id, c.id::text AS tx_id, c.amount,
                   COALESCE(c.type, 'contribution') AS category,
                   g.name || ' wants ' || c.amount || ' as ' || COALESCE(c.type, 'contribution') || ' for contribution ' || c.id AS what
            FROM public.contributions c JOIN public.groups g ON g.id = c.group_id
            WHERE c.status IN ('paid', 'partial')
              AND COALESCE(c.type, 'contribution') NOT IN ('registration_contribution', 'member_fee', 'member_fee_ledger')
            UNION ALL
            SELECT lr.group_id, lr.id::text, lr.amount, 'loan_repayment',
                   'loan repayment ' || lr.id || ' of ' || lr.amount
            FROM public.loan_repayments lr
            UNION ALL
            SELECT p.group_id, p.id::text, -p.amount, 'payout',
                   'completed payout ' || p.id || ' of ' || p.amount
            FROM public.payouts p WHERE p.status = 'completed'
            UNION ALL
            SELECT loan.group_id, loan.id::text, -loan.amount, 'loan_disbursement',
                   g.name || ' has loan ' || loan.id || ' (' || loan.status || ') of ' || loan.amount || ' with no disbursement row'
            FROM public.loans loan JOIN public.groups g ON g.id = loan.group_id
            WHERE loan.status NOT IN ('pending', 'rejected', 'cancelled')
            UNION ALL
            SELECT bc.group_id, bc.id::text, -bc.claim_amount, 'claim',
                   g.name || ' has paid claim ' || bc.id || ' of ' || bc.claim_amount || ' with no claim row'
            FROM public.beneficiary_payout_claims bc JOIN public.groups g ON g.id = bc.group_id
            WHERE bc.status = 'paid'
        )
        SELECT string_agg(what, ' | ' ORDER BY what) INTO v_list
        FROM (
            SELECT e.what
            FROM expected e
            WHERE NOT EXISTS (
                SELECT 1 FROM public.group_ledger l
                WHERE l.group_id = e.group_id
                  AND l.transaction_id = e.tx_id
                  AND l.amount = e.amount
                  AND l.category IS NOT DISTINCT FROM e.category
            )
            ORDER BY e.what
            LIMIT 8
        ) offenders;
        RAISE EXCEPTION '[SEED_003] SOURCE_ENTRY_UNBOOKED: % source row(s) the ledger never saw, so a consolidate_group_ledger_v1 rebuild would move the balance: %', v_bad, v_list;
    END IF;

    -- SEED_004  Registration money is platform revenue. Cloud carried R21,100.00 of it in neither
    --           ledger (SYNC_DELTA_20261101B P.5) because the seed wrote the contribution row
    --           directly instead of posting it; the helper routes it, and this is the check that
    --           says so forever.
    SELECT count(*) INTO v_bad
    FROM public.contributions c
    WHERE c.status IN ('paid', 'partial')
      AND c.type IN ('registration_contribution', 'member_fee', 'member_fee_ledger')
      AND NOT EXISTS (SELECT 1 FROM public.platform_ledger pl WHERE pl.transaction_id = c.id::text);

    IF v_bad > 0 THEN
        SELECT string_agg(x, ' | ' ORDER BY x) INTO v_list
        FROM (
            SELECT c.type || ' ' || c.id || ' of ' || c.amount AS x
            FROM public.contributions c
            WHERE c.status IN ('paid', 'partial')
              AND c.type IN ('registration_contribution', 'member_fee', 'member_fee_ledger')
              AND NOT EXISTS (SELECT 1 FROM public.platform_ledger pl WHERE pl.transaction_id = c.id::text)
            ORDER BY 1
            LIMIT 8
        ) offenders;
        RAISE EXCEPTION '[SEED_004] PLATFORM_REVENUE_UNBOOKED: % platform-type contribution(s) with no platform_ledger row: %', v_bad, v_list;
    END IF;

    -- SEED_005  The mirror image: a platform category in the group ledger inflates group money by
    --           the platform's fee. This is what P.1.c of SYNC_DELTA_20261101B had to delete.
    SELECT count(*) INTO v_bad
    FROM public.group_ledger
    WHERE category IN ('registration_contribution', 'member_fee', 'member_fee_ledger');

    IF v_bad > 0 THEN
        SELECT string_agg(x, ' | ' ORDER BY x) INTO v_list
        FROM (
            SELECT g.name || ' has ' || l.category || ' ' || l.amount || ' (' || l.transaction_id || ')' AS x
            FROM public.group_ledger l JOIN public.groups g ON g.id = l.group_id
            WHERE l.category IN ('registration_contribution', 'member_fee', 'member_fee_ledger')
            ORDER BY 1
            LIMIT 8
        ) offenders;
        RAISE EXCEPTION '[SEED_005] PLATFORM_TYPE_IN_GROUP_LEDGER: % group ledger row(s) carry a platform-only category: %', v_bad, v_list;
    END IF;

    -- SEED_006  An anonymous ledger row has no idempotency key, so a second run of the module that
    --           wrote it books the money twice - increment_group_balance can only compare rows it
    --           can find by transaction_id (02_FUNCTIONS_AND_TRIGGERS.sql:100-114).
    SELECT count(*) INTO v_bad FROM public.group_ledger WHERE transaction_id IS NULL;

    IF v_bad > 0 THEN
        SELECT string_agg(x, ' | ' ORDER BY x) INTO v_list
        FROM (
            SELECT g.name || ': ' || l.category || ' ' || l.amount || ' (' || COALESCE(l.description, 'no description') || ')' AS x
            FROM public.group_ledger l JOIN public.groups g ON g.id = l.group_id
            WHERE l.transaction_id IS NULL
            ORDER BY 1
            LIMIT 8
        ) offenders;
        RAISE EXCEPTION '[SEED_006] UNKEYED_LEDGER_ENTRY: % group ledger row(s) have no transaction_id and cannot be de-duplicated: %', v_bad, v_list;
    END IF;

    -- SEED_007  group_ledger.category has no CHECK constraint by design - the vocabulary is what
    --           the writers pass (02_FUNCTIONS_AND_TRIGGERS.sql:2076). A blank is not a category.
    SELECT count(*) INTO v_bad FROM public.group_ledger WHERE category IS NULL OR BTRIM(category) = '';

    IF v_bad > 0 THEN
        RAISE EXCEPTION '[SEED_007] BLANK_LEDGER_CATEGORY: % group ledger row(s) have no category', v_bad;
    END IF;

    RAISE NOTICE 'SEED INTEGRITY GATE passed: % groups, ledger rows % of which % carry source-backed money, no drift, no negative balance.',
        (SELECT count(*) FROM public.groups),
        (SELECT count(*) FROM public.group_ledger),
        (SELECT count(*) FROM public.group_ledger l WHERE EXISTS (
            SELECT 1 FROM public.contributions c
            WHERE c.id::text = l.transaction_id AND c.group_id = l.group_id));
END $$;
