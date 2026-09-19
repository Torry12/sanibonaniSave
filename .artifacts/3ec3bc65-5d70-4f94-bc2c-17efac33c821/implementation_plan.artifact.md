# Payout System Refactor & High-Integrity Guards

Refactor the payout request flow to include a centralized validation layer (`PayoutGuard`) that enforces strict financial and behavioral rules before allowing a disbursement request to be recorded.

## User Review Required

> [!IMPORTANT]
> The new guards will block payout requests if:
> 1. The group is suspended by the platform.
> 2. The member is in a probation period (Burial Society/Stokvel).
> 3. The group balance is insufficient (Solvency check).
> 4. There is an existing pending payout for the same entity to prevent double-dipping.

## Proposed Changes

### Domain Layer

#### [NEW] [PayoutExceptions.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/model/PayoutExceptions.kt)
Define specific exception types for payout failures:
- `InsufficientFundsException`: Group balance < requested amount.
- `MemberNotEligibleException`: Member in probation or suspended.
- `DuplicatePayoutRequestException`: Existing pending request.
- `GroupSuspendedException`: Platform-level group suspension.

#### [NEW] [PayoutGuard.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/service/PayoutGuard.kt)
Interface and implementation for payout validation:
- `validateRequest(group, member, amount, type)`
- Performs checks against group status, member probation, and treasury liquidity.

#### [MODIFY] [RequestPayoutUseCase.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/usecase/financial/RequestPayoutUseCase.kt)
- Integrate `PayoutGuard`.
- Use a `financialResult` block (if available) or standard `runCatching` with specific error mapping.
- Ensure all business logic for "unqualified" requests is handled via the Guard.

#### [MODIFY] [ExecuteRoscaPayoutUseCase.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/usecase/financial/ExecuteRoscaPayoutUseCase.kt)
- Align validation logic with the new `PayoutGuard` principles (already has some good logic, but needs standardization).

---

### Data Layer

#### [MODIFY] [PayoutRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/financial/PayoutRepositoryImpl.kt)
- Add a check for existing pending payouts before proceeding with `remoteSource.requestPayout`.

## Verification Plan

### Automated Tests
- `RequestPayoutUseCaseTest.kt`: Add cases for blocked payouts (probation, insolvency, duplicate).
- `PayoutGuardTest.kt`: Unit tests for each validation rule.

### Manual Verification
- Attempt to request a payout for a member in probation and verify the error message in the UI.
- Attempt to request a payout exceeding group balance and verify it is blocked.
