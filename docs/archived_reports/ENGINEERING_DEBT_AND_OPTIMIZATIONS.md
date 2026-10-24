# Engineering Debt & Performance Optimizations

This document tracks identified architectural weaknesses, their business impacts, and the optimizations implemented to ensure the long-term scalability of SanibonaniSave.

## 1. Atomic Financial Operations (Transaction Integrity)
**Weakness**: Business logic was split between the Android app and the backend. For example, "Approving a Loan" updated a status in one network call and recorded the disbursement in another.
*   **Risk**: Network failure between calls results in a "Ghost Loan" where the status is active but no money was deducted from the group ledger.
*   **Optimization (Implemented)**: Moved multi-step financial flows into single PostgreSQL RPCs (e.g., `approve_and_disburse_loan_v1`). Logic is now "all or nothing."

## 2. N+1 Analytics Problem (Performance)
**Weakness**: Platform-wide stats were calculated by fetching every group and member into the phone's memory and looping over them.
*   **Risk**: As the platform grows to 1,000+ groups, the Landing Screen would freeze or crash the app due to OOM (Out of Memory) errors.
*   **Optimization (Implemented)**: Created `public.platform_summary_stats` view. The app now fetches a single pre-calculated row from the server.

## 3. RLS Query Overhead
**Weakness**: Row Level Security (RLS) helpers used `plpgsql` (procedural) blocks which are difficult for the Postgres query planner to optimize.
*   **Risk**: Loading a list of 50 groups triggered 50 separate permission subqueries, leading to high latency.
*   **Optimization (Implemented)**: Refactored RLS helpers (`check_is_member`, `is_group_admin`) to pure SQL and marked them as `STABLE`. This allows Postgres to cache permission results within a single request.

## 4. Financial Precision (Floating Point Math)
**Weakness**: Money is represented as `Double` in Kotlin and `NUMERIC` in SQL.
*   **Risk**: `Double` calculations (like interest) can lead to rounding errors (e.g., R10.00 becoming R10.00000004). This destroys member trust.
*   **Recommendation**: Future refactor should migrate Android models to `BigDecimal` and ensure all interest calculations happen exclusively on the database side using `NUMERIC(12,2)`.

## 5. Metadata Fragility (Column Mapping)
**Weakness**: Repositories use hardcoded string lists (`GROUP_COLUMNS_SAFE`) to select data.
*   **Risk**: Adding a column to the database requires a manual update to the Kotlin code. Forgetting this results in features "silently failing" as data returns `null`.
*   **Mitigation**: Implement a Schema Registry or use a code-generation tool (like `supabase-kt` generated types) to keep the frontend and backend strictly typed.

## 6. Sync Latency (Offline-First Integrity)
**Weakness**: The app uses an "Optimistic UI" where Room updates immediately, but Supabase might reject the change later.
*   **Risk**: An admin records a contribution, it appears successful, but the server rejects it (e.g., RLS violation). The admin thinks it's paid, but the ledger is empty.
*   **Recommendation**: Add a `sync_status` flag to local entities (`PENDING`, `SYNCED`, `FAILED`) to provide visual feedback to users when data is not yet server-confirmed.
