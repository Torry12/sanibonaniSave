# Safe test seed (superseded)

The former `22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql` and its verifier are no longer part of this repository.

Use the canonical seed instead:

```powershell
npx supabase db query --local --file "supabase/11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql"
```

`11_` is re-runnable and already guarantees an owning admin plus linked admin/member records. Its verification queries are documented in `supabase/docs/README_TEST_DATA_10x100.md`.

For the complete local dataset (all group types, escalations, claims, and 12 months of ledger history), use `supabase/SEED_ALL_DATA.sql` via `npx supabase db reset`.
