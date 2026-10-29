-- SanibonaniSave schema digest. Returns ONE row: objects | per-kind counts | digest.
-- digest = md5 over the C-collated object inventory of schema public:
--   tables, columns (type + nullability + default), views, functions (body hash and
--   search_path pin state), triggers, indexes, constraints, RLS enabled flags,
--   policies, sequences, extensions.
-- Grants are deliberately excluded: Supabase adds platform-default grants that no tier
-- can control, so privileges are compared directionally instead (see notes).
-- Fingerprint method matches the SYNC_DELTA files. This statement writes nothing.
-- Paste into the Supabase Dashboard SQL editor, or run with psql -f.
--
-- EXPECTED (masters oracle == Local tier, measured 2026-10-28 after SYNC_DELTA_20261028):
--   1032|C=535 E=1 F=83 G=15 I=33 K=198 P=85 R=38 S=2 T=38 V=4|9752967a0c8a1e095d1de650a6132efb
--   F=83 because settle_expired_loan_v2 is new in the masters (the settlement refactor
--   renamed the RPC and this tier had not been updated); the earlier baseline (2026-10-26,
--   F=82, dbbb999efa971fd1ad072df099248a1e) predates both that addition and the
--   SEC_002 / FIN_003 body edits, whose hashes are recorded per-function in the dated deltas.
--   A different digest means a different object set or a changed definition; the
--   per-kind counts say which class, then SCHEMA_OBJECT_FINGERPRINTS.sql lists the
--   exact rows. E=0 is benign on a tier where pg_trgm lives in schema "extensions"
--   rather than "public" - compare the other kinds before treating it as drift.
--   OUTSTANDING: the masters now also define is_own_member(UUID), and fourteen bodies
--   change under SYNC_DELTA_20261029 (not applied on either tier yet). A tier that has run
--   it reports F=84 and 1033 objects; re-measure the digest there and replace the line
--   above - the hash cannot be derived from the files alone.
--
-- MAINTENANCE: the block between "WITH inv(kind, name, detail, fp) AS (" and the
-- first line containing only ")" is SCHEMA_OBJECT_FINGERPRINTS.sql verbatim. After
-- editing that file, replace this file's block with its body, dropping the body's
-- comment header and its trailing "ORDER BY 1, 2;" line. The ", lines AS (...)"
-- footer below is what excludes grants (kind Q) and computes the digest.
WITH inv(kind, name, detail, fp) AS (
-- Read-only object fingerprint extractor for the SanibonaniSave public tier.
-- Emits one normalized "kind|name|detail|fingerprint" line per object so two
-- databases can be compared with a plain text diff. Restricted to schema public.
-- Fingerprint method matches the SYNC_DELTA files:
--   md5(btrim(regexp_replace(replace(x, E'\r',''), E'[[:space:]]+', ' ', 'g')))

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

)
, lines AS (
    SELECT kind || '|' || name || '|' || detail || '|' || fp AS line
      FROM inv
     WHERE kind <> 'Q'
)
SELECT (SELECT count(*) FROM lines) AS objects,
       (SELECT string_agg(k || '=' || cnt::text, ' ' ORDER BY k)
          FROM (SELECT left(line, 1) AS k, count(*) AS cnt FROM lines GROUP BY 1) g) AS per_kind,
       (SELECT md5(string_agg(line, E'\n' ORDER BY line COLLATE "C")) FROM lines) AS digest;
