# Platform Admin Dashboard Upgrade

This document outlines the changes made to the Platform Admin Dashboard to enable dynamic fee management and enhanced actuarial analytics across all groups.

## 1. Dynamic Fee Management
The Platform Admin can now set global platform fees that apply to all groups.

### Features
- **Member Admin Charge**: Set the monthly fee charged per member (default: R10).
- **Group Registration Fee**: Set the one-time fee for new groups (default: R700).
- **Global Settings Update**: Changes persist to Supabase and affect future fee calculations.

## 2. Enhanced Actuarial Analytics
The Platform Admin has access to aggregated and group-specific actuarial data for strategic planning.

### Analytics Suite
- **Network-Wide KPIs**: Total groups, total members, combined balance, and total platform revenue.
- **Risk Aggregation**: Average composite risk score across the platform.
- **Group Distribution**: Breakdown of groups by province and type.
- **Detailed Group Metrics**: Access to `ActuarialMetrics` for every group, including:
  - Reserve Adequacy
  - Solvency Margin
  - Loss Ratio
  - Composite Risk Score

## 3. Technical Implementation

### Repository Layer
- `PlatformRepository`: Added `updateGlobalFees(memberCharge: Double, registrationFee: Double)` and `getDetailedActuarialMetrics(groupId: String)`.
- `ActuarialRepository`: Used to compute metrics for any group on demand.

### ViewModel Layer
- `PlatformAdminViewModel`:
  - New state fields for global fee inputs.
  - Integration with `ActuarialRepository` to fetch detailed metrics for the selected group.
  - Error handling for fee updates.

### UI Layer
- **Fee Management Tab**: Dedicated screen for updating `MONTHLY_PER_MEMBER` and `REGISTRATION` constants (stored in a `platform_settings` table).
- **Analytics Tab**: Enhanced with charts and list-based group risk summaries.

## 4. Database Schema Changes
- **Table**: `platform_settings`
  - `key` (TEXT, Primary Key)
  - `value` (NUMERIC)
  - `updated_at` (TIMESTAMP)

---
*Date: May 2024*
*Version: 2.0.0*
