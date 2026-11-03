-- ═════════════════════════════════════════════════════════════════════════════
-- ROTATE_SEED_PLATFORM_ADMIN_PASSWORD_20261103.sql
--
-- PURPOSE
--   Rotate the SEED FIXTURE platform admin, platform.admin@master.example.com.
--   This is the account that owns every LIGHT-*/SEED-* group, so admin and
--   impersonation flows work straight after a seed. Because it carries
--   role='platform_admin', is_platform_admin() answers for it on any tier that has
--   the row - which is why it was taken off the shared 'Pass123!' persona password
--   on 2026-10-31.
--
--   ROTATE_PLATFORM_ADMIN_PASSWORD_20261018.sql does NOT cover this account: its
--   Section B targets torrymsimango@gmail.com only. And re-seeding cannot rotate
--   this row either - 11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql only ever CREATES
--   the fixture, never re-hashes an existing one. So this UPDATE is the only route.
--
-- HOW THE PASSWORD IS SUPPLIED  (this file contains NO secret)
--   Read from a transaction-local custom setting, typed only into the SQL editor:
--
--       SET LOCAL sanibonani.new_seed_admin_password = 'type-the-new-password-here';
--
--   Consumed by extensions.crypt() and never stored in the clear. The optional
--   'previous_seed_admin_password' setting only powers the "does the old value still
--   work" proof in Section C; leave it on its marker and that proof is skipped.
--
--   Keep it in step with SEED_PLATFORM_ADMIN_PASSWORD in the untracked
--   local.properties, which scripts/run-supabase-local-reset.ps1 and
--   scripts/seed-supabase-online.ps1 inject as the session-level SET at seed time.
--   A tier whose row and local.properties disagree logs in as "wrong password".
--
-- RUN ON BOTH TIERS  (multi-tier parity is mandatory for this project)
--   1. CLOUD : Supabase Dashboard -> SQL Editor -> paste Sections A, B, C.
--   2. LOCAL : same script against the Docker tier.
--
-- IDEMPOTENT
--   Yes. Each run re-hashes with a fresh gen_salt('bf') cost-10 salt, identical to
--   how the seed hashes.
--
-- WHAT IT DOES NOT TOUCH
--   roles, raw_app_meta_data, profiles, identities, email confirmation, group
--   ownership, the other 117 shared-QA personas. Section B also deletes this
--   account's auth.sessions - deliberate, since the value being displaced may
--   already be copied. Drop that DELETE to keep current sessions alive.
--
-- ⚠ NEVER commit a filled-in copy. The version in git must keep the REPLACE_WITH_
--   markers. Check first:  git diff -- supabase/maintenance/ | grep -c REPLACE_WITH
-- ═════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- SECTION A — Pre-flight (read-only). Confirm the target row exists, is an admin,
--             and is not already the account the other script owns.
-- ─────────────────────────────────────────────────────────────────────────────
BEGIN;

SELECT
    u.id,
    lower(u.email)                                        AS email,
    u.raw_app_meta_data ->> 'role'                        AS meta_role,
    p.role                                                AS profile_role,
    u.email_confirmed_at IS NOT NULL                      AS email_confirmed,
    (u.encrypted_password IS NULL)                        AS cannot_sign_in_yet,
    u.updated_at
FROM auth.users u
LEFT JOIN public.profiles p ON p.id = u.id
WHERE lower(u.email) = 'platform.admin@master.example.com';

-- Guard against aiming at the wrong account: this must return 0.
SELECT count(*) AS rows_matched_by_mistake
FROM auth.users
WHERE lower(email) = 'torrymsimango@gmail.com';

COMMIT;

-- ─────────────────────────────────────────────────────────────────────────────
-- SECTION B — Rotate. ⚠ EDIT THE MARKED LINE ONLY, one per tier, in the editor.
-- ─────────────────────────────────────────────────────────────────────────────
BEGIN;

-- <<< EDIT THIS LINE >>>  The only place a secret is ever typed.
SET LOCAL sanibonani.new_seed_admin_password = 'REPLACE_WITH_YOUR_NEW_PASSWORD';

DO $$
DECLARE
    v_plain text := current_setting('sanibonani.new_seed_admin_password', true);
    v_admin auth.users%ROWTYPE;
    v_rows  integer;
BEGIN
    IF v_plain IS NULL OR btrim(v_plain) = ''
       OR v_plain = 'REPLACE_WITH_YOUR_NEW_PASSWORD' THEN
        RAISE EXCEPTION
            'Section B aborted: run  SET LOCAL sanibonani.new_seed_admin_password '
            '= ''<new password>'';  in this same transaction first.';
    END IF;

    -- Same minimum-strength tripwire the other rotation script enforces, so a weak
    -- value cannot lock the owner out of the app's own login form.
    IF length(v_plain) < 8 OR v_plain !~ '[A-Za-z]' OR v_plain !~ '[0-9]' THEN
        RAISE EXCEPTION
            'Section B aborted: new seed admin password must be >= 8 chars and '
            'contain a letter and a digit.';
    END IF;

    SELECT * INTO v_admin
      FROM auth.users
     WHERE lower(email) = 'platform.admin@master.example.com'
       FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'Section B aborted: no auth.users row for '
            'platform.admin@master.example.com on this tier. Seed the fixture '
            '(11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql) first, then re-run.';
    END IF;

    UPDATE auth.users
       SET encrypted_password = extensions.crypt(v_plain, extensions.gen_salt('bf')),
           updated_at         = now()
     WHERE id = v_admin.id;
    GET DIAGNOSTICS v_rows = ROW_COUNT;

    IF v_rows <> 1 THEN
        RAISE EXCEPTION 'Section B aborted: expected 1 row, updated %', v_rows;
    END IF;

    -- Deliberate: kill existing sessions (FK cascades to refresh tokens).
    IF to_regclass('auth.sessions') IS NOT NULL THEN
        DELETE FROM auth.sessions WHERE user_id = v_admin.id;
    END IF;

    RAISE NOTICE 'Seed fixture admin password rotated for % (1 row, sessions revoked).',
        v_admin.email;
