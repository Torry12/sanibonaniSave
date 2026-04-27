# SanibonaniSave — App Logic & Navigation Flow

This document details the core logic and navigation flows of the SanibonaniSave application. Use this as a reference when fixing or extending functionality.

---

## 1. Authentication & Role-Based Entry
The app uses Supabase for authentication. User roles determine the landing dashboard.

*   **Entry Point:** `LandingScreen`
*   **Login/Register:** `LoginScreen` / `RegisterScreen`
*   **Redirection Logic (NavGraph.kt):**
    *   `UserRole.PLATFORM_ADMIN` → `PlatformAdminScreen`
    *   `UserRole.GROUP_ADMIN` → `AdminDashboardScreen`
    *   `UserRole.MEMBER` → `MemberDashboardScreen`

---

## 2. Group Management Flow
### A. Group Creation (Admin)
1.  **Register Group:** `RegisterGroupScreen` collects group details (name, bank info, fees).
2.  **Payment:** Upon creation, redirected to `PaymentScreen` (Type: `registration`, Amount: R700.0).
3.  **Activation:** Once paid, group status becomes active, and admin lands on `AdminDashboardScreen`.

### B. Member Joining
1.  **Browse Groups:** `BrowseGroupsScreen` lists public groups.
2.  **Profile View:** `GroupProfileScreen` shows specific group details.
3.  **Join Request:** `RegisterMemberScreen` (Route: `join/{groupId}`) collects member info.
4.  **Joining Fee:** Redirected to `PaymentScreen` (Type: `joining_fee`, Amount: R200.0).
5.  **Dashboard:** On success, member lands on `MemberDashboardScreen`.

---

## 3. Financial Flows (YoCo Integration)
Payments are handled via a YoCo modal launched from `PaymentScreen`.

*   **Payment Types:**
    *   `registration`: Initial platform fee for new groups.
    *   `joining_fee`: Initial fee for a member to join a group.
    *   `contribution`: Monthly member savings contribution.
    *   `late_fee`: Penalty for overdue contributions.
*   **Recording:** On YoCo success, `PaymentRepository.recordPayment()` updates Supabase (Group balance, Member contribution history).

---

## 4. Admin Operations (AdminDashboard)
Admin manages group settings and monitors health.

*   **Settings Updates:** Update bank details, fees, and auto-suspension rules.
*   **Actuarial Metrics:** View group health (surplus/deficit) via `ActuarialRepository`.
*   **Viability Planning:** Calculate suggested contributions to reach specific savings goals using `calculateViabilityPlan`.
*   **Platform Fee Management:** Monitor group status (`DUE`, `WARNING`, `SUSPENDED`). If suspended, request restoration from Platform Admin.

---

## 5. Member Operations (MemberDashboard)
Members track their savings and policies.

*   **Contribution History:** View past payments.
*   **Make Payment:** Initiate `contribution` or `late_fee` payments.
*   **Policy Detail:** View specific burial/savings policy terms (`PolicyDetailScreen`).

---

## 6. Platform Administration (PlatformAdmin)
Global oversight of all groups.

*   **Monitor Groups:** View status of all savings groups on the platform.
*   **Manage Suspensions:** Lift suspensions for groups that have paid their platform fees or resolved issues.

---

## 7. Automated Background Tasks (WorkManager)
*   **FeeEnforcementWorker:** Runs daily to check group platform fee status.
    *   Updates status to `WARNING` or `SUSPENDED` if overdue.
    *   Triggers notifications (FCM/WhatsApp) to Group Admins.

---

## 8. Data Sync Strategy
*   **Supabase (Remote):** Source of truth.
*   **Room (Local):** Offline cache.
*   **Mappers:** `toEntity()` (Model to DB) and `toModel()` (DB to Model) handle the conversion in Repository implementations.
