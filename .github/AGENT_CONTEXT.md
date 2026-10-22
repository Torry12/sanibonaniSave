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
- Canonical sources: `supabase/01_DATABASE_SCHEMA.sql` (tables) and `supabase/02_FUNCTIONS_AND_TRIGGERS.sql` (RPCs and triggers); `03_SECURITY_AND_RLS.sql` for policies and grants.
- Generated copies (never hand-edit): `supabase/migrations/00000000000000_GROUND_ZERO.sql`, `supabase/CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql`, `supabase/online/000_CONSOLIDATED_ONLINE_SYNC.sql`, `supabase/SEED_ALL_DATA.sql`; regenerate with the four `supabase/scripts/generate_*.ps1` scripts in README order.
- Existing environments: add a new dated `supabase/online/SYNC_DELTA_<date>.sql` with a read-only pre-flight and expected body fingerprints. `20261014`, `20261021`, and `20261022` are applied on both tiers and must never be rewritten.
- Balance invariant: `groups.balance` is `NUMERIC(12,2) NOT NULL DEFAULT 0`, derived from `group_ledger`; spend guards use `COALESCE(v_group.balance, 0)` and raise on insufficiency.

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

