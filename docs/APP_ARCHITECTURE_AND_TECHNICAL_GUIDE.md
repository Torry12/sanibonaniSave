# 🏗️ APP_ARCHITECTURE_AND_TECHNICAL_GUIDE.md

## 🏗️ Architecture Overview

### Layered MVVM + Clean Architecture (SOLID Principles)
The project follows a strict layered architecture to ensure separation of concerns and maintainability.

```
UI Layer (Compose Screens + ViewModels)
    ↓ Orchestration (Use Case Layer)
    ↓ Repository Layer (Domain Interfaces)
├── Use Case Layer (SRP compliance)
│   ├── RegisterMemberUseCase (Member Registration + Group Count)
│   ├── CreateGroupUseCase (Admin-as-Member + Group Init)
│   ├── UpdateMemberStatusUseCase (Status Transitions)
│   ├── CalculateViabilityUseCase (Actuarial Logic)
│   ├── RequestPayoutUseCase (Group Fund Disbursement)
│   ├── ProcessPayoutUseCase (Notification-aware status updates)
│   ├── SendNotificationUseCase (Multi-channel Messaging)
│   ├── GetGroupBusinessInsightsUseCase (Specialized group-type logic)
│   ├── CalculateRoscaRotationUseCase (Automated ROSCA turn-taking)
│   ├── CalculateInvestmentClubValuationUseCase (NAV and Unit tracking)
│   └── CalculateStokvelPayoutsUseCase (Future payout projections)
├── Repository Layer (10 segregated interfaces)
│   ├── BaseRepository (Standardized offline-first + exponential backoff)
│   ├── Supabase (remote) — PostgREST, Realtime, Storage, Auth
│   ├── PayoutRepository (Fund disbursement tracking)
│   └── Room (local cache) — fallback sync via observeAndSync
└── Domain Models (@Parcelize, @Serializable)
```

---

## 📂 Project Structure

```
SanibonaniSave_Full/
├── app/ (UI, ViewModels, DI, Services, Workers, Analytics)
│   └── src/main/java/com/sanibonani/save/
│       ├── di/                 ← Hilt Modules
│       ├── ui/
│       │   ├── components/     ← Shared component library (single home)
│       │   ├── navigation/     ← NavHost + route definitions
│       │   ├── theme/          ← Colors, typography, shapes
│       │   ├── utils/          ← Formatting, transformations
│       │   └── AppBootloader.kt ← Splash / session bootstrap
│       ├── features/           ← Feature packages (auth, admin, member, group,
│       │                         financial, landing, audit, kyc, common)
│       │   └── <feature>/{ui/,ui/components/,ui/tabs/,presentation/}
│       ├── service/            ← Background + cache services
│       └── worker/             ← WorkManager Jobs
├── data/ (Implementation — the only module that speaks the Supabase SDK)
│   └── src/main/java/com/sanibonani/save/data/
│       ├── local/              ← Room DB, DAOs, Entities, Migrations, LocalDatabaseProvider
│       ├── remote/             ← Supabase, Retrofit (Geoapify, YoCo), SupabaseManager
│       ├── datasource/         ← Local/Remote port interfaces
│       ├── repository/         ← Repository implementations
│       └── service/            ← Service implementations
├── domain/ (Core Logic & Interfaces — SDK-free)
│   └── src/main/java/com/sanibonani/save/domain/
│       ├── model/              ← Domain Models (@Serializable; many @Parcelize)
│       ├── repository/         ← Repository Interfaces
│       ├── usecase/            ← Business Logic Orchestrators
│       ├── service/            ← Domain services
│       ├── validation/         ← Validation Utils
│       ├── utils/              ← Money math, keys, mappers
│       └── logging/            ← AppLogger
├── simulation/ (JVM financial-flow simulator + seed factory)
├── supabase/ (Backend)
│   ├── 00_REBUILD_ALL.sql      ← Entry point
│   ├── 01_DATABASE_SCHEMA.sql  ← Tables & constraints
│   ├── 02_FUNCTIONS_AND_TRIGGERS.sql ← RPCs & triggers
│   ├── 03_SECURITY_AND_RLS.sql ← RLS policies
│   └── functions/              ← Edge Functions (WhatsApp, sandbox bank, etc.)
└── gradle/
    └── libs.versions.toml      ← Version Management
```

