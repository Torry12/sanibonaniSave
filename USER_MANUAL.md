# 📘 SanibonaniSave — Comprehensive User Manual

Welcome to **SanibonaniSave**, South Africa's premier digital financial management and governance platform for stokvels, burial societies, investment clubs, ROSCAs, and savings groups.

---

## 🚀 1. Getting Started

### Installation & Launch
1. **Instant Bootloader**: Upon launching the app, the 3D animated translucent glass sphere branding runs smoothly for 3 seconds while connecting to secure cloud servers and hydrating your session data.
2. **Authentication**: Log in using your registered email and secure password, or register a new account via the onboarding wizard.

---

## 👤 2. Member Portal

The Member Portal empowers individual savers to track their financial standing, make contributions, apply for loans, and manage beneficiaries.

### Tabs & Features:
* **Overview Tab**: Displays total savings, next payment due dates, and recent ledger activity.
* **Transactions Tab**: View detailed ledger entries, export statements as CSV, or download PDF statements (individual and group statements).
* **Finance & Loans Tab**:
  * Check loan eligibility instantly via automated rules (maximum borrowing power = 30% of contributions over the last 6 months).
  * Apply for short-term emergency micro-loans (up to 3-month term) with South African NCR industry standard interest rates (20% p.a.) and transparent flat platform processing fees (R15.00).
  * View amortization schedules with exact principal and interest breakdowns.
* **Beneficiaries Tab**:
  * Manage dependents and beneficiaries.
  * Upload beneficiary ID documents directly under each beneficiary item.
  * For burial societies, initiate and track burial insurance claims.
* **Documents Tab**:
  * Access core KYC documents (Identity Document, Proof of Residence, Marriage Certificate).
  * Download official Group Constitutions and Membership Certificates.
* **Messages & Notifications Tab**:
  * Receive real-time activity notifications and alerts filtered by category (All, Financial, System).
  * Send direct messages and inquiries to group administrators.
* **Profile Tab**:
  * Manage banking details (FNB, Standard Bank, Absa, Nedbank, Capitec).
  * Enable biometric login (fingerprint / face ID) and monitor live synchronization status.

---

## 🛠️ 3. Administrator & Group Management Portal

Group Administrators and Platform Admins have access to robust management tools to oversee group finances, governance, and compliance.

### Core Administrator Features:
* **Dashboard Overview**: Real-time group financial health, member occupancy metrics, and risk scores.
* **Loans Management Tab**: Review pending loan applications, inspect applicant KYC verification status, and approve/disburse loans with single-tap audit logging.
* **Financial Ledger & Reconciliation**:
  * View and reconcile bank transactions against group ledger entries.
  * Consolidate ledgers and resolve payment discrepancies instantly.
* **Member Management**:
  * Inspect member details, KYC documents, and contribution standing.
  * Promote members from probationary status upon completion of requirements.
* **Platform Fee Management**:
  * Configure global platform settings (monthly member fees, registration fees, late fee percentages, shortfall recovery fees, default loan interest rates, and loan processing fees).
  * Map global fees to granular group tiers.
* **All Groups & Provinces**:
  * Browse groups organized into collapsible provincial accordions (Gauteng, Western Cape, KwaZulu-Natal, etc.).

---

## 🛡️ 4. Support Mode & Administrative Auditing

* **Support Mode**: Platform administrators can enter Support Mode to view and assist member accounts as an administrator. A persistent amber banner reminds users that support mode is active.
* **Top-Bar Actions**: Quick access to support mode exit routing directly from the top app bar.
* **Audit Center**: Comprehensive group and platform audit reports covering solvency ratios, reserve adequacy, inflow/outflow liquidity, and member risk distribution.

---

## 🔒 5. Security & Data Protection

* **End-to-End Encryption**: All financial transactions and uploaded KYC documents are securely encrypted and protected via PostgreSQL Row-Level Security (RLS).
* **Cent-Accurate Financial Engine**: All monetary calculations utilize high-precision BigDecimal arithmetic wrapped in `Money` to eliminate floating-point drift.
