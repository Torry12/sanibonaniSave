# Current Task: Authentication Stabilization & UI Verification

## Completed Actions
- [x] Fixed `IllegalStateException: Nesting verticalScroll` in `SharedComponents.kt`.
- [x] Added `adminSignUp` to `SupabaseRepository` and `SupabaseManager` to bypass email confirmation.
- [x] Updated `BroadcastFormTest.kt` to use `adminSignUp`.
- [x] Updated `MemberOnboardingIntegrationTest.kt` to use `adminSignUp`.
- [x] Updated `LiveSeeder.kt` to use `adminSignUp`.
- [x] Updated `bugs-errors.md` log with the resolution for `UnknownRestException`.
- [x] Verified project compiles successfully (`:app:assembleDebug`).

## Pending Actions
- [ ] Verify `BroadcastFormTest.kt` on a physical device or emulator (Requires active device).
- [ ] Verify `MemberOnboardingIntegrationTest.kt` on a physical device or emulator.
- [ ] Run `LiveSeeder.kt` if database seeding is required for further testing.

## Notes
- Instrumented tests couldn't be run due to "No connected devices!". 
- Code changes have been applied to all identified test locations that were previously using `signUp` and likely hitting the email confirmation barrier.
