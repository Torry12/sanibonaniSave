# AGENTS.md — SanibonaniSave AI Coding Reference

**Last Updated**: October 28, 2026
**Version**: 8.2 (Boundary Verified, Money Path Guarded, Delta-Synced)

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
  └── run via: gradlew :simulation:run  (puts android.jar on the classpath so it works off-device)
```
See `settings.gradle.kts` and build files for enforcement.

> **Boundary rule**: the Supabase SDK (`io.github.jan.supabase.*`) and `android.*`
> framework imports belong in `:data` and `:app` only. `:domain` exposes
> SDK-free ports (e.g. `SupabaseRepository` returns the domain-owned
> `AuthSession` / `AuthSessionStatus`, not the SDK's `UserSession`) and `:data`
> performs the mapping at the boundary.
> This is currently **clean**: `domain/src/main` has zero `android.*` and zero
> Supabase SDK imports — models are `@Serializable` only (no `@Parcelize`, so
> navigation passes ids) and logging leaves through the `AppLogSink` port.
> `DomainModuleBoundaryTest` fails if a new import leaks in.

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
- **Atomic updates**: Always use RPCs like `increment_group_balance`; `p_member_id` is optional (`DEFAULT NULL`) and only needed for member-attributed entries.
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

### 4. Validate Before Write — on Both Sides
**A rejected payload should never reach the ledger, and the server decides final truth.**

- `InputValidator` owns every input rule (SA ID, phone, bank account, branch code, card/expiry/CVV, name, email). `ValidationUtils` is the caller-facing surface and delegates to it — do not restate a rule with its own regex.
- Postgres RPCs raise a bracketed code before the first write: `[FIN_001]` insufficient liquidity, `[FIN_002]` state precondition failed (bad loan/payout status, term not expired), `[FIN_003]` input validation (amount sign/scale, unsupported `status`/`type` literal), `[SEC_001]` MFA required on disbursements ≥ R10,000, `[SEC_002]` caller not entitled, `[SYS_001]` referenced row not found, `[SYS_002]` a `transaction_id` reused with different content.
- Kotlin shows the human half of the message through `Throwable.toUserMessage()`; nothing keys on the bracket, so keep the code and the sentence paired when you add one.
- A `[FIN_*]` rejection is permanent for that payload: the outbox must not retry it forever.

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
 **Language** | Kotlin 2.4.21 | JVM Toolchain 17 |
 **AGP** | 9.4.1 | Required for Hilt 2.60.1 |
 **Hilt** | 2.60.1 | Full project-wide DI |
 **KSP** | 2.3.12 | Annotation processing |
 **Room** | 2.8.5 | Schema version 82 |
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

## 🗄️ Database Change Workflow

- **Canonical sources**: `supabase/01_DATABASE_SCHEMA.sql`, `02_FUNCTIONS_AND_TRIGGERS.sql`, `03_SECURITY_AND_RLS.sql`, `04_PLATFORM_ADMIN_SETUP.sql`, `05_PERFORMANCE_VIEWS.sql`, `30_STORAGE_RLS.sql`. Never edit generated copies by hand.
- **Regenerate** in this order after a source change: `generate_ground_zero.ps1` → `generate_consolidated_schema.ps1` → `generate_online_sync.ps1` → `generate_integrated_seed.ps1`. Do not use the superseded `scripts/generate_consolidated.ps1`.
- **`supabase/migrations/` holds the generated baseline and nothing else.** `supabase db reset --local` applies that directory *after* `00000000000000_GROUND_ZERO.sql`, so any extra file there silently rewrites whatever the masters just declared. `43_platform_fee_payment_idempotency.sql`, `44_loan_repayment_idempotency.sql` and `45_increment_group_balance_idempotency.sql` did exactly that until 2026-10-31: their idempotency idea had long since been folded into `02_FUNCTIONS_AND_TRIGGERS.sql`, but re-applying their older bodies left Local without the `FIN_002`/`FIN_003`/`SEC_002` guards on `increment_group_balance`, `pay_platform_fee_v2` and `record_loan_repayment_v1` while Cloud kept them. Object counts matched, only the digest differed. They now sit in `supabase/legacy_migrations/`, which nothing applies; a structural change goes in a master and is regenerated, never dropped next to the baseline. **Same counts with a different digest is a changed definition, not a missing object** - diff `SCHEMA_OBJECT_FINGERPRINTS.sql` to get the exact rows (over `db query --linked` emit one concatenated column, that route drops three of the four). That rule has a live example: when `20261101C` landed it was applied on Local only, so Local measured `1037 | C=535 E=1 F=88 G=15 I=33 K=198 P=85 R=38 S=2 T=38 V=4 | 8405ef938e6325bf03b6002e54f5ecca` while Cloud read the same counts with `a18f379d05164eb2a5adec30c06174a3` - one replaced function body, no added object. Cloud was given the delta the same day and both tiers now read `8405ef938e6325bf03b6002e54f5ecca`.
- **Existing tiers** change only through a new dated `supabase/online/SYNC_DELTA_<date>.sql` containing a read-only pre-flight, the write sections, and expected body fingerprints. `20261014` through `20261031`, plus `20261030B`, `20261031B` and `20261101`, are applied on **both** tiers (Local re-measured and brought current on 2026-10-29; Cloud measured read-only then given `20261031` on 2026-10-30) — and `20261101C` is the only delta in that series that was ever live on one tier alone: it was cut and applied on Local on 2026-10-31 and given to Cloud later the same day, which left Cloud running the old body for a few hours. Both tiers carry it now, so no delta is outstanding. `20261101` is the one data-only delta: it backfills an `opening_balance` ledger row for each group with a cached balance and no ledger rows (7 on Cloud, 0 on Local) and touches no `groups.balance`. `20261101B` is the second, and supersedes it on Cloud: it withdraws those seven synthetic rows, rebuilds every group ledger from the source tables with `consolidate_group_ledger_v1`, un-books the `registration_contribution` rows that function mis-files into the group ledger, then re-derives `groups.balance` per group — **applied on Cloud only** (2026-10-31, after a data restore dump; 4,266 ledger rows over 49 groups, drift 0, platform total R1,677,300.00, 7 groups left negative because their seed wrote outflows with no matching inflow) **(superseded the same day: Cloud was then rebuilt at ground zero from `000_CONSOLIDATED_ONLINE_SYNC.sql`, reseeded through `SEED_ALL_DATA.sql`, and passed `90_SEED_INTEGRITY_GATE.sql`, so the 49 reconstructed groups are gone and Cloud reads 19 groups / 1,397 ledger rows / 0 drift / R702,500.00 held across those groups plus R21,100.00 of platform revenue in 122 `platform_ledger` rows — identical to Local)**, **measured not run on Local**, whose ledger is nearly complete but still owes 6 `loan_disbursement` rows worth R17,000 across five groups **(superseded 2026-10-31: Local was rebuilt from the masters with `scripts/run-supabase-local-reset.ps1 -SeedingMode Extensive`, and the seed now books those debits itself, so Local reads 19 groups / 1,397 ledger rows / 0 drift / no negative group)**. `20261101C` rewrites `consolidate_group_ledger_v1` so a reconstruction books what `internal_post_contribution` books: the three platform contribution types go to `platform_ledger` (with `member_fee` mapped to `platform_fee`, as the live path maps it) instead of being re-filed into the group, `status IN ('paid','partial')` replaces `'paid'`, a NULL `contributions.type` no longer aborts the rebuild against the NOT NULL `group_ledger.category`, and the function delegates to `recompute_ledger_running_balances_v2`, so it rewrites every `balance_after` and re-derives `groups.balance` instead of stamping 0 and leaving the cached column alone — **a rebuild now moves `groups.balance`, which the old body never touched**. `entries_reconstructed` counts the rows actually inserted and the report adds `platform_entries`, per-source counts, `final_balance` and `ledger_rows`. Prove the behaviour with `supabase/maintenance/CONSOLIDATE_REBUILD_PROBE.sql` (rollback-only: it inserts the four shapes the old body got wrong, rebuilds, and rolls back). Local is measured, never assumed: on 2026-10-29 it was found to be missing `20261029` and `20261029B` while `20261028`, `20261030` and `20261031B` were present, contradicting its own record, so re-run each delta's `P.0` before trusting a tier-state note. `20261029` (money-RPC caller-identity guards, anonymous execute withdrawn, `search_members_v1` gains `behavior_score`), `20261029B` (`close_poll_v1`, `pay_burial_claim_v1`, `record_disbursement_v1` declared at last) and `20261030` (restores `vw_ledger_integrity_check`) have run on Local; `20261030B` (audit-report aggregate rewrite), `20261031` (`record_liquidity_v1`, the guarded inflow twin of `record_disbursement_v1`) and `20261031B` (re-revokes `anon` on `vw_ledger_integrity_check`) are on both tiers; `20261031B` was needed only on Local, whose `anon` grant had been reopened. `03_SECURITY_AND_RLS.sql` grants on ordinary and partitioned tables only: a view runs with its definer's rights, so its grant is the only gate and the old `ALL TABLES` grant reopened every view a later module locked down. Never rewrite an applied delta.
- **Balance invariant**: `groups.balance` is `NUMERIC(12,2) NOT NULL DEFAULT 0` and ledger-derived. Spend guards use `COALESCE(v_group.balance, 0)` and `RAISE` on insufficiency.
- **Seed money rule**: a seed that creates a spendable row must book the ledger row the live path would have booked. `consolidate_group_ledger_v1` rebuilds from five source tables, so a raw `INSERT INTO loans` with a disbursed status, or a claim marked `paid`, is money the balance only appears to have until someone reconciles — which is how the 2026-10-31 Cloud reconstruction produced seven negative groups. Contributions go through `pg_temp.seed_post_contribution`, loan outflows through `pg_temp.seed_post_loan_disbursement` (both in `10_SEED_HELPERS.sql`, which raises `[FIN_002]` rather than seeding an unaffordable fixture), and every manual injection carries a deterministic `transaction_id` so a second run cannot double-book it. Seeds never touch app-created `SIM-*` groups: `19_`/`21_` match `LIGHT-%`/`SEED-%` only.
- **Seed credential rule**: never write a `platform_admin` password into a seed file. The personas keep the shared `Pass123!` on purpose — the Payment Sandbox signs in as any of them (118 accounts per tier) — but a seeded account with `role='platform_admin'` answers `is_platform_admin()`, so `11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql` reads `current_setting('sanibonani.seed_platform_admin_password', true)` the way `04_PLATFORM_ADMIN_SETUP.sql` reads `sanibonani.platform_admin_password`: unset gives a NULL `encrypted_password` plus a `NOTICE`, and the fixture row is only ever created, never re-hashed, so no re-seed can restore a published credential. Both tiers were rotated off `Pass123!` for that fixture on 2026-10-31 (sessions revoked; `ROTATE_PLATFORM_ADMIN_PASSWORD_20261018.sql` Section D proves the inventory). The real value lives in `SEED_PLATFORM_ADMIN_PASSWORD` in the untracked `local.properties`, and `run-supabase-local-reset.ps1` / `seed-supabase-online.ps1` prefix it as a session-level `SET` — `SET LOCAL` would be cleared by the seed file's own inner `COMMIT`.
- **The seed ends at a gate**: `supabase/90_SEED_INTEGRITY_GATE.sql` is inlined last by `generate_integrated_seed.ps1`, inside `SEED_ALL_DATA.sql`'s `BEGIN/COMMIT`, so a failed check rolls the whole seed back. It asserts `groups.balance = SUM(group_ledger.amount)`, no negative group, every source row present in the ledger with the same amount and category, platform-type contributions in `platform_ledger` and never in `group_ledger`, and no unkeyed or unlabelled ledger row. Run it read-only on any tier to audit it; codes are `[SEED_001]`…`[SEED_007]`.
- **Verification** is always read-only first: body hashes, `groups_null_balance`, `balance_nullable`, and behavioural guard probes. Measure drift with `supabase/maintenance/SCHEMA_PARITY_DIGEST.sql` (object counts + digest) and list offenders with `SCHEMA_OBJECT_FINGERPRINTS.sql`; when the masters move, update the expected baseline in the digest file's header.
- **Never probe a `SECURITY DEFINER` RPC as `anon`** on the Local tier — it crashes Local Postgres (signal 11) over SQL and over the PostgREST route alike. Nor as `authenticated` once EXECUTE has been revoked from it: the 2026-10-29 application of `20261029` ran the delta's own "expect permission denied" probe against `increment_group_balance` and killed the postmaster (it recovered through WAL with no loss). Prove a revoke with `has_function_privilege('anon', 'fn(...)', 'EXECUTE')`. After a function body changes, run `SELECT pg_notify('pgrst','reload schema');`.
- **Client↔tier parity**: every `.rpc("...")` and every `postgrest["..."]` target in `:data` must exist on the tier. `close_poll_v1`, `pay_burial_claim_v1` and `record_disbursement_v1` were called with nothing behind them for the whole life of the repo; they are now declared in `02_FUNCTIONS_AND_TRIGGERS.sql` and cut as `SYNC_DELTA_20261029B`, so both tiers answer them (Cloud verified 2026-10-30). `record_liquidity_v1` (cut as `SYNC_DELTA_20261031`) is the same shape for the harness's seeding and is present on both tiers. A build against an un-deltaed tier keeps failing, and that is a tier-state defect, not a code one. The three phantom table reads this line used to record (`member_behavior_tracks` vs `member_behavior_track`, `database_backups`, the `burial_claims` fallback in `SyncRepositoryImpl`) are corrected: the reads name the real tables and an auth or network failure is no longer reported as "table missing". Treat any remaining mismatch as a defect, never as a fallback.

---

## ✅ Safe-Change Checklist

- [ ] **Financials**: Ensure no `Double` is used for currency storage or math.
- [ ] **State**: Use `launchJob(key)` to prevent overlapping async operations.
- [ ] **Domain**: If adding models, check `ActuarialModels.kt` or `AuditModels.kt` first to avoid redeclarations. Keep `android.*` and `io.github.jan.supabase.*` out of `:domain`; navigation takes ids, not model objects.
- [ ] **Validation**: Put a new input rule in `InputValidator`; `ValidationUtils` only forwards.
- [ ] **SQL**: A new `SECURITY DEFINER` RPC needs a pinned `search_path`, its own identity check, and a `REVOKE ... FROM PUBLIC, anon` if it reads past RLS.
- [ ] **Line endings**: This repo runs `core.autocrlf=true`; after editing any `.md`, confirm CRLF survived (`git diff --ignore-cr-at-eol --numstat` should then show only real content changes). SQL line endings are per-directory, not global: the schema and seed masters (`01`–`05`, `11_`/`20_`/`31_`, `04_PLATFORM_ADMIN_SETUP.sql`), the maintenance rotate scripts and every generated artifact are CRLF, while `10_SEED_HELPERS.sql`, `90_SEED_INTEGRITY_GATE.sql`, `online/SYNC_DELTA_*.sql` and the parity digests are LF. Match the file you are editing; check with `wc -l` against `tr -dc '\r' | wc -c`.
- [ ] **Build**: Run `:domain:assembleDebug` after any model change.
