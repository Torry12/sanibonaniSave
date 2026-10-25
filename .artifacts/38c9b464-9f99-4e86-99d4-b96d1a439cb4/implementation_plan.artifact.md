# Refactor Payout System and Improve Weaknesses

This plan outlines the refactoring of the payout and disbursement system to improve security, auditability, and reliability across all group types.

## User Review Required

> [!IMPORTANT]
> The refactoring will change how payout status updates are handled. All status transitions will now be audited, and a stricter state machine will be enforced.

## Proposed Changes

### 1. Model Enhancements
Add metadata support to `PayoutRequest` to allow group-type-specific details (e.g., ROSCA cycle index, Stokvel maturity date) and improve auditing.

#### [MODIFY] [PayoutModels.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/model/PayoutModels.kt)
- Add `metadata: JsonObject?` to `PayoutRequest`.
- Add `PayoutStatus.REJECTED` for clearer termination state.

### 2. Repository & Security Hardening
Improve the reliability and auditability of the `PayoutRepository`.

#### [MODIFY] [PayoutRepository.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/repository/financial/PayoutRepository.kt)
- Update signatures to support metadata and auditing.

#### [MODIFY] [PayoutRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/financial/PayoutRepositoryImpl.kt)
- Ensure ALL status changes are logged to the `AuditLogRepository`.
- Consolidate event emission to avoid duplicates.
- Use `safeApiCall` for status updates to support offline queuing.

### 3. Use Case Refactoring
Refactor the core logic for processing disbursements to enforce security and the state machine.

#### [MODIFY] [ProcessDisbursementUseCase.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/usecase/financial/ProcessDisbursementUseCase.kt)
- Enforce state transitions: `PENDING` -> `GROUP_APPROVED` -> `PROCESSING` -> `COMPLETED`.
- Ensure `FinancialSecurityService` is consistently applied.
- Handle `REJECTED` and `CANCELLED` states with appropriate reasons.

#### [MODIFY] [RequestPayoutUseCase.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/usecase/financial/RequestPayoutUseCase.kt)
- Update to support the new metadata field.

### 4. Cleanup & Consolidation
#### [MODIFY] [DisbursementBroadcastHandler.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/event/DisbursementBroadcastHandler.kt)
- Ensure it handles all relevant disbursement events correctly.

## Verification Plan

### Automated Tests
- Run `PayoutRepositoryImplTest.kt` to ensure basic CRUD and sync work.
- Run `ProcessDisbursementUseCaseTest.kt` to verify MFA and liquidity guards.
- Create a new integration test for the state machine transitions.

### Manual Verification
- Simulate a payout request and process it through the Admin UI.
- Verify that high-value payouts (>= R10,000) trigger the MFA requirement in the UI (mocking the MFA status).
- Check the `audit_logs` table in Supabase to ensure all status transitions are recorded.
