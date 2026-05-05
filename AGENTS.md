# AGENTS.md — SanibonaniSave AI Agent Guide

**South African Savings Groups Administration Platform**  
Burial societies, stokvels, ROSCAs, and community savings groups with actuarial science and institutional investment access.

---

## 🏗️ Architecture Overview

### Layered MVVM + Clean Architecture (SOLID Principles)
```
UI Layer (Compose Screens + ViewModels)
    ↓ Orchestration (Use Case Layer)
    ↓ Repository Layer (Domain Interfaces)
├── Use Case Layer (SRP compliance)
│   ├── RegisterMemberUseCase (Member Registration + Group Count)
│   ├── CreateGroupUseCase (Admin-as-Member + Group Init)
│   ├── UpdateMemberStatusUseCase (Status Transitions)
│   ├── CalculateViabilityUseCase (Actuarial Logic)
│   ├── RequestPayoutUseCase (Group Fund Disbursement)
│   ├── ProcessPayoutUseCase (Notification-aware status updates)
│   └── SendNotificationUseCase (Multi-channel Messaging)
├── Repository Layer (10 segregated interfaces)
│   ├── BaseRepository (Standardized offline-first + exponential backoff)
│   ├── Supabase (remote) — PostgREST, Realtime, Storage, Auth
│   ├── PayoutRepository (Fund disbursement tracking)
│   └── Room (local cache) — fallback sync via observeAndSync
└── Domain Models (@Parcelize, @Serializable)
```

---

## 📦 Project Structure (Key Paths)

```
src/main/java/com/sanibonani/save/
├── data/
│   ├── model/Models.kt                — 9 domain models (@Serializable + @Parcelize)
│   ├── local/SanibonaniDatabase.kt    — Room DB v31 + 9 DAOs
│   ├── repository/                    — 10 repo interfaces + impls
│   └── remote/SupabaseManager.kt      — Auth session + sign-up logic
├── viewmodel/                         — 7 ViewModels (Auth, Member, Group, Admin, Payment, etc.)
└── ui/
    ├── navigation/NavGraph.kt          — 10 routes + deep links
    └── screens/                       — 7 screen groups (auth, browse, group, admin, payment)
```

---

## 🔑 Critical Patterns & Recent Updates

### 0. **Database Reset & Mock Data**
- **Reset Script**: `supabase/reset_with_mock_data.sql` — Full database reset with platform admin and test data
- **RLS Policies**: `supabase/rls_policies.sql` — Row Level Security policies for all tables
- **S- **Schema**: | Platform Admin | torrymsimango@gmail.com | torry123M |
| Group Admin 2 | admin2@test.com | password123 |
| Member 1 — Active (8 mo, multi-group, verified docs, good standing) | member1@test.com | password123 |
| Member 2 — Probation (4 mo, 2 groups, PoR pending) | member2@test.com | password123 |
| Member 3 — Active Senior (22 mo, loan holder, 3 beneficiaries, multi-group) | member3@test.com | password123 |
| Member 4 — Probation New (2 mo, paid joining fee, 2 contributions, PoR pending) | member4@test.com | password123 |
| Member 5 — Suspended (9 paid, 3 overdue, late fee outstanding, 2 groups) | member5@test.com | password123 |
| Member 6 — Pending Payment (brand new, no contributions, joining fee due) | member6@test.com | password123 |

### 1. **Gradle & AGP Compatibility**
- **Gradle Version**: **8.11.1**.
- **AGP Version**: **8.7.3**.
- **Kotlin Version**: **2.1.0**.
- **KSP Version**: **2.1.0-1.0.29**.

### 2. **Real-time Sync & Reactive UI**
- **Pattern**: ViewModels (`AdminViewModel`, `MemberViewModel`) use `combine` flows to aggregate data from multiple sources.
- **State Reset** 🆕: Multi-group switching in both Admin and Member portals triggers a **deep state reset**. This clears group-specific lists (members, payouts, beneficiaries, notifications) and metrics to prevent data leakage during transition.

### 3. **Clean Architecture & SOLID**
- **Single Responsibility (SRP)**: Business logic involving multiple repositories or complex orchestrations MUST be placed in a `UseCase`.
- **Dependency Inversion (DIP)**: ViewModels and UseCases depend on **Repository Interfaces**.

### 4. **Settings & Persistence**
- **Persistence**: `AdminViewModel.saveSettings` persists group configurations including `max_beneficiaries`, `beneficiary_increase_pct`, `goal_amount`, and `period_months`.
- **Validation** 🆕: Payout requests now include strict banking validation (7-13 digit Account No, 6-digit Branch Code) with reactive error messages in the UI.

