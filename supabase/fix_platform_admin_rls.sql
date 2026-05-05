-- Fix Platform Admin RLS and permissions for global settings
-- Run this in Supabase SQL Editor if you see "RLS Violation" on Fee Management tab.

-- 1. Ensure RLS is active
ALTER TABLE public.platform_settings ENABLE ROW LEVEL SECURITY;

-- 2. Drop existing policies to start fresh
DROP POLICY IF EXISTS "Allow all view settings" ON public.platform_settings;
DROP POLICY IF EXISTS "Platform admin manage settings" ON public.platform_settings;
DROP POLICY IF EXISTS "Public view settings" ON public.platform_settings;

-- 3. Create robust policies
-- SELECT: Everyone (including anon) can see the fees (for discovery/registration)
CREATE POLICY "Public view settings"
ON public.platform_settings FOR SELECT
TO anon, authenticated
USING (true);

-- ALL: Only platform admins can update/delete/insert
CREATE POLICY "Platform admin manage settings"
ON public.platform_settings FOR ALL
TO authenticated
USING (public.is_platform_admin())
WITH CHECK (public.is_platform_admin());

-- 4. Ensure is_platform_admin function is reliable
-- It needs to be SECURITY DEFINER to bypass RLS on the profiles table.
CREATE OR REPLACE FUNCTION public.is_platform_admin()
RETURNS BOOLEAN AS $$
BEGIN
    -- First check the profiles table
    IF EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND role = 'platform_admin'
    ) THEN
        RETURN TRUE;
    END IF;

    -- Fallback: check JWT metadata (handles cases where profile might be lagging)
    RETURN (auth.jwt() -> 'user_metadata' ->> 'role') = 'platform_admin';
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 5. Ensure table GRANTS are complete
GRANT ALL ON public.platform_settings TO postgres, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.platform_settings TO authenticated;
GRANT SELECT ON public.platform_settings TO anon;

-- 6. Refresh PostgREST
NOTIFY pgrst, 'reload schema';

-- Verification Query (Run this and check if it returns true)
-- SELECT public.is_platform_admin();
