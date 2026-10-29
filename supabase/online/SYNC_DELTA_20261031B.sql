-- SYNC_DELTA_20261031B.sql
-- Re-closes the ledger integrity view to anonymous callers on a tier where a blanket table grant
-- reopened it. No ledger row, cached balance, or view definition is touched.
--
-- Why this exists: 03_SECURITY_AND_RLS.sql used to grant on ALL TABLES in schema public, and that
-- includes views. A view runs with the rights of its defining role, so RLS on the base tables does
-- not filter it; the grant is its only gate. Re-applying 03 on an existing tier therefore handed
-- SELECT/INSERT/UPDATE/DELETE back to anon on vw_ledger_integrity_check, undoing the REVOKE that
-- 05_PERFORMANCE_VIEWS.sql performs. The master now grants on relkind 'r'/'p' only, so it cannot
-- recur on the next apply; this delta repairs a tier that is already in the reopened state.
--
-- P.0 is read-only. P.1 is the only write and is idempotent. P.2 proves the result.

-- P.0.a  The view must exist with the canonical definition.
--        MISSING means SYNC_DELTA_20261030 has not run: stop, apply it, then rerun this file.
select c.oid is not null as defined,
       case
         when c.oid is null then 'MISSING - apply SYNC_DELTA_20261030 first, then stop'
         when md5(btrim(regexp_replace(pg_get_viewdef(c.oid, true), E'[[:space:]]+', ' ', 'g')))
              = 'cc3c6a36d0c89dc61bc7d17347bf7f6e' then 'POST (canonical)'
         else 'NEITHER - inspect the definition before writing'
       end as verdict
from (select to_regclass('public.vw_ledger_integrity_check') as oid) found
left join pg_class c on c.oid = found.oid;

-- P.0.b  Current exposure. anon_select=true is the state this delta repairs; false is a no-op.
select has_table_privilege('anon', 'public.vw_ledger_integrity_check', 'SELECT') as anon_select,
       has_table_privilege('authenticated', 'public.vw_ledger_integrity_check', 'SELECT') as authenticated_select;
select relacl as current_acl
from pg_class
where oid = 'public.vw_ledger_integrity_check'::regclass;

-- P.1  Only write. Mirrors 05_PERFORMANCE_VIEWS.sql.
revoke all on public.vw_ledger_integrity_check from public, anon;
grant select on public.vw_ledger_integrity_check to authenticated;

-- P.2.a  Expect anon_select=false, authenticated_select=true.
select has_table_privilege('anon', 'public.vw_ledger_integrity_check', 'SELECT') as anon_select,
       has_table_privilege('authenticated', 'public.vw_ledger_integrity_check', 'SELECT') as authenticated_select;

-- P.2.b  Expect no anon entry in the ACL. Owner and service_role entries are unchanged.
select relacl as acl
from pg_class
where oid = 'public.vw_ledger_integrity_check'::regclass;

-- P.2.c  Definition untouched. Expect cc3c6a36d0c89dc61bc7d17347bf7f6e and 491.
select md5(btrim(regexp_replace(pg_get_viewdef('public.vw_ledger_integrity_check'::regclass, true),
                                E'[[:space:]]+', ' ', 'g'))) as viewdef_hash,
       length(btrim(regexp_replace(pg_get_viewdef('public.vw_ledger_integrity_check'::regclass, true),
                                   E'[[:space:]]+', ' ', 'g'))) as norm_len;

-- P.2.d  Current data integrity. Observational, not a migration gate.
select status, count(*) as groups
from public.vw_ledger_integrity_check
group by status
order by status;

-- P.3  Operator notes
--   - No client change: nothing in :data or :app reads this view. It is an audit surface only.
--   - If P.0.a says POST and P.0.b already says anon_select=false, the tier is healthy and P.1
--     changes nothing. Run P.2 anyway to record the ACL.
--   - 03_SECURITY_AND_RLS.sql must not be re-applied to an existing tier by hand. If it ever is,
--     run this delta's P.1 afterwards; the master no longer reopens the view by itself.
--   - Cloud: run P.0 in the Dashboard, then P.1, then P.2. The anon curl probe is unnecessary -
--     P.2.a reads the same answer from has_table_privilege without a request.
