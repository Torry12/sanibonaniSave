# Group voting

Group polls are part of the canonical schema; the former `32_GROUP_VOTING_AND_MULTI_ADMIN.sql` pack is no longer a separate file. Note the "multi-admin" half of that old name: **the shipped schema has a single-admin model** — `is_group_admin(p_group_id)` only tests `groups.admin_user_id = auth.uid()` and `groups.admin_user_id` is `NOT NULL`. There is no extra-admin table and no `profiles.role` branch in that function.

## Where it lives

- `supabase/01_DATABASE_SCHEMA.sql` — `group_polls`, `group_poll_options`, `group_poll_votes`.
- `supabase/02_FUNCTIONS_AND_TRIGGERS.sql` — `is_group_admin(group_id)`, `is_group_member(group_id)`, and the poll triggers below.
- `supabase/03_SECURITY_AND_RLS.sql` — RLS for polls, options, and votes.

## Tables

| Table | Key columns |
| :--- | :--- |
| `group_polls` | `group_id`, `created_by_member_id`, `title` (`>= 3` chars), `type` (default `general`, no CHECK), `status` (`draft`/`open`/`closed`/`cancelled`), `effect_status` (default `none`), `effect_data`, `allow_multiple_choice`, `starts_at`, `ends_at` |
| `group_poll_options` | `poll_id`, `label`, `position`, `vote_count` |
| `group_poll_votes` | `poll_id`, `option_id`, `member_id`, `group_id`, `UNIQUE(poll_id, member_id)` |

`UNIQUE(poll_id, member_id)` means one option per member per poll at the database level. `allow_multiple_choice` is only honoured in Kotlin.

## Triggers

- `tr_sync_poll_vote_count` (AFTER INSERT OR DELETE on `group_poll_votes`) increments/decrements `group_poll_options.vote_count`. Do not write `vote_count` from the app.
- `tr_sync_poll_status` (BEFORE UPDATE on `group_polls`) flips an `open` poll to `closed` once `ends_at <= NOW()`.
- `tr_poll_audit` / `tr_vote_audit` write `POLL_CREATED` and `VOTE_CAST` to `audit_logs`. The auto-close path logs `POLL_AUTO_CLOSED` with actor `system_gov`.
- `close_poll_v1(UUID)` is the explicit early close the voting screen's Close button calls. It existed in no schema until `SYNC_DELTA_20261029B`, so until that delta is applied the only way a poll ends is its own `ends_at`. It takes `open` only (`[FIN_002] BAD_STATUS`), writes one `POLL_CLOSED` audit row, and decides entitlement itself — see Authorization.

## Authorization

1. `groups.admin_user_id` is the only group admin; `is_group_admin()` returns true for exactly that user (and it is `SECURITY DEFINER`, so it reads across RLS).
2. Members see their own group's data through `is_group_member(group_id)`, which matches `members.user_id = auth.uid()`.
3. `group_polls`, `group_poll_options` and `group_poll_votes` get `"Group Admin: Manage group"` policies; `group_poll_votes` additionally gets `"Member: Manage own votes"` — a `FOR ALL` policy keyed on `member_id IN (SELECT id FROM members WHERE user_id = auth.uid())`. RLS enforces vote ownership, not the open window; nothing server-side stops a member voting after `ends_at`.
4. On group insert a trigger auto-creates the admin's own `members` row (`'Group Admin'`, status `active`), so ownership always has a member record to hang votes on.
5. `close_poll_v1` is the exception to "RLS decides": closing is a governance decision about the whole group, so the function carries its own check — `service_role`, `is_platform_admin()` or `is_group_admin(poll.group_id)`, else `[SEC_002] POLL_CLOSE_ACCESS_DENIED`. The screen renders the Close button to every viewer; the database is what says no.

## Apply

Apply through the normal workflow: edit the canonical sources, regenerate the four artifacts, and for an existing tier add a dated `online/SYNC_DELTA_<date>.sql`. Do not run a feature script against an existing database.
