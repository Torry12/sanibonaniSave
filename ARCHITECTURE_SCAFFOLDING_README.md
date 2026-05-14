# Architecture Scaffolding Pass (1,2,3,4)

This pass implements four additive foundations without changing core business execution paths.

## Implemented

1. **Read-only API contracts from blueprint**
   - `domain/src/main/java/com/sanibonani/save/domain/architecture/api/ArchitectureReadApiContracts.kt`
   - `supabase/functions/architecture-read/index.ts` (edge endpoint handlers)
   - `supabase/functions/architecture-read/blueprint.ts` (endpoint source dataset)
   - `supabase/functions/architecture-read/README.md` (deployment + invocation)
2. **Event schema registry docs for outbox/realtime**
   - `docs/event-schemas/README.md`
   - `docs/event-schemas/event-envelope.schema.json`
   - `docs/event-schemas/core-events.registry.json`
3. **DB schema template drafts**
   - `supabase/28_ARCHITECTURE_MODEL_SCHEMA_TEMPLATES.sql`
4. **Policy router contract + reference implementation + tests**
   - `domain/src/main/java/com/sanibonani/save/domain/architecture/policy/PolicyRouter.kt`
   - `domain/src/test/java/com/sanibonani/save/domain/architecture/policy/PolicyRouterTest.kt`

## Test harness

- `domain/src/test/java/com/sanibonani/save/domain/architecture/api/ArchitectureReadApiContractsTest.kt`
- Existing blueprint tests remain valid.

## Quick verify

```powershell
Set-Location "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
.\gradlew.bat :domain:testDebugUnitTest --tests "com.sanibonani.save.domain.architecture.*" --console=plain
```

## Notes

- The SQL file is intentionally a template draft for staged rollout.
- Existing APIs, repositories, and payment flows are untouched.
- `PolicyRouter` is currently domain-only and not yet wired into runtime command handlers.

