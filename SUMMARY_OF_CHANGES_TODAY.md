# Summary of Implementation Changes — Today

This document details the critical bug fixes, UI enhancements, and actuarial features implemented today to improve the stability, security, and fairness of the SanibonaniSave platform.

---

## 1. Critical Bug Fixes & Stability
### Supabase Realtime Correction (`Repositories.kt`)
- **Issue**: The compiler was failing with "No value passed for parameter 'builder'" because it preferred the member function `channel(String, RealtimeChannelBuilder)` over the extension function.
- **Fix**: Appended empty lambdas `{}` to all `supabase.realtime.channel()` calls to force the selection of the correct DSL-based extension function.
- **Postgres Filtering**: Fixed a "private access" error by using the `filter(column, operator, value)` method instead of direct assignment to the private `filter` property in `PostgresChangeFilter`.

### Navigation Integrity (`MemberScreens.kt`)
- **Dynamic Policy Linking**: Replaced a hardcoded `"dummy_policy_id"` in the Member Portal with a dynamic lookup. The "View Policy Progress" button now correctly navigates to the group's active policy by inspecting contribution records or falling back to a group-level default.

---

## 2. Admin Dashboard & Group Management
### Intelligent Status & Suspension
- **Platform Control**: Added `is_platform_suspended` to the `Group` model and database schema. Groups now only show as "Suspended" if they are manually flagged by a platform admin or have unresolved fee issues.
- **Realtime Card Updates**: Re-engineered the `AdminViewModel` to trigger immediate actuarial recalculations whenever group or member data changes. Cards for **Members**, **Balance**, and **Risk Score** now update in realtime.
- **Integrated Observation**: Unified group, member, and fee status observation into a single persistent lifecycle in the `AdminViewModel`.

---

## 3. Member Portal UX Enhancements
### Location & Mapping (`MemberScreens.kt`)
- **Group Context**: Added the group's township, city, and province to the Member Dashboard "Overview" tab.
- **Interactive Maps**: Integrated the `SaOsmMap` component into the member's view. Members can now see their society's physical base of operations directly in the portal, matching the "Group Details" experience.

---

## 4. KYC & Upload Workflow Improvements
### Document Management (`MemberViewModel.kt` & `Repositories.kt`)
- **File Extensions**: Refined the `uploadDocument` logic to extract and append file extensions (e.g., `.pdf`) to storage paths, ensuring files are saved and retrieved with correct formats.
- **Status Reset Logic**: Implemented a business rule where re-uploading a document automatically resets its status to `"pending"` in the database, signaling administrators that a new review is required.
- **Repository Pattern**: Moved database update logic from the ViewModel to `MemberRepository.updateMemberDocuments` to maintain clean architecture and ensure local Room cache synchronization.

---

## 5. Actuarial & Financial Engineering
### Collective Progress Tracking (`PolicyRepository.kt`)
- **Goal Measurement**: Re-engineered the "Policy Progress" calculation. Instead of only counting payments explicitly tagged with a policy ID, the system now measures the **total contributions collected by the group** (including monthly fees and joining fees) against the target goal.

### Dynamic Joining Fee Logic (`ActuarialRepositoryImpl.kt`)
- **Equity Buy-In**: Designed and implemented `calculateDynamicJoiningFee`. 
- **Fairness Principle**: As a group accumulates reserves, new members are charged a dynamic "buy-in" fee (40% of the per-member reserve share) to ensure they contribute fairly to the safety net built by founding members.
- **Fraud Prevention**: Prevents "payout hunting" by increasing the cost of entry for wealthy groups.

---

## 6. Security & Database Audit
### RLS Policy Identification
- Identified that the "new row violates row-level security policy" error was due to missing `INSERT` and `UPDATE` permissions for members.
- **Required Fix**: SQL policies for `members` and `contributions` tables have been documented for execution in the Supabase Editor.

---
**Date**: March 28, 2026  
**Status**: Implementation Complete / Pending SQL Execution
