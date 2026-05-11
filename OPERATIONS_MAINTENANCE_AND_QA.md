# 🛠️ OPERATIONS_MAINTENANCE_AND_QA.md

## 🧪 Testing Strategy

### 1. Test Layers
- **Unit Tests**: Business logic in UseCases, Calculators, and ViewModels (using MockK).
- **Integration Tests**: Repository-to-Room and Repository-to-Supabase flows (Android Instrumented Tests).
- **End-to-End (E2E)**: Complete user flows (Registration → Payment → Dashboard) using Espresso.

### 2. Critical Test Scenarios
- **Serialization**: Ensure Enums (GroupType, MemberStatus) serialize correctly to snake_case for Supabase.
- **Race Conditions**: Verify `total_paid` and `balance` stay consistent during simultaneous payments.
- **State Isolation**: Verify group switching clears all previous group data.
- **Validation**: Strict banking (account/branch) and ID number validation checks.

---

## 🗄️ Database Management

### 1. Database Reset (Development)
Use `supabase/reset_with_mock_data.sql` for a full refresh.
- **Platform Admin**: `torrymsimango@gmail.com` / `torry123M`
- **Group Admin**: `admin2@test.com` / `password123`
- **Members**: `member1@test.com` to `member6@test.com` (various statuses).

### 2. Room & Supabase Sync
- The app uses the **observeAndSync** pattern.
- Local data is the primary source for UI (Offline-first).
- Network fetches update Room, which then triggers UI updates via Flow.

### 3. RLS (Row Level Security)
- Policies are defined in `supabase/rls_policies.sql`.
- Members can only see their own data and public group info.
- Admins see all data within their group.
- Platform Admin has superuser access via service role or specific policies.

---

## 🛠️ Maintenance Tools

### 1. Impersonation
Platform Admins can "impersonate" any Group Admin or Member via the **Maintenance** tab in the Platform Portal. All impersonated actions are logged.

### 2. Audit Logs
Significant actions (logins, payments, impersonations, settings changes) are recorded in the `audit_logs` table.

### 3. Debugging Guide
- **Logcat**: Filter by `SanibonaniSave` or specific class names (e.g., `GroupViewModel`).
- **Room Inspection**: Use Android Studio **App Inspection** tab to view the live SQLite database.
- **Supabase Logs**: Check the Supabase Dashboard "Logs" section for API errors or RLS violations.

---

## 🚀 Deployment Pipeline

### 1. Pre-Release Checklist
- [ ] `./gradlew ktlintCheck` passes.
- [ ] All unit and integration tests pass.
- [ ] `local.properties` configured for production.
- [ ] ProGuard rules verified for Supabase/Serialization.

### 2. Build Commands
- **Debug Build**: `./gradlew assembleDebug`
- **Release Build**: `./gradlew assembleRelease` (requires signing keys).

---

## 📜 Historical Fixes & Changelog
- **Enum Fixes**: Corrected serialization of `GroupType` and `MemberStatus` (April 2026).
- **Business Logic**: Prevented contribution duplication and fixed probation end-date calculation.
- **UI/UX**: Improved error message mapping and added real-time payment notifications.
- **Specialized Modules**: Integrated ROSCA rotation scheduling, Investment Club unit valuations, and Stokvel payout projections (June 2026).
- **Document Engine**: Implemented robust multi-page PDF generation and secure in-app file viewing for PDF/Images (July 2026).
- **Biometric Security**: Stabilized biometric login via `FragmentActivity` migration and profile-level preference sync (July 2026).
