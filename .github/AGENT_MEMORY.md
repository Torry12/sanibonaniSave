# Agent Memory - SanibonaniSave

## Last Updated
2026-05-14

## Recent Decisions
- Standardized banking validation to SA formats:
  - Account: 7..11 digits
  - Branch: 6 digits
- Standardized `paymentDueDay` to 1..28 across app/domain/docs/SQL
- Loan eligibility now evaluates requested amount against group max loan
- Beneficiary claim submission now validates banking fields pre-submit
- Payment flow removed broad `try/catch` to avoid swallowing coroutine cancellation

## Domain Reliability Improvements Applied
- `RegisterMemberUseCase`: group capacity guard + `runCatching`
- `ProcessEmergencyWithdrawalUseCase`: persist balance update via repository
- `ValidateBurialClaimEligibilityUseCase`: pending-payment exclusion + richer cause matching
- `UpdateMemberStatusUseCase`: explicit transition guard map
- `CalculateViabilityUseCase`: strict positive input checks

## Shared Logic Consolidation
- ROSCA participant ordering moved to `RoscaRotationUtils.sortRoscaParticipants(...)`
- Consumers aligned in actuarial and ROSCA use-cases

## Documentation/SQL Sync Rules
When business rules change:
1. Update domain/use-case and ViewModel callers
2. Update `ValidationUtils.kt`
3. Update SQL baseline/migration scripts in `supabase/`
4. Update verification SQL (`26_VERIFY_FULL_APP_E2E.sql`)
5. Update docs (`README.md`, deep-analysis doc, doc index)

## Open Follow-Ups
- Confirm legacy DB instances have executed `29_ALIGN_VALIDATION_CONSTRAINTS_WITH_APP.sql`
- Optionally tighten older schema comments that still mention `1..31`
- Consider centralizing all bank-validation regexes in one shared source for SQL + Kotlin generation

