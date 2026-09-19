# Walkthrough - Fixed Compilation Error in GroupListScreen.kt

I have fixed the property delegate error in `GroupListScreen.kt` that was preventing the project from building.

## Changes

### app module

#### [GroupListScreen.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/screens/group/GroupListScreen.kt)

- Updated the UI state collection to use the correct property `vm.state` instead of the non-existent `vm.listState`.

```diff
-    val state by vm.listState.collectAsStateWithLifecycle()
+    val state by vm.state.collectAsStateWithLifecycle()
```

## Verification Results

### Automated Tests
- Successfully ran the Kotlin compilation task for the app module:
  - Command: `./gradlew :app:compileDebugKotlin`
  - Result: **Build finished successfully.**

render_diffs(file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/screens/group/GroupListScreen.kt)