### 5. **Error Reporting Strategy**
- **Dual-Layer Reporting**: 
    1. **Blocking Errors**: Full-screen "Retry Connection" buttons.
    2. **Transient Errors**: Inline `InfoBox` or `TextField` error states (e.g., validation failures).

### 6. **Group Registration & Onboarding**
- **Flow**: Groups are created and activated after successful payment of the registration fee. 
- **Auto-Onboarding**: The `CreateGroupUseCase` automatically registers the creator as the first `ACTIVE` member.
- **Activation Logic**: `GroupRepositoryImpl.activateGroup` handles status transitions, joining fee credits, and count increments in a single flow.
- **Admin as Member** 🆕: Group admin is automatically added as a member when the group is created. The DB trigger increments `current_members` count.
- **Registration Fee Credit** 🆕: The platform registration fee (R700) is credited as the admin's first contribution record (type: `registration_contribution`). This does NOT add to group balance as it goes to the platform.
- **Joining Fee Credit** 🆕: If the group has a joining fee, it is auto-credited to the admin's account and added to the group balance.

### 7. **Member Joining Flow** 🆕
- **Two-Phase Registration**: Members register → created with `PENDING_PAYMENT` status → pay joining fee → status updated to `ACTIVE/PROBATION`.
- **Payment Context Loading**: `PaymentViewModel.loadPaymentContext()` loads member and group data for both `contribution` and `joining_fee` payments.
- **Status Transitions**: `MemberRepositoryImpl.registerMember()` allows creating members with `PENDING_PAYMENT` status without requiring immediate payment.

### 8. **Contribution RPC Contract** 🆕
- **Atomic Write Path**: Use `record_contribution_v1` for contribution posting so `contributions`, `members.total_paid`, and `groups.balance` stay consistent.
- **Type Mapping Rule**: RPC parameter `p_type` must map to `contributions.type` (`contribution`, `joining_fee`, `registration_contribution`, `late_fee`).
- **Payment Method Rule**: Keep `payment_method` as transport/method metadata (e.g., `yoco`), not contribution type.
- **Payment Type Safety**: Never silently map unknown payment types to contribution; fail with a user-friendly error.

---

### 2. **Smart Loan Rules** 🆕
- **Eligibility**:
    1. **6-Month Membership**: Member must have been joined for > 6 months.
    2. **Good Standing**: Must be up to date with all monthly contributions.
- **Surety Logic**:
    - Member's total contributions serve as surety for the loan.
    - **Default Handling**: If a repayment is missed, the amount is automatically extracted from the member's accumulated contributions.
    - **Group Liability**: If the loan remains unpaid after the repayment period lapses and contributions are exhausted, the remainder is charged to the group's general account.
- **Contract**: A formal loan contract must be generated and available for download upon loan approval.

---

## 🕊️ Burial Society & Actuarial Features

### 1. **Actuarial Logic & Viability**
- **Centralized Calc**: `PaymentCalculator` is the single source of truth for premiums and fees.
- **Viability UI**: Admins can visualize "Suggested Strategies" and apply them directly to group settings.

### 2. **Payouts & Disbursements**
- **Workflow**: Admins request payouts → Platform Admin approves/completes.
- **Cancellation** 🆕: Admins can cancel `PENDING` payout requests directly from the history list.
- **Notifications** 🆕: `ProcessPayoutUseCase` triggers automated notifications to the group for all status changes (`PENDING` → `PROCESSING` → `COMPLETED`/`FAILED`/`CANCELLED`).

---

## 🗺️ Map & Location Features

### 1. **Group Discovery Map** 🆕
- **OSMDroid Integration**: `SaOsmMap` composable displays groups on OpenStreetMap.
- **Coordinates**: Groups with `latitude` and `longitude` appear as markers.
- **Geohash Support**: Spatial indexing via `geohash` column for proximity queries.
- **Mock Data**: Test groups include coordinates for Johannesburg, Durban, and Cape Town.

### 2. **Address Autocomplete**
- **Geoapify API**: Live address suggestions via `GeoapifyService`.
- **Auto-Populate**: Selecting an address fills city, province, and coordinates.
- **Geohash Encoding**: `LocationUtils.encodeGeohash()` generates geohash from lat/lng.

---

## 📤 Storage & Documents

### 1. **Supabase Storage Buckets** 🆕
- **`documents` bucket**: Private member documents (ID, Proof of Residence, etc.)
- **`constitutions` bucket**: Public group constitutions
- **RLS Policies**: Members can upload/view their own docs; Admins can view group member docs.

