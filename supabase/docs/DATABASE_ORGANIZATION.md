# 🗄️ SanibonaniSave — SQL Organization Guide

This document explains the organization of the SQL infrastructure for the SanibonaniSave platform as of June 2026 (Canonical Release).

---

## 🚀 The Canonical Source of Truth: `v4_canonical/`

The authoritative backend scripts are located in `supabase/v4_canonical/`. This set of scripts is designed to rebuild the platform from a clean slate with hardened logic and synchronized schema.

### Master Driver
- **`SUPABASE_COMPLETE_RESET_AND_REBUILD.sql`**: Orchestrates the entire deployment in the correct order.

### Script Breakdown
1.  **`00_RESET.sql`**: Destroys all existing tables in the `public` schema.
2.  **`01_SCHEMA.sql`**: The primary data structure definition (Tables, Extensions, Indexes).
3.  **`02_FUNCTIONS.sql`**: Business logic triggers, atomicity helpers, and RPCs.
4.  **`03_SECURITY.sql`**: Row-Level Security (RLS) policies and user permission grants.
5.  **`04_ADMIN_SETUP.sql`**: Provisions the primary platform administrator.
6.  **`05_VIEWS.sql`**: High-performance dashboard projections.
7.  **`06_SEED_DATA.sql`**: Comprehensive test data (10 Groups, 100 Members).

---

## 🛠️ Folder Hierarchy

| Folder | Status | Purpose |
| :--- | :--- | :--- |
| `v4_canonical/` | **ACTIVE** | **Current authoritative production-ready logic.** |
| `functions/` | **ACTIVE** | Supabase Edge Functions (WhatsApp gateway, etc.). |
| `migrations/` | **ACTIVE** | Supabase CLI version tracking. |
| `archived_sql/` | **LEGACY** | Retired scripts from previous iterations (v1-v3), including old base schema and experimental seeds. |
| `maintenance/` | **UTILITY** | Diagnostic queries and one-off maintenance scripts. |

---

## ✅ Consistency Standards

1.  **Ledger Type Safety**: All `transaction_id` columns in `group_ledger` and `platform_ledger` are enforced as `TEXT` to accommodate alphanumeric gateway references.
2.  **Trigger Resilience**: Triggers (like member count updates) include defensive logic (`GREATEST(0, ...)`) to prevent negative underflows during deletions.
3.  **Schema Alignment**: The `02_FUNCTIONS.sql` script includes a mandatory "Auto-Alignment" block to ensure column types are compatible before function creation.
4.  **Bulletproof Security**: Policies in `03_SECURITY.sql` use existence checks to prevent "relation does not exist" errors during partial deployments.
