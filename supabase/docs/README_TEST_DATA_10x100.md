# Test data seed (10 groups / 100 members)

`supabase/11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql` creates a realistic QA dataset: 10 groups, 100 members, contributions, payments, notifications, loans, loan repayments, platform fees, actuarial metrics, beneficiaries, claims, payouts, audit logs, and ledger rows.

## Run

```powershell
npx supabase db query --local --file "supabase/11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql"
```

It is safe to re-run: the cleanup block deletes the ten `SEED-*` groups and the `seed.member.%@example.com` auth users before recreating them.

## Verify

```sql
SELECT count(*) FROM public.groups WHERE name LIKE 'SEED-%';        -- 10
SELECT count(*) FROM public.members WHERE email LIKE 'seed.member.%@example.com'; -- 100
```

## Notes

- Group ownership is assigned to `platform.admin@master.example.com` so admin dashboard and impersonation flows work immediately.
- That fixture account carries `role='platform_admin'`, so `is_platform_admin()` answers for it. Its password is NOT the shared `Pass123!` the personas use: `11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql` reads it from `current_setting('sanibonani.seed_platform_admin_password')` and, when the setting is absent, creates it with a NULL `encrypted_password` rather than a published one. An existing row is never re-hashed — the seed only creates — so a re-seed cannot restore the leaked credential. `scripts/run-supabase-local-reset.ps1` and `scripts/seed-supabase-online.ps1` supply the setting from `SEED_PLATFORM_ADMIN_PASSWORD` in the untracked `local.properties`.
- The full local dataset also includes `MASTER_SEED_DATA.sql`, the escalation/claims packs, and 12 months of history; `SEED_ALL_DATA.sql` bundles everything.
- Local development only. Never apply this seed to the cloud tier.
