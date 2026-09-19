# Walkthrough - Fixing Unresolved Reference 'WHATSAPP'

I have resolved the compilation error `Unresolved reference 'WHATSAPP'` in `GroupOnboardingIntegrationTest.kt`.

## Changes Made

### Domain Models

#### [MODIFY] [Models.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/model/Models.kt)

Added the `WHATSAPP` entry to the `NotificationPref` enum to align with the requirements specified in the integration tests and `AGENTS.md`.

```diff
 @Serializable
 enum class NotificationPref(val displayName: String) {
     @SerialName("in_app")   IN_APP("App Only"),
     @SerialName("email")    EMAIL("Email"),
+    @SerialName("whatsapp") WHATSAPP("WhatsApp"),
     @SerialName("both")     BOTH("Both")
 }
```

## Verification Results

### Automated Tests
- Ran `:app:compileDebugAndroidTestKotlin` which now finishes successfully.

> [!TIP]
> The `NotificationPref` enum is used across the app, including in `MemberProfileTab` for display and `SanibonaniDatabase` for persistence. The changes are backward-compatible as the database converter uses `entries.find`, which will automatically pick up the new value.
