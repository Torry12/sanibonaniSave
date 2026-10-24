# MapView Freeze and ANR Fix Plan

The application is experiencing Application Not Responding (ANR) errors on the **Discover Groups** screen, specifically related to the `MapView` implementation (`SaOsmMap`). The root cause is heavy processing (marker creation, filtering, and icon generation) being performed on the UI thread inside the `AndroidView` `update` block, which executes on every recomposition.

## Proposed Changes

### UI Components

#### [MODIFY] [MapComponents.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/components/MapComponents.kt)
- Refactor `SaOsmMap` to move marker management from the `update` block to a `LaunchedEffect`.
- Use `remember(groups)` to filter valid groups only when the input list changes.
- Implement a diffing or "once-per-change" update mechanism for the `RadiusMarkerClusterer`.
- Optimize icon caching and ensure `createMarkerIcon` is not called more than necessary.
- Apply similar optimizations to `SaLocationPickerMap`.

#### [MODIFY] [BrowseGroupsScreen.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/features/group/ui/BrowseGroupsScreen.kt)
- Implement search debouncing for the group search field. This will prevent rapid state emissions and redundant map re-renders while the user is typing.

## Verification Plan

### Automated Tests
- Run existing unit tests for `GroupViewModel` to ensure filtering logic remains correct.
- `gradlew :app:connectedDebugAndroidTest` (if a device is available) to verify map interactions.

### Manual Verification
1. Open **Discover Groups** screen.
2. Toggle the Map view.
3. Rapidly type in the search bar.
4. Verify that the UI remains responsive and no ANR dialog appears.
5. Verify that markers appear correctly for the filtered results.
6. Zoom in/out and verify smooth performance.
