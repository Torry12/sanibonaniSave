# Auth layers V3

The three-level authorization model is current:

- `platform_admin`
- `group_admin`
- `member`

## Where it lives

The former standalone `auth_layers_v3.sql` / `test_auth_layers_v3.sql` files are not part of this repository. The model is implemented in the canonical sources:

- `supabase/02_FUNCTIONS_AND_TRIGGERS.sql` — helpers `is_platform_admin()`, `is_group_admin(group_id)`, `is_group_member(group_id)`, `is_aal2()`.
- `supabase/03_SECURITY_AND_RLS.sql` — RLS policies and grants for groups, members, contributions, payouts, platform fees, and storage.
- `supabase/04_PLATFORM_ADMIN_SETUP.sql` — platform administrator provisioning.

Generate the dashboard bundle with `supabase/scripts/generate_consolidated_schema.ps1`; do not paste the individual files by hand.

## Notes

- `is_group_admin` recognises the primary `groups.admin_user_id` and extra admins who are group members with the `group_admin` role.
- Apply schema changes to an existing tier only through a dated `online/SYNC_DELTA_<date>.sql`.
