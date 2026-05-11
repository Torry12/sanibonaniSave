# 💼 BUSINESS_LOGIC_AND_FEATURE_SPEC.md

## 🎯 Core Product Vision
SanibonaniSave is a South African Savings Groups Administration Platform. It digitizes burial societies, stokvels, ROSCAs, and community savings groups, providing them with actuarial science tools and institutional investment access.

---

## 🔑 Key Features & Logic

### 1. Group Registration & Onboarding
- **Flow**: User registers group → Selects type (Stokvel/Burial Society) → Configures fees → Uploads Constitution → Pays R700 Registration Fee → Group Activated.
- **Auto-Member**: The creator is automatically registered as the first `ACTIVE` member.
- **Geocoding**: Uses Geoapify for address autocomplete and OSMDroid for map display.

### 2. Member Lifecycle
- **Statuses**: `PENDING_PAYMENT` → `PROBATION` (typically 3-6 months) → `ACTIVE`.
- **Joining**: New members register → Must pay joining fee to become active/on probation.
- **Multi-Group**: Members can belong to multiple groups. Switching groups triggers a **deep state reset** in the UI to prevent data leakage.

### 3. Financial Engine
- **Contributions**: Atomic write path via `record_contribution_v1` RPC to maintain consistency between `contributions`, `members.total_paid`, and `groups.balance`.
- **Payment Types**: `joining_fee`, `contribution`, `registration_contribution`, `late_fee`.
- **Gateways**: Integrated with YoCo for card payments and supports manual Bank Transfer/Cash entries.

### 4. Smart Loans
- **Full Lifecycle**: Request → Admin Approval → Member Agreement Acceptance → Loan Disbursement → Repayment Tracking.
- **Eligibility**: Requires > 6 months membership and "Good Standing" (all contributions up to date).
- **Surety**: Member's total contributions serve as surety.
- **Disbursement**: Atomic disbursement via `disburse_loan_v1` RPC which records a `loan_disbursement` payment, updates group balance, and logs to `audit_logs`.
- **Documentation**: Admins can upload loan contracts (PDF); members can download and must accept agreements before disbursement.
- **Default Logic**: Missed repayments are auto-extracted from accumulated contributions.

### 5. Burial Society (Actuarial) Features
- **Beneficiaries**: Members can add dependants (Burial Society type only).
- **Premiums**: `PaymentCalculator` dynamically adjusts premiums based on the number of beneficiaries over 65 and group-defined increase percentages.
- **Claims**: Member submits → Group Admin reviews → Platform Admin approves/pays out.
- **Viability Score**: Actuarial metrics (Pure Premium, Solvency Margin, Risk Score) provided to admins to ensure long-term fund sustainability.

### 6. Specialized Group Intelligence
- **ROSCA (Rotating Savings)**: Automated rotation scheduling. Determines the "Pot" size based on membership and assigns transparent payout dates to every member.
- **Investment Clubs**: Real-time Net Asset Value (NAV) and Unit Price tracking. Calculates member equity based on historical contributions and group asset growth.
- **Stokvels**: Annual payout projections. Estimates year-end fund values and individual member distributions for end-of-cycle financial planning.

### 7. Document Management & In-App Viewing
- **Buckets**: `documents` (private IDs/PoR), `loan_contracts` (private), and `constitutions` (private).
- **In-App Viewer**: Dual-choice action for remote files—users can choose to **"View In-App"** (PDF/Images) or **"Download"** to their device.
- **Multi-page PDF Engine**: Standardized paging and header redrawing logic for Loan Agreements, Constitutions, and Financial Statements.
- **Limits**: 3MB max file size per upload.
- **Tracking**: `DocumentStatus` (PENDING, VERIFIED, REJECTED).

---

## 📅 System Rules & Defaults
- **Payment Day**: Default 28th of the month.
- **Grace Period**: Default 5 days for late fees.
- **Registration Fee**: R700 (credited to admin as `registration_contribution`).
- **Probation**: Default 3 months membership before full benefits.

---

## 📱 User Portals
- **Member Portal**: Dashboard with Overview, Transactions, Loans, Beneficiaries, Documents, Messages, and Notifications.
- **Group Admin Portal**: Member management, payout requests, group settings, and viability analytics.
- **Platform Admin Portal**: Global fee management, group suspension/unsuspension, and disbursement approval.

---

## 📡 Messaging & Notifications

### 1. Multi-Channel Strategy
- **Email**: Used for formal receipts, official group communications, and magic links.
- **WhatsApp**: The primary channel for high-engagement alerts and password recovery in low-connectivity environments.
- **In-App**: Persistent notification center for transaction history and administrative tasks.

### 2. WhatsApp Integration
- **Edge Functions**: Proxies Meta Graph API calls via Supabase to keep tokens secure.
- **Compliance**: Uses Meta-approved templates for business-initiated conversations (e.g., `password_reset`, `platform_fee_due`, `general_notification`).
- **Flow**: User enters number → System normalizes to E.164 → Edge function triggers template → User receives link/code.
- **Automated Alerts**: Triggers WhatsApp notifications for overdue fees, system alerts, and loan requests (sent to group admins).

---

## 🔐 Security & Access Control

### 1. Authentication Layers
- **Magic Links**: Passwordless entry via email for increased accessibility.
- **Biometrics**: Native Fingerprint/Face ID support for "Quick Login" on trusted devices. Automatically triggers on the login screen if 'Remember Me' and biometrics were previously enabled.
- **Session Management**: JWT-based sessions with automatic token refresh via Supabase GoTrue.

### 2. Role-Based Permissions (RBAC)
- **Member**: Access to personal dashboard, group public info, and their own contributions/loans.
- **Group Admin**: full CRUD on group members, approval of contributions, and initialization of payout requests.
- **Platform Admin**: Global oversight, audit log visibility, and final disbursement authority for payouts.

---

## 📶 Offline-First Philosophy

### 1. Local-First UI
- **Room Persistence**: Every UI state is derived from the local Room database.
- **Optimistic Updates**: UI updates immediately on user action; synchronization happens in the background.

### 2. Sync Logic
- **observeAndSync**: Repositories observe local changes and attempt to push to Supabase using exponential backoff.
- **Conflict Resolution**: Last-write-wins strategy for basic fields; atomic RPCs for financial calculations to prevent race conditions.

---

## 🛠️ Administrative & Maintenance Tools

### 1. Impersonation Mode
- **Capability**: Platform Admins can view the app exactly as a specific Member or Group Admin would.
- **Auditing**: Every action taken during an impersonation session is flagged and logged in the `audit_logs` table for accountability.

### 2. Audit & Activity Logs
- **Tracking**: Critical events (logins, payments, setting changes, status transitions) are recorded with timestamps, user IDs, and IP metadata.
- **Transparency**: Provides a verifiable trail for financial disputes or regulatory compliance.

### 3. Group Viability Engine
- **Scoring**: Uses actuarial formulas (Pure Premium, Solvency Margin) to flag groups at risk of insolvency.
- **Intervention**: Platform Admins can suspend groups or adjust fee structures based on viability data.
