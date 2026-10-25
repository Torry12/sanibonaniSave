# Task: Refactor and Harden Project Structure

- `[ ]` Finalize Common Infrastructure
    - `[ ]` Fix `BaseViewModel.kt` imports
    - `[ ]` Fix `UIEvent.kt` content
- `[ ]` Global Import Refactor
    - `[ ]` Update all `BaseViewModel` imports
    - `[ ]` Update all `UIEvent` imports
    - `[ ]` Update all Repository imports (admin, auth, financial, group, member, system)
    - `[ ]` Update all UseCase imports
- `[ ]` Persistence Layer Alignment
    - `[ ]` Harden `SanibonaniDatabase.kt`
    - `[ ]` Update `RepoModule.kt` bindings
- `[ ]` Navigation Hardening
    - `[ ]` Fix `NavGraph.kt` types and imports
- `[ ]` Final Verification
    - `[ ]` Build `:domain`, `:data`, and `:app`
