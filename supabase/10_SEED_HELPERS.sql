-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — SEED HELPERS
-- Version: 1.0 (October 2026)
-- ─────────────────────────────────────────────────────────────────────────────
-- Every money RPC reached from a seed session is refused. The 20261029 [SEC_002] guards
-- authorise on auth.role() / auth.uid(), and a seed session — `supabase db reset`, the SQL
-- editor, `psql -f` — carries no JWT, so both read NULL and CONTRIBUTION_ACCESS_DENIED is
-- raised on the first row. Posting fixtures by impersonating service_role would mean forging
-- the claim the guards trust, so the seed posts them the way internal_post_contribution does
-- instead: the contributions row is the audit record, one ledger row carries its id as
-- transaction_id (the shape consolidate_group_ledger_v1 reconstructs), and the group balance
-- moves through the unguarded primitive.
--
-- pg_temp: created per session and dropped with it, so nothing lands in the schema and no
-- tier needs a delta before the seed can run. Call it schema-qualified — pg_temp is never
-- searched for function names.
--
-- Run this module before any seed module that posts money
-- (MASTER_SEED_DATA.sql, 11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql,
-- 20_SEED_12_MONTHS_TRANSACTIONS.sql).
--
-- seed_post_loan_disbursement is the same rule for the other side of the ledger. A loan row is an
-- outflow the group has already paid, and consolidate_group_ledger_v1 block 4 reconstructs it as
-- -loans.amount under category 'loan_disbursement' keyed by the loan id. Inserting the loan without
-- booking that debit leaves the cached balance too high until a repair moves it (SYNC_DELTA_20261101B
-- P.4: six Local loans, R17,000). The helper books it at seed time, in the shape the reconstruction
-- produces, so the repair is a no-op; and it refuses a loan the group cannot cover, because a fixture
-- that only exists while nobody reconciles is the defect that delta was written to expose.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION pg_temp.seed_post_contribution(
    p_member_id UUID,
    p_group_id UUID,
    p_amount NUMERIC,
    p_due_date DATE,
    p_paid_at TIMESTAMPTZ,
    p_status TEXT,
    p_tx_id TEXT,
    p_type TEXT,
    p_member_name TEXT,
    p_description TEXT DEFAULT NULL
) RETURNS UUID
LANGUAGE plpgsql
AS $$
DECLARE
    v_contrib_id UUID;
    v_description TEXT := COALESCE(p_description, initcap(replace(p_type, '_', ' ')) || ' from ' || p_member_name);
BEGIN
    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, transaction_id, type)
    VALUES (p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_tx_id, p_type)
    RETURNING id INTO v_contrib_id;

    IF p_status IN ('paid', 'partial') THEN
        IF p_type IN ('member_fee', 'member_fee_ledger', 'registration_contribution') THEN
            -- Registration money is platform revenue: it never enters the group's ledger.
            INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category, group_id)
            VALUES (
                v_contrib_id::text,
                p_amount,
                (SELECT COALESCE(SUM(amount), 0) FROM public.platform_ledger) + p_amount,
                v_description,
                p_type,
                p_group_id
            );
        ELSE
            PERFORM public.increment_group_balance(
                p_group_id, p_amount, v_description, p_type, v_contrib_id::text, p_member_id
            );
        END IF;
    END IF;

    RETURN v_contrib_id;
END;
$$;

CREATE OR REPLACE FUNCTION pg_temp.seed_post_loan_disbursement(
    p_loan_id UUID,
    p_group_id UUID,
    p_member_id UUID,
    p_amount NUMERIC,
    p_member_name TEXT,
    p_at TIMESTAMPTZ DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_balance NUMERIC;
    v_at TIMESTAMPTZ := COALESCE(p_at, NOW());
BEGIN
    IF p_amount IS NULL OR p_amount <= 0 OR p_amount <> ROUND(p_amount, 2) THEN
        RAISE EXCEPTION '[FIN_003] INVALID_AMOUNT: Disbursement must be positive and have at most two decimal places';
    END IF;

    SELECT balance INTO v_balance FROM public.groups WHERE id = p_group_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND: %', p_group_id;
    END IF;

    -- The loan id is the key the reconstruction uses, so a row already booked under it - by this
    -- helper on an earlier run, or by approve_and_disburse_loan_v1 - is the same money.
    IF EXISTS (SELECT 1 FROM public.group_ledger WHERE group_id = p_group_id AND transaction_id = p_loan_id::text) THEN
        RETURN;
    END IF;

    IF COALESCE(v_balance, 0) < p_amount THEN
        RAISE EXCEPTION '[FIN_002] SEED_LOAN_UNFUNDED: group % holds % but seeded loan % would disburse %',
            p_group_id, COALESCE(v_balance, 0), p_loan_id, p_amount;
    END IF;

    UPDATE public.groups
    SET balance = balance - p_amount, updated_at = NOW()
    WHERE id = p_group_id
    RETURNING balance INTO v_balance;

    INSERT INTO public.group_ledger (
        group_id, member_id, transaction_id, amount, balance_after, description, category, created_at
    ) VALUES (
        p_group_id, p_member_id, p_loan_id::text, -p_amount, v_balance,
        'Loan disbursement to ' || p_member_name, 'loan_disbursement', v_at
    );
END;
$$;
