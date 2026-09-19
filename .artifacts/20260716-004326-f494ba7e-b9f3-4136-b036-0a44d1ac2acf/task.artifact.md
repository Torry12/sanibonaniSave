# Task Management

- [/] Researching Database Connections and Switching Logic
    - [x] Read `AppModule.kt` and `DevSettings.kt`
    - [x] Analyze `SupabaseManager` and `BaseRepository`
    - [x] Identify stale singleton issue in `AppModule`
    - [x] Identify observation bug in `MemberOverviewViewModel`
- [ ] Implement Dynamic Connection Switching
    - [ ] Create `SupabaseConnectionManager`
    - [ ] Update `AppModule` for dynamic provisioning
    - [ ] Refactor `NetworkModule` for dynamic Retrofit
- [ ] Fix Group Switching Bugs
    - [ ] Update `MemberOverviewViewModel` observation logic
- [ ] UI Refinement
    - [ ] Update `LandingScreen` and `DevSettingsOverlay`
- [ ] Verification
    - [ ] Run automated tests
    - [ ] Perform manual switching tests
