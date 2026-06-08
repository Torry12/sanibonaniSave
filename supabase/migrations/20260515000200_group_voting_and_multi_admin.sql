-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — GROUP VOTING + MULTI-ADMIN AUTHORIZATION
-- Date: 2026-05-15
-- Purpose:
--   1) Add group voting tables (polls, options, votes)
--   2) Extend group-admin authorization to support extra group admins
--      linked via profiles.role='group_admin' and group membership.
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

CREATE OR REPLACE FUNCTION public.is_group_admin(p_group_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1
        FROM public.groups g
        WHERE g.id = p_group_id
          AND g.admin_user_id = auth.uid()
    )
    OR EXISTS (
        SELECT 1
        FROM public.members m
        JOIN public.profiles p ON p.id = m.user_id
        WHERE m.group_id = p_group_id
          AND m.user_id = auth.uid()
          AND p.role = 'group_admin'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TABLE IF NOT EXISTS public.group_polls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    created_by_member_id UUID REFERENCES public.members(id) ON DELETE SET NULL,
    title TEXT NOT NULL CHECK (char_length(trim(title)) >= 3),
    description TEXT,
    status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('draft', 'open', 'closed', 'cancelled')),
    allow_multiple_choice BOOLEAN NOT NULL DEFAULT FALSE,
    starts_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (ends_at IS NULL OR ends_at >= starts_at)
);

CREATE TABLE IF NOT EXISTS public.group_poll_options (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id UUID NOT NULL REFERENCES public.group_polls(id) ON DELETE CASCADE,
    label TEXT NOT NULL CHECK (char_length(trim(label)) >= 1),
    position INTEGER NOT NULL DEFAULT 1 CHECK (position > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (poll_id, position)
);

CREATE TABLE IF NOT EXISTS public.group_poll_votes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id UUID NOT NULL REFERENCES public.group_polls(id) ON DELETE CASCADE,
    option_id UUID NOT NULL REFERENCES public.group_poll_options(id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES public.members(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (poll_id, member_id)
);

CREATE INDEX IF NOT EXISTS idx_group_polls_group_id ON public.group_polls(group_id);
CREATE INDEX IF NOT EXISTS idx_group_poll_options_poll_id ON public.group_poll_options(poll_id);
CREATE INDEX IF NOT EXISTS idx_group_poll_votes_poll_id ON public.group_poll_votes(poll_id);
CREATE INDEX IF NOT EXISTS idx_group_poll_votes_member_id ON public.group_poll_votes(member_id);

DROP TRIGGER IF EXISTS trigger_update_group_polls_updated_at ON public.group_polls;
CREATE TRIGGER trigger_update_group_polls_updated_at
BEFORE UPDATE ON public.group_polls
FOR EACH ROW EXECUTE PROCEDURE public.update_updated_at_column();

DROP TRIGGER IF EXISTS trigger_update_group_poll_votes_updated_at ON public.group_poll_votes;
CREATE TRIGGER trigger_update_group_poll_votes_updated_at
BEFORE UPDATE ON public.group_poll_votes
FOR EACH ROW EXECUTE PROCEDURE public.update_updated_at_column();

ALTER TABLE public.group_polls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_poll_options ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_poll_votes ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Polls: Group members view" ON public.group_polls;
CREATE POLICY "Polls: Group members view" ON public.group_polls
FOR SELECT TO authenticated
USING (public.is_group_member(group_id) OR public.is_group_admin(group_id));

DROP POLICY IF EXISTS "Polls: Admin manage" ON public.group_polls;
CREATE POLICY "Polls: Admin manage" ON public.group_polls
FOR ALL TO authenticated
USING (public.is_group_admin(group_id))
WITH CHECK (public.is_group_admin(group_id));

DROP POLICY IF EXISTS "Poll options: Group members view" ON public.group_poll_options;
CREATE POLICY "Poll options: Group members view" ON public.group_poll_options
FOR SELECT TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.group_polls p
        WHERE p.id = group_poll_options.poll_id
          AND (public.is_group_member(p.group_id) OR public.is_group_admin(p.group_id))
    )
);

DROP POLICY IF EXISTS "Poll options: Admin manage" ON public.group_poll_options;
CREATE POLICY "Poll options: Admin manage" ON public.group_poll_options
FOR ALL TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.group_polls p
        WHERE p.id = group_poll_options.poll_id
          AND public.is_group_admin(p.group_id)
    )
)
WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.group_polls p
        WHERE p.id = group_poll_options.poll_id
          AND public.is_group_admin(p.group_id)
    )
);

DROP POLICY IF EXISTS "Poll votes: Group members view" ON public.group_poll_votes;
CREATE POLICY "Poll votes: Group members view" ON public.group_poll_votes
FOR SELECT TO authenticated
USING (public.is_group_member(group_id) OR public.is_group_admin(group_id));

DROP POLICY IF EXISTS "Poll votes: Member cast own" ON public.group_poll_votes;
CREATE POLICY "Poll votes: Member cast own" ON public.group_poll_votes
FOR INSERT TO authenticated
WITH CHECK (
    member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid() AND group_id = group_poll_votes.group_id)
    AND EXISTS (
        SELECT 1 FROM public.group_polls p
        WHERE p.id = group_poll_votes.poll_id
          AND p.group_id = group_poll_votes.group_id
          AND p.status = 'open'
          AND (p.ends_at IS NULL OR p.ends_at >= NOW())
    )
    AND EXISTS (
        SELECT 1 FROM public.group_poll_options o
        WHERE o.id = group_poll_votes.option_id
          AND o.poll_id = group_poll_votes.poll_id
    )
);

DROP POLICY IF EXISTS "Poll votes: Member update own" ON public.group_poll_votes;
CREATE POLICY "Poll votes: Member update own" ON public.group_poll_votes
FOR UPDATE TO authenticated
USING (
    member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid())
)
WITH CHECK (
    member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid() AND group_id = group_poll_votes.group_id)
    AND EXISTS (
        SELECT 1 FROM public.group_polls p
        WHERE p.id = group_poll_votes.poll_id
          AND p.status = 'open'
          AND (p.ends_at IS NULL OR p.ends_at >= NOW())
    )
    AND EXISTS (
        SELECT 1 FROM public.group_poll_options o
        WHERE o.id = group_poll_votes.option_id
          AND o.poll_id = group_poll_votes.poll_id
    )
);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.group_polls TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.group_poll_options TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.group_poll_votes TO authenticated;

COMMIT;

