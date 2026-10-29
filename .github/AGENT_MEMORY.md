# Agent Memory - SanibonaniSave

## Last Updated
2026-10-28

## Recent Decisions & Architectural Refactorings
- **Module Boundary Held Clean**: `:domain` compiles with no `io.github.jan.supabase.*` and no `android.*` imports. Models are `@Serializable` only (no `@Parcelize`) and navigation passes ids; logging crosses through the `AppLogSink` port implemented in `:app`. `DomainModuleBoundaryTest` is the guard.
- **Validation Ownership**: `InputValidator` owns every input rule (SA ID Luhn + real date of birth, phone, bank account, branch code, card/expiry/CVV, name, email); `ValidationUtils` is the convenience surface callers use and delegates to it. A new rule goes in `InputValidator`, never as a second regex.
- **Burial Society Policy Settings**: Group admins can configure `burialClaimAmount`, `minMembersForClaims`, and `minReservePercentage` in `GroupSettings`. `ValidateBurialClaimEligibilityUseCase` enforces member tenure, active membership thresholds, and reserve adequacy.
- **Overdue Shortfall & Platform Fee Precision**: `PaymentCalculator` computes net overdue shortfalls and falls back to `member.totalPaid` when contribution lists are unpopulated. `PlatformFeeService` uses `config.monthlyMemberFee`.
- **RPC Serialization Safety**: `SupabaseContributionDataSourceImpl` and `SupabasePayoutDataSourceImpl` use `.decodeAs<JsonElement>()` with pattern matching for `JsonObject` and `JsonArray` PostgREST responses.
- **Platform Analytics & Yield**: `PlatformRepositoryImpl` calculates actual platform yield from active member subscriptions and registration fees.
- **Room Migration Safety**: `LocalDatabaseProvider` enables `.fallbackToDestructiveMigration(dropAllTables = true)` behind a debug flag, and the schema is at **v82** (`MIGRATION_81_82` in `MigrationsV81ToV82.kt`, registered in `ALL_MIGRATIONS`).
- **Audit RPC Guards (2026-10-27)**: the four report/repair RPCs raise `[SEC_002]` unless the caller is a platform admin, that group's admin, or the member themselves; the platform report is single-pass; `EXECUTE` is withdrawn from `PUBLIC` and `anon` in `03_SECURITY_AND_RLS.sql`.
- **Loan Settlement Closure (2026-10-28)**: `settle_expired_loan_v2(UUID)` is the only settlement entry point (authenticated/service_role only); `record_loan_settlement_extraction_v1` is revoked from every role because its caller-supplied member/group/amount were never validated as one atomic write. Money RPCs now reject bad amounts and unknown status/type literals with `[FIN_003]` before writing.
- **Master Seed & Database Sync**: Generated `SEED_ALL_DATA.sql` seeds 8 core group types, 12 months of ledger history, and 15 valid simulation burial claims. Seeds are **local development only** and are not part of the online rebuild.
- **SQL Delta & Balance Invariants**: `groups.balance` is `NUMERIC(12,2) NOT NULL DEFAULT 0` and ledger-derived; spend guards use `COALESCE(v_group.balance, 0)` and raise on insufficiency. `20261014` through `20261028` are applied on **Local**; **Cloud still owes `20261027` and `20261028`**. Add a new dated delta with bumped fingerprints instead of editing an applied one.
- **Open Items**: `close_poll_v1`, `pay_burial_claim_v1` and `record_disbursement_v1` are now declared in `02_FUNCTIONS_AND_TRIGGERS.sql` and cut as `SYNC_DELTA_20261029B`, so the gap that remains is tier state — until `20261027` → `20261028` → `20261029` → `20261029B` are applied those three flows still fail with `PGRST202` on both tiers; the four money RPCs above are still `anon`-executable until `20261029` lands; the `settle_expired_loan_v2` HTTP route is unverified because probing it as `anon` crashes Local Postgres (signal 11); `trigger-backup` and `compute-platform-metrics` exist under `supabase/functions/`, pass `deno check`, and were deployed to the cloud project on 2026-10-29 but have not yet been invoked from an installed build; `PlatformRepositoryImpl` checks the invoke status because `functions.invoke` returns the raw response and never throws on 4xx/5xx.
- **Named RPC wire contract**: the RPC name and its JSON parameter keys are the whole client↔database contract, because PostgREST binds named arguments and a missing or renamed key fails before any table is read. `DisbursementRpcParamsTest`, `ClosePollRpcParamsTest` and `BeneficiaryClaimRepositoryImplTest` pin the names and keys for these three; new RPC calls follow the same shape (`SupabasePayoutDataSourceImpl.buildRoscaPayoutRpcParams` is the pattern).

## Test Coverage
- **890 test cases, 0 failures, 0 errors, 23 skipped → 867 passing** (`:domain` 357, `:data` 249, `:app` 281 + 23 skipped, `:simulation` 3) as measured from the JUnit XML on 2026-10-29.

## Documentation/SQL Sync Rules
When business rules change:
1. Update domain/use-case and ViewModel callers
2. Update `GroupModels.kt`, `GroupEntities.kt`, and `InputValidator.kt` (`ValidationUtils.kt` only forwards)
3. Update the canonical SQL sources in `supabase/` (`01_DATABASE_SCHEMA.sql`, `02_FUNCTIONS_AND_TRIGGERS.sql`, `03_SECURITY_AND_RLS.sql`), then regenerate the four artifacts in README order
4. For an existing tier, add `supabase/online/SYNC_DELTA_<date>.sql` with a read-only pre-flight and expected fingerprints; never rewrite an applied delta
5. Re-measure parity with `supabase/maintenance/SCHEMA_PARITY_DIGEST.sql` and update its expected baseline when the masters move
6. Update docs (`README.md`, `docs/TECHNICAL_STATE_LATEST.md`, `supabase/SQL_DOCUMENTATION.md`, `QA_LOGIN_CREDENTIALS.md`)
