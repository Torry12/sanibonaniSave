# 🧪 SanibonaniSave — Seed Data & Environment Setup Guide

This document describes how to populate a **local development** database with the project's demo data. Seeds are local-only: never run them against the cloud tier or any database that already holds real data.

---

## 🏗️ 1. Seed Files

| File | Purpose |
| :--- | :--- |
| `SEED_ALL_DATA.sql` | **Generated** integrated entry point. Concatenates every module below in order. `supabase/config.toml` points `sql_paths` at this file, so `supabase db reset` runs it automatically. Never hand-edit. |
| `10_SEED_HELPERS.sql` | `pg_temp` helpers the seed posts money through. Must run before any money module: a seed session has no JWT, so the `[SEC_002]` guards on `record_contribution_v2` and friends refuse it (see §5). |
| `MASTER_SEED_DATA.sql` | Consolidated demo environment: QA actors, the core group types, members, loans, beneficiaries, polls, and opening ledger history. |
| `11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql` | 10 groups / 100 members with 12 months of history and banking details. |
| `15_SEED_LIVE_PAYOUT_REQUESTS.sql` | Payout requests layered onto an existing data set; not part of the full rebuild order. |
| `16_SEED_50_PAYMENT_REQUESTS_AND_APPROVALS.sql` | 50 multi-type payout requests with approval conditions. |
| `17_SEED_PLATFORM_FINANCIAL_ESCALATIONS.sql` | Platform escalations, pending loans, and claim fixtures. |
| `19_SEED_BURIAL_CLAIM_ESCALATIONS.sql` | Burial-society claim escalation scenarios. |
| `20_SEED_12_MONTHS_TRANSACTIONS.sql` | 12-month transaction and ledger history for support and simulation flows. |
| `21_SEED_VALID_BURIAL_CLAIMS.sql` | Valid burial-claim scenarios. |
| `31_REAL_LIFE_INTEGRITY_SEEDS.sql` | Deliberate forensic/audit fixtures, scoped to `LIGHT-Stokvel`; these are intentional drift records, not bugs. |

`00_REBUILD_ALL.sql` is the authoritative local execution order and includes every module above except the standalone `15_` file.

---

## 🚀 2. How to Run the Seeds

### Local Development (Docker)

```bash
npx supabase start
npx supabase db reset      # rebuilds the schema and runs SEED_ALL_DATA.sql
```

`db reset` is destructive to the **local** database only.

### Manual local run (Supabase CLI)

```bash
# `supabase db query --file` cannot run this script: it prepares each statement, and the file is
# one transaction of many statements ("cannot insert multiple commands into a prepared statement").
# psql runs the whole file in one session, which the pg_temp helper needs.
docker exec -i supabase_db_aszmodkqpafwtnfufmow psql -U postgres -d postgres -X \
  -v ON_ERROR_STOP=1 -f - < supabase/SEED_ALL_DATA.sql
```

The container name follows the project ref (`docker ps | grep supabase_db`). The script is
re-runnable: it recreates its own groups and removes the platform receipts they produced, so a
second run leaves the same balances and the same platform-ledger total, not twice as many rows.

### Online/Cloud

Do **not** seed the cloud tier. Existing environments change only through a dated `supabase/online/SYNC_DELTA_<date>.sql` (see `supabase/docs/DATABASE_ORGANIZATION.md`). `SEED_ALL_DATA.sql` and the module seeds are development fixtures and are excluded from the online rebuild.

---

## 📊 3. Verifying the Data

Run these read-only queries after a reset:

