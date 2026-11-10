-- ═════════════════════════════════════════════════════════════════════════════
-- ROTATE_PLATFORM_ADMIN_PASSWORD_20261018.sql
--
-- PURPOSE
--   The platform admin password was hardcoded in tracked source AND written as a
--   bcrypt hash into the seed scripts (MASTER_SEED_DATA.sql, SEED_ALL_DATA.sql,
--   migrations/00000000000000_GROUND_ZERO.sql, online/000_CONSOLIDATED_ONLINE_SYNC.sql,
--   04_PLATFORM_ADMIN_SETUP.sql). Because those files are published on GitHub, both the
--   plaintext and its hash are public: the hash is offline-crackable and the plaintext is
--   already exposed. This script replaces the admin credential in auth.users.
--
-- HOW THE PASSWORDS ARE SUPPLIED  (this file contains NO secret)
--   Both are read from transaction-local custom settings, so they are typed only into the
--   Dashboard editor and discarded at COMMIT:
--
--       SET LOCAL sanibonani.new_admin_password          = 'type-the-new-password-here';
--       SET LOCAL sanibonani.published_admin_password   = 'the password being replaced';
--
--   The new one is consumed by extensions.crypt() and never stored in the clear. The second
--   one only powers the three "is this account still on the old password" checks; leave it on
--   its marker and those columns report NULL (unchecked) and everything else still works.
--   It is never written to this file in git.
--
-- IDEMPOTENT
--   Yes. Safe to re-run; each run re-hashes with a fresh gen_salt('bf') cost-10 salt
--   (identical to how the seed scripts hash). Verify with Section C afterwards.
--
-- RUN ON BOTH TIERS  (multi-tier parity is mandatory for this project)
--   1. CLOUD  : Supabase Dashboard → SQL Editor → paste Sections A, B, C.
--   2. LOCAL  : same script against the Docker tier (Dashboard-equivalent psql session).
--
-- WHAT IT DOES NOT TOUCH
--   roles, raw_app_meta_data, profiles, identities, email confirmation, other members.
--   Section B also revokes the admin's existing auth.sessions — deliberate, because the
--   leaked password may already be in someone else's hand. Delete that one DELETE
--   statement if you would rather keep current sessions alive.
--
-- OTHER PUBLISHED CREDENTIAL
--   The 38-persona master seed deliberately uses the shared QA password 'Pass123!'. That is by
--   design for seeded test personas and is NOT changed by this script. It WAS changed for one of
--   them: the seed fixture platform.admin@master.example.com carries role='platform_admin', so
--   is_platform_admin() answered for it on the published password. On 2026-10-31 that row was
--   rotated on both tiers (sessions revoked) and 11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql now
--   reads current_setting('sanibonani.seed_platform_admin_password', true) instead of 'Pass123!'.
--   With that setting unset the fixture is created with a NULL encrypted_password, and an existing
--   fixture keeps its password - the seed only ever creates that row, it never re-hashes one - so no
--   re-seed can restore the shared credential either. scripts/run-supabase-local-reset.ps1 and
--   scripts/seed-supabase-online.ps1 supply the setting from SEED_PLATFORM_ADMIN_PASSWORD in the
--   untracked local.properties.
--
-- SEED MASTERS ARE PARAMETERISED — this run cannot be silently undone
--   The follow-up that this file used to warn about is DONE: MASTER_SEED_DATA.sql,
--   SEED_ALL_DATA.sql, 04_PLATFORM_ADMIN_SETUP.sql and the regenerated
--   migrations/00000000000000_GROUND_ZERO.sql and online/000_CONSOLIDATED_ONLINE_SYNC.sql
--   no longer contain the published admin password or its bcrypt hash. They now read
--   current_setting('sanibonani.platform_admin_password', true) and fail safe: with the
--   setting unset an existing admin keeps its password, and a newly created admin gets an
--   NULL encrypted_password plus a NOTICE pointing here. So no future re-seed or GROUND_ZERO
--   rebuild can restore the leaked credential. The stale CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql.bak_*
--   snapshot that still carried it was deleted.
--
-- ⚠ NEVER commit a filled-in copy of this file. Both passwords are substituted in the Dashboard
--   editor only; the version in git must keep the REPLACE_WITH_ markers.
--   Check before committing:  git diff -- supabase/maintenance/ | grep -c REPLACE_WITH
-- ═════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- SECTION A — Pre-flight (read-only). Confirm the target row exists, and optionally
--             whether it is still on the published password, before changing anything.
-- ─────────────────────────────────────────────────────────────────────────────
BEGIN;

