# Actuarial Viability Planning & Dynamic Contributions

This document details the implementation of the actuarial viability planning engine, which allows group admins to determine and set sustainable contribution levels based on specific financial goals and timelines.

## 1. Actuarial Logic (ActuarialRepository)

The `ActuarialRepository` has been enhanced with a `calculateViabilityPlan` function that computes required contribution levels based on group type, member count, and goal parameters.

### Viability Algorithms
- **Base Calculation**: `Monthly_Per_Member = Goal_Amount / (Active_Members * Period_Months)`
- **Burial Society Adjustments**:
    - Adds a **25% safety buffer** to the calculated monthly amount.
    - Requires **2 months upfront** as an initial contribution to ensure immediate claim readiness.
- **Investment/Savings Groups**:
    - Factors in a **8% projected annual return** (compounded monthly) to reduce the required individual contribution.
    - Calculation: Uses the Future Value of an Ordinary Annuity formula: `Goal = PMT * [((1 + r)^n - 1) / r]`
- **Risk Buffers**:
    - Automatically flags groups with **fewer than 5 members** as high-burden, suggesting recruitment to lower individual costs.

## 2. Admin Empowerment (AdminViewModel)

Group Admins can now perform "What-If" analysis directly from their dashboard:
- **Parameter Manipulation**: Admins can adjust the `Goal Amount` and `Time Period` in real-time.
- **Dynamic Feedback**: The system instantly recalculates the `Initial Contribution`, `Monthly Contribution`, and `Projected Total Value`.
- **One-Tap Implementation**: Admins can apply the suggested contribution to the group settings with a single click.

## 3. Member Transparency & Communication

When an admin applies a new contribution level:
- **Automated Notifications**: A system-wide message is sent to all group members via the `NotificationRepository`.
- **Message Content**: "Group Admin has updated the monthly contribution to R[Amount] based on group viability goals."
- **Transparency**: Members see the rationale for the change, fostering trust and group accountability.

## 4. UI Implementation (ViabilityPlanningTab)

The `AdminDashboard` now includes a dedicated **Viability** tab:
- **Input Section**: Clean interface for entering target amounts and durations.
- **Result Card**: A high-impact visualization of the calculated plan, including projected values and actuarial warnings/advice.
- **Apply Action**: A prominent button to commit the changes to the group's official settings.

---
**Status**: Implementation complete. Build passing.
