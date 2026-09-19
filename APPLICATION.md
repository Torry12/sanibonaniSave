# 📱 SanibonaniSave — Complete Application Reference

This document serves as the primary maintenance and upgrade manual for the SanibonaniSave platform. It consolidates architectural patterns, business rules, and operational procedures required to sustain and evolve the system.

---

## 🌟 1. Project Vision & Identity
SanibonaniSave is a **Distributed Cooperative Financial Infrastructure** specifically designed for South African savings groups (Stokvels, Burial Societies, ROSCAs, etc.). It enables traditional communal saving models to operate with digital transparency, professional actuarial modeling, and automated rule enforcement.

### Core Value Proposition:
- **Transparency**: Immutable financial ledger entries instead of simple balance mutations.
- **Resilience**: Offline-first architecture using Room with seamless Supabase synchronization.
- **Specialization**: Tailored logic for 5 distinct group types (Burial, Stokvel, ROSCA, Investment, Emergency).
- **Compliance**: Built-in KYC document verification and automated fee enforcement.

---

## 🛠 2. Technical Stack & Dependencies

### Core Frameworks
- **Language**: Kotlin 2.1.0 (K2 Compiler enabled)
- **UI**: Jetpack Compose 2024.12.01 (Material 3)
- **Architecture**: MVVM + Clean Architecture (3-module structure)
- **Dependency Injection**: Hilt 2.51.1
- **Persistence**: Room 2.6.1 (SQLite)
- **Backend-as-a-Service**: Supabase 3.1.4 (Auth, PostgREST, Realtime, Storage)
- **Async/Reactive**: Coroutines + Flow (StateFlow preferred)

### Integration Services
- **Payments**: YoCo Gateway (via custom repository)
- **Geolocation**: Geoapify (for address autocomplete)
- **Maps**: OSMDroid (OpenStreetMap rendering)
- **Networking**: Ktor 3.0.1 (Supabase engine)
- **Image Handling**: Coil 2.6.0 (Authenticated headers supported)

---

## 🏗 3. Architectural Blueprint

### Module Boundaries
The project is divided into three Gradle modules with strict dependency rules:
1. **`:domain`**: Pure Kotlin. Contains Models, Repository Interfaces, Use Case logic, and Validation rules. *Depends on nothing.*
2. **`:data`**: Android Library. Contains Repository Implementations, Supabase integrations, Room Entities/DAOs, and Service implementations. *Depends on `:domain`.*
3. **`:app`**: Android Application. Contains UI (Compose), ViewModels, DI Modules, Background Workers, and Navigation. *Depends on `:domain` and `:data`.*

### Data Flow Pattern (`observeAndSync`)
We use a standardized pattern for offline-first data:
1. UI observes a `StateFlow` from a ViewModel.
2. ViewModel observes a `Flow<Result<T>>` from a Repository.
3. Repository emits local Room data immediately.
4. Repository triggers a background fetch from Supabase.
5. On success, Repository updates local Room cache, which triggers a new emission to the UI.
6. On failure, Repository handles retries with **exponential backoff**.

---

## ⚖️ 4. Business Logic & Group Types

### The Immutable Ledger Rule
**Critical**: NEVER directly update a `balance` field in the database. Balance updates must be the result of a `LedgerEntry` creation.
- Every financial movement must have a corresponding `Transaction` record.
- The `record_contribution_v1` RPC in Supabase is the single point of entry for adding funds.

### Specialized Logic
| Group Type | Primary Use Case | Key Logic |
|------------|------------------|-----------|
| **Burial Society** | `CalculateViabilityUseCase` | Actuarial surrender values, age-based premiums. |
| **Stokvel** | `CalculateStokvelPayoutsUseCase` | End-of-cycle projections based on contribution consistency. |
| **ROSCA** | `CalculateRoscaRotationUseCase` | Automated slot assignment based on join date/seniority. |
| **Investment Club** | `CalculateInvestmentClubValuationUseCase` | NAV (Net Asset Value) calculation and unit price fluctuations. |
| **Emergency Fund** | `ProcessEmergencyWithdrawalUseCase` | Eligibility checks (active status) and 50% liquidity caps. |

---

## 📡 5. Backend & Security (Supabase)

### Row-Level Security (RLS)
The platform uses Postgres RLS to enforce data isolation:
- **Platform Admins**: Full access to platform analytics and escalated claims.
- **Group Admins**: Full access to their specific group and its members' data.
- **Members**: Access to their own profile, their group info, and public group listings.

### Custom Claims
User roles are stored in JWT custom claims (`role`, `group_id`). These are updated via the `update_user_role` RPC and verified by RLS policies.

### Edge Functions
- **WhatsApp Integration**: Sends notifications via Meta's API.
- **Fee Enforcement**: Periodic cleanup and auto-suspension logic.

---

## 🔧 6. Maintenance Procedures

### Database Migrations (Room)
1. Update `SanibonaniDatabase.kt` version number.
2. Define a new `MIGRATION_X_Y` object in `Migrations.kt`.
3. Add the migration to the `ALL_MIGRATIONS` array.
4. **Important**: Always use `db.safeExec()` for `ALTER TABLE` to avoid crashes if a column already exists.
5. Export the new schema JSON (auto-generated) and ensure it matches the Supabase production schema.

### Testing Strategy
- **Unit Tests**: Run `./gradlew test`. Covers business logic in `:domain`.
- **Integration Tests**: Run `./gradlew :app:connectedDebugAndroidTest`. Verifies Supabase mocks and Room migrations.
- **Diagnostic Scripts**:
    - `verify-live-ready.ps1`: Full environmental check.
    - `runtime-signal-unified.ps1`: End-to-end connectivity and function probe.

---

## 🚀 7. Future Upgrade Roadmap

### Phase 1: Enhanced Financials
- [ ] **AI Credit Scoring**: Integration of the `BehaviorTracking` system with external credit bureaus.
- [ ] **Automated Reconciliations**: Automated matching of bank statements to Ledger entries.

### Phase 2: Platform Scaling
- [ ] **White-labeling Support**: Allow large societies to brand their own sub-portals.
- [ ] **Multi-currency Support**: Support for cross-border SADC savings groups.

### Phase 3: Advanced UX
- [ ] **Offline Map Tiles**: Downloadable map regions for rural usage.
- [ ] **Voice-assisted Contributions**: Using Speech-to-Text for non-literate members.

---

## 🆘 8. Troubleshooting Common Issues

| Issue | Potential Cause | Solution |
|-------|-----------------|----------|
| **Data not syncing** | Stale RLS claims or network timeout. | Sign out/in to refresh JWT. Check `AppLogger` for 403 errors. |
| **Hilt Injection Fail** | ViewModel used in `@Inject` without `hiltViewModel()`. | Manually instantiate in tests or use `EntryPoint` for services. |
| **Migration Failure** | Schema mismatch or malformed JSON. | Validate `defaultValue` and `notNull` naming in `.json` schema files. |
| **Payment Pending** | YoCo webhook not received. | Admin can manually override via `AdminViewModel.verifyPayment`. |

---
**Maintained by**: SanibonaniSave Engineering Team  
**Last Revised**: June 2026
