-- SanibonaniSave migration
-- Date: 2026-04-19
-- Purpose: Fix PostgREST runtime error `column contributions.receipt_url does not exist`
--
-- Safe to run multiple times.

ALTER TABLE public.contributions
ADD COLUMN IF NOT EXISTS receipt_url TEXT;

