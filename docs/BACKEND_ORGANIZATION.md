# 🗄️ Backend Organization Guide — SanibonaniSave

This document details the canonical structure and execution order for the Supabase PostgreSQL backend.

---

## 🚀 Canonical V4 Rebuild (Current Source of Truth)

The authoritative backend scripts are located in `supabase/v4_canonical/`. This set of scripts is designed to rebuild the platform from scratch with hardened business logic and synchronized schema.

### Execution Order (Mandatory)
Run via `psql -f SUPABASE_COMPLETE_RESET_AND_REBUILD.sql` or individually:

1.  **`00_RESET.sql`**: Destructive reset. Destroys all tables in the `public` schema.
2.  **`01_SCHEMA.sql`**: Table definitions, extensions (UUID, pgcrypto, pg_trgm), and core indexes.
3.  **`02_FUNCTIONS.sql`**: Business logic, Triggers, and RPCs. Includes auto-aligning schema blocks for Ledger compatibility.
4.  **`03_SECURITY.sql`**: RLS Policies and Permissions. Bulletproof existence checks for all tables.
5.  **`04_ADMIN_SETUP.sql`**: Platform Administrator bootstrapping (`torrymsimango@gmail.com`).
6.  **`05_VIEWS.sql`**: Performance views for analytics and landing screens.
7.  **`06_SEED_DATA.sql`**: (Optional) 10 groups and 100 members with realistic contribution history and loan data.

---

## 🛠️ Deployment Scripts

-   **`supabase/apply_v4.ps1`**: PowerShell helper for one-click V4 deployment (requires Supabase CLI).
-   **`supabase/v4_canonical/SUPABASE_COMPLETE_RESET_AND_REBUILD.sql`**: The master SQL driver for manual `psql` execution.

---

## 📁 Directory Purpose

| Directory | Purpose |
| :--- | :--- |
| `v4_canonical/` | **Authoritative production-ready scripts.** |
| `base/` | Legacy baseline schema (reference only). |
| `migrations/` | Past migration history for environmental tracking. |
| `archived_sql/` | Deprecated experimental scripts and older seed variations. |
| `seeds/` | Specialized test data scenarios. |
| `functions/` | Supabase Edge Functions (WhatsApp gateway, etc.). |
| `maintenance/` | Diagnostic and cleanup scripts. |

---

## ✅ Consistency Checklist

-   [ ] **Type Safety**: `transaction_id` in ledgers must be `TEXT` (not `UUID`) to support gateway references.
-   [ ] **Constraint Handling**: Member counts must use `GREATEST(0, current_members - 1)` in triggers.
-   [ ] **Real-time Analytics**: Views must aggregate directly from source tables for dashboard accuracy.
-   [ ] **Idempotence**: All creation scripts should use `CREATE OR REPLACE` or `IF NOT EXISTS` where applicable, though V4 rebuild enforces a clean slate.
