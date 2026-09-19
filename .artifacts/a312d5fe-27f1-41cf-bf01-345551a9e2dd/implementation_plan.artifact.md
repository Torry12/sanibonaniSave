# Plan: Complete Domain Event Handlers and Tests

This plan involves expanding the `AuditLogEventHandler` to cover all relevant domain events and adding comprehensive unit tests for both `NotificationEventHandler` and `AuditLogEventHandler`.

## Proposed Changes

### Domain Layer

#### [MODIFY] [AuditLogRepository.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/repository/AuditLogRepository.kt)
- Add specialized logging methods for domain events:
    - `logLoanRequest(loan: Loan)`
    - `logLoanApproval(loan: Loan)`
    - `logPayoutRequest(payout: PayoutRequest)`
    - `logClaimSubmission(claim: BeneficiaryPayoutClaim)`
    - `logMemberStatusChange(member: Member, oldStatus: MemberStatus, newStatus: MemberStatus)`

#### [MODIFY] [AuditLogEventHandler.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/event/AuditLogEventHandler.kt)
- Expand the `handle` method to support:
    - `LoanRequestedEvent`
    - `LoanApprovedEvent`
    - `PayoutRequestedEvent`
    - `ClaimSubmittedEvent`
    - `MemberStatusChangedEvent`

### Data Layer

#### [MODIFY] [AuditLogRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/AuditLogRepositoryImpl.kt)
- Implement the new methods in `AuditLogRepository`.
- Each method will construct an `AuditLog` entry with a specific `action` string and a JSON `details` object containing relevant event data.

### Test Layer

#### [MODIFY] [EventHandlerTest.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/test/java/com/sanibonani/save/domain/event/EventHandlerTest.kt)
- Add test cases for `NotificationEventHandler`:
    - `LedgerEntryCreatedEvent`
    - `LoanRequestedEvent`
    - `LoanApprovedEvent`
    - `PayoutRequestedEvent`
    - `ClaimSubmittedEvent`
    - `MemberStatusChangedEvent`
- Add test cases for `AuditLogEventHandler`:
    - `PaymentProcessedEvent`
    - `LoanRequestedEvent`
    - `LoanApprovedEvent`
    - `PayoutRequestedEvent`
    - `ClaimSubmittedEvent`
    - `MemberStatusChangedEvent`

## Verification Plan

### Automated Tests
- Run `EventHandlerTest` using Gradle:
  ```powershell
  ./gradlew.bat :domain:test --tests "com.sanibonani.save.domain.event.EventHandlerTest"
  ```
- Verify that all tests pass and coverage is increased for event handlers.