END
$$;

COMMIT;

-- ─────────────────────────────────────────────────────────────────────────────
-- SECTION C — Post-verify, in its OWN transaction so the secret is again
--             transaction-local. Aborts loudly if the new value does not verify.
-- ─────────────────────────────────────────────────────────────────────────────
BEGIN;

-- <<< EDIT THIS LINE >>>  must match Section B.
SET LOCAL sanibonani.new_seed_admin_password = 'REPLACE_WITH_YOUR_NEW_PASSWORD';

-- <<< EDIT THIS LINE (optional) >>>  the value being displaced, to prove below that
-- it no longer works. Leave the marker and that proof is skipped.
SET LOCAL sanibonani.previous_seed_admin_password = 'REPLACE_WITH_THE_PREVIOUS_PASSWORD';

DO $$
DECLARE
    v_plain text := current_setting('sanibonani.new_seed_admin_password', true);
    v_old   text := nullif(current_setting('sanibonani.previous_seed_admin_password', true),
                           'REPLACE_WITH_THE_PREVIOUS_PASSWORD');
BEGIN
    IF v_plain IS NULL OR btrim(v_plain) = ''
       OR v_plain = 'REPLACE_WITH_YOUR_NEW_PASSWORD' THEN
        RAISE EXCEPTION
            'Section C aborted: set LOCAL sanibonani.new_seed_admin_password to the '
            'password used in Section B, inside this same transaction.';
    END IF;

    PERFORM 1
      FROM auth.users
     WHERE lower(email) = 'platform.admin@master.example.com'
       AND extensions.crypt(v_plain, encrypted_password) = encrypted_password;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Section C FAILED: the new password does not verify against auth.users.';
    END IF;

    RAISE NOTICE 'Section C OK: the new seed fixture password verifies against auth.users.';

    IF v_old IS NOT NULL THEN
        PERFORM 1
          FROM auth.users
         WHERE lower(email) = 'platform.admin@master.example.com'
           AND extensions.crypt(v_old, encrypted_password) = encrypted_password;

        IF FOUND THEN
            RAISE EXCEPTION
                'Section C FAILED: the previous password still verifies against auth.users.';
        END IF;
        RAISE NOTICE 'Section C OK: the previous password no longer verifies.';
    ELSE
        RAISE NOTICE 'Section C skipped the previous-password proof (setting on its marker).';
    END IF;
END
$$;

COMMIT;

-- Secret-free evidence, always runnable: the row was touched and confirmation intact.
SELECT email_confirmed_at IS NOT NULL AS email_confirmed, updated_at
FROM auth.users
WHERE lower(email) = 'platform.admin@master.example.com';

-- Session revocation evidence (this admin must sign in again).
SELECT count(*) AS seed_admin_sessions_remaining
FROM auth.sessions
WHERE user_id = (SELECT id FROM auth.users
                 WHERE lower(email) = 'platform.admin@master.example.com');

-- ─────────────────────────────────────────────────────────────────────────────
-- SECTION D — Inventory. Run last on BOTH tiers and compare: the two tiers must
--             agree, and no admin may sit on the shared persona password.
-- ─────────────────────────────────────────────────────────────────────────────
SELECT
    (extensions.crypt('Pass123!', encrypted_password) = encrypted_password) AS on_shared_qa_password,
    coalesce(raw_app_meta_data ->> 'role', 'unset')                          AS meta_role,
    count(*)                                                                AS accounts
FROM auth.users
GROUP BY 1, 2
ORDER BY 1 DESC, 2;

-- Neither admin account may still answer to a displaced value. Expect 0 rows.
-- Type the two values being displaced here (the seed fixture's and the real admin's);
-- unlike Section C this block proves the OTHER script's rotation landed too.
BEGIN;
SET LOCAL sanibonani.previous_seed_admin_password  = 'REPLACE_WITH_THE_PREVIOUS_SEED_PASSWORD';
SET LOCAL sanibonani.previous_admin_password       = 'REPLACE_WITH_THE_PREVIOUS_ADMIN_PASSWORD';

SELECT lower(u.email) AS still_on_a_displaced_password
FROM auth.users u
CROSS JOIN (SELECT nullif(current_setting('sanibonani.previous_seed_admin_password', true),
                         'REPLACE_WITH_THE_PREVIOUS_SEED_PASSWORD')  AS seed_pw,
                 nullif(current_setting('sanibonani.previous_admin_password', true),
                         'REPLACE_WITH_THE_PREVIOUS_ADMIN_PASSWORD') AS admin_pw) old_pw
WHERE lower(u.email) IN ('platform.admin@master.example.com', 'torrymsimango@gmail.com')
  AND (
        (old_pw.seed_pw  IS NOT NULL AND extensions.crypt(old_pw.seed_pw,  u.encrypted_password) = u.encrypted_password)
     OR (old_pw.admin_pw IS NOT NULL AND extensions.crypt(old_pw.admin_pw, u.encrypted_password) = u.encrypted_password)
  );
COMMIT;
