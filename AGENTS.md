# AGENTS.md — SanibonaniSave AI Coding Reference

**Last Updated**: August 31, 2026
**Version**: 8.0 (Refactored & Solidified)

---

## 🚀 Quick Start (Read First)

### Project Identity
- **Product**: South African Savings Groups Administration Platform (Android + Kotlin + Jetpack Compose)
- **Vision**: Distributed Cooperative Financial Infrastructure supporting burial societies, stokvels, ROSCAs, investment clubs, and emergency funds
- **Backend**: Supabase (PostgreSQL + PostgREST + Realtime + Storage + Auth)
- **Offline-First**: Room SQLite cache with sync-on-reconnect pattern

### Module Boundaries (4-module architecture)
```
:app         (UI, ViewModels, DI, Workers, Services, Analytics)
  ├── ViewModels organized by domain pillar (treasury, equity, etc.)
  └── depends on :domain + :data + :simulation (test source sets only)
:domain      (Models, Interfaces, UseCases, Services, Validation)
  ├── Specialized sub-packages: treasury, equity, credit, disbursement, dynamics
  └── depends on nothing (no Android framework imports, no Supabase SDK)
:data        (Room entities/DAOs, Supabase implementations, Repositories)
  ├── Repository implementations mirror domain pillars
  └── depends on :domain  ← the ONLY module allowed to speak the Supabase SDK
:simulation  (JVM-runnable financial flow simulator + domain seed factory)
  ├── depends on :domain; :app consumes it only from test/androidTest
  └── run via: gradlew :simulation:run  (mocks android.util.Log to work off-device)
```
See `settings.gradle.kts` and build files for enforcement.

> **Boundary rule**: the Supabase SDK (`io.github.jan.supabase.*`) and `android.*`
> framework imports belong in `:data` and `:app` only. `:domain` exposes
> SDK-free ports (e.g. `SupabaseRepository` returns the domain-owned
> `AuthSession` / `AuthSessionStatus`, not the SDK's `UserSession`) and `:data`
> performs the mapping at the boundary.

### Core Data Flow
1. **Local First**: Repository emits Room cache data immediately.
2. **Sync Network**: Simultaneously fetches from Supabase in background (Pillar-Parallel).
3. **Offline Outbox**: Failed writes are queued in `pending_operations`.
4. **Auto-Recovery**: `SyncRepository` processes the outbox automatically.
5. **Conflict Resolution**: `observeAndSync` pattern with exponential backoff.

---

## 🏛️ Core Engineering Principles

### 1. High-Precision Financial Ledger (CRITICAL)
**ERADICATE `Double` for financial math. Use the `Money` (BigDecimal) domain model.**

```kotlin
// ❌ WRONG
val ratio = balance / target // Floating point drift
val total = list.sumOf { it.amount } // Double sum unsafe

// ✅ CORRECT
val ratio = balance.ratio(target) // Safe Double return for ratios
val total = list.fold(Money.ZERO) { acc, i -> acc + i.amount }
val plain = amount.toPlainString() // Safe serialization
```

**Required financial entities**:
- `Money` — Domain value object wrapping `BigDecimal` with 2-decimal scale.
- `LedgerEntry` — immutable transaction record.
- `ConsolidationReport` — ledger reconstruction result.

**Best Practices**:
- **Atomic updates**: Always use RPCs like `increment_group_balance` which require `p_member_id`.
- **Ratio/Division**: Use `money.ratio(other)` for percentages and `money.dividedBy(Int|Double)` for allocation.
- **Serialization**: `MoneySerializer` handles standard conversion; use `.toPlainString()` for DB/API.

### 2. UI Performance: Parallel Warmup Pattern
**ViewModels must fetch independent data streams concurrently to minimize latency.**

```kotlin
// Implementation in ViewModels
launchJob("load_feature_data") {
    coroutineScope {
        val data1 = async { repo.fetch1() }
        val data2 = async { repo.fetch2() }
        updateState { it.copy(d1 = data1.await(), d2 = data2.await()) }
    }
}
```

### 3. ML-Ready Behavior Tracking
**System is optimized for finetuning Azure AI Foundry models.**

- **Ground Truth**: `LiveSimulationRunner` injects labelled fraud patterns.
- **Dataset Export**: `BehaviorTrackingRepository.generateFinetuningDataset` exports JSONL in SFT/DPO formats.
- **Tunable Scoring**: Weights are externalized in `ScoringWeights` and `FraudWeights`.

---

## 📊 Group Type Specific Business Logic

### 1. Burial Society (Funeral Insurance)
- **Actuarial**: Uses `BurialSocietyStrategy` with `SA_WORKING_AGE_MORT_PER_1000`.
- **Money Migration**: All solvency and reserve formulas now use `Money.ratio()`.

### 2. ROSCA (Rotating Savings)
- **Payouts**: `ExecuteRoscaPayoutUseCase` derives pots from ledger-verified contributions.
- **Automation**: Automatic deduction of outstanding loans from the cycle pot.

### 3. Investment Club
- **Valuation**: NAV is strictly `group.balance`. Unit price is `balance.ratio(totalContributions)`.
- **Precision**: Fractional ownership calculated via `Money.ratio()`.

---

## 💻 Tech Stack & Versions

**Authoritative source: `gradle/libs.versions.toml`.** Verify there before trusting
any version below.

 Component | Version | Notes |
-----------|---------|-------|
 **Language** | Kotlin 2.4.20 | JVM Toolchain 17 |
 **AGP** | 9.4.1 | Required for Hilt 2.60.1 |
 **Hilt** | 2.60.1 | Full project-wide DI |
 **KSP** | 2.3.12 | Annotation processing |
 **Room** | 2.8.5 | Schema version 81 |
 **Compose** | 2026.09.00 (BOM) | Material 3 + Adaptive |
 **Supabase** | 3.8.0 | SDK used in `:data`/`:app` only |
 **Ktor** | 3.6.0 | HTTP client for Supabase |

---

## 📁 Repository Pillars

1. **Treasury**: `LedgerRepository`, `ReconciliationRepository` (Derive balance from Ledger).
2. **Equity**: `ContributionRepository` (Investment Principal tracking).
3. **Credit**: `LoanRepository` (Lifecycle management).
4. **Disbursement**: `PayoutRepository`, `BeneficiaryClaimRepository`.
5. **Dynamics**: `ActuarialRepository` (Viability), `BehaviorTrackingRepository` (ML/Fraud).

---

## ✅ Safe-Change Checklist

- [ ] **Financials**: Ensure no `Double` is used for currency storage or math.
- [ ] **State**: Use `launchJob(key)` to prevent overlapping async operations.
- [ ] **Domain**: If adding models, check `ActuarialModels.kt` or `AuditModels.kt` first to avoid redeclarations.
- [ ] **Build**: Run `:domain:assembleDebug` after any model change.
