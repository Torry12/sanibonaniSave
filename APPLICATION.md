# 📱 SanibonaniSave — Complete Application Reference

This document serves as the primary maintenance and upgrade manual for the SanibonaniSave platform. It consolidates architectural patterns, business rules, and operational procedures.

---

## 🌟 1. Project Vision & Identity
SanibonaniSave is a **Distributed Cooperative Financial Infrastructure** specifically designed for South African savings groups. It enables traditional communal saving models to operate with digital transparency, professional actuarial modeling, and precision-safe accounting.

### Core Value Proposition:
- **Precision**: Strict `Money` (BigDecimal) domain model for all ZAR math.
- **Transparency**: Immutable financial ledger entries as the source of truth for all balances.
- **ML-Ready**: Behavior tracking optimized for Azure AI Foundry finetuning.

---

## 🛠 2. Technical Stack & Dependencies

### Core Frameworks
- **Language**: Kotlin 2.4.21 (JVM Toolchain 17)
- **AGP**: 9.4.1 (Supporting Hilt 2.60.1)
- **UI**: Jetpack Compose 2026.09.00 BOM (Material 3)
- **Persistence**: Room 2.8.5 (Precision-safe converters, schema version 82)
- **Backend**: Supabase 3.8.0 (Auth, Realtime, RPCs)
- **Annotation Processing**: KSP 2.3.12

> All versions live in `gradle/libs.versions.toml` — edit there, not here.

---

## 🏗 3. Architectural Blueprint

### Module Boundaries
1. **`:domain`**: Business logic. Models, Interfaces, UseCases, Services. Imports **neither** the Supabase SDK **nor** `android.*` — models are `@Serializable` only, navigation passes ids, and logging goes through the `AppLogSink` port implemented in `:app`. It is still packaged as an Android library for `BuildConfig` and the test runner.
2. **`:data`**: Android Library. Repositories, Supabase, Room. The only module permitted to speak the Supabase SDK directly.
3. **`:app`**: UI, ViewModels, DI, Workers, Analytics. The only other module permitted to touch Android framework and Supabase SDK types.
4. **`:simulation`**: JVM-runnable financial-flow simulator and domain seed factory; consumed by `:app` test source sets only.

### Performance Patterns
- **Parallel Warmup**: ViewModels use `async { ... }.await()` to load independent data streams concurrently.
- **Job Hardening**: `launchJob(key)` manages lifecycle-aware coroutines to prevent race conditions.

---

## ⚖️ 4. Business Logic & Group Types

### Financial Precision Rules
**Critical**: Eradicate `Double` for financial math.
- Use `money.ratio(other)` for percentage and coverage indicators.
- Use `money.dividedBy(Int|Double)` for equal distribution/allocations.
- derivation: Balances MUST be derived from `LedgerEntry` sums or `ConsolidationReport`.

### Specialized Logic
| Group Type | Key Formula / Pattern |
| :--- | :--- |
| **Burial Society** | Solvency Ratio: `(Balance + Expected).ratio(APV_Claims)` |
| **Stokvel** | Payout Projection: `Balance + (MonthlyTotal * MonthsRemaining)` |
| **ROSCA** | Pot Derivation: `Sum(Ledger.Contributions.filter(period))` |
| **Investment Club** | NAV Per Unit: `Balance.ratio(TotalContributions)` |

---

## 📡 5. Backend & Security (Supabase)

### Row-Level Security (RLS)
Postgres RLS enforces data isolation between groups and members. JWT custom claims (`role`, `group_id`) are verified by RLS policies.

### Function Security
- **MFA Enforcement** is in-database, not in an Edge Function: `assert_mfa_for_disbursement_v1(p_amount)` raises `[SEC_001] MFA_REQUIRED` when a disbursement is ≥ R10,000 and the session is not AAL2, and both outflow RPCs `PERFORM` it.
- `SECURITY DEFINER` functions read past RLS inside their body, so the audit and settlement RPCs carry their own `[SEC_002]` identity checks and have `EXECUTE` revoked from `PUBLIC` and `anon` in `03_SECURITY_AND_RLS.sql`.
- Every `SECURITY DEFINER` function pins `search_path`; a new RPC without a pin is a review blocker.
- Canonical source is `supabase/02_FUNCTIONS_AND_TRIGGERS.sql`; a live tier changes only through a new dated `supabase/online/SYNC_DELTA_<date>.sql`. See `supabase/SQL_DOCUMENTATION.md`.

### Edge Functions
`supabase/functions/` hosts `agent-orchestrator`, `architecture-read`, `financial-orchestrator`, `mobile-admin-actions`, `process-kyc-ai`, `sandbox-bank-api`, `send-whatsapp`, and `yoco-webhook`.

---

## 🛠 6. Maintenance & Upgrade Guide

### Database Schema Management (Room)
1. **Export Schema**: Run `./gradlew kspDebugKotlin`.
2. **Verify Converters**: Ensure `MoneyConverter` maps `BigDecimal` to `String` (toPlainString) for persistence.
3. **Migration Logic**: Use `db.safeExec` for standard ALTER commands.

### Build Failures & Integrity
If the `:domain` module fails after model changes, check for redeclarations or missing imports in consolidated files like `ActuarialModels.kt` or `AuditModels.kt`.

---

## 🔍 7. Future Upgrade Roadmap

- [ ] **Azure AI Integration**: Deploy finetuned models for automated loan risk scoring.
- [ ] **Cross-Border Payouts**: Support for SADC remittance via integrated gateways.
- [ ] **Biometric Authorization**: Use Fingerprint/FaceID for high-value disbursement MFA.

---
**Maintained by**: SanibonaniSave Engineering Team
**Last Revised**: October 28, 2026
