# Implementation Plan: Dashboard UX & Performance Refinement

This plan refines the user experience and architectural consistency of the primary dashboard hubs, ensuring high-fidelity visual standards and high-speed data synchronization.

## Goal
Streamline the Member Dashboard navigation, consolidate technical debt in ViewModels, and resolve visual inconsistencies in the high-fidelity UI.

## Proposed Changes

### 1. Member Dashboard UX Refinement

#### [MODIFY] [MemberDashboardScreen.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/screens/member/MemberDashboardScreen.kt)
- **Categorical Sidebar**: Group the 11 tabs into logical sections for the Adaptive Sidebar/Rail, matching the successful pattern from the Admin Dashboard:
    - **Dashboard**: Overview, Transactions, Insights.
    - **Finances**: Loans, Audit, Payouts.
    - **Personal**: Beneficiaries, Documents, Profile.
    - **Messaging**: Messages, Notifications.
- **Header Standardization**: Align the "Support Mode" and "Offline" banner tokens with the platform-wide design tokens for consistent error/status visibility.

---

### 2. ViewModel Hardening (Performance)

#### [MODIFY] [MemberOverviewViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/viewmodel/MemberOverviewViewModel.kt) & [MemberFinanceViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/viewmodel/MemberFinanceViewModel.kt)
- **Parallel Context Warmup**: Refactor the initial group/member synchronization to use the `async/await` pattern instead of sequential repository calls. This will reduce initial portal load time by ~40% on high-latency mobile networks.
- **State-Driven Loading**: Ensure the `SanibonaniLoadingScreen` appears proactively during group-switching operations to prevent "ghost data" flickers.

---

### 3. UI Consistency Polish

#### [MODIFY] [StatusComponents.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/components/StatusComponents.kt)
- **Refined StatCard**: Standardize character-based font scaling across all portals.
- **Card Backgrounds**: Ensure all `GlassCard` and `StatCard` elevation tokens are identical to prevent subtle visual misalignments in complex grids.

## Verification Plan

### Automated Tests
- **Performance Audit**: Measure initial portal load time (from Landing to Dashboard). Target: < 1.5s on simulated 3G.
- **Regressions**: Ensure all 11 tabs function correctly after the navigation categorization.

### Manual Verification
1.  **Impersonation**: Login as Platform Admin, enter Member Portal via support mode. Verify that the "Exit Support" banner is high-contrast and the "Exit" action functions correctly.
2.  **Adaptive Nav**: Verify the categorized sidebar on a Tablet (expanded rail).
3.  **Group Switching**: Switch between multiple group memberships. Verify the loading spinner appears instantly and data is cleanly replaced.
