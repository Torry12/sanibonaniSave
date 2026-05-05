-- Group health score cache table for admin/member dashboards.
-- Stores latest computed actuarial score per group.

create table if not exists public.group_health_scores (
    id uuid primary key default gen_random_uuid(),
    group_id uuid not null references public.groups(id) on delete cascade,
    overall_score integer not null check (overall_score between 0 and 100),
    zone text not null check (zone in ('RED', 'YELLOW', 'GREEN')),
    components jsonb not null default '{}'::jsonb,
    recommendations jsonb not null default '[]'::jsonb,
    generated_at timestamptz not null default now(),
    expires_at timestamptz,
    updated_at timestamptz not null default now(),
    unique (group_id)
);

create index if not exists idx_group_health_scores_generated_at
    on public.group_health_scores (generated_at desc);

create index if not exists idx_group_health_scores_zone
    on public.group_health_scores (zone);

alter table public.group_health_scores enable row level security;

-- Group admins can read/write their own group health scores.
drop policy if exists "group_admin_manage_health_scores" on public.group_health_scores;
create policy "group_admin_manage_health_scores"
    on public.group_health_scores
    for all
    using (
        exists (
            select 1
            from public.groups g
            where g.id = group_health_scores.group_id
              and g.admin_user_id = auth.uid()
        )
    )
    with check (
        exists (
            select 1
            from public.groups g
            where g.id = group_health_scores.group_id
              and g.admin_user_id = auth.uid()
        )
    );

-- Group members can read health scores for groups they belong to.
drop policy if exists "member_read_group_health_scores" on public.group_health_scores;
create policy "member_read_group_health_scores"
    on public.group_health_scores
    for select
    using (
        exists (
            select 1
            from public.members m
            where m.group_id = group_health_scores.group_id
              and m.user_id = auth.uid()
        )
    );

-- Keep updated_at fresh on update.
create or replace function public.set_group_health_scores_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists trg_group_health_scores_updated_at on public.group_health_scores;
create trigger trg_group_health_scores_updated_at
before update on public.group_health_scores
for each row
execute function public.set_group_health_scores_updated_at();

