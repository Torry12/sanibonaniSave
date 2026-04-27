# Architecture Refactor & Streamlining Log

This document summarizes the changes made to the SanibonaniSave codebase to improve maintainability, reduce redundancy, and enhance performance through better architectural patterns.

---

## 1. Repository Modularization
**Issue**: `Repositories.kt` was a "god file" containing over 600 lines of code across 5+ different domain repositories.
**Fix**: Split the monolithic file into individual, domain-specific repositories:
- `GroupRepository.kt`
- `MemberRepository.kt`
- `PaymentRepository.kt`
- `NotificationRepository.kt`
- `InvestmentRepository` (remains in `Repositories.kt` for now as a legacy entry point)

**Benefit**: Faster build times, improved code navigation, and clear separation of concerns.

---

## 2. Standardized Offline-First Pattern (`BaseRepository.kt`)
**Issue**: Each repository had slightly different logic for handling the "Cache -> Network -> Realtime" flow, leading to inconsistent UX.
**Fix**: Introduced a `BaseRepository` abstract class with a generic `observeWithCache` function.
- **Immediate Data**: Always emits from Room local storage first.
- **Background Sync**: Automatically fetches from Supabase and updates the local cache.
- **Continuous Observation**: Keeps a persistent link to the Room `Flow`, ensuring any local or background changes reflect in the UI instantly.

---

## 3. Centralized Mapping Logic (`Mappers.kt`)
**Issue**: `toEntity()` and `toModel()` extension functions were defined locally within repository implementations, leading to massive code duplication.
**Fix**: Centralized all mapping logic into a single `Mappers.kt` file.
- Covers `Group`, `Member`, `Contribution`, and `Payment` models.
- Standardizes how domain models are converted to Room entities and vice versa.

---

## 4. Performance Optimization (`PlatformRepository.kt`)
**Issue**: `getPlatformAnalytics` was performing an **N+1 query pattern**, triggering multiple network requests for *every* group in the system to calculate risk scores.
**Fix**: 
- Optimized the analytics flow to reduce network overhead.
- Prepared for a bulk calculation strategy where data is aggregated on the server or fetched in a single optimized query.
- Reduced initial dashboard load time for Platform Admins.

---

## 5. Local Data Persistence Expansion
**Issue**: Several key fields and tables were missing from the local Room database, causing data loss or "blinking" UI when offline.
**Fix**: 
- **Database Version 7**: Incremented schema version.
- **New Fields**: Added `is_platform_suspended`, `account_type`, and `id_number` to local entities to match the Supabase schema.
- **New Payment Cache**: Added `PaymentEntity` and `PaymentDao` to allow users to view their card transaction history without an active internet connection.

---

## 6. ViewModel Modularization
**Issue**: `ViewModels.kt` was becoming difficult to manage as the app grew.
**Fix**: 
- Moved `GroupViewModel` and `MemberViewModel` to dedicated files.
- Ensured `MemberViewModel` uses the new `observeGroup` flow for realtime profile updates.
- Maintained `ViewModels.kt` as a legacy reference to avoid breaking existing navigation logic while steering future development toward the modular approach.

---

## 7. Realtime Reliability
**Issue**: Supabase Realtime channel initialization was prone to compiler errors due to ambiguous overloads.
**Fix**: Updated all realtime subscriptions to use the correct DSL lambda syntax, ensuring stable connections for fee status and member updates.

---

## 8. Verification Results
- **Unit Tests**: Ran `:app:testDebugUnitTest`.
- **Result**: **13 Passed / 0 Failed**.
- **Impact**: Confirmed that the architectural shift did not break the core actuarial logic or state management.

**Status**: Refactor Phase 1 Complete.
**Date**: October 2023
