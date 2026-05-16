# AGENTS.md

## Fast Boot (Read First)
- Product: Android savings-group platform with Supabase backend and offline-first Room cache.
- Module boundaries: `:app` (UI/DI/workers), `:domain` (models + interfaces + use cases), `:data` (Room + Supabase implementations) in `settings.gradle.kts`.
- Main flow: repositories emit Room first, sync network second, then UI updates from Room (`data/src/main/java/com/sanibonani/save/data/repository/BaseRepository.kt`).
- DI rule: bind interfaces in `app/src/main/java/com/sanibonani/save/di/RepoModule.kt`; avoid concrete repo dependencies in ViewModels/use cases.
- Supabase wiring lives in `app/src/main/java/com/sanibonani/save/di/AppModule.kt` (Auth/Postgrest/Storage/Realtime/Functions + shared JSON).
- Route/role enforcement is centralized in `app/src/main/java/com/sanibonani/save/ui/navigation/NavGraph.kt`.
- Fee lifecycle is dual-path: worker (`app/src/main/java/com/sanibonani/save/worker/FeeEnforcementWorker.kt`) + edge/backend enforcement.

## Code Patterns To Follow
- ViewModels: `StateFlow` + `_state.update { it.copy(...) }` (see `app/src/main/java/com/sanibonani/save/viewmodel/PaymentViewModel.kt`).
- Models: `@Serializable` + `@Parcelize` + `@SerialName("snake_case")` (see `domain/src/main/java/com/sanibonani/save/domain/model/Models.kt`).
- UI-safe errors: always map exceptions via `toUserMessage()` (`domain/src/main/java/com/sanibonani/save/data/utils/SafeResultExtensions.kt`).
- Supabase writes should be idempotent where duplicates are possible (prefer upsert/conflict-safe patterns).
- Room schema changes must be reflected in `app/src/main/java/com/sanibonani/save/data/local/Migrations.kt` and `ALL_MIGRATIONS`.

## Critical Commands (PowerShell)
- Debug build: `./gradlew.bat :app:assembleDebug`
- JVM tests: `./gradlew.bat test`
- Instrumentation tests: `./gradlew.bat :app:connectedDebugAndroidTest`
- Nav-graph transition checks: `./run-navgraph-role-transition-test.ps1 -CheckOnly`
- Device build/install/launch helper: `./scripts/run-android-debug.ps1`

## Safe-Change Checklist (Migrations / RLS / Seed)
- DB migration edits: keep SQL in `supabase/migrations/` aligned with top-level scripts under `supabase/`.
- Room migration edits: bump DB version and wire migration through `ALL_MIGRATIONS`; do not rely on release destructive migration.
- RLS edits: validate role paths used by `NavGraph.kt` and repository filters before merging.
- Seed edits: preserve idempotence markers (`SEED-%`) and targeted cleanup behavior (see `supabase/11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql`).
- Edge-function contract edits: keep app callers in `data/src/main/java/com/sanibonani/save/data/remote/EdgeFunctionGateway.kt` compatible with payload/response shape.
- For fresh backend setup, apply SQL in documented order from `README.md` (`01_DATABASE_SCHEMA.sql` -> `03_PLATFORM_ADMIN_SETUP.sql` -> alignment/hotfix scripts).

## Environment Assumptions
- Required local secrets come from `local.properties` (see `app/build.gradle.kts`).
- `WHATSAPP_TOKEN` stays server-side as Edge Function secret (not in app `BuildConfig`).
- `google-services.json` is required under `app/` for Firebase-enabled builds.

