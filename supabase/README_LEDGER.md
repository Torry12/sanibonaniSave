Ledger-first policy

This repository enforces an immutable ledger-first policy for all financial changes.

What changed:
- `increment_group_balance` RPCs now write a `public.group_ledger` entry in the same transaction as the balance update. Files updated:
  - `supabase/migrations/20260515000100_atomic_balance_updates.sql`
  - `supabase/31_ATOMIC_BALANCE_UPDATES.sql`
  - `supabase/migrations/20260514000100_initial_schema.sql`

Why:
- Ensures all balance changes are auditable and reversible via ledger entries.
- Keeps `groups.balance` as a cache for quick reads and derives authoritative history from `group_ledger`.

Notes and next steps:
- Seeds and legacy scripts may still set `groups.balance` directly for deterministic test data; this is acceptable for tests but production RPCs must always create ledger entries.
- Consider migrating existing groups to rebuild `groups.balance` from `group_ledger` to guarantee consistency.
- When adding new RPCs that affect balances, follow the pattern:
  1. Update `groups.balance` with `SET balance = balance +/- p_amount` and RETURNING balance INTO v_new_balance.
  2. INSERT INTO `group_ledger` (group_id, transaction_id, amount, balance_after, description, category) VALUES (...)
  3. RETURN v_new_balance

For questions contact the team maintaining the supabase schema.
