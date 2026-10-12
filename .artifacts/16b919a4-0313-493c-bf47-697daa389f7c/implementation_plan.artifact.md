# Refactor and Polish Implementation Plan

The goal is to clean up the codebase, extract hardcoded UI strings, and ensure consistent component usage across the application.

## User Review Required

> [!NOTE]
> This task involves widespread non-breaking changes to UI code (extracting strings) and minor logic cleanup.

## Proposed Changes

### [UI & Localization]

#### [MODIFY] [strings.xml](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/res/values/strings.xml)
- Add missing strings for Platform Admin portal: "Financial Escalations", "Payout Requests", "High-Risk Watchlist", "Impersonate", "Summary", etc.

#### [MODIFY] [DisbursementsTab.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/features/admin/ui/tabs/platform/DisbursementsTab.kt)
- Replace hardcoded strings with resource references.
- Ensure consistent spacing and alignment.

#### [MODIFY] [PlatformComponents.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/features/admin/ui/components/PlatformComponents.kt)
- Standardize `StatusChip` internal logic.
- Extract button labels and detail row headers.

### [Code Quality & Optimization]

#### [MODIFY] [ActuarialConstants.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/usecase/admin/ActuarialConstants.kt) & [ActuarialMath.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/usecase/admin/ActuarialMath.kt)
- Clean up unused imports and properties identified during the actuarial refactor.

#### [MODIFY] [MemberComponents.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/features/member/ui/MemberComponents.kt)
- Clean up unused imports.

#### [MODIFY] [BaseViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/features/common/presentation/BaseViewModel.kt)
- Ensure consistent error handling propagation.

## Verification Plan

### Automated Tests
- Run `:app:testDebugUnitTest` to ensure UI refactors didn't break ViewModel state logic.

### Manual Verification
- Cold start the app to verify bootloader strings.
- Navigate through all Platform Admin tabs to check for consistent styling and translated strings.
