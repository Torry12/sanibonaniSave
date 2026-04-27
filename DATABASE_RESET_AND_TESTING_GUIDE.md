# Database Reset & Comprehensive Testing Guide

This document explains how to reset the SanibonaniSave database and use the mock data to test various application scenarios.

## 1. Database Reset Procedure
To clear all data and start fresh, run the first part of the SQL script in the Supabase SQL Editor. This uses `TRUNCATE ... CASCADE` to ensure all tables are emptied while respecting foreign key relationships.

## 2. Mock Data Scenarios
The provided SQL script populates the database with several real-world scenarios:

### Scenario A: The Healthy Group ("Sunshine Stokvel")
- **Status**: Paid & Active.
- **Admin**: Your currently logged-in user.
- **Testing Goal**: Verify the "Happy Path." You should be able to see all Quick Actions unlocked, view policy progress, and see a healthy group balance.

### Scenario B: The Overpayment Logic ("Sunshine Admin")
- **Setup**: Member 1 has paid R1500 for a R500 monthly requirement.
- **Testing Goal**: Navigate to the Member Portal. You should see that contributions are recorded for the current month and the next two months in advance.

### Scenario C: The Shortfall & Risk Detection ("Struggling Payer")
- **Setup**: Member 2 has a partial payment (R200 instead of R500) and an overdue payment from last month.
- **Testing Goal**: Log in as Admin and check the Analytics tab. The risk score should reflect these defaults, and the Actuarial Review should flag this member.

### Scenario D: The Suspended Group ("Rainy Day Burial Society")
- **Status**: Suspended due to non-payment of platform fees.
- **Testing Goal**: Log in as Admin. You should see a 🔴 **Red Banner** and all Quick Action buttons should be locked. Test the "Restore" button to trigger the payment flow.

### Scenario E: New Group Onboarding ("Zama Zama Savings")
- **Status**: Pending Activation.
- **Testing Goal**: Verify the first-time user experience. The Admin must pay the registration fee before they can manage the group.

## 3. Financial Reporting Function
We have implemented a server-side function `get_member_financial_report(uuid)`.
- **Purpose**: Calculates real-time shortfalls and future 3-month obligations.
- **Usage**: Run `SELECT * FROM get_member_financial_report('member_uuid_here');` in SQL Editor.

## 4. Storage & Uploads
- **Bucket**: `documents`
- **Testing**: Go to the Member Portal -> Documents tab. Upload a PDF. If the SQL script was run correctly, the RLS policies will allow the upload, and you'll see an "Uploading..." progress indicator.

---
**Warning**: Running the reset script will permanently delete all existing user-created groups and members. Ensure you have backed up any critical data before proceeding.
