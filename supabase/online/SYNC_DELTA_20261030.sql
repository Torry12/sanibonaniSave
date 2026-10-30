-- SYNC_DELTA_20261030.sql
-- Restores the canonical read-only ledger integrity view on an existing tier where it is
-- missing. No ledger or cached balance rows are changed by this delta.
--
-- P.0 is a read-only pre-flight. If the view is missing, verify P.0.b and then apply
-- P.1/P.2. If its fingerprint is already POST, do not rerun this delta; run P.3 only.
-- Stop for review if the view is present with any other definition. The source definition
-- is supabase/05_PERFORMANCE_VIEWS.sql.
--
-- P.1/P.2 are the only writes: create the view and restrict SELECT to authenticated.
-- P.3 verifies the normalized view fingerprint, access, and current ledger drift.

-- P.0.a  Expected state: the view is absent, or already matches this delta's POST hash.
select c.oid is not null as defined,
       case
         when c.oid is null then 'MISSING (expected pre-state)'
         when md5(btrim(regexp_replace(pg_get_viewdef(c.oid, true), E'[[:space:]]+', ' ', 'g')))
              = 'cc3c6a36d0c89dc61bc7d17347bf7f6e' then 'POST (already applied)'
         else 'NEITHER - inspect before writing'
       end as verdict,
       case when c.oid is null then null
            else md5(btrim(regexp_replace(pg_get_viewdef(c.oid, true), E'[[:space:]]+', ' ', 'g')))
       end as viewdef_hash
from (select to_regclass('public.vw_ledger_integrity_check') as oid) found
left join pg_class c on c.oid = found.oid;
-- Expect: defined=false, verdict='MISSING (expected pre-state)' on the target tier.

-- P.0.b  Dependencies required by the canonical view.
select expected.relation_name,
       expected.column_name,
       c.column_name is not null as present
from (values
        ('groups'::text, 'id'::text),
        ('groups', 'name'),
        ('groups', 'balance'),
        ('group_ledger', 'group_id'),
        ('group_ledger', 'amount')
     ) expected(relation_name, column_name)
left join information_schema.columns c
  on c.table_schema = 'public'
 and c.table_name = expected.relation_name
 and c.column_name = expected.column_name
order by expected.relation_name, expected.column_name;
-- Expect: five rows, all present=true. Stop if any dependency is absent.

-- P.1  Canonical definition copied from supabase/05_PERFORMANCE_VIEWS.sql.
create or replace view public.vw_ledger_integrity_check as
with group_audit as (
    select
        id as group_id,
        name as group_name,
        balance as table_balance,
        (select coalesce(sum(amount), 0) from public.group_ledger where group_id = public.groups.id) as ledger_sum
    from public.groups
)
select
    group_id,
    group_name,
    table_balance,
    ledger_sum,
    (ledger_sum - table_balance) as discrepancy,
    case
        when abs(ledger_sum - table_balance) > 0.01 then 'DRIFT'
        else 'CONSISTENT'
    end as status
from group_audit;

-- P.2  Restrict the view to authenticated callers, then match the canonical grant.
revoke all on public.vw_ledger_integrity_check from public, anon;
grant select on public.vw_ledger_integrity_check to authenticated;

-- P.3.a  Definition fingerprint. Expect POST and normalized length 491.
select md5(btrim(regexp_replace(pg_get_viewdef('public.vw_ledger_integrity_check'::regclass, true),
                                E'[[:space:]]+', ' ', 'g'))) as viewdef_hash,
       length(btrim(regexp_replace(pg_get_viewdef('public.vw_ledger_integrity_check'::regclass, true),
                                   E'[[:space:]]+', ' ', 'g'))) as norm_len;
-- Expect: cc3c6a36d0c89dc61bc7d17347bf7f6e / 491.

-- P.3.b  Access and current data integrity. Data counts are observational, not migration gates.
select has_table_privilege('authenticated', 'public.vw_ledger_integrity_check', 'SELECT') as authenticated_select,
       has_table_privilege('anon', 'public.vw_ledger_integrity_check', 'SELECT') as anon_select;
select status, count(*) as groups
from public.vw_ledger_integrity_check
group by status
order by status;
