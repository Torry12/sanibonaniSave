# 🤖 PROJECT CONTEXT — SanibonaniSave
**Target Audience**: Future AI Project Agents

---

## 🎯 Overview
SanibonaniSave is a specialized South African Savings Groups Administration Platform. It digitizes Traditional Savings Schemes (Stokvels, Burial Societies, ROSCAs) using a modern tech stack (Android/Kotlin + Supabase).

---

## 🏗️ Architecture Stack
- **Frontend**: Native Android (Kotlin 2.4.21, Jetpack Compose 2026.09.00 BOM, Material 3).
- **Backend**: Supabase (PostgreSQL 17, Auth, Realtime, Storage).
- **Database**: Room 2.8.5 (Local Cache, schema version 82) + PostgREST (Remote).
- **DI**: Hilt 2.60.1 (with WorkManager and Navigation-Compose integrations).
- **Build**: Gradle 9.8.1, AGP 9.4.1, KSP 2.3.12, JVM Toolchain 17.
- **Logic**: Clean Architecture (Layered: UI → ViewModel → UseCase → Repository).

---

## 🔑 Critical Logic Rules
1. **Enum Serialization**: All enums (`GroupType`, `MemberStatus`, `ContributionStatus`) must be serialized as **snake_case** for Supabase.
2. **Financial Atomicity**: Use the `record_contribution_v2` RPC for all payment entries and `approve_and_disburse_loan_v2` / `complete_payout_v2` for outflows to keep `members.total_paid` and `groups.balance` in sync.
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
- `DATA_ARCHITECTURE.md`: Data layer, Room schema and sync strategy.
- `BUSINESS_PROCESSES.md`: Business rules and lifecycle flows.
- `AGENTS.md` (project root): Engineering principles and coding standards.
- `README.md` (project root): High-level summary and quick setup.

---

## 💾 Consolidated Database Scripts (`supabase/`)
1. `00_REBUILD_ALL.sql`: Entry point that rebuilds the schema from the numbered files.
2. `01_DATABASE_SCHEMA.sql`: Core tables, constraints and enums.
3. `02_FUNCTIONS_AND_TRIGGERS.sql`: RPCs (`record_contribution_v2`, `pay_platform_fee_v2`, …) and triggers.
4. `03_SECURITY_AND_RLS.sql`: Row-Level Security policies.
5. `04_PLATFORM_ADMIN_SETUP.sql`: Identity and role management.
6. `05_PERFORMANCE_VIEWS.sql`: Reporting/dashboard views.
7. `07_RESET_DATABASE.sql`: Full teardown for a clean rebuild.
8. `11…31`: Seed and scenario data (test data, payout requests, escalations, integrity checks).
9. `MASTER_SEED_DATA.sql` / `SEED_ALL_DATA.sql`: Consolidated seed entry points (local development only).
10. `online/SYNC_DELTA_<date>.sql`: The only change path for an existing tier. `20261014`, `20261021`, and `20261022` are applied history; never rewrite one; add a new dated delta with bumped fingerprints.

Regenerate generated SQL with `supabase/scripts/generate_ground_zero.ps1` → `generate_consolidated_schema.ps1` → `generate_online_sync.ps1`, then `generate_integrated_seed.ps1` for the seed bundle.

---

## ⚠️ Known Gotchas
- **Map<String, Any>**: Do NOT use `Any` in serializable models (triggers "Serializer not found"). Use `String` or `JsonObject`.
- **JWT Latency**: After sign-up, the `profiles` table is populated via trigger. Allow 500ms for propagation before querying the profile.
- **Balance Consistency**: `groups.balance` is a cached figure derived from `group_ledger`, is `NOT NULL DEFAULT 0` (since `SYNC_DELTA_20261022`), and is never written directly by the app. Spend guards must treat a missing balance as zero and fail closed (`COALESCE(v_group.balance, 0)`).
