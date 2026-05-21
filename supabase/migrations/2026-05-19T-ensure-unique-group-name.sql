-- MIGRATION: Ensure unique group names for ON CONFLICT (name)
-- This migration adds a unique constraint to public.groups.name if not already present.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name='groups' AND constraint_type='UNIQUE' AND constraint_name='groups_name_unique'
    ) THEN
        ALTER TABLE public.groups ADD CONSTRAINT groups_name_unique UNIQUE (name);
    END IF;
END$$;

