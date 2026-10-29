-- Read-only object fingerprint extractor for the SanibonaniSave public tier.
-- Emits one normalized "kind|name|detail|fingerprint" line per object so two
-- databases can be compared with a plain text diff. Restricted to schema public.
-- Fingerprint method matches the SYNC_DELTA files:
--   md5(btrim(regexp_replace(replace(x, E'\r',''), E'[[:space:]]+', ' ', 'g')))
--
-- OBJECT KINDS
--   T tables, C columns (type|nullability + default hash), V view definitions,
--   F functions (normalized prosrc hash + search_path PINNED/UNPINNED),
--   G trigger definitions, I index definitions (non-primary), K constraint definitions,
--   R per-table RLS enabled flag, P policies (command|grantee + qual/with_check hash),
--   S sequences, E extensions in public, Q grants to anon/authenticated/service_role.
--
-- USAGE
--   Run against any tier, then diff the two listings:
--     psql "$CONN" -v ON_ERROR_STOP=1 -A -F'|' -P footer=off -t \
--       -f supabase/maintenance/SCHEMA_OBJECT_FINGERPRINTS.sql > tier.txt
--     LC_ALL=C sort a.s b.s | uniq ...   # or: comm -23 oracle.s tier.s
--   Rows only in the masters-derived listing = the tier is missing objects.
--   Rows only in the tier = extras (usually Supabase platform-default grants on Q).
--   Compare C-collated, not with a plain diff of the two listings: the trailing
--   ORDER BY uses the database default collation, so near-identical names (the four
--   beneficiary_payout_claims_*_check constraints, say) can appear in a different
--   order on two tiers while the row sets stay equal. The digest in
--   SCHEMA_PARITY_DIGEST.sql sorts with COLLATE "C" and is not affected.
--
--   The expected baseline comes from an oracle database built from the seven
--   structural masters in generate_ground_zero.ps1 order
--   (01, 02, 18, 03, 04, 05, 30). Install pgcrypto and uuid-ossp into schema
--   "extensions" and pg_trgm into "public" so the inventory matches a real
--   Supabase layout, and stub schema auth (users, uid(), jwt()) plus schema
--   storage (buckets, objects, foldername()) so the masters load.
--
-- MEASURED 2026-10-26: the Local tier and that oracle agree on all 1,031 non-grant
--   objects (digest dbbb999efa971fd1ad072df099248a1e) with zero master-declared
--   grants missing.
--
-- Writes nothing. Safe for the Supabase Dashboard SQL editor.

-- T: tables
SELECT 'T', c.table_name, '', ''
  FROM information_schema.tables c
 WHERE c.table_schema = 'public' AND c.table_type = 'BASE TABLE'

UNION ALL
-- C: columns (type + nullability + default)
SELECT 'C', c.table_name || '.' || c.column_name,
       c.data_type || '|' || c.is_nullable,
       md5(btrim(regexp_replace(replace(coalesce(c.column_default, ''), E'\r', ''), E'[[:space:]]+', ' ', 'g')))
  FROM information_schema.columns c
 WHERE c.table_schema = 'public'

UNION ALL
-- V: views (definition body)
SELECT 'V', vv.viewname, '',
       md5(btrim(regexp_replace(replace(pg_get_viewdef(vv.schemaname || '.' || vv.viewname, true), E'\r', ''), E'[[:space:]]+', ' ', 'g')))
  FROM pg_views vv
 WHERE vv.schemaname = 'public'

UNION ALL
-- F: functions (signature + body hash + search_path pin state)
SELECT 'F', p.proname || '(' ||
       md5(btrim(regexp_replace(replace(pg_get_function_identity_arguments(p.oid), E'\r', ''), E'[[:space:]]+', ' ', 'g'))) || ')',
       CASE WHEN array_to_string(p.proconfig, ',') LIKE '%search_path%' THEN 'PINNED' ELSE 'UNPINNED' END,
       coalesce(md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))), 'ABSENT')
  FROM pg_proc p
 WHERE p.pronamespace = 'public'::regnamespace

