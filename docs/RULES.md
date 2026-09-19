# 📜 Project Coding Standards & Agent Rules

This document defines the strict engineering standards for the SanibonaniSave project. All developers and AI agents must adhere to these patterns to maintain architectural integrity.

---

## 🏛 1. Architectural Integrity

### Layered Responsibility
- **`:app`**: UI (Compose), ViewModels, DI Wiring, Services, Workers.
- **`:domain`**: Pure business logic. Models, Repository Interfaces, UseCases, Services. **Zero dependencies on other modules or Android frameworks.**
- **`:data`**: Implementation detail. Room DB, Supabase clients, Repository implementations, Mappers.

### Data Flow (Offline-First)
- Use the `observeAndSync` pattern in repositories.
- **Source of Truth**: The local Room database.
- **Sync Logic**: Repositories must emit local data immediately, then fetch from network and update the local cache.

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
- **Version**: Current version is **41**.
- **Migrations**: Every schema change requires a migration in `Migrations.kt`.
- **Safety**: Use `db.safeExec()` for all `ALTER TABLE` operations.

### Supabase (Backend)
- **RPCs**: Critical financial transactions MUST use Postgres RPCs (e.g., `record_contribution_v1`) to ensure atomicity.
- **RLS**: Row-Level Security must be verified when adding new tables.
- **Serialization**: Use `@SerialName("snake_case")` for all network models.

---

## 💰 4. Financial Engineering (The Ledger)

### Immutable Ledger
- **No Direct Mutations**: Never update a group's `balance` or member's `total_paid` directly in code. 
- **Ledger Entries**: Every balance change must be accompanied by a `LedgerEntry`.
- **Precision**: Use `BigDecimal` (via `toMoneyBigDecimal()`) for all currency calculations to avoid floating-point errors.

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
5. ❌ Duplicate UI logic across screens (use `SharedComponents.kt`).