-- <<< EDIT THIS LINE (optional) >>>  Type the password being replaced to enable the two
-- checks below. Leaving the marker makes them report NULL, which means "not checked".
SET LOCAL sanibonani.published_admin_password = 'REPLACE_WITH_THE_PUBLISHED_PASSWORD';

SELECT
    u.id,
    lower(u.email)                                                   AS email,
    u.raw_app_meta_data ->> 'role'                                   AS meta_role,
    p.role                                                           AS profile_role,
    -- True means this account still authenticates with the password being replaced.
    (extensions.crypt(old_pw.v, u.encrypted_password) = u.encrypted_password)
        AS still_on_published_admin_password,
    u.email_confirmed_at IS NOT NULL                                 AS email_confirmed,
    u.updated_at
FROM auth.users u
LEFT JOIN public.profiles p ON p.id = u.id
CROSS JOIN (SELECT nullif(current_setting('sanibonani.published_admin_password', true),
                          'REPLACE_WITH_THE_PUBLISHED_PASSWORD') AS v) old_pw
WHERE lower(u.email) = 'torrymsimango@gmail.com';

-- Any OTHER account that also uses that published password (should be zero).
SELECT count(*) AS other_accounts_on_published_admin_password
FROM auth.users u
CROSS JOIN (SELECT nullif(current_setting('sanibonani.published_admin_password', true),
                          'REPLACE_WITH_THE_PUBLISHED_PASSWORD') AS v) old_pw
WHERE lower(u.email) <> 'torrymsimango@gmail.com'
  AND extensions.crypt(old_pw.v, u.encrypted_password) = u.encrypted_password;

COMMIT;

-- ─────────────────────────────────────────────────────────────────────────────
-- SECTION B — Rotate.  ⚠ EDIT THE LINE MARKED <<< EDIT THIS LINE >>> ONLY.
--             Paste Section B as-is with your new password substituted.
-- ─────────────────────────────────────────────────────────────────────────────
BEGIN;

-- <<< EDIT THIS LINE >>>  The only place a secret is ever typed.
SET LOCAL sanibonani.new_admin_password = 'REPLACE_WITH_YOUR_NEW_PASSWORD';

DO $$
DECLARE
    v_plain   text := current_setting('sanibonani.new_admin_password', true);
    v_admin   auth.users%ROWTYPE;
    v_rows    integer;
BEGIN
    -- Guard: refuse to run on the unedited template.
    IF v_plain IS NULL OR btrim(v_plain) = '' OR v_plain = 'REPLACE_WITH_YOUR_NEW_PASSWORD' THEN
        RAISE EXCEPTION
            'Section B aborted: run  SET LOCAL sanibonani.new_admin_password = ''<new password>'';'
            ' in this same transaction first (see the EDIT THIS LINE marker).';
    END IF;

    -- Minimum-strength tripwire: the InputValidator in the app enforces this shape,
    -- so a weaker value here would lock the owner out of the UI's own login form.
    IF length(v_plain) < 8 OR v_plain !~ '[A-Za-z]' OR v_plain !~ '[0-9]' THEN
        RAISE EXCEPTION
            'Section B aborted: new admin password must be >= 8 chars and contain a letter and a digit.';
    END IF;

    SELECT * INTO v_admin FROM auth.users WHERE lower(email) = 'torrymsimango@gmail.com' FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'Section B aborted: no auth.users row for torrymsimango@gmail.com on this tier.'
            ' Seed the platform admin first (MASTER_SEED_DATA.sql), then re-run.';
    END IF;

    UPDATE auth.users
       SET encrypted_password = extensions.crypt(v_plain, extensions.gen_salt('bf')),
           updated_at         = now()
     WHERE id = v_admin.id;
    GET DIAGNOSTICS v_rows = ROW_COUNT;

    IF v_rows <> 1 THEN
        RAISE EXCEPTION 'Section B aborted: expected to update exactly 1 row, updated %', v_rows;
    END IF;

    -- Deliberate: kill existing sessions for this account (FK cascades to refresh tokens).
    IF to_regclass('auth.sessions') IS NOT NULL THEN
        DELETE FROM auth.sessions WHERE user_id = v_admin.id;
    END IF;

    RAISE NOTICE 'Platform admin password rotated for % (1 row).', v_admin.email;