### 2. **Upload Flow**
- **`StorageRepositoryImpl`**: Handles file uploads with retry logic.
- **Path Structure**: `members/{memberId}/doc_{index}.{ext}`
- **Size Limit**: 3MB max enforced in UI via `FileUploadLimits.MAX_FILE_SIZE_BYTES`.

### 3. **Download Flow**
- **`FileDownloader`**: Uses Android `DownloadManager` for background downloads.
- **Authorization**: Bearer token passed via headers for private buckets.
- **MIME Types**: Supports PDF, JPEG, PNG detection.

---

## 🔧 Build & Secrets
- `local.properties`: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `YOCO_PUBLIC_KEY`. Injected via `BuildConfig`.

---

## 🔐 Admin & Maintenance

### 1. Platform Admin Credentials
- **Email**: `torryymsimango@gmail.com`
- **Email**: `torrymsimango@gmail.com`
- **Role**: Superuser / Platform Owner

---

## 👤 Member Portal Features

### 1. **Dashboard Tabs**
The Member Portal (`MemberDashboardScreen`) includes 8 tabs:
| Tab | Index | Description |
|-----|-------|-------------|
| Overview | 0 | Quick stats, payment action, recent activity |
| Transactions | 1 | Contribution history with CSV/PDF export |
| Loans | 2 | Smart Loans: Request loans, view balance, and repayments |
| Beneficiaries | 3 | Manage dependents (Burial Society only) |
| Documents | 4 | Upload ID, Proof of Residence, certificates |
| Messages | 5 | Direct communication with Group Admin |
| Notifications | 6 | System alerts and updates |
| Profile | 7 | Personal info and membership details |

### 2. **Multi-Group Membership** 🆕
- **Group Switcher**: Members belonging to multiple groups see a dropdown to switch active group context.
- **Deep State Reset**: `vm.switchGroup(groupId)` clears contributions, beneficiaries, notifications, and reloads group-specific data.
- **Visual Indicator**: Current group highlighted with checkmark in dropdown.

### 3. **Document Management** 🆕
- **5 Document Slots**: ID, Proof of Residence, Beneficiary Form, Marriage Certificate, Constitution.
- **Status Tracking**: Each document has `DocumentStatus` (PENDING, VERIFIED, REJECTED).
- **Download Support**: Verified documents can be downloaded via `FileDownloader` utility using `DownloadManager`.
- **Upload Limits**: Max file size of 3MB enforced via `FileUploadLimits.MAX_FILE_SIZE_BYTES`.

### 4. **Statement Export** 🆕
- **CSV Export**: `vm.exportMyStatement()` generates CSV file shared via Android Intent.
- **PDF Download**: `vm.downloadPdfStatement()` triggers background PDF generation.
- **FileProvider**: Secure file sharing via `${context.packageName}.provider`.

### 5. **Payment Calculation Display**
- **PaymentCalculation Model**: Shows `totalDueNow`, `nextDueDate`, `shortfall`, `overpayment`, `isOverdue`.
- **Visual Indicators**: Overdue accounts highlighted in red with "YOUR ACCOUNT IS OVERDUE" warning.
- **Dynamic Button Text**: Payment button shows "Pay Joining Fee" or "Make Contribution" based on member status.

### 6. **Profile Photo Upload**
- **In-line Edit**: Profile photo clickable with camera icon overlay.
- **AsyncImage**: Uses Coil for image loading with fallback to UI Avatars API.

---

## 📝 Member Registration Flow

### RegisterMemberScreen
- **Address Autocomplete** 🆕: `AutoCompleteTextField` with live address suggestions via `state.addressSuggestions`.
- **ID Validation**: 13-digit SA ID with `IDNumberTransformation` visual formatting.
- **Phone Validation**: 10-digit with `PhoneNumberTransformation` (format: 0XX XXX XXXX).
- **Province Picker**: Dropdown with `SA_PROVINCES` constant list.
- **Notification Preference**: `NotificationPref` enum selection (EMAIL, SMS, WHATSAPP, etc.).

---

## 🧪 Testing & QA
- **Sync Verification**: Ensure `startObservingGroup` (Admin) and `switchGroup` (Member) correctly reset all UI state before loading new data.
- **Validation Verification**: Test payout fields with various invalid lengths and characters to confirm the `isError` triggers.
- **Document Upload Testing**: Test with files >3MB to verify size limit enforcement.
- **Multi-Group Testing**: Test group switching with members belonging to 2+ groups to verify state isolation.
- **Joining Fee Testing** 🆕: Test member registration → payment → activation flow for groups with joining fees.
- **Map Testing** 🆕: Verify groups with coordinates appear on the Discover Groups map view.
