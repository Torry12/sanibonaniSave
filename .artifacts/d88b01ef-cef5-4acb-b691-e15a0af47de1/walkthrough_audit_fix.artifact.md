# Walkthrough - Resolve Audit Tab Errors & Consistency Warning

I have successfully resolved the "unexpected response" error in the Audit tab and implemented a "Fix Now" mechanism for the Financial Consistency Warning.

## Changes Made

### 1. Fixed Serialization Mismatch
- **Repository Fixes**: Updated [AuditRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/AuditRepositoryImpl.kt) and [LedgerRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/LedgerRepositoryImpl.kt) to use `decodeAs<T>()` instead of `decodeSingleOrNull()`.
- **Reason**: The backend RPC functions return a single `JSONB` object (e.g., the report), which PostgREST serves as a single object, not a list. Using `decodeAs` ensures the app correctly parses these singular responses.

### 2. Implemented Ledger Consolidation Action
- **ViewModel Logic**: Added `consolidateLedger()` to [AdminOverviewViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/viewmodel/AdminOverviewViewModel.kt). This function calls the backend's deep ledger recomputation and then refreshes the group context to update the UI balance.
- **UI Interaction**: Enhanced [IntegrityBanner.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/screens/admin/components/IntegrityBanner.kt) to include a **FIX NOW** button when a consistency issue is detected.
- **Wiring**: Connected the banner's button to the ViewModel's consolidation logic in [AdminDashboardScreen.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/screens/admin/AdminDashboardScreen.kt).

## Verification Results

### Build Status
- Ran `./gradlew.bat :app:assembleDebug` and it completed successfully.

### Manual Verification Path
1. **Audit Tab**: Click "Generate Report". The error dialog should no longer appear, and the audit stats (Liquidity, Risk, etc.) should populate.
2. **Dashboard**: When the "Financial Consistency Warning" appears (e.g., after a manual DB edit or initial setup), click **FIX NOW**. The group balance should update to match the ledger sum, and the warning should disappear.

> [!IMPORTANT]
> The "Fix Now" action triggers a deep backend recomputation. Depending on the size of the group's transaction history, this may take a few seconds. The UI will show a loading state during this process.
