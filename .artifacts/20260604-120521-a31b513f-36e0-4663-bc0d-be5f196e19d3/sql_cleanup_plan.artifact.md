# SQL Script Cleanup & Canonicalization Plan

This plan aims to establish a single source of truth for the Supabase database schema and logic while removing redundant and outdated scripts.

## Canonical Structure

We will maintain three primary "Master" files in the `supabase/` directory:
1.  **`SUPABASE_MASTER_SCHEMA.sql`**: Full table, index, and constraint definitions.
2.  **`SUPABASE_MASTER_FUNCTIONS.sql`**: All RPCs, triggers, and actuarial logic.
3.  **`02_SECURITY_AND_RLS.sql`**: Row-level security policies.

The `rebuild_kit_v4/` directory will serve as a modular setup tool that references these masters.

## Proposed Changes

### 1. Accuracy Hardening (Logic Sync)

-   **`SUPABASE_MASTER_FUNCTIONS.sql`**:
    -   Update `record_contribution_v1` to return `JSONB` and exclude `member_fee` from member stats (matches recent Kotlin fixes).
    -   Include `record_loan_repayment_v1` (currently missing from master).
    -   Integrate and harden `calculate_group_health_score`:
        -   Filter by `type = 'contribution'` for sustainable income calculations.
        -   Add `search_path = public` for security.

### 2. Redundancy Removal (Cleanup)

The following files and directories are identified as redundant/outdated and will be **deleted**:

-   **Project Root**: `01_SCHEMA.sql`, `02_SECURITY_AND_RLS.sql`, `03_SEED.sql`, `schema.sql`, `rls.sql`, `engineering.sql`, `seed_safe.sql`, `consolidated_full.sql`, `CONSOLIDATED_INLINE_SUPABASE_SAFE.sql`, `consolidated_supabase_safe.sql`.
-   **`docs/`**: `MASTER_REBUILD_AND_SEED.sql`, `rebuild_and_seed.sql`, `REBUILD_DATABASE.sql`, `SCHEMA_ONLY.sql`, `SEED_ONLY.sql`.
-   **`consolidated/`**: Entire directory.
-   **`schema/`**: Entire directory (table definitions are in Master Schema).
-   **`seed/`**: Entire directory (seed data is in `supabase/seeds`).
-   **`supabase/db-scripts/`**: Entire directory.
-   **`supabase/` snapshots**: `CONSOLIDATED_APPLY.sql`, `CONSOLIDATED_SCHEMA_ONLY.sql`, `CONSOLIDATED_SYNCED_SCHEMA.sql`, `SCHEMA_FULL_RESET.sql` (if redundant to `rebuild_kit_v4/00_SCHEMA_RESET.sql`).

### 3. Setup Orchestration

-   Update `supabase/CONSOLIDATED_FULL.sql` to be the primary driver for a complete database reset and setup, using the canonical master files.

## Verification Plan

1.  **Static Analysis**: Verify all `\i` (include) paths in drivers (`CONSOLIDATED_FULL.sql`, `rebuild_kit_v4/07_MASTER_REBUILD.sql`) point to existing files.
2.  **Schema Integrity**: Cross-check that all tables used in `SUPABASE_MASTER_FUNCTIONS.sql` are defined in `SUPABASE_MASTER_SCHEMA.sql`.
3.  **Logic Check**: Verify `record_contribution_v1` return type matches the expected `RecordContributionResult` in Kotlin code.
