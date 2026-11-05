-- SYNC_DELTA_20261104.sql
-- ROW-LEVEL-SECURITY DELTA. Adds one INSERT policy to public.audit_logs so a platform-admin support
-- session can record itself, and changes nothing else.
--
-- The defect this closes:
--   audit_logs had no INSERT policy of its own. Its only mutation policies are
--     * "MFA Mandatory: Sensitive mutation"   FOR ALL  USING/WITH CHECK public.is_aal2()
--     * "Platform Admin Bypass"               FOR ALL  USING/WITH CHECK is_platform_admin() AND is_aal2()
--   and is_aal2() is (auth.jwt() ->> 'aal') = 'aal2' (02_FUNCTIONS_AND_TRIGGERS.sql:7-12). The app's
--   admin sessions are password-only, so the token answers aal1 and no policy admits the write: every
--   IMPERSONATION_REQUESTED insert is refused with 42501 "new row violates row-level security policy
--   for table \"audit_logs\"". Measured on-device 2026-11-04 in files/diagnostics.log at 18:08:16 and
--   18:09:10, on both attempts to open a member's portal in Support Mode.
--   So a support session - a human deciding to read a member's book - leaves no server-side record of
--   itself, which is the one thing the trail exists to prevent.
--
-- What the new policy does NOT relax:
--   * Reading. SELECT stays behind the AAL2 policies plus "Audit Logs: View personal"; this delta adds
--     no read path, so nothing that could not be seen before can be seen now.
--   * Editing or deleting the trail. UPDATE/DELETE keep requiring MFA, so audit_logs stays append-only.
--   * Attribution. WITH CHECK pins actor_id to the caller's own uid, with one exception: a row stamped
--     'SYSTEM' - which is what AuditLogRepository writes for an automated entry with no human actor -
--     is only admitted from a platform admin. A group admin may append only inside their own group.
--     A stolen password-only session can therefore add a truthful row about an action it was already
--     entitled to take, and nothing forged about someone else.
--
-- Re-runnable: P.1 is DROP POLICY IF EXISTS followed by CREATE POLICY, so applying it twice leaves one
-- policy, and it drops nothing that already works.
--
-- Tier state before the run: NOT measured by the author. No credentials were used to write this file
-- and nothing here has been executed on either tier. Run P.0 on the tier you are about to write and
-- confirm it answers the way this header says it will, then run P.1, then P.2.
--
-- Rollback: the single statement in R.1 at the bottom. No data is written by this delta, so no dump is
-- required to undo it - but a row that was admitted after P.1 stays, by design.

-- ─────────────────────────────────────────────────────────────────────────────
-- P.0  PRE-FLIGHT - READ ONLY. Run each on its own, on the tier you are about to write.
-- ─────────────────────────────────────────────────────────────────────────────

-- P.0.a  The policies audit_logs carries today, with the command each one covers.
--        Expect three rows on a tier built from the current masters:
--        "MFA Mandatory: Sensitive mutation" (*) , "Platform Admin Bypass" (*),
--        "Audit Logs: View personal" (r) and - only if this delta already ran - the new (a) row.
--        If a fifth, unexpected policy appears, stop and read it before writing.
SELECT polname,
       polcmd,
       pg_get_expr(polqual,  polrelid) AS using_expr,
       pg_get_expr(polwithcheck, polrelid) AS check_expr
FROM pg_policy
WHERE polrelid = 'public.audit_logs'::regclass
ORDER BY polname;

-- P.0.b  RLS is enforced on the table, and no RESTRICTIVE policy is hiding behind the permissive ones.
--        A restrictive policy would keep refusing the insert after P.1, because permissive policies OR
--        together and restrictive ones AND on top. Expect relrowsecurity = true and
--        relforcerowsecurity = false.
SELECT c.relrowsecurity, c.relforcerowsecurity
FROM pg_class c
WHERE c.oid = 'public.audit_logs'::regclass;

-- P.0.c  The size of the hole. Expect 0 impersonation rows on both tiers: the refusal has been total,
--        not intermittent, so a non-zero count here means the tier is not the one the app was pointing
--        at, or somebody already opened the policy.
SELECT count(*) AS impersonation_rows_present,
       max(created_at) AS newest
FROM public.audit_logs
WHERE action = 'IMPERSONATION_REQUESTED';

-- P.0.d  The role that will run P.1 may add policies to this table.
SELECT has_table_privilege(current_user, 'public.audit_logs', 'INSERT'),
       has_schema_privilege(current_user, 'public', 'CREATE');

-- ─────────────────────────────────────────────────────────────────────────────
-- P.1  THE WRITE. Verbatim from 03_SECURITY_AND_RLS.sql section 8.
-- ─────────────────────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "Audit Logs: Append by named actor" ON public.audit_logs;
CREATE POLICY "Audit Logs: Append by named actor" ON public.audit_logs FOR INSERT TO authenticated
WITH CHECK (
    (actor_id = auth.uid()::text OR (actor_id = 'SYSTEM' AND public.is_platform_admin()))
    AND (public.is_platform_admin() OR public.is_group_admin(target_group_id))
);

-- P.1.z  PostgREST caches the schema; tell it to reload so the client's next POST sees the policy.
SELECT pg_notify('pgrst', 'reload schema');

-- ─────────────────────────────────────────────────────────────────────────────
-- P.2  VERIFICATION - READ ONLY, after P.1.
-- ─────────────────────────────────────────────────────────────────────────────

-- P.2.a  The policy exists, covers INSERT only, and its check expression is the one from the master.
--        Expect one row: polcmd = 'a' (append) and using_expr NULL - an INSERT policy has no USING.
SELECT polname, polcmd, polpermissive,
       pg_get_expr(polwithcheck, polrelid) AS check_expr
FROM pg_policy
WHERE polrelid = 'public.audit_logs'::regclass
  AND polname = 'Audit Logs: Append by named actor';

-- P.2.b  Reading is unchanged: the two AAL2 gates are still there and no SELECT policy was added.
--        Expect the same three rows as P.0.a plus one (a) row - and "Audit Logs: View personal"
--        untouched.
SELECT polname, polcmd
FROM pg_policy
WHERE polrelid = 'public.audit_logs'::regclass
ORDER BY polname;

-- P.2.c  The proof that matters is the client's, not SQL's: sign in as a platform admin without MFA,
--        open a member's portal in Support Mode, then read the count. Expect 1 more row than P.0.c
--        reported, owned by that admin's uid.
SELECT actor_id, action, target_group_id, target_member_id, created_at
FROM public.audit_logs
WHERE action = 'IMPERSONATION_REQUESTED'
ORDER BY created_at DESC
LIMIT 5;

-- P.2.d  Nothing was edited or deleted by the wider grant, and the trail is still append-only: this
--        answers 42501 for an UPDATE attempted as the same password-only admin. Prove it with
--        has_row_security + P.2.a's check_expr only covering INSERT, or from the app - do NOT probe a
--        SECURITY DEFINER function as anon on Local (it crashes the postmaster).

-- ─────────────────────────────────────────────────────────────────────────────
-- R.1  ROLLBACK - removes only what P.1 added. Run it on its own.
-- ─────────────────────────────────────────────────────────────────────────────
-- DROP POLICY IF EXISTS "Audit Logs: Append by named actor" ON public.audit_logs;
-- SELECT pg_notify('pgrst', 'reload schema');
