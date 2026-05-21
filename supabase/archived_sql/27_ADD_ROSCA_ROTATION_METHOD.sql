-- Adds explicit ROSCA payout rotation configuration to groups.
-- This removes reliance on free-text description parsing.

ALTER TABLE IF EXISTS public.groups
    ADD COLUMN IF NOT EXISTS rosca_rotation_method text NOT NULL DEFAULT 'fixed';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'groups_rosca_rotation_method_check'
          AND conrelid = 'public.groups'::regclass
    ) THEN
        ALTER TABLE public.groups
            ADD CONSTRAINT groups_rosca_rotation_method_check
            CHECK (rosca_rotation_method IN ('fixed', 'random_draw', 'need_based', 'auction'));
    END IF;
END
$$;
