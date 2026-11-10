-- SanibonaniSave -- USER ROLE AND LOGIN STATUS LOOKUP (read-only)
-- Rewritten 2026-11-03. This is the query the Dashboard snippet of the same name held: the
-- role / credential inventory the platform-admin password rotation ran as its Cloud pre-flight.
-- It reads and writes nothing, and the BEGIN/ROLLBACK wrapper makes that structural rather than
-- a promise -- an accidental INSERT in an edited copy dies with the rollback.
--
-- WHAT IT ANSWERS
--   Who can sign in, in which role, when they last did, and whether the two places a role is
--   recorded still agree: public.profiles.role (what the app renders) and
--   auth.users.raw_app_meta_data->>'role' (what the RLS policies read). A MISMATCH row means the
--   app and the server are deciding from different facts, which is a sync defect, not a display
--   quirk. cannot_sign_in = true is normal for seeded fixture rows created with a NULL password.
--
-- NO SECRET LIVES IN THIS FILE
--   The optional password proof reads its candidate off a transaction-local setting, exactly the
--   way ROTATE_PLATFORM_ADMIN_PASSWORD_20261018.sql supplies one:
--
--       SET LOCAL sanibonani.password_under_check = 'REPLACE_WITH_THE_PASSWORD_UNDER_CHECK';
--
--   Leave the marker (or drop the SET) and on_checked_password reads NULL -- "not checked". Set a
--   real value and it reads true/false per account, then dies at the ROLLBACK with the session.
--   That is deliberate: the original snippet carried the compared passwords inline, so every run
--   left them in Query History. Do not put them back.
--
-- EXPECTED (Local tier, measured by running this file on 2026-11-03 -- 120 accounts)
--   118 member + 2 platform_admin, role_agreement = 'agree' on every row, banned = 0, and
--   cannot_sign_in = 0: both admins carry a hash since the 2026-11-03 rotation, and the seed only
--   writes a NULL password when sanibonani.seed_platform_admin_password is left unset.
--   never_signed_in = 120 and live_sessions = 0 everywhere. Every account here was inserted
--   straight into auth.users by a seed, never through GoTrue, so last_sign_in_at is NULL and
--   days_since_sign_in is blank. An all-NULL sign-in column is a seed artifact, not stale data --
--   it only starts carrying information once an account actually signs in.
--   The 118 personas share 'Pass123!' on purpose (the Payment Sandbox signs in as any of them);
--   none of them is a platform_admin, because the seeded fixture admin came off the shared
--   password on 2026-10-31 and 11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql no longer writes it.
--   A row with on_checked_password = true AND profile_role = 'platform_admin' is a defect: that is
--   a live admin still answering to a shared or published credential.
--
-- With a password set, on_checked_password bcrypts against every account -- roughly ten seconds
-- over 118 rows. Run it without the setting for the instant roster.
--
-- Paste into the Supabase Dashboard SQL editor, or run with psql -f. On the Local tier:
--   docker exec <db-container> psql -U postgres -d postgres -f /tmp/this_file.sql
BEGIN;

-- Edit only if you want the password proof; the marker means "not checked".
SET LOCAL sanibonani.password_under_check = 'REPLACE_WITH_THE_PASSWORD_UNDER_CHECK';

-- ── 1. The roster: every account, its two roles, and its sign-in state ──────────────────────
SELECT
    lower(u.email)                                                          AS email,
    coalesce(p.role, 'no profile row')                                      AS profile_role,
    coalesce(u.raw_app_meta_data ->> 'role', 'unset')                       AS meta_role,
    CASE
        WHEN p.id IS NULL                                   THEN 'no profile'
        WHEN coalesce(u.raw_app_meta_data ->> 'role', '')
             IS DISTINCT FROM p.role                        THEN 'MISMATCH'
        ELSE 'agree'
    END                                                                     AS role_agreement,
    (u.email_confirmed_at IS NOT NULL)                                      AS email_confirmed,
    (u.encrypted_password IS NULL)                                          AS cannot_sign_in,
    u.last_sign_in_at                                                       AS last_sign_in_at,
    round(extract(epoch FROM (now() - u.last_sign_in_at)) / 86400.0, 1)      AS days_since_sign_in,
    (u.banned_until IS NOT NULL AND u.banned_until > now())                 AS is_banned,
    live.sessions                                                           AS live_sessions,
    CASE
        WHEN v.pw IS NULL OR u.encrypted_password IS NULL THEN NULL
        ELSE extensions.crypt(v.pw, u.encrypted_password) = u.encrypted_password
    END                                                                     AS on_checked_password,
    u.updated_at
FROM auth.users u
LEFT JOIN public.profiles p ON p.id = u.id
LEFT JOIN LATERAL (
    SELECT count(*) AS sessions
      FROM auth.sessions s
     WHERE s.user_id = u.id
       AND (s.not_after IS NULL OR s.not_after > now())
) live ON true
CROSS JOIN LATERAL (
    SELECT nullif(current_setting('sanibonani.password_under_check', true),
                  'REPLACE_WITH_THE_PASSWORD_UNDER_CHECK') AS pw
) v
ORDER BY
    (p.role = 'platform_admin') DESC NULLS LAST,
    (p.role = 'group_admin')    DESC NULLS LAST,
    u.last_sign_in_at           DESC NULLS LAST,
    email;

-- ── 2. The rollup: what the roster adds up to, per role ─────────────────────────────────────
--    Answers "is the population still what the seed says" without scrolling 118 rows.
SELECT
    coalesce(p.role, 'no profile row')                                  AS profile_role,
    count(*)                                                            AS accounts,
    count(*) FILTER (WHERE u.encrypted_password IS NULL)                AS cannot_sign_in,
    count(*) FILTER (WHERE u.email_confirmed_at IS NULL)                AS unconfirmed,
    count(*) FILTER (WHERE u.last_sign_in_at IS NULL)                   AS never_signed_in,
    count(*) FILTER (WHERE u.banned_until IS NOT NULL
                          AND u.banned_until > now())                   AS banned,
    max(u.last_sign_in_at)                                              AS most_recent_sign_in
FROM auth.users u
LEFT JOIN public.profiles p ON p.id = u.id
GROUP BY 1
ORDER BY 1;

ROLLBACK;
