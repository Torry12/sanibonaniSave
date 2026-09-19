# Walkthrough - Database Backup System

I have implemented a comprehensive database backup and recovery system for the SanibonaniSave platform. This system includes cloud snapshots of critical backend tables and local database portability for debugging.

## Key Features

### 1. Cloud Data Snapshots
- **Mechanism**: A new action `create_data_snapshot` added to the `mobile-admin-actions` Edge Function.
- **Scope**: Exports all rows from critical tables (`groups`, `members`, `contributions`, `loans`, `payouts`, `ledger`, etc.) into a structured JSON file.
- **Security**: Snapshots are stored in a private `backups` bucket, restricted to the `platform_admin` role via RLS (Row Level Security) policies.
- **Traceability**: Each snapshot is timestamped for easy auditing.

### 2. Platform Admin Backup Dashboard
- **New UI**: Integrated a **Backups** tab into the Platform Administrator dashboard.
- **Cloud Management**: Admins can trigger new snapshots, list historical backups, and delete old snapshots.
- **Local DB Export**: Added utilities to export the internal Room SQLite files (`sanibonani_online.db` and `sanibonani_local.db`) to the device's shareable cache, useful for emergency low-level auditing.

### 3. Architecture Hardening
- **Domain Alignment**: Added `DatabaseBackup` model and `PlatformRepository` methods for backup orchestration.
- **Data Integrity**: Used `upsert` and RPC patterns for state transitions to ensure compatibility across environments.
- **Parallel Loading**: Updated `PlatformAdminViewModel` to fetch backups in parallel with other dashboard data for optimal performance.

## Changes by Component

### Backend (Supabase)
- **Edge Function**: Updated `mobile-admin-actions/index.ts` with `create_data_snapshot`.
- **Storage**: Created `42_BACKUP_SYSTEM_SETUP.sql` to provision the `backups` bucket and policies.

### Android Client
- **Domain**: Created [BackupModels.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/model/BackupModels.kt) and updated `PlatformRepository.kt`.
- **Data**: Implemented logic in `PlatformRepositoryImpl.kt` using `EdgeFunctionGateway` and Room file access.
- **UI**: Created [BackupTab.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/screens/admin/tabs/platform/BackupTab.kt) and integrated it into `PlatformAdminScreen.kt`.

## How to Verify
1. Log in as Platform Admin.
2. Navigate to the **Backups** tab (the 8th tab).
3. Tap **Create New Snapshot** and verify the "Cloud snapshot triggered" message.
4. Verify the snapshot appears in the **Historical Backups** list.
5. (Advanced) Tap **Online DB** or **Local DB** in the Local Debugging section to export the SQLite file.

> [!TIP]
> The cloud snapshots are stored as JSON for transparency and easy programmatic restoration if needed. For full binary point-in-time recovery, continue to rely on Supabase's managed daily backups.
