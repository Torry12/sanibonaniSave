# Task: Code Cleanup and Financial Precision Hardening

- `[x]` **Phase 1: Repository & Data Layer Cleanup**
    - `[x]` Remove `activateGroup` from `GroupRepository`
    - `[x]` Remove `activateGroup` and unused `platformRepo` from `GroupRepositoryImpl`
    - `[x]` Update `MemberRepositoryImpl.kt` RPC parameters (BigDecimal)
    - `[x]` Update `LoanRepositoryImpl.kt` RPC parameters (BigDecimal)
    - `[x]` Update `PayoutRepositoryImpl.kt` RPC parameters (BigDecimal)
- `[x]` **Phase 2: Use Case Refinement**
    - `[x]` Cleanup `ProcessPaymentUseCase.kt` (Remove notif repo, fix allocations)
    - `[x]` Cleanup `ProcessPayoutUseCase.kt` (Remove notif repo)
    - `[x]` Cleanup `ProcessBurialClaimUseCase.kt` (Remove notif repo)
- `[x]` **Phase 3: Application Layer Migration**
    - `[x]` Refactor `GroupRegistrationViewModel.kt` to use `ActivateGroupUseCase`
    - `[x]` Refactor `LiveSimulationRunner.kt` to use `ActivateGroupUseCase`
- `[x]` **Phase 4: Utility Cleanup & Final Verification**
    - `[x]` Cleanup `PaymentCalculator.kt` unused parameters
    - `[ ]` Run validation tests
