# 🤖 PROJECT CONTEXT — SanibonaniSave
**Target Audience**: Future AI Project Agents

---

## 🎯 Overview
SanibonaniSave is a specialized South African Savings Groups Administration Platform. It digitizes Traditional Savings Schemes (Stokvels, Burial Societies, ROSCAs) using a modern tech stack (Android/Kotlin + Supabase).

---

## 🏗️ Architecture Stack
- **Frontend**: Native Android (Kotlin 2.1.0, Jetpack Compose, Material 3).
- **Backend**: Supabase (PostgreSQL 15+, Auth, Realtime, Storage).
- **Database**: Room (Local Cache) + PostgREST (Remote).
- **DI**: Hilt (Dependency Injection).
- **Logic**: Clean Architecture (Layered: UI → ViewModel → UseCase → Repository).

---

## 🔑 Critical Logic Rules
1. **Enum Serialization**: All enums (`GroupType`, `MemberStatus`, `ContributionStatus`) must be serialized as **snake_case** for Supabase.
2. **Financial Atomicity**: Use the `record_contribution_v1` RPC for all payment entries and `disburse_loan_v1` for loan payouts to keep `members.total_paid` and `groups.balance` in sync.
3. **Offline-First**: Use the `observeAndSync` pattern in repositories. The UI always observes the local Room database, which is updated by background network fetches.
4. **Member Lifecycle**: Members start as `PENDING_PAYMENT`, move to `PROBATION` after joining fee, then to `ACTIVE` after the probation period (default 3 months).
5. **Specialized Insights**: Each group type (ROSCA, Stokvel, Investment Club) has a dedicated intelligence module in the domain layer to calculate rotations, NAV valuations, and payout projections.
6. **In-App Viewing**: Remote documents (PDF/Images) can be viewed natively using `PdfRenderer` and `Coil`, providing a seamless review experience without mandatory downloads.

---

## 🛡️ Security Model (Supabase RLS)
- **Platform Admin**: Superuser role. Bypasses most RLS filters to manage the entire network.
- **Group Admin**: Can manage members, settings, and payouts for their specific group only.
- **Member**: Can see their own contributions, loans, and beneficiaries. Can see public group metadata.
- **Audit Logs**: All sensitive administrative actions (impersonation, status changes) must be recorded in the `audit_logs` table.

---

## 🧪 Documentation Index
- `APP_ARCHITECTURE_AND_TECHNICAL_GUIDE.md`: Full technical breakdown.
- `BUSINESS_LOGIC_AND_FEATURE_SPEC.md`: Product features and business rules.
- `OPERATIONS_MAINTENANCE_AND_QA.md`: Testing and database maintenance guide.
- `README.md`: High-level summary and quick setup.

---

## 💾 Consolidated Database Scripts
1. `01_DATABASE_SCHEMA.sql`: Core tables and RPCs.
2. `02_SECURITY_AND_RLS.sql`: Master security policies.
3. `03_PLATFORM_ADMIN_SETUP.sql`: Identity and role management.
4. `04_MIGRATIONS_AND_UPDATES.sql`: Recent schema evolutions.
5. `05_SEED_FRESH_START.sql`: Primary testing data.
6. `06_UTILITY_QUERIES.sql`: Debugging and verification.

---

## ⚠️ Known Gotchas
- **Map<String, Any>**: Do NOT use `Any` in serializable models (triggers "Serializer not found"). Use `String` or `JsonObject`.
- **JWT Latency**: After sign-up, the `profiles` table is populated via trigger. Allow 500ms for propagation before querying the profile.
- **Balance Consistency**: Never update `groups.balance` directly from the app; always use a database function to avoid race conditions.
