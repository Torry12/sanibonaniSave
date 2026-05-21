-- MIGRATION: Ensure required extensions and constraints for seed scripts
-- Enables pgcrypto for gen_random_uuid() and crypt(), and ensures unique group names.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name='groups' AND constraint_type='UNIQUE' AND constraint_name='groups_name_unique'
    ) THEN
        ALTER TABLE public.groups ADD CONSTRAINT groups_name_unique UNIQUE (name);
    END IF;
END$$;

