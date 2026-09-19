# Implementation Plan - Update and Sync All Databases

This plan addresses the request to "update and sync all databases" by ensuring structural alignment between the Supabase backend and the Room local cache, applying the latest financial and audit patches, and performing a full data synchronization.

## User Review Required

> [!IMPORTANT]
> This process involves resetting the local Supabase database. If you have any unsaved local test data that is NOT part of the seed scripts, it will be lost. The Room database will be migrated to version 62.

## Proposed Changes

### 1. Supabase Backend
Ensure the local Supabase instance is running the latest schema (Version 7.1) including the new Audit and Ledger consolidation facilities.

#### [ACTION] Execute Local Reset
- Run `scripts/run-supabase-local-reset.ps1` to rebuild the local Supabase environment.

### 2. Android App (Room Database)
Align the Room schema with the latest Supabase tables and add missing DAOs.

#### [MODIFY] [SanibonaniDatabase.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/local/SanibonaniDatabase.kt)
- Increment version to 62.
- Add `BankTransactionDao` to the abstract methods.
- Add `AuditLogEntity` to the entities list.

#### [NEW] [AuditLogEntity.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/local/AuditLogEntity.kt)
- Define Room entity for `AuditLog` to support offline audit trails.

#### [MODIFY] [Migrations.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/local/Migrations.kt)
- Add `MIGRATION_61_62` to handle the new `audit_logs` table and `bank_transactions` indices.

### 3. Data Synchronization
Trigger a full sync to populate the local database with the fresh backend state.

#### [MODIFY] [SyncRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/SyncRepositoryImpl.kt)
- Ensure `syncAllData` includes the new `BankTransaction` and `AuditLog` (optional, for groups/members).

## Verification Plan

### Automated Tests
- Run `SanibonaniDatabaseTest` (if exists) or a new test to verify migration 61 -> 62.
- Run `SyncRepositoryTest` to verify data flow.

### Manual Verification
- Deploy the app to an emulator/device.
- Trigger "Sync All" from the UI (or verify it runs on startup).
- Check the "Audit" tab to ensure data is correctly displayed after sync.
