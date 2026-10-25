# Tasks — Maximize Seed Data Integrity & Optimal Design

- `[x]` Expand `DomainSeedFactory.kt` with `Loan`, `PayoutRequest`, and `HybridPotAllocation` entity creation helpers
- `[x]` Update `LiveSimulationRunner.kt` to leverage `DomainSeedFactory` for multi-group simulation loops
- `[x]` Update `MASTER_SEED_DATA.sql` and `31_REAL_LIFE_INTEGRITY_SEEDS.sql` for 100% active users, zero ledger drift, and 12-month histories
- `[x]` Reset database and execute forensic balance audit
- `[x]` Run full unit test suite across all project modules