UNION ALL
-- G: triggers
SELECT 'G', tgtab.nspname || '.' || tgtab.relname || '.' || tg.tgname,
       btrim(regexp_replace(replace(pg_get_triggerdef(tg.oid), E'\r', ''), E'[[:space:]]+', ' ', 'g')), ''
  FROM pg_trigger tg
  JOIN LATERAL (SELECT c.relname, n.nspname
                  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE c.oid = tg.tgrelid) tgtab ON true
 WHERE NOT tg.tgisinternal
   AND tgtab.nspname = 'public'

UNION ALL
-- I: indexes (excluding primary-key indexes, covered by constraints)
SELECT 'I', itab.nspname || '.' || itab.relname || '.' || ic.relname,
       btrim(regexp_replace(replace(pg_get_indexdef(ic.oid), E'\r', ''), E'[[:space:]]+', ' ', 'g')), ''
  FROM pg_index i
  JOIN pg_class ic ON ic.oid = i.indexrelid
  JOIN LATERAL (SELECT c.relname, n.nspname
                  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE c.oid = i.indrelid) itab ON true
 WHERE itab.nspname = 'public'
   AND NOT i.indisprimary

UNION ALL
-- K: constraints
SELECT 'K', ktab.nspname || '.' || ktab.relname || '.' || k.conname,
       k.contype::text || ':' || btrim(regexp_replace(replace(pg_get_constraintdef(k.oid), E'\r', ''), E'[[:space:]]+', ' ', 'g')), ''
  FROM pg_constraint k
  JOIN LATERAL (SELECT c.relname, n.nspname
                  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE c.oid = k.conrelid) ktab ON true
 WHERE k.connamespace = 'public'::regnamespace

UNION ALL
-- R: row-level-security policy set per table
SELECT 'R', relname,
       CASE WHEN relrowsecurity THEN 'RLS_ON' ELSE 'RLS_OFF' END, ''
  FROM pg_class
 WHERE relkind = 'r' AND relnamespace = 'public'::regnamespace

UNION ALL
-- P: policies
SELECT 'P', tablename || '.' || policyname,
       cmd || '|' || array_to_string(roles, ','),
       md5(btrim(regexp_replace(replace(coalesce(qual, '') || '~' || coalesce(with_check, ''), E'\r', ''), E'[[:space:]]+', ' ', 'g')))
  FROM pg_policies
 WHERE schemaname = 'public'

UNION ALL
-- S: sequences
SELECT 'S', sequence_name, '', ''
  FROM information_schema.sequences
 WHERE sequence_schema = 'public'

UNION ALL
-- Q: privileges (tables, views, materialized views, sequences, functions)
SELECT 'Q', qtab.nspname || '.' || c.relname || '|' || pr.privilege_type || '|' ||
       coalesce(gr.rolname, 'PUBLIC'), '', ''
  FROM pg_class c
  JOIN pg_namespace qtab ON qtab.oid = c.relnamespace
  CROSS JOIN LATERAL aclexplode(coalesce(c.relacl, acldefault('r', c.relowner))) pr
  LEFT JOIN pg_roles gr ON gr.oid = pr.grantee
 WHERE qtab.nspname = 'public'
   AND c.relkind IN ('r', 'v', 'm', 'p', 'f', 'S')
   AND coalesce(gr.rolname, 'PUBLIC') IN ('anon', 'authenticated', 'service_role')

UNION ALL
SELECT 'Q', 'function.' || p.proname || '|' || pr.privilege_type || '|' || coalesce(gr.rolname, 'PUBLIC'), '', ''
  FROM pg_proc p
  CROSS JOIN LATERAL aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) pr
  LEFT JOIN pg_roles gr ON gr.oid = pr.grantee
 WHERE p.pronamespace = 'public'::regnamespace
   AND coalesce(gr.rolname, 'PUBLIC') IN ('anon', 'authenticated', 'service_role')

UNION ALL
-- E: extensions
SELECT 'E', x.extname, ns.nspname || '|' || x.extversion, ''
  FROM pg_extension x
  JOIN pg_namespace ns ON ns.oid = x.extnamespace
 WHERE ns.nspname = 'public'

ORDER BY 1, 2;
