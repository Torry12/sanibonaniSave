# 🧪 SanibonaniSave — Seed Data & Environment Setup Guide

This document provides instructions on how to populate the SanibonaniSave backend with high-fidelity test data for development, demonstration, and stress testing.

---

## 🏗️ 1. Overview of Seed Scripts

The project maintains two primary seeding mechanisms:

| File | Purpose | Scale |
| :--- | :--- | :--- |
| `06_SEED_DATA.sql` | Standard development seed for local testing. | 50 Groups, 1000 Members |
| `12_EXTENSIVE_SEED_DATA.sql` | **High-Fidelity Production Simulation**. Includes platform escalations, 1-year history, and tiered fee logic. | 100 Groups, 2000 Members |

---

## 🚀 2. How to Run the Seed Script

### Target: Online/Remote Database
The most common use case is populating the hosted Supabase environment.

1.  **Ensure CLI Access**: You must be logged into the Supabase CLI.
    ```bash
    npx supabase login
    ```
2.  **Execute the Extensive Seed**:
    Run the following command from the project root:
    ```bash
    npx supabase db query --linked -f "supabase/12_EXTENSIVE_SEED_DATA.sql"
    ```
    *Note: This script includes cleanup logic (`DELETE`) for previous SEED-prefixed data, making it safe to run multiple times.*

### Target: Local Development (Docker)
If you are running Supabase locally:

1.  **Start Supabase**:
    ```bash
    npx supabase start
    ```
2.  **Reset and Seed**:
    The local reset command uses the `sql_paths` defined in `supabase/config.toml`.
    ```bash
    npx supabase db reset
    ```

---

## 📊 3. Verifying the Data

After running the seed, you can verify the integrity of the financial environment using these SQL queries via the Supabase Dashboard or CLI:

### 1. Member & Group Count
```sql
SELECT count(*) FROM public.members; -- Should be 2,000
SELECT count(*) FROM public.groups;  -- Should be 100
```

### 2. Platform Escalations (Financial Review)
Check if the Platform Admin's "Financial Escalations" tab will be populated:
```sql
SELECT count(*) FROM public.payouts WHERE status = 'pending';
SELECT count(*) FROM public.beneficiary_payout_claims WHERE status = 'escalated';
SELECT count(*) FROM public.loans WHERE status = 'pending';
```

### 3. Ledger Integrity
Ensure member payments were correctly split into Platform and Group ledgers:
```sql
SELECT * FROM public.platform_ledger ORDER BY created_at DESC LIMIT 5;
SELECT * FROM public.group_ledger ORDER BY created_at DESC LIMIT 5;
```

---

## ⚠️ 4. Critical Rules for Seed Data

If you are modifying or creating a new seed script, you **MUST** follow these platform rules to ensure the app functions correctly:

1.  **Tiered Allocation**: Do not insert directly into `groups.balance`. Always use the `public.record_contribution_v2` or `public.increment_group_balance` RPCs. This ensures the `group_ledger` and `platform_ledger` are updated atomically.
2.  **ID Number Format**: Valid South African ID numbers (13 digits) are required for KYC status indicators in the UI.
3.  **Member Keys**: All seed members must have a `member_key` starting with `SEED-` to allow for clean targeted deletion during script re-runs.
4.  **Transaction History**: Seeding members without at least 3 months of history will result in "High Risk" flags in the actuarial engine.
5.  **Role Alignment**: Ensure the `admin_user_id` for groups matches an existing UUID in `auth.users` to allow for Group Admin login testing.

---

## 🆘 Troubleshooting

*   **Duplicate Key Violation**: If a seed fails due to a `groups_name_key` error, ensure the cleanup block at the top of the SQL script includes the specific naming patterns you are trying to insert.
*   **Check Constraint Violation**: Ensure the `type` field in `public.contributions` uses `member_fee_ledger` for platform fees (not just `member_fee`).
*   **Missing Storage Buckets**: The seed script attempts to create `avatars` and `constitutions` buckets. If these fail, verify that your Supabase user has the `storage` admin role.
