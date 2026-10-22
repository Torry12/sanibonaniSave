# 📜 Project Coding Standards & Agent Rules

This document defines the strict engineering standards for the SanibonaniSave project. All developers and AI agents must adhere to these patterns to maintain architectural integrity.

---

## 🏛 1. Architectural Integrity

### Layered Responsibility
- **`:app`**: UI (Compose), ViewModels, DI Wiring, Services, Workers. *The only other module besides `:data` allowed to touch Android framework and Supabase SDK types.*
- **`:domain`**: Pure business logic. Models, Repository Interfaces, UseCases, Services. Intends to be **free of the Supabase SDK and any backend dependency**.
- **`:data`**: Implementation detail. Room DB, Supabase clients, Repository implementations, Mappers. *The only module allowed to speak the Supabase SDK directly.*
- **`:simulation`**: JVM-runnable financial-flow simulator and domain seed factory; consumed by `:app` from test source sets only.

> **Current boundary state**: `:domain` no longer imports the Supabase SDK —
> `SupabaseRepository` exposes the domain-owned `AuthSession` / `AuthSessionStatus`,
> and `:data` maps the SDK objects at the boundary. `:domain` still imports
> `android.*` (mostly `@Parcelize` on models plus `android.util.Log` in
> `AppLogger`), so it remains an Android library for now; the long-term goal is a
> pure-JVM module. Do not add new `android.*` or `io.github.jan.supabase.*`
> imports to `:domain`.

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
- **Version**: Current version is **81**.
- **Migrations**: Every schema change requires a migration in `Migrations.kt`.
- **Safety**: Use `db.safeExec()` for all `ALTER TABLE` operations.

### Supabase (Backend)
- **RPCs**: Critical financial transactions MUST use Postgres RPCs (e.g., `record_contribution_v2`) to ensure atomicity.
- **RLS**: Row-Level Security must be verified when adding new tables.
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