```sql
-- Core counts
SELECT count(*) FROM public.groups;
SELECT count(*) FROM public.members;

-- Platform escalation tabs
SELECT count(*) FROM public.payouts WHERE status = 'pending';
SELECT count(*) FROM public.beneficiary_payout_claims WHERE status = 'escalated';
SELECT count(*) FROM public.loans WHERE status = 'pending';

-- Ledger integrity: cached balance must equal the ledger sum
SELECT g.id, g.name, g.balance,
       COALESCE(SUM(l.amount), 0) AS ledger_sum
FROM public.groups g
LEFT JOIN public.group_ledger l ON l.group_id = g.id
GROUP BY g.id, g.name, g.balance
HAVING g.balance <> COALESCE(SUM(l.amount), 0);

-- Ledger split after a member payment
SELECT * FROM public.platform_ledger ORDER BY created_at DESC LIMIT 5;
SELECT * FROM public.group_ledger ORDER BY created_at DESC LIMIT 5;
```

The `HAVING` query should return no rows (`SYNC_DELTA_20261022` made `groups.balance` `NOT NULL` and reconciled it with the ledger).

### Checking a tier without a local PostgreSQL client

`scripts/connect-supabase-docker.ps1` uses the `psql` that already ships inside the cached
Supabase database image (`public.ecr.aws/supabase/postgres:17.6.1.111`), so no PostgreSQL
install is needed. It splits the connection string into `PG*` environment variables so the
password never reaches an argv, mounts the checks as a file, and forces
`default_transaction_read_only`, so it cannot write:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\connect-supabase-docker.ps1
```

It prompts for the connection string with hidden input. `-DatabaseUrl` (or `$env:DATABASE_URL`)
and `-SslMode` exist for scripting; the local stack has no TLS, so point it at
`postgresql://postgres:postgres@host.docker.internal:45432/postgres` with `-SslMode disable`.

---

## ⚠️ 4. Rules for Seed Changes

1. **Ledger-first**: Do not insert a non-zero value directly into `groups.balance`. Use `public.increment_group_balance` / `pg_temp.seed_post_contribution`, which write `group_ledger` atomically. Deterministic local fixtures that must set a balance directly are acceptable only when paired with matching ledger rows.
2. **ID numbers**: Use valid 13-digit South African IDs so the KYC indicators render correctly.
3. **Scoping**: Seed members use `SEED-` member keys; groups use `LIGHT-`, `SEED-`, or `SIM-FLOWS-` prefixes so cleanup can delete exactly what the seed created.
4. **History**: Give members at least 3 months of activity; otherwise the actuarial engine flags them as high risk.
5. **Roles**: A group's `admin_user_id` must reference an existing `auth.users` row so group-admin login works.
6. **Regenerate**: After editing any module, run `supabase/scripts/generate_integrated_seed.ps1` to rebuild `SEED_ALL_DATA.sql`.
7. **Deltas**: Never encode a production fix as a seed. Production changes go into a dated delta.

## 5. Why the seed does not call `record_contribution_v2`

Every money RPC carries the `20261029` `[SEC_002]` guard, which authorises on `auth.role()` and
`auth.uid()`. A seed session — `supabase db reset`, the SQL editor, `psql -f` — has no JWT, so both
read NULL and the first call raises `CONTRIBUTION_ACCESS_DENIED`. Setting `request.jwt.claims` to
`service_role` would work and is precisely what not to do: it forges the claim the guards trust.

`pg_temp.seed_post_contribution` posts the same rows instead — the `contributions` row first, then
one `group_ledger` row (or one `platform_ledger` row for registration fees) keyed by that row's id,
through the unguarded `increment_group_balance`. That is the shape `internal_post_contribution`
writes, so `consolidate_group_ledger_v1` reconstructs seeded history exactly like live history. Add
new fixture money through that helper, not through the guarded RPCs.

---

## 🆘 Troubleshooting

- **Duplicate key on group name**: extend the cleanup block at the top of the seed to match the new naming pattern; the seed must be re-runnable.
- **Check constraint violation on `contributions.type`**: use `member_fee_ledger` for platform fees, not `member_fee`.
- **Missing storage buckets**: the seed creates `avatars` and `constitutions`; verify the local user has the storage admin role.
- **Balance invariant failure**: run `public.audit_and_fix_group_balance_v1(group_id, false)` to inspect the divergence before repairing.
