# Agent Context - SanibonaniSave

## Purpose
This file gives coding agents fast, high-signal project context so edits stay consistent with architecture and business rules.

## Project Snapshot
- Product: South African savings-group platform (burial society, stokvel, ROSCA, investment club, emergency fund)
- Stack: Kotlin 2.4.21, Jetpack Compose, Hilt, StateFlow, Supabase, Room (schema v82)
- Modules: `:app`, `:domain`, `:data`, `:simulation`
- Boundary: `:domain` imports neither `android.*` nor the Supabase SDK; models are `@Serializable` only and navigation passes ids.

## Non-Negotiable Coding Rules
- Use `StateFlow`/`SharedFlow` (no `LiveData`)
- Use `@HiltViewModel` + constructor injection
- Use `viewModelScope.launch` for ViewModel coroutines
- Use `runCatching`/`Result<T>` for domain operations
- Use `Money` (BigDecimal) for every currency value; never `Double`
- Keep business logic out of composables
- Use `Throwable.toUserMessage()` for UI-safe errors

## Current Business Invariants
- Payment due day: `1..28`
- SA bank account format: `7..13` digits per bank (`BankAccountValidation.ACCOUNT_NUMBER_LENGTHS`)
- SA branch code format: `6` digits
- Loan requests are capped by `ValidateLoanEligibilityUseCase` at 30% of the member's trailing-six-month contributions, less their active loan total, and `termMonths > 3` is rejected. `group.loanMaxAmount` is the per-loan ceiling: a positive cap tightens the 30% rule, while zero/null means "not configured". Both `RequestLoanUseCase` (before pricing) and `ProcessLoanDecisionUseCase` (before approval) call the same validator, so an over-cap request is refused at submission rather than queued.
- Member status transitions must follow explicit allowed-transition map
- `groups.balance` is only ever written by an RPC that also appends to `group_ledger`

## SQL Alignment
- Canonical sources: `supabase/01_DATABASE_SCHEMA.sql` (tables) and `supabase/02_FUNCTIONS_AND_TRIGGERS.sql` (RPCs and triggers); `03_SECURITY_AND_RLS.sql` for policies and grants.
- Generated copies (never hand-edit): `supabase/migrations/00000000000000_GROUND_ZERO.sql`, `supabase/CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql`, `supabase/online/000_CONSOLIDATED_ONLINE_SYNC.sql`, `supabase/SEED_ALL_DATA.sql`; regenerate with the four `supabase/scripts/generate_*.ps1` scripts in README order.
- Existing environments: add a new dated `supabase/online/SYNC_DELTA_<date>.sql` with a read-only pre-flight and expected body fingerprints. `20261014` through `20261028` are applied on Local; **Cloud still owes `20261027` and `20261028`**. `20261029` (money-RPC `[SEC_002]` caller guards plus the anonymous execute revoke) is cut and applied on neither tier. Never rewrite an applied delta.
- Balance invariant: `groups.balance` is `NUMERIC(12,2) NOT NULL DEFAULT 0`, derived from `group_ledger`; spend guards use `COALESCE(v_group.balance, 0)` and raise on insufficiency.
- RPC raise codes: `[FIN_001]` liquidity, `[FIN_002]` state precondition, `[FIN_003]` input validation, `[SEC_001]` MFA, `[SEC_002]` entitlement, `[SYS_001]` row not found, `[SYS_002]` transaction-id reuse.

## High-Value Files For Logic Changes
- `domain/src/main/java/com/sanibonani/save/domain/usecase/`
- `domain/src/main/java/com/sanibonani/save/domain/validation/InputValidator.kt` (the rule owner; `ValidationUtils.kt` only forwards)
- `app/src/main/java/com/sanibonani/save/features/<pillar>/presentation/` (ViewModels are organised by feature/pillar, not a flat `viewmodel/` package)
- `supabase/` SQL schema/migration/verification scripts

## Review Checklist For Agents
1. Are rules enforced in both domain and UI entry points?
2. Are all user-facing errors friendly and actionable?
3. Are cancellation semantics preserved in coroutines?
4. Are SQL constraints and verification scripts updated when app rules change?
5. Did we avoid introducing business logic inside composables?