---

## 🔧 Technical Stack & Versions

> All versions are centrally managed in `gradle/libs.versions.toml` — check there first.

- **Kotlin**: 2.4.20 (K2 compiler)
- **Gradle**: 9.8.0
- **AGP**: 9.4.1
- **Compose BOM**: 2026.09.00
- **KSP**: 2.3.12
- **Hilt**: 2.60.1 (Dependency Injection)
- **Supabase**: 3.8.0 (Auth, PostgREST, Realtime, Storage) — `:data`/`:app` only
- **Room**: 2.8.5 (Local SQLite Cache, schema version 81)
- **Ktor**: 3.6.0 (Network Engine)
- **Retrofit / OkHttp**: 3.0.0 / 5.5.0 (YoCo REST integration)
- **Coil**: 2.7.0 (Image Loading)
- **OSMDroid**: 6.1.20 (OpenStreetMap)
- **Firebase BOM**: 34.19.0 (Messaging, Analytics, Crashlytics)
- **Google Services**: 4.5.0

---

## 🚀 Developer Setup

### 1. Prerequisites
- Android Studio (recent stable) or later.
- Java 17+ (JVM Toolchain 17).

### 2. Secrets Management
Copy `local.properties.template` to `local.properties` and fill in:
- `SUPABASE_URL` / `SUPABASE_ANON_KEY`
- `YOCO_PUBLIC_KEY`
- `GEOAPIFY_API_KEY` (for address autocomplete)

### 3. Firebase Config
Place `google-services.json` in the `app/` directory.

---

## 📝 Coding Standards

- **State Management**: Use `StateFlow` in ViewModels. Use `.update { it.copy(...) }` for atomic state changes.
- **Dependency Injection**: Use Hilt. ViewModels and UseCases should depend on Repository **Interfaces**, not implementations.
- **Error Handling**: Use `Result<T>` for repository/usecase outputs. Use `toUserMessage()` extension for UI display.
- **Persistence**: Follow the **Offline Outbox** pattern. ViewModels observe Room via Repositories. Failed writes are queued in `pending_operations` and automatically synced by `SyncRepository` when online.
- **Concurrency**: Use `viewModelScope` for UI-bound operations. Use `Dispatchers.IO` for database and network tasks in Repositories.

---

## 🔐 Error Reporting Strategy
1. **Blocking Errors**: Full-screen "Retry" states for network loss.
2. **Transient Errors**: Inline `InfoBox` or `TextField` error states for validation or non-critical failures.
3. **Dual Reporting**: All errors logged via `AppLogger` and optionally tracked via Firebase Crashlytics.

---

## 📄 Advanced UI & Rendering

### 1. Multi-page PDF Engine
Standardized in `ExportRepositoryImpl.kt`, this engine uses `android.graphics.pdf.PdfDocument` with manual page management. It tracks vertical offsets (`y`) and automatically triggers `startNewPage()` when content exceeds the safe margin, redrawing column headers for professional financial statements and legal agreements.

### 2. In-App File Viewer
Provides a native alternative to downloading remote files.
- **Images**: Rendered via `Coil` with secure authenticated headers.
- **PDFs**: Downloaded to a temporary cache file and rendered as a list of Bitmaps using `android.graphics.pdf.PdfRenderer`, allowing smooth scrolling through multi-page documents without leaving the app.

### 3. Biometric Security
Native integration via `androidx.biometric:biometric`. Requires `MainActivity` to extend `FragmentActivity` for proper lifecycle management of the biometric prompt.
