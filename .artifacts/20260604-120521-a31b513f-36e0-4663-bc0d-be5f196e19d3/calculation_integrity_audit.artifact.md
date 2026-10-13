# Calculation Integrity Audit & Hardening Plan

This document outlines critical issues found during the audit of financial and actuarial calculations and the proposed fixes to ensure platform reliability.

## Identified Integrity Issues

### 1. Contribution Type Pollution
- **Problem**: `PaymentCalculator.calculateStatus` and `CalculateGroupHealthScoreUseCase` sum all contribution records regardless of `type`.
- **Impact**: Joining fees, late fees, and platform fees are incorrectly credited towards a member's monthly contribution target, leading to false "fully paid" statuses and under-reporting of debt.
- **Fix**: Filter contributions by `type == "contribution"` in all "savings" and "shortfall" calculations.

### 2. Member Savings Inflation
- **Problem**: The `record_contribution_v1` SQL RPC increments `member.total_paid` for all contribution types, including platform fees (`member_fee`).
- **Impact**: Members appear to have higher individual savings than they actually do.
- **Fix**: Update the RPC to only increment `total_paid` and `total_contributions` if `type != 'member_fee'`.

### 3. Balance Race Conditions
- **Problem**: `MemberRepositoryImpl.recordContribution` manually increments the local `Group.balance` cache after the RPC returns.
- **Impact**: Overwrites Realtime balance updates with potentially stale data if another transaction occurred concurrently.
- **Fix**: Change `record_contribution_v1` to return the updated group balance and use that returned value to update the local cache, OR remove the manual local increment and rely on the existing Realtime observer.

### 4. Fraud Score Inflation
- **Problem**: `BehaviorTrackingRepositoryImpl` counts duplicate transactions twice (A counts B, then B counts A).
- **Impact**: Fraud scores are artificially high for accidental double-taps.
- **Fix**: Update the duplicate detection loop to only count unique duplicate pairs or use a set-based comparison.

### 5. Fragile Date Logic
- **Problem**: Hardcoded `substringBefore("-")` and direct `LocalDate.parse` on strings that might be malformed or empty.
- **Impact**: Crashes or incorrect grouping of monthly data.
- **Fix**: Use `parseInstantOrNull` helper or wrap in `try-catch` with safe defaults.

## Proposed Changes

### Database (Supabase)
- Modify `record_contribution_v1` in `02_FUNCTIONS_AND_TRIGGERS.sql`:
    - Add logic to return the new balance.
    - Conditionally update `member.total_paid`.

### Domain Logic
- Update `PaymentCalculator.kt`:
    - Filter contributions by `type == "contribution"` in `calculateStatus`.
- Update `CalculateGroupHealthScoreUseCase.kt`:
    - Sanitize date strings.
    - Filter by `type` for incoming/outgoing funds.

### Data Layer
- Update `MemberRepositoryImpl.kt`:
    - Sync balance update logic with the new RPC return value.
- Update `BehaviorTrackingRepositoryImpl.kt`:
    - Fix duplicate detection logic.

## Verification Plan
1. **Unit Tests**: Add tests to `PaymentCalculatorTest` with mixed contribution types.
2. **Integration Tests**: Verify `record_contribution_v1` with a platform fee doesn't increase member savings.
3. **Manual Verification**: Perform a payment with a joining fee and verify shortfall is calculated correctly.
