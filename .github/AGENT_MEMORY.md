# Agent Memory - SanibonaniSave

## Last Updated
2026-10-22

## Recent Decisions & Architectural Refactorings
- **Burial Society Policy Settings**: Group admins can configure `burialClaimAmount`, `minMembersForClaims`, and `minReservePercentage` in `GroupSettings`. `ValidateBurialClaimEligibilityUseCase` enforces member tenure, active membership thresholds, and reserve adequacy.
- **Overdue Shortfall & Platform Fee Precision**: `PaymentCalculator` computes net overdue shortfalls and falls back to `member.totalPaid` when contribution lists are unpopulated. `PlatformFeeService` uses `config.monthlyMemberFee`.
- **RPC Serialization Safety**: `SupabaseContributionDataSourceImpl` and `SupabasePayoutDataSourceImpl` use `.decodeAs<JsonElement>()` with pattern matching for `JsonObject` and `JsonArray` PostgREST responses.
- **Platform Analytics & Yield**: `PlatformRepositoryImpl` calculates actual platform yield from active member subscriptions and registration fees.
- **Room Migration Safety**: `LocalDatabaseProvider` enables `.fallbackToDestructiveMigration()`, and `Migrations.kt` (v81) alters `groups` table safely.
- **Master Seed & Database Sync**: Generated `SEED_ALL_DATA.sql` seeds 8 core group types, 12 months of ledger history, and 15 valid simulation burial claims. Seeds are **local development only** and are not part of the online rebuild.
- **SQL Delta & Balance Invariants (2026-10-22)**: `online/SYNC_DELTA_20261022.sql` repaired NULL cached balances from `group_ledger`, made `groups.balance` `NUMERIC(12,2) NOT NULL DEFAULT 0`, and replaced both spend guards with `COALESCE(v_group.balance, 0)` checks that raise on insufficiency. `20261014`, `20261021`, and `20261022` are applied on both tiers; add a new dated delta with bumped fingerprints instead of editing an applied one.

## Test Coverage
- **308 / 308 Passing Unit & Simulation Tests** across all modules (`:app`, `:data`, `:domain`, `:simulation`).

## Documentation/SQL Sync Rules
When business rules change:
1. Update domain/use-case and ViewModel callers
2. Update `GroupModels.kt`, `GroupEntities.kt`, and `ValidationUtils.kt`
3. Update the canonical SQL sources in `supabase/` (`01_DATABASE_SCHEMA.sql`, `02_FUNCTIONS_AND_TRIGGERS.sql`, `03_SECURITY_AND_RLS.sql`), then regenerate the four artifacts in README order
4. For an existing tier, add `supabase/online/SYNC_DELTA_<date>.sql` with a read-only pre-flight and expected fingerprints; never rewrite an applied delta
5. Update docs (`README.md`, `docs/TECHNICAL_STATE_LATEST.md`, `supabase/SQL_DOCUMENTATION.md`, `QA_LOGIN_CREDENTIALS.md`)