END
$$;

COMMIT;

-- ─────────────────────────────────────────────────────────────────────────────
-- SECTION C — Post-verify.  Runs in its OWN transaction so the password is again
--             transaction-local. Aborts loudly if the new password does not verify.
--             Paste this block, substituting the same new password.
-- ─────────────────────────────────────────────────────────────────────────────
BEGIN;

-- <<< EDIT THIS LINE >>>  must match what you used in Section B.
SET LOCAL sanibonani.new_admin_password = 'REPLACE_WITH_YOUR_NEW_PASSWORD';

-- <<< EDIT THIS LINE (optional) >>>  the password being replaced, to prove below that it
-- no longer works. Leave the marker and that proof is skipped.
SET LOCAL sanibonani.published_admin_password = 'REPLACE_WITH_THE_PUBLISHED_PASSWORD';

DO $$
DECLARE
    v_plain  text := current_setting('sanibonani.new_admin_password', true);
    v_old    text := nullif(current_setting('sanibonani.published_admin_password', true),
                            'REPLACE_WITH_THE_PUBLISHED_PASSWORD');
BEGIN
    IF v_plain IS NULL OR btrim(v_plain) = '' OR v_plain = 'REPLACE_WITH_YOUR_NEW_PASSWORD' THEN
        RAISE EXCEPTION
            'Section C aborted: set LOCAL sanibonani.new_admin_password to the password you '
            'used in Section B, inside this same transaction.';
    END IF;

    PERFORM 1
      FROM auth.users
     WHERE lower(email) = 'torrymsimango@gmail.com'
       AND extensions.crypt(v_plain, encrypted_password) = encrypted_password;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Section C FAILED: the new password does not verify against auth.users.';
    END IF;

    RAISE NOTICE 'Section C OK: the new admin password verifies against auth.users.';

    -- Independent evidence the rotation actually displaced the published credential.
    IF v_old IS NOT NULL THEN
        PERFORM 1
          FROM auth.users
         WHERE lower(email) = 'torrymsimango@gmail.com'
           AND extensions.crypt(v_old, encrypted_password) = encrypted_password;

        IF FOUND THEN
            RAISE EXCEPTION
                'Section C FAILED: the published password still verifies against auth.users.';
        END IF;
        RAISE NOTICE 'Section C OK: the published password no longer verifies.';
    ELSE
        RAISE NOTICE 'Section C skipped the published-password proof (setting left on its marker).';
    END IF;
END
$$;

COMMIT;

-- Secret-free evidence, always runnable: the row was touched and confirmation is intact.
SELECT
    email_confirmed_at IS NOT NULL  AS email_confirmed,
    updated_at
FROM auth.users
WHERE lower(email) = 'torrymsimango@gmail.com';

-- Session revocation evidence (admin must sign in again).
SELECT count(*) AS admin_sessions_remaining
FROM auth.sessions
WHERE user_id = (SELECT id FROM auth.users WHERE lower(email) = 'torrymsimango@gmail.com');

-- ─────────────────────────────────────────────────────────────────────────────
-- SECTION D — Inventory of the seeded QA accounts (informational, changes nothing).
--             The 38-persona master seed intentionally shares 'Pass123!' so the
--             Payment Sandbox can log in as any persona. Rotate only if those
--             personas ever reach a non-sandbox tier.
--             Read this WITH the role column: any row that is on_shared_qa_password = true and
--             also a platform_admin is a defect. The seed fixture admin was taken off the shared
--             password on 2026-10-31 and 11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql no longer
--             writes it, so both tiers now read 118 shared-QA accounts and none of them is an admin.
-- ─────────────────────────────────────────────────────────────────────────────
SELECT
    (extensions.crypt('Pass123!', encrypted_password) = encrypted_password) AS on_shared_qa_password,
    coalesce(raw_app_meta_data ->> 'role', 'unset')                          AS meta_role,
    count(*)                                                                AS accounts
FROM auth.users
GROUP BY 1, 2
ORDER BY 1 DESC, 2;
