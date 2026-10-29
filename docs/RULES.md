# 📜 Project Coding Standards & Agent Rules

This document defines the strict engineering standards for the SanibonaniSave project. All developers and AI agents must adhere to these patterns to maintain architectural integrity.

---

## 🏛 1. Architectural Integrity

### Layered Responsibility
- **`:app`**: UI (Compose), ViewModels, DI Wiring, Services, Workers. *The only other module besides `:data` allowed to touch Android framework and Supabase SDK types.*
- **`:domain`**: Pure business logic. Models, Repository Interfaces, UseCases, Services. Intends to be **free of the Supabase SDK and any backend dependency**.
- **`:data`**: Implementation detail. Room DB, Supabase clients, Repository implementations, Mappers. *The only module allowed to speak the Supabase SDK directly.*
- **`:simulation`**: JVM-runnable financial-flow simulator and domain seed factory; consumed by `:app` from test source sets only.

> **Current boundary state**: `:domain` imports neither the Supabase SDK nor
> `android.*`. `SupabaseRepository` exposes the domain-owned `AuthSession` /
> `AuthSessionStatus` and `:data` maps the SDK objects at the boundary; models are
> `@Serializable` only (no `@Parcelize`), so navigation passes ids. Logging leaves
> the module through the `AppLogSink` port, whose `android.util.Log` implementation
> lives in `:app`. The module is still built as `com.android.library` for
> `BuildConfig.PLATFORM_ADMIN_EMAIL` and the instrumentation runner — that is a
> packaging detail, not an import leak. Do not add new `android.*` or
> `io.github.jan.supabase.*` imports to `:domain`; `DomainModuleBoundaryTest` fails
> if you do.

### Data Flow (Offline-First & Outbox)
- Use the `observeAndSync` pattern in repositories for reads.
- Use the **Offline Outbox** pattern for writes (contributions, loans, payouts).
- **Source of Truth**: The local Room database.
- **Sync Logic**: Pillar-Parallel realtime subscriptions for low-latency updates.

---

## 💻 2. Kotlin & Compose Standards

### State Management
- **StateFlow Only**: Use `StateFlow` for UI state in ViewModels.
- **UiState Pattern**: ViewModels must expose a single `val state: StateFlow<UiState>`.
- **Updates**: Use `_state.update { it.copy(...) }` for thread-safe state mutations.
- **Lifecycle**: Collect flows in Composables using `collectAsStateWithLifecycle()`.

### Jetpack Compose
- **Stateless Composables**: Prefer passing state and lambdas over ViewModels directly where possible.
- **Theme**: Use `MaterialTheme.colorScheme`, `Navy`, `SuccessGreen`, and `ErrorRed` from the project theme.
- **Performance**: Use `remember` and `derivedStateOf` to minimize recompositions.

---

## 🗄 3. Database & Backend

### Room (Local DB)
- **Version**: Current version is **82**.
- **Migrations**: Every schema change requires a migration in `Migrations.kt` (register it in `ALL_MIGRATIONS`; `fallbackToDestructiveMigration` is debug-only, so release builds must never rely on it).
- **Safety**: Use `db.safeExec()` for all `ALTER TABLE` operations.

### Supabase (Backend)
- **RPCs**: Critical financial transactions MUST use Postgres RPCs (e.g., `record_contribution_v2`) to ensure atomicity. `settle_expired_loan_v2` is the only loan-settlement entry point; `record_loan_settlement_extraction_v1` is revoked on every role.
- **Validate Before Write**: an RPC rejects a bad amount or an unknown `status`/`type` literal with `[FIN_003]` before touching a row. Do the same check in Kotlin so the outbox never retries a permanently invalid payload.
- **RLS**: Row-Level Security must be verified when adding new tables. A `SECURITY DEFINER` RPC bypasses RLS inside its body, so it needs its own identity check (`[SEC_002]`) **and** a `REVOKE ... FROM PUBLIC, anon` in `03_SECURITY_AND_RLS.sql` — revoking only `anon` leaves the `PUBLIC` entry open.
- **Tier Changes**: go through a new dated `supabase/online/SYNC_DELTA_<date>.sql` with a read-only pre-flight; never edit an applied delta. Re-measure with `supabase/maintenance/SCHEMA_PARITY_DIGEST.sql`.
- **Serialization**: Use `@SerialName("snake_case")` for all network models.

---

## 💰 4. Financial Engineering (The Ledger)

### Immutable Ledger
- **No Direct Mutations**: Never update a group's `balance` or member's `total_paid` directly in code. `groups.balance` is a `NOT NULL` cache of `group_ledger`, and SQL spend guards must treat a missing balance as zero and fail closed.
- **Ledger Entries**: Every balance change must be accompanied by a `LedgerEntry`.
- **Precision & Serialization**: Use the `Money` (BigDecimal) class for all currency values. Floating-point math is strictly forbidden in financial paths. Use `amount.toPlainString()` when passing monetary values to Supabase RPCs.

---

## 🧪 5. Testing & Quality Assurance

### Testing Levels
- **Unit Tests**: Mandatory for all UseCases and ViewModels.
- **Integration Tests**: Required for Repositories (using `MockEngine` for Supabase).
- **Migration Tests**: Required for every new Room migration.

### Debugging
- Use `AppLogger` for structured logging.
- Run `./scripts/runtime-signal-unified.ps1` to verify environment health.

---

## 🆘 6. Common Pitfalls (Anti-Patterns)
1. ❌ Referencing `Context` in a ViewModel.
2. ❌ Using `LiveData` (legacy).
3. ❌ Hardcoding colors instead of using the project's color palette.
4. ❌ Performing network calls directly in ViewModels (use Repositories).
5. ❌ Duplicate UI logic across screens (reuse the shared component library in `ui/components/`).
6. ❌ Adding `io.github.jan.supabase.*` or `android.*` imports to `:domain` — the port must stay SDK-free and `:data` performs the mapping.
7. ❌ Guessing at a version — read `gradle/libs.versions.toml` instead of trusting a doc.
8. ❌ Restating an input rule outside `InputValidator` — `ValidationUtils` forwards to it, and a second regex means registration and KYC can disagree.
9. ❌ Editing a `SYNC_DELTA_*` file that a tier has already run — cut a new dated delta and bump the expected fingerprints.
