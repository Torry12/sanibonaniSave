-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — GROUP VOTING & GOVERNANCE
-- Version: 1.0 (August 3, 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. VOTING TABLES
CREATE TABLE IF NOT EXISTS public.group_polls (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id              UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    created_by_member_id  UUID REFERENCES public.members(id) ON DELETE SET NULL,
    title                 TEXT NOT NULL CHECK (char_length(title) >= 3),
    description           TEXT,
    status                TEXT DEFAULT 'open' CHECK (status IN ('open', 'closed', 'cancelled')),
    allow_multiple_choice BOOLEAN DEFAULT FALSE,
    ends_at               TIMESTAMPTZ,
    created_at            TIMESTAMPTZ DEFAULT NOW(),
    updated_at            TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.group_poll_options (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id     UUID REFERENCES public.group_polls(id) ON DELETE CASCADE NOT NULL,
    label       TEXT NOT NULL,
    position    INTEGER DEFAULT 0,
    vote_count  INTEGER DEFAULT 0,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.group_poll_votes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id     UUID REFERENCES public.group_polls(id) ON DELETE CASCADE NOT NULL,
    option_id   UUID REFERENCES public.group_poll_options(id) ON DELETE CASCADE NOT NULL,
    member_id   UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(poll_id, member_id) -- One vote per member per poll
);

-- 2. RLS POLICIES
ALTER TABLE public.group_polls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_poll_options ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_poll_votes ENABLE ROW LEVEL SECURITY;

-- group_polls
DROP POLICY IF EXISTS "Polls: View joined" ON public.group_polls;
CREATE POLICY "Polls: View joined" ON public.group_polls FOR SELECT TO authenticated USING (public.is_group_member(group_id) OR public.is_group_admin(group_id));

DROP POLICY IF EXISTS "Polls: Admin manage" ON public.group_polls;
CREATE POLICY "Polls: Admin manage" ON public.group_polls FOR ALL TO authenticated USING (public.is_group_admin(group_id));

-- group_poll_options
DROP POLICY IF EXISTS "Options: View joined" ON public.group_poll_options;
CREATE POLICY "Options: View joined" ON public.group_poll_options FOR SELECT TO authenticated USING (poll_id IN (SELECT id FROM public.group_polls));

-- group_poll_votes
DROP POLICY IF EXISTS "Votes: View joined" ON public.group_poll_votes;
CREATE POLICY "Votes: View joined" ON public.group_poll_votes FOR SELECT TO authenticated USING (poll_id IN (SELECT id FROM public.group_polls));

DROP POLICY IF EXISTS "Votes: Cast own" ON public.group_poll_votes;
CREATE POLICY "Votes: Cast own" ON public.group_poll_votes FOR INSERT TO authenticated WITH CHECK (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

-- 3. TRIGGERS
CREATE OR REPLACE FUNCTION public.fn_sync_poll_vote_count()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        UPDATE public.group_poll_options SET vote_count = vote_count + 1 WHERE id = NEW.option_id;
    ELSIF (TG_OP = 'DELETE') THEN
        UPDATE public.group_poll_options SET vote_count = vote_count - 1 WHERE id = OLD.option_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_sync_poll_vote_count
AFTER INSERT OR DELETE ON public.group_poll_votes
FOR EACH ROW EXECUTE FUNCTION public.fn_sync_poll_vote_count();
