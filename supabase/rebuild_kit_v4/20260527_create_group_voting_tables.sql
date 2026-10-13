-- Migration to create group voting tables for SanibonaniSave
-- Place in supabase/rebuild_kit_v4/

BEGIN;

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

-- Triggers and RLS policies can be added here if needed

COMMIT;
