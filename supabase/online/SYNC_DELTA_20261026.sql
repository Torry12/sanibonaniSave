-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave - DELTA 2026-10-26  (pin search_path on the two money RPCs)
-- Target: Supabase Cloud (Dashboard SQL editor) and the Local tier.
-- Hand-maintained. SYNC_DELTA_20261014.sql, SYNC_DELTA_20261021.sql and
-- SYNC_DELTA_20261022.sql are applied history - do not re-run or edit them.
-- ─────────────────────────────────────────────────────────────────────────────
-- WHAT THIS DOES
--   M.1  re-declares public.increment_group_balance and public.pay_platform_fee_v2
--        with the exact body the masters carry, adding the one attribute both tiers
--        are missing: `SET search_path = public, pg_temp`.
--
-- WHAT THIS DOES NOT DO
--   No body change. L.2.b expects the same prosrc hashes L.0.a reports, so error
--   codes, return shapes and behaviour are identical. No signature change, no DROP,
--   no data write, no GRANT/REVOKE, no RLS change, no index. CREATE OR REPLACE with
--   an unchanged signature keeps the existing owner and grants.
--
-- WHY (the defect)
--   Both functions are SECURITY DEFINER and both are called on the money path.
--   supabase/02_FUNCTIONS_AND_TRIGGERS.sql pins search_path on them; a tier built
--   from an earlier baseline carries the same prosrc without that attribute. Because
--   the baseline keeps migration version 00000000000000, `supabase migration list`
--   shows such a tier fully migrated while these two functions still resolve names
--   under the caller's search_path. A local `supabase db diff --local` against the
--   masters reported exactly these two objects and nothing else; every other public
--   function already pins search_path, and every table reference in these bodies is
--   already schema-qualified, so this closes an attribute gap rather than a known
--   exploitable path.
--
-- ORDER OF WORK
--   1. Run M.0 alone. It writes nothing. Expect body=MATCH for both rows and
--      search_path=UNPINNED. If body=DRIFT, the tier holds a hot fix this file does
--      not know about: port that body into supabase/02_FUNCTIONS_AND_TRIGGERS.sql
--      first, regenerate GROUND_ZERO, and re-cut this delta. If search_path=PINNED
--      for both, this delta already ran on that tier - skip to M.2.
--   2. Run M.1. It is a single CREATE OR REPLACE per function and is idempotent.
--   3. Run M.2. Expect body=MATCH and search_path=PINNED for both rows.
--
-- TIER NOTES
--   - Local was measured on 2026-10-26: body=MATCH, search_path=UNPINNED for both,
--     and these were the only SECURITY DEFINER functions in public without a pin.
--     The cloud state is whatever M.0 reports; this file handles both cases.
--   - Do NOT reach either tier by re-applying supabase/01_DATABASE_SCHEMA.sql or by
--     running the generators: they drop and recreate tables.
--   - `supabase db diff --local` also prints `drop extension if exists "pg_net"`.
--     That is the local stack provisioning pg_net, not part of the masters. Do not
--     apply that line to any tier.
--
-- FINGERPRINT METHOD (same as the three previous deltas)
--   md5(btrim(regexp_replace(replace(prosrc, E'\r',''), E'[[:space:]]+',' ', 'g')))
--   over prosrc only. The hashes below are what the masters and both tiers carry
--   today; M.1 changes proconfig, not prosrc, so M.2.b must reproduce them.

-- ─────────────────────────────────────────────────────────────────────────────
-- M.0  PRE-FLIGHT - READ ONLY.  Run this first, on its own.
-- ─────────────────────────────────────────────────────────────────────────────

