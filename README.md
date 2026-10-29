# SanibonaniSave — Android App

> **Savings Groups, Unified & Empowered**
> South African burial society, stokvel, ROSCA and community savings group
> administration platform with built-in actuarial science and institutional investment access.

---

## 📚 Detailed Documentation

For in-depth information, please refer to the following compiled guides:

1. **[AGENTS.md](AGENTS.md)**: **AI Coding Reference.** Engineering principles, precision-safe patterns, and ML-readiness.
2. **[APPLICATION.md](APPLICATION.md)**: **Complete Application Reference.** Primary manual for maintenance, architecture, and diagnostics.
3. **[DESIGN.md](DESIGN.md)**: Visual identity and UI component specifications.
4. **[SQL_DOCUMENTATION.md](supabase/SQL_DOCUMENTATION.md)**: Database schema, RPCs, balance invariants, and the dated delta workflow.

The working notes live under `docs/` and are indexed by **[docs/INDEX.md](docs/INDEX.md)**:
**[docs/TECHNICAL_STATE_LATEST.md](docs/TECHNICAL_STATE_LATEST.md)** for the current build/test snapshot,
**[docs/BUSINESS_PROCESSES.md](docs/BUSINESS_PROCESSES.md)** for the end-to-end flows, and
**[supabase/docs/README_LEDGER.md](supabase/docs/README_LEDGER.md)** for the ledger-first policy every money path must follow.
Agents should also read **[.github/AGENT_CONTEXT.md](.github/AGENT_CONTEXT.md)** before editing SQL.

---

## Quick Setup (Android Studio)

### 1. Open Project
File → Open → select the `SanibonaniSave_Full` folder.
Wait for Gradle sync. Requires **AGP 9.4.1** and **JVM Toolchain 17**.

### 2. Configure Secrets
Copy `local.properties.template` → `local.properties` and fill in Supabase and YoCo credentials.

### 3. Add Firebase
Download `google-services.json` from your Firebase project and place it in the `app/` folder.

---

## Tech Stack

| Component | Technology | Version |
| :--- | :--- | :--- |
| **Language** | Kotlin (K2 Compiler) | 2.4.21 |
| **Build System** | Gradle + AGP | 9.4.1 |
| **UI** | Jetpack Compose | 2026.09.00 (BOM) |
| **DI** | Hilt | 2.60.1 |
| **Local Cache** | Room | 2.8.5 |
| **Backend** | Supabase | 3.8.0 |
| **Annotation Processing** | KSP | 2.3.12 |

> Versions are centrally managed in `gradle/libs.versions.toml`.

---

## 🏛️ Core Architectural Patterns

### 1. Financial Precision (`Money` Model)
All financial logic has been migrated from `Double` to `Money` (BigDecimal). This prevents catastrophic rounding errors in South African Rand (ZAR) calculations.
- **Ratio Math**: Use `total.ratio(goal)` for percentages.
- **Precision**: Enforced 2-decimal scale via `RoundingMode.HALF_EVEN`.

### 2. Parallel Warmup Pattern
Dashboards leverage `async/await` coroutines to fetch data streams in parallel, ensuring a high-performance "instant-load" experience even with large datasets.

### 3. ML-Ready Behavior Tracking
The behavior tracking system is optimized for exporting datasets to **Azure AI Foundry**.
- **JSONL Export**: Supports SFT and DPO formats.
- **Labelled Truth**: Simulation tool injects verified fraud patterns for model training.

---

## 💎 Specialized Group Modules

| Group Type | Key Specialized Logic |
| :--- | :--- |
| **Burial Society** | Actuarial risk analysis and death-benefit management; required reserve is five months of contributions (`GroupFinancialPolicy`). |
| **Stokvel** | Annual payout projections, 10% balance reserve. |
| **ROSCA** | Automated turn-taking, rotation scheduling, and pot derivation from ledger-verified contributions. |
| **Investment Club** | Unit-based valuation (NAV = `group.balance`) with cost-basis equity share; probation acts as a lock-in. |
| **Emergency Fund** | 20% balance reserve plus the 50% single-withdrawal cap enforced in `PayoutGuard`. |

---

*"Sanibonani" — Hello everyone, in Zulu & Ndebele*
