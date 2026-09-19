# Live Simulation Tests Implementation Plan

This plan outlines the steps to execute a comprehensive suite of live simulation tests for the SanibonaniSave platform, covering database stress tests, JVM-based historical simulations, and environment health diagnostics.

## User Review Required

> [!IMPORTANT]
> The simulations involve real database interactions.
> 1. `live_simulation_v2.sql` runs against the **local Supabase** instance and performs cleanup automatically.
> 2. `LiveSimulationJVMRunner.kt` runs against the **remote Supabase** instance (`aszmodkqpafwtnfufmow`) using pre-configured test accounts. It creates temporary data for validation.
> 3. `runtime-signal-unified.ps1` probes the current environment for readiness.

## Proposed Changes

### 1. Database Stress Simulation

#### [EXECUTE] `scripts/live_simulation_v2.sql`
- **Goal**: Verify financial integrity, ledger partitioning, and equity logic in the PostgreSQL layer.
- **Method**: Run via `supabase db execute` or `psql` against the local database.
- **Success Criteria**: All 5 internal verification checks pass with notice "STRESS TEST COMPLETE".

### 2. JVM Historical Simulation

#### [EXECUTE] `LiveSimulationJVMRunner.kt`
- **Goal**: Simulate long-term (6-month) endurance, behavior scoring, and automated debt recovery logic.
- **Method**: Run via Gradle: `./gradlew.bat test --tests *LiveSimulationJVMRunner`.
- **Success Criteria**: Both `runDeepHistoricalActivitySimulation` and `runForcedSettlementSimulation` pass.

### 3. Unified Diagnostic Probe

#### [EXECUTE] `scripts/runtime-signal-unified.ps1`
- **Goal**: Ensure the development environment and CI/CD signals are GREEN.
- **Method**: Run via PowerShell: `./scripts/runtime-signal-unified.ps1 -CheckLocalFunction -Debug`.
- **Success Criteria**: Signal returned is GREEN.

## Verification Plan

### Automated Tests
- Gradle test reports for JVM simulations.
- SQL notices/logs for the database stress test.
- JSON report output from the runtime signal probe.

### Manual Verification
- Reviewing the `SIMULATION_RESULTS.md` or similar updated logs if the scripts update them (some might).
- Checking the local Supabase studio for any left-over data in case cleanup fails.
