# Agent Context - SanibonaniSave

## Purpose
This file gives coding agents fast, high-signal project context so edits stay consistent with architecture and business rules.

## Project Snapshot
- Product: South African savings-group platform (burial society, stokvel, ROSCA, investment club, emergency fund)
- Stack: Kotlin, Jetpack Compose, Hilt, StateFlow, Supabase, Room
- Modules: `:app`, `:domain`, `:data`

## Non-Negotiable Coding Rules
- Use `StateFlow`/`SharedFlow` (no `LiveData`)
- Use `@HiltViewModel` + constructor injection
- Use `viewModelScope.launch` for ViewModel coroutines
- Use `runCatching`/`Result<T>` for domain operations
- Keep business logic out of composables
- Use `Throwable.toUserMessage()` for UI-safe errors

## Current Business Invariants
- Payment due day: `1..28`
- SA bank account format: `7..11` digits
- SA branch code format: `6` digits
- Loan requests must enforce `loanMaxAmount` when configured
- Member status transitions must follow explicit allowed-transition map

## SQL Alignment
- Schema baseline: `supabase/01_DATABASE_SCHEMA.sql`
- Existing-env constraint aligner: `supabase/29_ALIGN_VALIDATION_CONSTRAINTS_WITH_APP.sql`
- E2E verification pack: `supabase/25_SEED_FULL_APP_E2E.sql` + `supabase/26_VERIFY_FULL_APP_E2E.sql`

## High-Value Files For Logic Changes
- `domain/src/main/java/com/sanibonani/save/domain/usecase/`
- `domain/src/main/java/com/sanibonani/save/domain/validation/ValidationUtils.kt`
- `app/src/main/java/com/sanibonani/save/viewmodel/`
- `supabase/` SQL schema/migration/verification scripts

## Review Checklist For Agents
1. Are rules enforced in both domain and UI entry points?
2. Are all user-facing errors friendly and actionable?
3. Are cancellation semantics preserved in coroutines?
4. Are SQL constraints and verification scripts updated when app rules change?
5. Did we avoid introducing business logic inside composables?

