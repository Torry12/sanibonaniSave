# Debug logic scenarios

The former `DBG-*` scenario pack (`17_SEED_DEBUG_LOGIC_SCENARIOS.sql` and its verify/cleanup/orchestrator companions) is not part of this repository.

## Current debug paths

- `supabase/SEED_ALL_DATA.sql` — integrated local seed with all group types, mixed statuses, 12 months of ledger history, and claim/escalation fixtures.
- `supabase/31_REAL_LIFE_INTEGRITY_SEEDS.sql` — deliberate forensic and fraud fixtures, scoped to `LIGHT-Stokvel`. Do not "repair" the intended drift.
- `supabase/scripts/LIVE_SIMULATION_DEBUG.sql` — live backend simulation and integrity debug harness.
- `supabase/scripts/TOTAL_INTEGRITY_AUDIT.sql` — full-system integrity audit report.
- The `:simulation` module — `gradlew :simulation:run` for offline financial-flow scenarios.

## Principles

- Debug data must be scoped by a deterministic prefix (`SEED-`, `LIGHT-`, `SIM-FLOWS-`, `DBG-`) so cleanup is safe.
- Never load debug fixtures into the cloud tier; production changes go through dated deltas.
- When a scenario exposes a bug, fix the canonical source or RPC, regenerate artifacts, and record the expected fingerprints in a new delta.
