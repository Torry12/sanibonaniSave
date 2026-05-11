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
├── app/ (UI, ViewModels, DI, Services)
│   ├── src/main/java/com/sanibonani/save/
│   │   ├── di/                 ← Hilt Modules
│   │   ├── ui/                 ← Compose Screens, Components, Navigation
│   │   ├── viewmodel/          ← ViewModels (Auth, Group, Member, Admin, etc.)
│   │   ├── service/            ← Background Services, Cache Services
│   │   └── worker/             ← WorkManager Jobs
├── data/ (Implementation)
│   ├── src/main/java/com/sanibonani/save/data/
│   │   ├── local/              ← Room DB, DAOs, Entities
│   │   ├── remote/             ← Supabase, Retrofit Services (Geoapify, YoCo)
│   │   ├── repository/         ← Repository Implementations
│   │   └── logging/            ← AppLogger
├── domain/ (Core Logic & Interfaces)
│   ├── src/main/java/com/sanibonani/save/domain/
│   │   ├── model/              ← Domain Models (@Serializable, @Parcelize)
│   │   ├── repository/         ← Repository Interfaces
│   │   ├── usecase/            ← Business Logic Orchestrators
│   │   └── validation/         ← Validation Utils
├── supabase/ (Backend)
│   ├── schema.sql              ← Database Schema
│   ├── rls_policies.sql       ← Row Level Security
│   └── functions/              ← Edge Functions (WhatsApp, etc.)
└── gradle/
    └── libs.versions.toml      ← Version Management
```

---

## 🔧 Technical Stack & Versions

- **Kotlin**: 2.1.0 (with K2 compiler)
- **Gradle**: 8.11.1
- **AGP**: 8.7.3
- **Compose BOM**: 2024.12.01
- **KSP**: 2.1.0-1.0.29
- **Hilt**: 2.51.1 (Dependency Injection)
- **Supabase**: 3.1.4 (Auth, PostgREST, Realtime, Storage)
- **Room**: 2.6.1 (Local SQLite Cache)
- **Ktor**: 3.0.1 (Network Engine)
- **Coil**: 2.6.0 (Image Loading)
- **OSMDroid**: 6.1.18 (OpenStreetMap)

---

## 🚀 Developer Setup

### 1. Prerequisites
- Android Studio Ladybug (2024.2.1) or later.
- Java 17+.

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
- **Persistence**: Follow the **Offline-First** pattern. ViewModels observe Room via Repositories, while Repositories handle Supabase sync in the background.
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
