# Auth Layers V3

This update introduces a strict 3-level authorization model:

- `platform_admin`
- `group_admin`
- `member`

## Files

- `supabase/auth_layers_v3.sql` - applies hardened RBAC helper functions and RLS policies.
- `supabase/test_auth_layers_v3.sql` - verifies function/policy setup and runs role-visibility checks.

## Apply

Run in Supabase SQL Editor as a privileged role:

1. `supabase/auth_layers_v3.sql`
2. `supabase/test_auth_layers_v3.sql`

## Expected outcomes

- `AuthV3` policies are present in `pg_policies`.
- RLS is enabled on `profiles`, `groups`, `members`, `contributions`, `payouts`, `platform_fees`.
- Visibility checks print role-specific counts in `NOTICE` output.

## Notes

- This script drops permissive legacy policies listed in `auth_layers_v3.sql`.
- Apply first in a staging project, then production.