-- M.0.a  The two bodies must still be the ones the masters hold. Expect both
--        body=MATCH and search_path=UNPINNED on a tier that needs this delta.
WITH expected(name, hash) AS (VALUES
  ('increment_group_balance', '0e96e06f47613f191008e7df6ef47550'),
  ('pay_platform_fee_v2',     '42921eb9257bde763294c40e8f30dc8c')
), actual AS (
  SELECT e.name, e.hash AS want, p.oid, p.prosrc,
         array_to_string(p.proconfig, ',') AS config
    FROM expected e
    LEFT JOIN pg_proc p
           ON p.proname = e.name
          AND p.pronamespace = 'public'::regnamespace
)
SELECT name,
       COALESCE(md5(btrim(regexp_replace(replace(prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))), 'ABSENT') AS body_hash,
       CASE WHEN oid IS NULL THEN 'ABSENT'
            WHEN md5(btrim(regexp_replace(replace(prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) = want THEN 'MATCH'
            ELSE 'DRIFT' END AS body,
       COALESCE(config, '(none)') AS proconfig,
       CASE WHEN oid IS NULL THEN 'ABSENT'
            WHEN COALESCE(config, '') LIKE '%search_path%' THEN 'PINNED'
            ELSE 'UNPINNED' END AS search_path
  FROM actual
 ORDER BY name;

-- M.0.b  Every other SECURITY DEFINER function in public should already be pinned.
--        Expect rows=0. A row here is a different gap, not part of this delta.
SELECT p.proname
  FROM pg_proc p
 WHERE p.pronamespace = 'public'::regnamespace
   AND p.prosecdef
   AND NOT EXISTS (SELECT 1 FROM unnest(p.proconfig) c WHERE c LIKE 'search_path=%')
   AND p.proname NOT IN ('increment_group_balance', 'pay_platform_fee_v2')
 ORDER BY 1;

-- ─────────────────────────────────────────────────────────────────────────────
-- M.1  THE TWO FUNCTIONS.  Bodies copied verbatim from the masters; the only edit
--      against the tiers is the SET search_path line. Idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.increment_group_balance(
    p_group_id UUID,
    p_amount NUMERIC,
    p_description TEXT DEFAULT 'Atomic balance update',
    p_category TEXT DEFAULT 'adjustment',
    p_transaction_id TEXT DEFAULT NULL,
    p_member_id UUID DEFAULT NULL
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_new_balance NUMERIC;
    v_existing_amount NUMERIC;
BEGIN
    IF p_transaction_id IS NOT NULL THEN
        PERFORM 1 FROM public.groups WHERE id = p_group_id FOR UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND'; END IF;

        SELECT amount INTO v_existing_amount
        FROM public.group_ledger
        WHERE group_id = p_group_id AND transaction_id = p_transaction_id
        LIMIT 1;

        IF FOUND THEN
            IF v_existing_amount <> p_amount THEN
                RAISE EXCEPTION '[SYS_002] TRANSACTION_ID_REUSED_WITH_DIFFERENT_AMOUNT';
            END IF;
            SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;
            RETURN v_new_balance;
        END IF;
    END IF;

    UPDATE public.groups SET balance = balance + p_amount, updated_at = NOW()
    WHERE id = p_group_id RETURNING balance INTO v_new_balance;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND'; END IF;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id)
    VALUES (p_group_id, p_transaction_id, p_amount, v_new_balance, p_description, p_category, p_member_id);

    RETURN v_new_balance;
END;
$$;

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
    -- Row lock first: serialises concurrent submissions of the same tx_id.
    SELECT name, balance INTO v_group_name, v_current_bal FROM public.groups WHERE id = p_group_id FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND: %', p_group_id;
    END IF;

    -- ── Idempotency guard ────────────────────────────────────────────────
    -- A NULL tx_id cannot be deduplicated, so fall through to normal processing;
    -- callers are expected to always supply a deterministic reference.
    IF p_tx_id IS NOT NULL AND (
        EXISTS (SELECT 1 FROM public.group_ledger   WHERE group_id = p_group_id AND transaction_id = p_tx_id)
        OR EXISTS (SELECT 1 FROM public.platform_ledger WHERE transaction_id = p_tx_id)
        OR EXISTS (SELECT 1 FROM public.platform_fees  WHERE group_id = p_group_id AND transaction_id = p_tx_id)
    ) THEN
        RETURN jsonb_build_object(
            'paid', p_amount,
            'remaining_allocated', p_amount,
            'transaction_id', p_tx_id,
            'status', 'idempotent'
        );
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

-- ─────────────────────────────────────────────────────────────────────────────
-- M.2  VERIFICATION - READ ONLY.  Run after M.1.
-- ─────────────────────────────────────────────────────────────────────────────

-- M.2.a  The point of the delta. Expect body=MATCH and search_path=PINNED for both.
WITH expected(name, hash) AS (VALUES
  ('increment_group_balance', '0e96e06f47613f191008e7df6ef47550'),
  ('pay_platform_fee_v2',     '42921eb9257bde763294c40e8f30dc8c')
), actual AS (
  SELECT e.name, e.hash AS want, p.oid, p.prosrc,
         array_to_string(p.proconfig, ',') AS config
    FROM expected e
    LEFT JOIN pg_proc p
           ON p.proname = e.name
          AND p.pronamespace = 'public'::regnamespace
)
SELECT name,
       CASE WHEN oid IS NULL THEN 'ABSENT'
            WHEN md5(btrim(regexp_replace(replace(prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) = want THEN 'MATCH'
            ELSE 'DRIFT' END AS body,
       COALESCE(config, '(none)') AS proconfig,
       CASE WHEN COALESCE(config, '') LIKE '%search_path%' THEN 'PINNED'
            ELSE 'UNPINNED' END AS search_path
  FROM actual
 ORDER BY name;

-- M.2.b  No SECURITY DEFINER function in public may be left unpinned. Expect rows=0.
SELECT p.proname
  FROM pg_proc p
 WHERE p.pronamespace = 'public'::regnamespace
   AND p.prosecdef
   AND NOT EXISTS (SELECT 1 FROM unnest(p.proconfig) c WHERE c LIKE 'search_path=%')
 ORDER BY 1;
