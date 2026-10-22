# Group voting + multi-admin

Group polls and multi-admin authorization are part of the canonical schema; the former `32_GROUP_VOTING_AND_MULTI_ADMIN.sql` pack is no longer a separate file.

## Where it lives

- `supabase/01_DATABASE_SCHEMA.sql` — `group_polls`, `group_poll_options`, `group_poll_votes`.
- `supabase/02_FUNCTIONS_AND_TRIGGERS.sql` — `is_group_admin(group_id)` (primary admin + extra group admins who are group members), poll vote-count and status triggers.
- `supabase/03_SECURITY_AND_RLS.sql` — RLS for polls, options, and votes.

## Behaviour

1. `groups.admin_user_id` is always an admin.
2. A member with `user_profiles.role = 'group_admin'` who has a `members` row in that group is also an admin.
3. Group members/admins can read polls, options, and votes; admins manage polls; members cast or update only their own vote while the poll is open.

## Apply

Apply through the normal workflow: edit the canonical sources, regenerate the four artifacts, and for an existing tier add a dated `online/SYNC_DELTA_<date>.sql`. Do not run a feature script against an existing database.
