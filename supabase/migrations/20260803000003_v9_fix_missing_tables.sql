-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — FIX MISSING CORE TABLES
-- Version: 1.0 (August 3, 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. MEMBER DOCUMENTS
CREATE TABLE IF NOT EXISTS public.member_documents (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id         UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    document_type     TEXT NOT NULL,
    document_url      TEXT NOT NULL,
    status            TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'verified', 'rejected')),
    notes             TEXT,
    verified_at       TIMESTAMPTZ,
    verified_by       UUID REFERENCES auth.users(id),
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.member_documents ENABLE ROW LEVEL SECURITY;

-- 2. POLICIES (From core security)
DROP POLICY IF EXISTS "Member Documents: View own" ON public.member_documents;
DROP POLICY IF EXISTS "Member Documents: Admin view group" ON public.member_documents;
DROP POLICY IF EXISTS "Member Documents: Manage own" ON public.member_documents;

CREATE POLICY "Member Documents: View own" ON public.member_documents FOR SELECT TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));
CREATE POLICY "Member Documents: Admin view group" ON public.member_documents FOR SELECT TO authenticated USING (public.is_group_admin(group_id));
CREATE POLICY "Member Documents: Manage own" ON public.member_documents FOR ALL TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid())) WITH CHECK (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

-- 3. UNIVERSAL BYPASS
DROP POLICY IF EXISTS "Platform Admin Bypass" ON public.member_documents;
CREATE POLICY "Platform Admin Bypass" ON public.member_documents FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin());
