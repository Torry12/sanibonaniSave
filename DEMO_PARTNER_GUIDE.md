# 🌟 SanibonaniSave — Business Partner Demonstration Guide

**Platform Overview**: South African Savings Groups Administration & Financial Inclusion Platform (Android + Kotlin + Jetpack Compose + Supabase)
**Target Audience**: Fintech Investors, Burial Society Executives, Stokvel Federation Leaders, and Enterprise Banking Partners.

---

## 🎯 Executive Value Proposition

SanibonaniSave is a comprehensive, production-grade financial infrastructure platform designed specifically for the informal and semi-formal cooperative savings economy in South Africa (Burial Societies, Stokvels, ROSCAs, Investment Clubs, and Emergency Funds).

Unlike generic banking apps, SanibonaniSave delivers **group-type specific business logic**, **offline-first outbox resilience**, **high-precision BigDecimal financial ledgers**, and **multitenant administrative governance**.

---

## 🏛️ Core Group Type Pillars

### 1. Burial Society (Funeral Insurance & Risk Pooling)
- **Actuarial Solvency Engine**: Calculates mortality risk profiles (`SA_WORKING_AGE_MORT_PER_1000`) and sustainability metrics.
- **Automated Claims & Eligibility**: Validates burial claim eligibility against accumulated contributions and waiting periods.

### 2. Stokvel & ROSCA (Rotating Savings & Credit Associations)
- **Cyclical Payouts**: Automatically derives pots from ledger-verified contributions.
- **Loan Deduction Automation**: Automatically deducts outstanding member loans from cycle payouts to protect group liquidity.

### 3. Investment Club
- **Net Asset Value (NAV)**: Strictly tracks group balance and fractional unit prices (`balance.ratio(totalContributions)`).
- **Fractional Ownership**: Precise decimal allocation for dividend distributions and asset growth.

---

## 🛡️ Enterprise Engineering Highlights

1. **High-Precision Financial Ledger (`Money` Domain Model)**:
   - Zero floating-point drift. All currency math is executed using 2-decimal scale `BigDecimal` with strict rounding modes (`HALF_EVEN`).
2. **Offline-First Resilience (Outbox Pattern)**:
   - Designed for low-connectivity environments across rural and urban South Africa. Failed network writes are queued in a local Room SQLite `pending_operations` table and auto-reconciled upon reconnection.
3. **Multi-Gateway Payment Layer**:
   - Integrated with **Stitch** (Instant EFT), **PayFast**, **YoCo** (Card payments), and manual bank transfers via a standardized payment adapter layer.
4. **Real-time Synchronization**:
   - Multiplexed Supabase Realtime socket channel (`rt_sanibonani_public`) ensures zero-latency state reflection across group treasurers and members.

---

## 🎬 3-Step Partner Demonstration Script

### Step 1: Platform Administrator Portal (Global Oversight)
- **Login**: `torrymsimango@gmail.com` (Password: `Pass123!`)
- **Key Features to Showcase**:
  - **Analytics Dashboard**: Real-time overview of total platform balance, active groups, and geographic distribution across South African provinces.
  - **Fee Management**: Configurable platform subscription fees, registration fees, and late-fee penalties.
  - **Support Mode (Impersonation)**: Securely access any group or member view for remote assistance without compromising credentials.

### Step 2: Member & Treasurer Experience
- **Login**: `persona.1.1@master.example.com` (Password: `Pass123!`)
- **Key Features to Showcase**:
  - **Interactive Financial Dashboard**: View monthly contribution breakdown, current shortfall, and credit overpayments in real time.
  - **Instant Payment Gateway**: Initiate secure payments via Card (YoCo), Instant EFT (Stitch), or Bank Transfer with real-time shortfall preview.
  - **Loan & Claim Management**: Request loans, check amortization schedules, and track burial claims transparently.

### Step 3: Resilience & Auditability
- **Maintenance Tab**: Showcase system-wide health scores, automated ledger integrity checks (detecting balance drifts instantly), and database export tools.

---

## 📊 Technical Stack Summary

| Layer | Technology |
| :--- | :--- |
| **Mobile Client** | Kotlin 2.4.20, Jetpack Compose (Material 3 + Adaptive Layouts) |
| **Dependency Injection** | Dagger Hilt 2.60.1 |
| **Local Persistence** | Room SQLite (v78 schema with precision converters) |
| **Backend & Cloud** | Supabase (PostgreSQL + PostgREST + Realtime + Storage + Auth) |
| **Networking** | Ktor Client & Retrofit with OkHttp Logging |
