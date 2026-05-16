# Group Voting + Multi-Admin SQL

This SQL pack enables group-level voting and upgrades group-admin authorization to support extra admins who are members of the group.

## Files

- `supabase/32_GROUP_VOTING_AND_MULTI_ADMIN.sql`
- `supabase/migrations/20260515000200_group_voting_and_multi_admin.sql`

## What it adds

1. Updates `public.is_group_admin(group_id)`:
   - Primary admin (`groups.admin_user_id`) is admin
   - Extra admins are also admin when:
     - `profiles.role = 'group_admin'`
     - and they are a `members` row in that group

2. Creates voting tables:
   - `public.group_polls`
   - `public.group_poll_options`
   - `public.group_poll_votes`

3. Adds RLS policies:
   - Group members/admins can view polls/options/votes
   - Group admins can create/manage polls and options
   - Members can cast/update only their own vote while poll is open

## Apply order (manual SQL editor)

```sql
-- after core setup
-- 01_DATABASE_SCHEMA.sql
-- 02_SECURITY_AND_RLS.sql
-- 03_PLATFORM_ADMIN_SETUP.sql

-- feature migration
-- 32_GROUP_VOTING_AND_MULTI_ADMIN.sql
```

## Local CLI

```powershell
cd "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
supabase db reset --local --no-seed --yes
```

The local migration chain auto-applies `20260515000200_group_voting_and_multi_admin.sql`.

