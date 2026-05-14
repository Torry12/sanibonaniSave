# SanibonaniSave Deep Analysis (App Structure + Business Logic)

Date: 2026-05-14

## Scope reviewed

- Module boundaries and dependency direction in:
  - `settings.gradle.kts`
  - `app/build.gradle.kts`
  - `domain/build.gradle.kts`
  - `data/build.gradle.kts`
- Core business flow implementation in:
  - `app/src/main/java/com/sanibonani/save/viewmodel/GroupViewModel.kt`
  - `app/src/main/java/com/sanibonani/save/ui/screens/group/GroupScreens.kt`
  - `domain/src/main/java/com/sanibonani/save/domain/usecase/groups/GetGroupBusinessInsightsUseCase.kt`
  - `domain/src/main/java/com/sanibonani/save/domain/usecase/CalculateViabilityUseCase.kt`
  - `domain/src/main/java/com/sanibonani/save/domain/usecase/ApplyViabilityPlanUseCase.kt`
- Migration health and schema-evolution resilience in:
  - `app/src/main/java/com/sanibonani/save/data/local/Migrations.kt`

## Current architecture snapshot

- Multi-module setup exists (`:app`, `:domain`, `:data`) and is structurally good.
- `:app` currently depends on both `:domain` and `:data` directly.
- `:data` depends on `:domain` (expected).
- `:domain` includes Android/Hilt/Supabase concerns, so it behaves as a mixed domain+platform module rather than pure domain.

## Business logic assessment

### Strengths

- ViewModel state handling uses `StateFlow` and `update { copy(...) }` patterns.
- Use-case layer exists for key business operations (group creation, insights, actuarial viability).
- Group-type specialization is explicit and readable in `GetGroupBusinessInsightsUseCase`.
- Error mapping extension (`toUserMessage`) is already used in major ViewModel paths.

### Risks / opportunities

1. Collector lifecycle risk in group listing flow
   - `GroupViewModel.loadGroups()` can be called multiple times and previously created a new collector each call.
   - Risk: duplicate collectors, repeated emissions, unnecessary network and geocoding churn.

2. Background geocoding overlap risk
   - Batch geocode work could overlap between successive list refreshes.
   - Risk: duplicated API calls and noisy writeback cycles.

3. UI error message hygiene
   - Constitution upload UI surfaced raw exception messages to users.
   - Risk: inconsistent UX and exposure of technical internals.

4. Migration file maintainability
   - Repeated local `safeExec` helpers and unused catch variables increased warning noise and reduced clarity.

## Improvements applied in this pass

### 1) Flow and job lifecycle hardening

Updated `GroupViewModel`:

- Added `loadGroupsJob` and cancel-before-reload behavior.
- Switched group list collection to `collectLatest`.
- Added `geocodeBatchJob` and cancel-before-new-batch behavior.

Why this matters:
- Prevents concurrent long-lived collectors from stacking.
- Reduces duplicate geocoding/API work during refresh-triggered reloads.

### 2) UI-safe error messaging

Updated `GroupScreens` constitution upload step:

- Replaced raw exception toast content with user-friendly message.

Why this matters:
- Aligns with product rule: no raw exception messages in UI.

### 3) Migration warning and duplication cleanup

Updated `Migrations.kt`:

- Replaced unused `catch (e: Exception)` with `catch (_: Exception)`.
- Consolidated repeated local `safeExec` functions to the top-level `db.safeExec(...)` helper in:
  - `MIGRATION_31_32`
  - `MIGRATION_32_33`
  - `MIGRATION_33_34`
  - `MIGRATION_35_36`

Why this matters:
- Removes warning noise.
- Keeps migration behavior consistent and easier to reason about.

## Strategic architecture recommendations (next phase)

1. Enforce strict layering
   - Keep `:domain` pure Kotlin (models, rules, ports/use-cases only).
   - Move Android/Hilt/Supabase dependencies fully into `:data` and `:app` wiring.

2. Reduce business logic pressure in large ViewModels
   - `GroupViewModel` handles listing, geocoding, form flow, payment-finalization, and uploads.
   - Split into focused use-cases/interactors for:
     - Geocoding orchestration
     - Registration workflow state machine
     - Constitution upload coordination

3. Strengthen workflow robustness
   - Add idempotency around activation + constitution upload finalization.
   - Record workflow stage checkpoints for crash/restart safety.

4. Add regression tests for collector/job behavior
   - Verify `loadGroups()` does not create duplicate collectors after repeated calls.
   - Verify geocode batch cancellation when new list arrives.

## Suggested execution order

1. Run focused tests for GroupViewModel and migration compilation checks.
2. Introduce a `GroupRegistrationCoordinator` use-case with explicit state transitions.
3. Move geocoding into domain-facing interface + data implementation.
4. Start domain purity refactor by removing Android/Hilt from `:domain`.

## Expected impact

- Better runtime stability under repeated refresh and navigation events.
- Cleaner user-facing error behavior.
- Lower migration maintenance overhead and warning count.
- Clear roadmap for scaling business logic without monolithic ViewModel growth.

## Addendum: Latest logic hardening updates (May 14, 2026)

The following additional improvements were applied after the initial deep-analysis pass:

- Domain use-case consistency and safety
  - Standardized multiple use-cases to `runCatching`-first error handling.
  - Added max-member enforcement in `RegisterMemberUseCase`.
  - Added transition guardrails in `UpdateMemberStatusUseCase`.
  - Added input guards in `CalculateViabilityUseCase`.

- Financial and claim validation alignment
  - Enforced South African banking validation as 7-11 digit account numbers and 6-digit branch codes across domain and app paths.
  - Wired `requestedAmount` into loan eligibility checks so group max-loan caps are actually enforced at request time.
  - Added pre-submit beneficiary-claim banking validation in `MemberViewModel`.
  - Tightened registration/settings payment due-day handling to `1..28` for calendar-safe monthly scheduling.

- Persistence and behavior correctness fixes
  - Emergency withdrawal flow now persists group balance updates via repository instead of returning computed values only.
  - Burial-claim eligibility now blocks `pending_payment` members and has expanded accidental/suicide rule matching.
  - Stokvel payout-cycle logic now derives cycle boundaries from `group.createdAt` instead of hardcoded year-end assumptions.

- Shared logic extraction and duplication removal
  - Introduced `RoscaRotationUtils.sortRoscaParticipants(...)` as a single source of truth.
  - Updated both actuarial and ROSCA rotation use-cases to consume shared rotation ordering logic.

- UI/ViewModel reliability improvements
  - Replaced remaining raw throwable message usage in admin/member-facing flows with `toUserMessage()`.
  - Fixed admin document verification rollback path to actually restore fresh member state on failure.
  - Removed cancellation-swallowing outer `try/catch` in payment processing flow to preserve structured coroutine cancellation behavior.

Operational note:
- No schema-breaking database migration was required for these updates; behavior is primarily domain/viewmodel rule hardening and validation alignment.

