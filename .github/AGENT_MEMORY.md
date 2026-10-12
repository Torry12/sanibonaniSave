# Agent Memory - SanibonaniSave

## Last Updated
2026-10-05

## Recent Decisions & Architectural Refactorings
- **Burial Society Policy Settings**: Group admins can configure `burialClaimAmount`, `minMembersForClaims`, and `minReservePercentage` in `GroupSettings`. `ValidateBurialClaimEligibilityUseCase` enforces member tenure, active membership thresholds, and reserve adequacy.
- **Overdue Shortfall & Platform Fee Precision**: `PaymentCalculator` computes net overdue shortfalls and falls back to `member.totalPaid` when contribution lists are unpopulated. `PlatformFeeService` uses `config.monthlyMemberFee`.
- **RPC Serialization Safety**: `SupabaseContributionDataSourceImpl` and `SupabasePayoutDataSourceImpl` use `.decodeAs<JsonElement>()` with pattern matching for `JsonObject` and `JsonArray` PostgREST responses.
- **Platform Analytics & Yield**: `PlatformRepositoryImpl` calculates actual platform yield from active member subscriptions and registration fees.
- **Room Migration Safety**: `LocalDatabaseProvider` enables `.fallbackToDestructiveMigration()`, and `Migrations.kt` (v81) alters `groups` table safely.
- **Master Seed & Database Sync**: Consolidated `SEED_ALL_DATA.sql` seeds 8 core group types, 12 months of ledger history, and 15 valid simulation burial claims across local and online Supabase databases.

## Test Coverage
- **308 / 308 Passing Unit & Simulation Tests** across all modules (`:app`, `:data`, `:domain`, `:simulation`).

## Documentation/SQL Sync Rules
When business rules change:
1. Update domain/use-case and ViewModel callers
2. Update `GroupModels.kt`, `GroupEntities.kt`, and `ValidationUtils.kt`
3. Update SQL baseline/migration scripts in `supabase/` (`01_DATABASE_SCHEMA.sql`, `GROUND_ZERO.sql`, `SEED_ALL_DATA.sql`)
4. Update docs (`README.md`, `TECHNICAL_STATE_LATEST.md`, `QA_LOGIN_CREDENTIALS.md`)
