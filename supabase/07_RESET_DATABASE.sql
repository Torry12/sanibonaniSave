-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — DATABASE RESET SCRIPT (Hardened)
-- WARNING: DESTRUCTIVE ACTION
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    r RECORD;
BEGIN
    -- 1. Drop triggers first
    FOR r IN (SELECT trigger_name, event_object_table FROM information_schema.triggers WHERE trigger_schema = 'public') LOOP
        EXECUTE 'DROP TRIGGER IF EXISTS ' || quote_ident(r.trigger_name) || ' ON public.' || quote_ident(r.event_object_table) || ' CASCADE';
    END LOOP;

    -- 2. Drop functions (handles overloads), skipping extension functions
    FOR r IN (
        SELECT ns.nspname as schema_name, p.proname as function_name, pg_get_function_identity_arguments(p.oid) as args
        FROM pg_proc p
        JOIN pg_namespace ns ON p.pronamespace = ns.oid
        LEFT JOIN pg_depend d ON d.objid = p.oid AND d.deptype = 'e'
        WHERE ns.nspname = 'public'
          AND d.objid IS NULL
    ) LOOP
        EXECUTE 'DROP FUNCTION IF EXISTS ' || quote_ident(r.schema_name) || '.' || quote_ident(r.function_name) || '(' || r.args || ') CASCADE';
    END LOOP;

    -- 3. Drop views
    FOR r IN (SELECT table_name FROM information_schema.views WHERE table_schema = 'public') LOOP
        EXECUTE 'DROP VIEW IF EXISTS public.' || quote_ident(r.table_name) || ' CASCADE';
    END LOOP;

    -- 4. Drop tables
    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE 'DROP TABLE IF EXISTS public.' || quote_ident(r.tablename) || ' CASCADE';
    END LOOP;

    -- 5. Drop types
    FOR r IN (SELECT typname FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace WHERE n.nspname = 'public' AND t.typtype = 'e') LOOP
        EXECUTE 'DROP TYPE IF EXISTS public.' || quote_ident(r.typname) || ' CASCADE';
    END LOOP;

    -- 6. Clean up seed auth users and identities to prevent constraint errors on re-seeding
    BEGIN
        DELETE FROM auth.identities WHERE user_id IN (
            SELECT id FROM auth.users WHERE email LIKE '%@master.example.com' OR email LIKE '%@scen.com' OR email = 'torrymsimango@gmail.com'
        );
        DELETE FROM auth.users WHERE email LIKE '%@master.example.com' OR email LIKE '%@scen.com' OR email = 'torrymsimango@gmail.com';
    EXCEPTION WHEN OTHERS THEN
        NULL;
    END;

END $$;
